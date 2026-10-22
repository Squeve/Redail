package com.squeve.redail.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** slot is 0-based. handle may be null when the phone doesn't expose it; the dialer then falls back to slot hints. */
data class SimOption(val slot: Int, val label: String, val handle: PhoneAccountHandle?)

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
fun listSims(ctx: Context): List<SimOption> {
    val telecom = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val handles: List<PhoneAccountHandle> =
        runCatching { telecom.callCapablePhoneAccounts.toList() }.getOrDefault(emptyList())
    val subs: List<SubscriptionInfo> = runCatching {
        val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        sm.activeSubscriptionInfoList?.toList() ?: emptyList()
    }.getOrDefault(emptyList())

    // Best case: real subscriptions, each matched to its calling account.
    if (subs.isNotEmpty()) {
        return subs.sortedBy { it.simSlotIndex }.map { sub ->
            val handle = handles.firstOrNull { h ->
                h.id == sub.subscriptionId.toString() ||
                    (Build.VERSION.SDK_INT >= 30 && runCatching {
                        tm.createForPhoneAccountHandle(h)?.subscriptionId == sub.subscriptionId
                    }.getOrDefault(false))
            }
            val carrier = (sub.carrierName ?: sub.displayName)?.toString().orEmpty()
            val name = "SIM ${sub.simSlotIndex + 1}"
            SimOption(sub.simSlotIndex, if (carrier.isBlank()) name else "$name · $carrier", handle)
        }
    }

    // Subscriptions hidden (permission/OEM): fall back to the calling accounts themselves.
    if (handles.size > 1) {
        return handles.mapIndexed { i, h ->
            val label = runCatching { telecom.getPhoneAccount(h)?.label?.toString() }.getOrNull().orEmpty()
            SimOption(i, if (label.isBlank()) "SIM ${i + 1}" else "SIM ${i + 1} · $label", h)
        }
    }

    // Last resort: the phone says it has several SIM slots.
    val slots = runCatching { tm.phoneCount }.getOrDefault(1)
    return if (slots > 1) (0 until slots).map { SimOption(it, "SIM ${it + 1}", null) } else emptyList()
}

class TelecomDialer(
    private val ctx: Context,
    private val sim: SimOption?,
) : Dialer {
    override val slot: Int get() = sim?.slot ?: -1

    private val telecom get() = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    @SuppressLint("MissingPermission")
    override fun place(number: String, speaker: Boolean): Boolean = runCatching {
        val extras = Bundle()
        if (speaker) extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
        if (sim != null) {
            if (sim.handle != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim.handle)
            } else {
                // No account handle: use the slot hints many phone apps understand.
                extras.putInt("com.android.phone.extra.slot", sim.slot)
                extras.putInt("slot", sim.slot)
                extras.putInt("simSlot", sim.slot)
                extras.putInt("slot_id", sim.slot)
            }
        }
        telecom.placeCall(Uri.fromParts("tel", number, null), extras)
        true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun hangUp(): Boolean = runCatching { telecom.endCall() }.getOrDefault(false)
}

/**
 * Speaker comes from the Telecom start flag; this is the extra nudge plus microphone mute.
 * Mute is best effort: Android only guarantees it for the default dialer, so we re-apply while the call runs.
 */
class AndroidAudioControl(ctx: Context) : AudioControl {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var applied = false

    @Suppress("DEPRECATION")
    override fun apply(speaker: Boolean, mute: Boolean) {
        runCatching {
            if (speaker && !am.isSpeakerphoneOn) am.isSpeakerphoneOn = true
            if (mute && !am.isMicrophoneMute) am.isMicrophoneMute = true
            applied = true
        }
    }

    @Suppress("DEPRECATION")
    override fun unmute() {
        runCatching { am.isMicrophoneMute = false }
    }

    @Suppress("DEPRECATION")
    override fun restore() {
        if (!applied) return
        runCatching {
            am.isMicrophoneMute = false
            am.isSpeakerphoneOn = false
        }
        applied = false
    }
}

/**
 * Single app-wide listener. offhook = true while any call is active (dialing, ringing out or connected).
 * A StateFlow, so transitions can never be "missed" the way one-shot events can.
 */
object PhoneStateMonitor {
    private val _offhook = MutableStateFlow(false)
    val offhook: StateFlow<Boolean> = _offhook.asStateFlow()

    private val _ringing = MutableStateFlow(false)
    val ringing: StateFlow<Boolean> = _ringing.asStateFlow()

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
                        _ringing.value = state == TelephonyManager.CALL_STATE_RINGING
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
                        _ringing.value = state == TelephonyManager.CALL_STATE_RINGING
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
        repeat(4) {
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
            Thread.sleep(300)
        }
        return 0
    }
}
