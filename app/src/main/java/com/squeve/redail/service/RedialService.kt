package com.squeve.redail.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.squeve.redail.MainActivity
import com.squeve.redail.R
import com.squeve.redail.engine.*
import com.squeve.redail.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RedialService : Service() {

    companion object {
        const val ACTION_START = "com.squeve.redail.START"
        const val ACTION_PAUSE = "com.squeve.redail.PAUSE"
        const val ACTION_RESUME = "com.squeve.redail.RESUME"
        const val ACTION_STOP = "com.squeve.redail.STOP"
        const val ACTION_HOLD = "com.squeve.redail.HOLD"      // cancel the auto hang-up for this call
        const val ACTION_REARM = "com.squeve.redail.REARM"    // start the auto hang-up countdown again
        private const val CHANNEL = "redail"
        private const val NOTIF_ID = 1

        /** Hand-off from the UI. */
        @Volatile var pendingQueue: List<RedialJob> = emptyList()
        @Volatile var pendingOrder: CallOrder = CallOrder.SEQUENTIAL
        @Volatile var pendingSim: SimOption? = null
        @Volatile var engine: RedialEngine? = null

        /** Live state for the UI. */
        val status = MutableStateFlow<EngineState>(EngineState.Idle)
        val paused = MutableStateFlow(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlay: OverlayController? = null

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(CHANNEL, "Redial", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, notification(status.value))

                // Only ever one engine running. A second Start tap must not spawn a second dialer.
                if (engine?.isRunning != true) {
                    PhoneStateMonitor.init(applicationContext)
                    val e = RedialEngine(
                        dialer = TelecomDialer(this, pendingSim),
                        phoneBusy = PhoneStateMonitor.offhook,
                        callLog = CallLogDurationReader(this),
                        audio = AndroidAudioControl(this),
                    )
                    engine = e
                    acquireWakeLock()
                    if (overlay == null) overlay = OverlayController(this)
                    overlay?.show()
                    e.start(scope, pendingQueue, pendingOrder)

                    scope.launch {
                        e.state.collect { s ->
                            status.value = s
                            if (s is EngineState.Finished) {
                                releaseWakeLock()
                                delay(4000)   // leave "Done" visible on the overlay for a moment
                                if (engine?.isRunning != true) stopSelf()
                            } else if (s !is EngineState.Idle) {
                                getSystemService(NotificationManager::class.java)
                                    .notify(NOTIF_ID, notification(s))
                            }
                        }
                    }
                    scope.launch { e.isPaused.collect { paused.value = it } }
                }
            }
            ACTION_PAUSE -> engine?.pause()
            ACTION_RESUME -> engine?.resume()
            ACTION_HOLD -> engine?.holdCall()
            ACTION_REARM -> engine?.rearmTimer()
            ACTION_STOP -> {
                engine?.stop()
                status.value = EngineState.Idle
                paused.value = false
                overlay?.hide()
                stopSelf()
            }
        }
        if (action != ACTION_START && engine?.isRunning != true) stopSelf()
        return START_NOT_STICKY
    }

    private fun describe(s: EngineState): String = when (s) {
        is EngineState.Idle -> "Starting…"
        is EngineState.Dialing -> "Dialing ${s.p.number} · call ${s.p.step}/${s.p.steps}"
        is EngineState.InCall -> "Calling ${s.p.number} · call ${s.p.step}/${s.p.steps}"
        is EngineState.Cooldown -> "Next: ${s.next.number} · call ${s.next.step}/${s.next.steps}"
        is EngineState.Paused -> "Paused"
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
            .setContentTitle("Squeve Redail")
            .setContentText(describe(s))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action("Pause", ACTION_PAUSE))
            .addAction(action("Resume", ACTION_RESUME))
            .addAction(action("Stop", ACTION_STOP))

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
        if (engine?.isRunning == true) engine?.stop()
        overlay?.hide()
        overlay = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
