package com.squeve.redail.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.telecom.PhoneAccountHandle
import androidx.core.app.NotificationCompat
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
        private const val CHANNEL = "redail"
        private const val NOTIF_ID = 1

        /** Hand-off from the UI. */
        @Volatile var pendingQueue: List<RedialJob> = emptyList()
        @Volatile var pendingSim: PhoneAccountHandle? = null
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
                startForeground(NOTIF_ID, notification(describe(status.value)))

                // Only ever one engine running. A second Start tap must not spawn a second dialer.
                if (engine?.isRunning != true) {
                    PhoneStateMonitor.init(applicationContext)
                    val e = RedialEngine(
                        dialer = TelecomDialer(this, pendingSim),
                        phoneBusy = PhoneStateMonitor.offhook,
                        callLog = CallLogDurationReader(this),
                    )
                    engine = e
                    acquireWakeLock()
                    if (overlay == null) overlay = OverlayController(this)
                    overlay?.show()
                    e.start(scope, pendingQueue)

                    scope.launch {
                        e.state.collect { s ->
                            status.value = s
                            if (s is EngineState.Finished) {
                                releaseWakeLock()
                                delay(4000)   // leave "Done" visible on the overlay for a moment
                                if (engine?.isRunning != true) stopSelf()
                            } else if (s !is EngineState.Idle) {
                                getSystemService(NotificationManager::class.java)
                                    .notify(NOTIF_ID, notification(describe(s)))
                            }
                        }
                    }
                    scope.launch { e.isPaused.collect { paused.value = it } }
                }
            }
            ACTION_PAUSE -> engine?.pause()
            ACTION_RESUME -> engine?.resume()
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
        is EngineState.Dialing -> "Dialing ${s.p.number} · call ${s.p.attempt}/${s.p.total} · number ${s.p.jobIndex + 1}/${s.p.jobCount}"
        is EngineState.InCall -> "Calling ${s.p.number} · call ${s.p.attempt}/${s.p.total} · number ${s.p.jobIndex + 1}/${s.p.jobCount}"
        is EngineState.Cooldown -> "Next: ${s.next.number} · call ${s.next.attempt}/${s.next.total}"
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

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.sym_action_call)
        .setContentTitle("Squeve Redail")
        .setContentText(text)
        .setOngoing(true)
        .addAction(action("Pause", ACTION_PAUSE))
        .addAction(action("Resume", ACTION_RESUME))
        .addAction(action("Stop", ACTION_STOP))
        .build()

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
