package com.squeve.redail

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.squeve.redail.engine.SimOption
import com.squeve.redail.engine.RecentCall
import com.squeve.redail.engine.listSims
import com.squeve.redail.engine.loadRecentCalls
import com.squeve.redail.model.*
import com.squeve.redail.data.Checkpoint
import com.squeve.redail.data.EventLog
import com.squeve.redail.data.HistoryRecord
import com.squeve.redail.data.HistoryStore
import com.squeve.redail.data.ListStore
import com.squeve.redail.data.RunStore
import com.squeve.redail.data.Scheduled
import com.squeve.redail.data.Scheduler
import com.squeve.redail.service.RedialService
import com.squeve.redail.service.RunLauncher
import com.squeve.redail.service.showBubblePreview
import com.squeve.redail.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Per-number settings. A null field follows the global setting. hangUp is in seconds; 0 means no limit. */
internal data class NumOverride(
    val calls: Int? = null,
    val hangUp: Int? = null,
    val gap: Int? = null,
    val speaker: Boolean? = null,
    val mute: Boolean? = null,
)

internal fun overrideLine(number: String, o: NumOverride) =
    "$number|${o.calls ?: ""}|${o.hangUp ?: ""}|${o.gap ?: ""}|${o.speaker.flag()}|${o.mute.flag()}"

/** Inverse of overrideLine: "number|calls|hangUp|gap|speaker|mute" (the last two are optional). */
internal fun parseOverride(line: String): Pair<String, NumOverride>? {
    val f = line.split('|')
    if (f.size < 4 || f[0].isBlank()) return null
    fun flag(i: Int): Boolean? = f.getOrNull(i)?.let { v -> if (v.isEmpty()) null else v == "1" }
    return f[0] to NumOverride(f[1].toIntOrNull(), f[2].toIntOrNull(), f[3].toIntOrNull(), flag(4), flag(5))
}

internal class RemovedEntry(val items: List<Pair<Int, String>>)

/** All user settings in one place; changes only recompose whatever reads that field. */
@Stable
internal class Config(private val prefs: SharedPreferences) {
    val numbers = mutableStateListOf<String>().apply {
        prefs.getString("numbers", "")?.split("\n")?.filter { it.isNotBlank() }?.let { addAll(it) }
    }

    /** Display names for numbers that came from contacts or the call log. */
    val names = mutableStateMapOf<String, String>().apply {
        prefs.getString("names", "")?.split("\n")?.forEach { line ->
            val i = line.indexOf('|')
            if (i > 0) put(line.substring(0, i), line.substring(i + 1))
        }
    }

    /** Numbers that have their own calls / hang-up / interval / audio. Keyed by number. */
    val overrides = mutableStateMapOf<String, NumOverride>().apply {
        prefs.getString("overrides", "")?.split("\n")?.forEach { line ->
            parseOverride(line)?.let { (k, v) -> put(k, v) }
        }
    }

    var calls by mutableStateOf(prefs.getInt("calls", 5))
    var gapSec by mutableStateOf(prefs.getInt("gap", 5))
    var gapRandom by mutableStateOf(prefs.getBoolean("gapRandom", false))
    var gapMaxSec by mutableStateOf(prefs.getInt("gapMax", 10))
    var limitOn by mutableStateOf(prefs.getBoolean("limitOn", true))
    var durSec by mutableStateOf(prefs.getInt("dur", 30))
    var skipAnswered by mutableStateOf(prefs.getBoolean("skip", false))
    var answersToStop by mutableStateOf(prefs.getInt("answersToStop", 1))
    var answerSec by mutableStateOf(prefs.getInt("answerSec", 5))
    var speakerOn by mutableStateOf(prefs.getBoolean("speaker", false))
    var muteOn by mutableStateOf(prefs.getBoolean("mute", false))
    var simSlot by mutableStateOf(prefs.getInt("simSlot", -1))     // -1 = phone default
    var simMode by mutableStateOf(prefs.getInt("simMode", 0))      // 0 fixed, 1 fall back, 2 alternate
    var order by mutableStateOf(
        CallOrder.values().getOrElse(prefs.getInt("order", 0)) { CallOrder.SEQUENTIAL },
    )
    var bubbleLook by mutableStateOf(prefs.getInt("bubbleLook", 0)) // 0 dark, 1 light, 2 neon, 3 aurora, 4 gold, 5 carbon, 6 frost
    var alertOn by mutableStateOf(prefs.getBoolean("alertOn", true))
    var dailyCap by mutableStateOf(prefs.getInt("dailyCap", 0))
    var quietOn by mutableStateOf(prefs.getBoolean("quietOn", false))
    var quietStart by mutableStateOf(prefs.getInt("quietStart", 22 * 60))
    var quietEnd by mutableStateOf(prefs.getInt("quietEnd", 7 * 60))
    var batteryPause by mutableStateOf(prefs.getBoolean("batteryPause", false))
    var countryCode by mutableStateOf(prefs.getString("countryCode", "") ?: "")
    var allowDup by mutableStateOf(prefs.getBoolean("allowDup", false))
    var lockOn by mutableStateOf(prefs.getBoolean("lockOn", false))
    var accentIdx by mutableStateOf(prefs.getInt("accent", 0))
    var blackOn by mutableStateOf(prefs.getBoolean("black", false))
    var uiSize by mutableStateOf(prefs.getInt("uiSize", 1))
    var hapticsOn by mutableStateOf(prefs.getBoolean("haptics", true))
    var themeMode by mutableStateOf(prefs.getInt("themeMode", 0))

    /** Not remembered between launches on purpose: a forgotten dry-run switch must never hide a real run. */
    var dryRun by mutableStateOf(false)

    /** The last number taken out of the queue, kept for a few seconds so it can be put back. */
    var removed by mutableStateOf<RemovedEntry?>(null)

    fun removeWithUndo(i: Int) = removeManyWithUndo(listOf(i))

    fun removeManyWithUndo(indices: List<Int>) {
        val idx = indices.filter { it in numbers.indices }.distinct().sorted()
        if (idx.isEmpty()) return
        val items = idx.map { it to numbers[it] }
        idx.asReversed().forEach { numbers.removeAt(it) }
        removed = RemovedEntry(items)
    }

    fun undoRemove() {
        val r = removed ?: return
        // Ascending order puts every number back exactly where it was.
        r.items.forEach { (i, n) -> numbers.add(i.coerceIn(0, numbers.size), n) }
        removed = null
        save()
    }

    fun move(from: Int, to: Int) {
        if (from !in numbers.indices || to !in numbers.indices || from == to) return
        numbers.add(to, numbers.removeAt(from))
    }

    fun callsFor(n: String): Int = overrides[n]?.calls ?: calls
    fun gapFor(n: String): Int = overrides[n]?.gap ?: gapSec
    fun speakerFor(n: String): Boolean = overrides[n]?.speaker ?: speakerOn
    fun muteFor(n: String): Boolean = overrides[n]?.mute ?: muteOn

    /** Hang-up in seconds for this number, or null when calls should run until they end by themselves. */
    fun hangUpFor(n: String): Int? = when (val h = overrides[n]?.hangUp) {
        null -> if (limitOn) durSec else null
        0 -> null
        else -> h
    }

    /** Short description of what is custom about this number, or null when it follows the defaults. */
    fun customSummary(n: String): String? {
        val o = overrides[n] ?: return null
        val parts = listOfNotNull(
            o.calls?.let { "×$it calls" },
            o.hangUp?.let { if (it == 0) "no hang-up" else "${it}s hang-up" },
            o.gap?.let { "${it}s gap" },
            o.speaker?.let { if (it) "speaker" else "no speaker" },
            o.mute?.let { if (it) "muted" else "unmuted" },
        )
        return if (parts.isEmpty()) null else "CUSTOM · " + parts.joinToString(" · ")
    }

    fun buildJobs(list: List<String>): List<RedialJob> = list.map { n ->
        val ownGap = overrides[n]?.gap != null
        RedialJob(
            number = n,
            attempts = callsFor(n),
            gapMs = gapFor(n) * 1000L,
            gapMaxMs = if (gapRandom && !ownGap) maxOf(gapMaxSec, gapSec) * 1000L else null,
            hangUpAfterMs = hangUpFor(n)?.let { it * 1000L },
            stopOnConnected = skipAnswered,
            stopAfterAnswers = answersToStop,
            speaker = speakerFor(n),
            muteMic = muteFor(n),
        )
    }

    fun buildPlan(): RunPlan = RunPlan(buildJobs(numbers.toList()), order, simSlot, dryRun)

    /** Reads every saved setting, so a snapshotFlow over it fires whenever anything changes. */
    fun fingerprint(): List<Any?> = listOf(
        calls, gapSec, gapRandom, gapMaxSec, limitOn, durSec, skipAnswered, answersToStop, answerSec,
        speakerOn, muteOn, simSlot, simMode, order, bubbleLook, alertOn, dailyCap, quietOn, quietStart,
        quietEnd, batteryPause, countryCode, allowDup, lockOn, accentIdx, blackOn, uiSize, hapticsOn, themeMode,
    )

    fun save() {
        prefs.edit()
            .putString("numbers", numbers.joinToString("\n"))
            .putString(
                "names",
                names.entries.filter { it.key in numbers }
                    .joinToString("\n") { "${it.key}|${it.value.replace('|', ' ').replace('\n', ' ')}" },
            )
            .putString(
                "overrides",
                overrides.entries.filter { it.key in numbers }.joinToString("\n") { overrideLine(it.key, it.value) },
            )
            .putInt("calls", calls).putInt("gap", gapSec)
            .putBoolean("gapRandom", gapRandom).putInt("gapMax", gapMaxSec)
            .putInt("dur", durSec).putBoolean("limitOn", limitOn)
            .putBoolean("skip", skipAnswered).putInt("answersToStop", answersToStop).putInt("answerSec", answerSec)
            .putBoolean("speaker", speakerOn).putBoolean("mute", muteOn)
            .putInt("simSlot", simSlot).putInt("simMode", simMode).putInt("order", order.ordinal)
            .putInt("bubbleLook", bubbleLook).putBoolean("alertOn", alertOn)
            .putInt("dailyCap", dailyCap).putBoolean("quietOn", quietOn)
            .putInt("quietStart", quietStart).putInt("quietEnd", quietEnd)
            .putBoolean("batteryPause", batteryPause)
            .putString("countryCode", countryCode).putBoolean("allowDup", allowDup)
            .putBoolean("lockOn", lockOn)
            .putInt("accent", accentIdx).putBoolean("black", blackOn).putInt("uiSize", uiSize)
            .putBoolean("haptics", hapticsOn).putInt("themeMode", themeMode)
            .apply()
    }

    /** Everything for a backup (full = true) or just the queue and run settings for a saved list. */
    fun toJson(full: Boolean): JSONObject = JSONObject().apply {
        put("numbers", JSONArray(numbers.toList()))
        put("names", JSONObject().also { o -> names.entries.forEach { e -> o.put(e.key, e.value) } })
        put("overrides", JSONObject().also { o -> overrides.entries.forEach { e -> o.put(e.key, overrideLine(e.key, e.value)) } })
        put("calls", calls)
        put("gap", gapSec)
        put("gapRandom", gapRandom)
        put("gapMax", gapMaxSec)
        put("limitOn", limitOn)
        put("dur", durSec)
        put("skip", skipAnswered)
        put("answersToStop", answersToStop)
        put("answerSec", answerSec)
        put("speaker", speakerOn)
        put("mute", muteOn)
        put("simSlot", simSlot)
        put("simMode", simMode)
        put("order", order.ordinal)
        if (full) {
            put("bubbleLook", bubbleLook)
            put("alertOn", alertOn)
            put("dailyCap", dailyCap)
            put("quietOn", quietOn)
            put("quietStart", quietStart)
            put("quietEnd", quietEnd)
            put("batteryPause", batteryPause)
            put("countryCode", countryCode)
            put("allowDup", allowDup)
            put("accent", accentIdx)
            put("black", blackOn)
            put("uiSize", uiSize)
            put("haptics", hapticsOn)
            put("themeMode", themeMode)
        }
    }

    fun applyJson(o: JSONObject) {
        o.optJSONArray("numbers")?.let { arr ->
            numbers.clear()
            for (i in 0 until arr.length()) numbers.add(arr.getString(i))
        }
        o.optJSONObject("names")?.let { n ->
            names.clear()
            n.keys().forEach { k -> names[k] = n.getString(k) }
        }
        o.optJSONObject("overrides")?.let { ov ->
            overrides.clear()
            ov.keys().forEach { k -> parseOverride(ov.getString(k))?.let { (num, v) -> overrides[num] = v } }
        }
        calls = o.optInt("calls", calls)
        gapSec = o.optInt("gap", gapSec)
        gapRandom = o.optBoolean("gapRandom", gapRandom)
        gapMaxSec = o.optInt("gapMax", gapMaxSec)
        limitOn = o.optBoolean("limitOn", limitOn)
        durSec = o.optInt("dur", durSec)
        skipAnswered = o.optBoolean("skip", skipAnswered)
        answersToStop = o.optInt("answersToStop", answersToStop)
        answerSec = o.optInt("answerSec", answerSec)
        speakerOn = o.optBoolean("speaker", speakerOn)
        muteOn = o.optBoolean("mute", muteOn)
        simSlot = o.optInt("simSlot", simSlot)
        simMode = o.optInt("simMode", simMode)
        order = CallOrder.values().getOrElse(o.optInt("order", order.ordinal)) { order }
        bubbleLook = o.optInt("bubbleLook", bubbleLook)
        alertOn = o.optBoolean("alertOn", alertOn)
        dailyCap = o.optInt("dailyCap", dailyCap)
        quietOn = o.optBoolean("quietOn", quietOn)
        quietStart = o.optInt("quietStart", quietStart)
        quietEnd = o.optInt("quietEnd", quietEnd)
        batteryPause = o.optBoolean("batteryPause", batteryPause)
        countryCode = o.optString("countryCode", countryCode)
        allowDup = o.optBoolean("allowDup", allowDup)
        accentIdx = o.optInt("accent", accentIdx)
        blackOn = o.optBoolean("black", blackOn)
        uiSize = o.optInt("uiSize", uiSize)
        hapticsOn = o.optBoolean("haptics", hapticsOn)
        themeMode = o.optInt("themeMode", themeMode)
    }
}
