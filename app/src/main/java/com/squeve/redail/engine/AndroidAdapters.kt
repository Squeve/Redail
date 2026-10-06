package com.squeve.redail.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimOption(val label: String, val handle: PhoneAccountHandle)

@SuppressLint("MissingPermission")
fun listSims(ctx: Context): List<SimOption> = runCatching {
    val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    tm.callCapablePhoneAccounts.mapIndexed { i, h ->
        val name = tm.getPhoneAccount(h)?.label?.toString().orEmpty()
        SimOption(if (name.isBlank()) "SIM ${i + 1}" else "SIM ${i + 1} · $name", h)
    }
}.getOrDefault(emptyList())

class TelecomDialer(
    private val ctx: Context,
    private val account: PhoneAccountHandle?,
) : Dialer {
    private val telecom get() = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    @SuppressLint("MissingPermission")
    override fun place(number: String): Boolean = runCatching {
        val extras = Bundle()
        if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
        telecom.placeCall(Uri.fromParts("tel", number, null), extras)
        true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun hangUp(): Boolean = runCatching { telecom.endCall() }.getOrDefault(false)
}

/**
 * Single app-wide listener. offhook = true while any call is active (dialing, ringing out or connected).
 * A StateFlow, so transitions can never be "missed" the way one-shot events can.
 */
object PhoneStateMonitor {
    private val _offhook = MutableStateFlow(false)
    val offhook: StateFlow<Boolean> = _offhook.asStateFlow()

    private var registered = false
    private var keepAlive: Any? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun init(context: Context) {
        if (registered) return
        val app = context.applicationContext
        val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        _offhook.value = state == TelephonyManager.CALL_STATE_OFFHOOK
                    }
                }
                tm.registerTelephonyCallback(app.mainExecutor, cb)
                keepAlive = cb
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        _offhook.value = state == TelephonyManager.CALL_STATE_OFFHOOK
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
                keepAlive = l
            }
            registered = true
        }
    }
}

class CallLogDurationReader(private val ctx: Context) : CallLogReader {
    @SuppressLint("MissingPermission")
    override fun lastOutgoingDurationSec(number: String, sinceMs: Long): Long {
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
