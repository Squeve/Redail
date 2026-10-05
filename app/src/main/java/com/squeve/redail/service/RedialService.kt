package com.squeve.redail.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
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

        /** Temporary hand-off until Room is wired in. */
        @Volatile var pendingQueue: List<RedialJob> = emptyList()
        @Volatile var engine: RedialEngine? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(CHANNEL, "Redial", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, notification("Starting…"))
                val e = RedialEngine(
                    dialer = TelecomDialer(this),
                    callState = TelephonyCallState(this),
                    callLog = CallLogDurationReader(this),
                    onResult = { /* TODO: persist to Room */ },
                ).also { engine = it }
                e.start(scope, pendingQueue)
                scope.launch {
                    e.state.collect { s ->
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(describe(s)))
                        if (s is EngineState.Finished) stopSelf()
                    }
                }
            }
            ACTION_PAUSE -> engine?.pause()
            ACTION_RESUME -> engine?.resume()
            ACTION_STOP -> { engine?.stop(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun describe(s: EngineState) = when (s) {
        is EngineState.Idle -> "Idle"
        is EngineState.Dialing -> "Dialing ${s.number} (${s.attempt}/${s.total})"
        is EngineState.InCall -> "Calling ${s.number} (${s.attempt}/${s.total})"
        is EngineState.Cooldown -> "Next attempt for ${s.number} shortly"
        is EngineState.Paused -> "Paused"
        is EngineState.Finished -> "Done: ${s.results.size} attempts"
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

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
