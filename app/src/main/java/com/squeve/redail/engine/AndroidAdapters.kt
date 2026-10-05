package com.squeve.redail.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.squeve.redail.model.CallEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class TelecomDialer(private val ctx: Context) : Dialer {
    private val telecom get() = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    @SuppressLint("MissingPermission")
    override fun place(number: String): Boolean = runCatching {
        telecom.placeCall(Uri.fromParts("tel", number, null), null)  // TODO: pass PhoneAccountHandle for dual SIM
        true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun hangUp(): Boolean = runCatching { telecom.endCall() }.getOrDefault(false)
}

class TelephonyCallState(private val ctx: Context) : CallStateSource {
    private val tm get() = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @SuppressLint("MissingPermission")
    override val events: Flow<CallEvent> = callbackFlow {
        fun map(state: Int): CallEvent? = when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> CallEvent.OFFHOOK
            TelephonyManager.CALL_STATE_IDLE -> CallEvent.IDLE
            else -> null
        }
        val executor = ctx.mainExecutor
        if (Build.VERSION.SDK_INT >= 31) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) { map(state)?.let { trySend(it) } }
            }
            tm.registerTelephonyCallback(executor, cb)
            awaitClose { tm.unregisterTelephonyCallback(cb) }
        } else {
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) { map(state)?.let { trySend(it) } }
            }
            @Suppress("DEPRECATION")
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            @Suppress("DEPRECATION")
            awaitClose { tm.listen(l, PhoneStateListener.LISTEN_NONE) }
        }
    }.shareIn(kotlinx.coroutines.GlobalScope, SharingStarted.Eagerly)
}

class CallLogDurationReader(private val ctx: Context) : CallLogReader {
    @SuppressLint("MissingPermission")
    override fun lastOutgoingDurationSec(number: String, sinceMs: Long): Long {
        // Small delay-tolerant read: the log row can land a moment after IDLE.
        repeat(5) {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.DURATION, CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.DATE}>=?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), sinceMs.toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val digits = number.filter { it.isDigit() }.takeLast(9)
                while (c.moveToNext()) {
                    if (c.getString(1).orEmpty().filter { it.isDigit() }.endsWith(digits)) return c.getLong(0)
                }
            }
            Thread.sleep(400)
        }
        return 0
    }
}
