package com.squeve.redail.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.squeve.redail.MainActivity
import com.squeve.redail.R
import com.squeve.redail.data.*
import com.squeve.redail.engine.*
import com.squeve.redail.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar

class RedialService : Service() {

    companion object {
        const val ACTION_START = "com.squeve.redail.START"
        const val ACTION_PAUSE = "com.squeve.redail.PAUSE"
        const val ACTION_RESUME = "com.squeve.redail.RESUME"
        const val ACTION_STOP = "com.squeve.redail.STOP"
        const val ACTION_HOLD = "com.squeve.redail.HOLD"      // cancel the auto hang-up for this call
        const val ACTION_REARM = "com.squeve.redail.REARM"    // start the auto hang-up countdown again
        const val ACTION_UNMUTE = "com.squeve.redail.UNMUTE"  // open the microphone for this call
        private const val CHANNEL = "redail"
        private const val DONE_CHANNEL = "redail_done"
        private const val NOTIF_ID = 1
        private const val DONE_ID = 2

        /** Hand-off from RunLauncher. */
        @Volatile var pendingQueue: List<RedialJob> = emptyList()
        @Volatile var pendingOrder: CallOrder = CallOrder.SEQUENTIAL
        @Volatile var pendingSimSlot: Int = -1
        @Volatile var pendingDry: Boolean = false
        @Volatile var engine: RedialEngine? = null

        /** Live state for the UI. */
        val status = MutableStateFlow<EngineState>(EngineState.Idle)
        val paused = MutableStateFlow(false)
        val waitReason = MutableStateFlow<String?>(null)
        val dry = MutableStateFlow(false)

        /** Numbers the user has put on hold during the current run. */
        val held = MutableStateFlow<Set<String>>(emptySet())

        fun setHeld(number: String, hold: Boolean) {
            held.value = if (hold) held.value + number else held.value - number
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlay: OverlayController? = null

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(CHANNEL, "Redial", NotificationManager.IMPORTANCE_LOW))
        mgr.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, "Run finished", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, notification(status.value))
                // Only ever one engine running. A second Start must not spawn a second dialer.
                if (engine?.isRunning != true) beginRun()
            }
            ACTION_PAUSE -> engine?.pause()
            ACTION_RESUME -> engine?.resume()
            ACTION_HOLD -> engine?.holdCall()
            ACTION_REARM -> engine?.rearmTimer()
            ACTION_UNMUTE -> engine?.releaseMic()
            ACTION_STOP -> {
                engine?.stop()
                EventLog.add("Stopped by you")
                RunStore.clearCheckpoint(applicationContext)
                status.value = EngineState.Idle
                paused.value = false
                waitReason.value = null
                overlay?.hide()
                stopSelf()
            }
        }
        if (action != ACTION_START && engine?.isRunning != true) stopSelf()
        return START_NOT_STICKY
    }

    private fun beginRun() {
        val app = applicationContext
        val prefs = app.getSharedPreferences("redail", Context.MODE_PRIVATE)
        val queue = pendingQueue
        val isDry = pendingDry
        val slot = pendingSimSlot
        val order = pendingOrder
        val plan = RunPlan(queue, order, slot, isDry)

        PhoneStateMonitor.init(app)
        val sims = if (isDry) emptyList() else runCatching { listSims(app) }.getOrDefault(emptyList())
        val simMode = prefs.getInt("simMode", 0)
        val names = NameStore.load(prefs)

        EventLog.clear()
        EventLog.add(if (isDry) "Dry run started: no real calls" else "Run started · ${queue.size} numbers")
        dry.value = isDry
        held.value = emptySet()
        RunStore.clearCheckpoint(app)

        val e = RedialEngine(
            dialers = buildDialers(sims, slot, simMode),
            phoneBusy = PhoneStateMonitor.offhook,
            phoneRinging = PhoneStateMonitor.ringing,
            callLog = CallLogDurationReader(this),
            audio = AndroidAudioControl(this),
            onResult = { r -> recordResult(r, isDry, names) },
            onEvent = { EventLog.add(it) },
            onCheckpoint = { done -> if (!isDry) RunStore.saveCheckpoint(app, plan, done) },
            gate = { checkGate(prefs) },
            isHeld = { n -> held.value.contains(n) },
            simMode = simMode,
            answeredAfterSec = prefs.getInt("answerSec", 5),
            dryRun = isDry,
        )
        engine = e
        acquireWakeLock()
        if (overlay == null) overlay = OverlayController(this)
        overlay?.show()
        e.start(scope, queue, order)

        scope.launch {
            e.state.collect { s ->
                status.value = s
                if (s is EngineState.Finished) {
                    releaseWakeLock()
                    RunStore.clearCheckpoint(app)
                    finishAlert(s.results, isDry, e.stopReason, prefs)
                    delay(4000)   // leave "Done" visible on the overlay for a moment
                    if (engine?.isRunning != true) stopSelf()
                } else if (s !is EngineState.Idle) {
                    getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(s))
                }
            }
        }
        scope.launch { e.isPaused.collect { paused.value = it } }
        scope.launch { e.waiting.collect { waitReason.value = it } }
    }

    /** One dialer for a fixed SIM, or several (primary first) when falling back / alternating. */
    private fun buildDialers(sims: List<SimOption>, slot: Int, mode: Int): List<Dialer> {
        val chosen = sims.firstOrNull { it.slot == slot }
        return if (mode >= 1 && sims.size > 1) {
            val first = chosen ?: sims.first()
            (listOf(first) + sims.filter { it.slot != first.slot }).map { TelecomDialer(this, it) }
        } else {
            listOf(TelecomDialer(this, chosen))
        }
    }

    private suspend fun recordResult(r: AttemptResult, isDry: Boolean, names: Map<String, String>) {
        if (isDry) return
        withContext(Dispatchers.IO) {
            runCatching { HistoryStore.append(applicationContext, r, names[r.number].orEmpty()) }
            Limits.bump(applicationContext)
        }
    }

    /** Daily cap ends the run; quiet hours, airplane mode, no signal and low battery make it wait. */
    private fun checkGate(prefs: SharedPreferences): Gate {
        val cap = prefs.getInt("dailyCap", 0)
        if (cap > 0 && Limits.callsToday(this) >= cap) return Gate.Stop("daily cap of $cap calls reached")

        if (prefs.getBoolean("quietOn", false)) {
            val start = prefs.getInt("quietStart", 22 * 60)
            val end = prefs.getInt("quietEnd", 7 * 60)
            val cal = Calendar.getInstance()
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val inside = if (start <= end) m in start until end else (m >= start || m < end)
            if (inside) return Gate.Wait("quiet hours until " + "%02d:%02d".format(end / 60, end % 60))
        }

        if (Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1) {
            return Gate.Wait("airplane mode is on")
        }
        if (noService()) return Gate.Wait("no mobile signal")

        if (prefs.getBoolean("batteryPause", false)) {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (!bm.isCharging && pct in 1..14) return Gate.Wait("battery low ($pct%)")
        }
        return Gate.Go
    }

    @SuppressLint("MissingPermission")
    private fun noService(): Boolean = runCatching {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.serviceState?.state == ServiceState.STATE_OUT_OF_SERVICE
    }.getOrDefault(false)

    private fun finishAlert(results: List<AttemptResult>, isDry: Boolean, stopReason: String?, prefs: SharedPreferences) {
        val answered = results.count { it.outcome == AttemptOutcome.CONNECTED }
        val title = when {
            isDry -> "Dry run finished"
            stopReason != null -> "Run stopped"
            else -> "Run complete"
        }
        val text = (if (stopReason != null) "$stopReason · " else "") +
            "${results.size} calls" + (if (isDry) "" else " · $answered answered")
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setColor(Color.parseColor("#00E5FF"))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(!prefs.getBoolean("alertOn", true))
            .build()
        getSystemService(NotificationManager::class.java).notify(DONE_ID, n)
    }

    private fun describe(s: EngineState): String = when (s) {
        is EngineState.Idle -> "Starting…"
        is EngineState.Dialing -> "Dialing ${s.p.number} · call ${s.p.step}/${s.p.steps}"
        is EngineState.InCall ->
            "Calling ${s.p.number} · call ${s.p.step}/${s.p.steps}" +
                (if (s.hasLimit && s.hangUpAt == null) " · timer off" else "")
        is EngineState.Cooldown -> "Next: ${s.next.number} · call ${s.next.step}/${s.next.steps}"
        is EngineState.Paused -> waitReason.value?.let { "Waiting: $it" } ?: "Paused"
        is EngineState.Finished -> "Done · ${s.results.size} calls"
    }

    private fun action(label: String, action: String) = NotificationCompat.Action(
        0, label,
        PendingIntent.getService(
            this, action.hashCode(), Intent(this, RedialService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )

    private fun notification(s: EngineState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setColor(Color.parseColor("#00E5FF"))
            .setContentTitle(if (dry.value) "Squeve Redail · dry run" else "Squeve Redail")
            .setContentText(describe(s))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        // Only the buttons that make sense right now (the shade shows three at most).
        when {
            s is EngineState.Paused -> b.addAction(action("Resume", ACTION_RESUME))
            s is EngineState.InCall && s.hasLimit -> {
                if (s.hangUpAt != null) {
                    b.addAction(action(if (s.micMuted) "Keep & unmute" else "Keep call", ACTION_HOLD))
                } else {
                    b.addAction(action("Auto hang-up", ACTION_REARM))
                }
                b.addAction(action("Pause", ACTION_PAUSE))
            }
            s is EngineState.InCall && s.micMuted -> {
                b.addAction(action("Unmute", ACTION_UNMUTE))
                b.addAction(action("Pause", ACTION_PAUSE))
            }
            else -> b.addAction(action("Pause", ACTION_PAUSE))
        }
        b.addAction(action("Stop", ACTION_STOP))

        // Run progress bar.
        val p = s.progress()
        if (p != null) b.setProgress(p.steps, p.step - 1, false)

        // Live countdown (hang-up timer or wait before the next call) that ticks without re-posting.
        val until: Long? = when (s) {
            is EngineState.InCall -> s.hangUpAt
            is EngineState.Cooldown -> s.untilMs
            else -> null
        }
        if (until != null) {
            b.setWhen(until).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }
        return b.build()
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "squeve:redail").apply {
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        if (engine?.isRunning == true) engine?.stop()   // checkpoint stays so the run can be resumed
        overlay?.hide()
        overlay = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
