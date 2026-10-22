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

// ───────────────────────── Palette & type ─────────────────────────
/** Accent colours, pure-black mode and UI size, all changeable at runtime. */
private class Accent(val name: String, val main: Color, val second: Color)

private val ACCENTS = listOf(
    Accent("CYAN", Color(0xFF00E5FF), Color(0xFF7C4DFF)),
    Accent("MINT", Color(0xFF00E676), Color(0xFF00B8D4)),
    Accent("VIOLET", Color(0xFFB388FF), Color(0xFF536DFE)),
    Accent("AMBER", Color(0xFFFFC400), Color(0xFFFF6D00)),
    Accent("PINK", Color(0xFFFF4081), Color(0xFF7C4DFF)),
    Accent("BLUE", Color(0xFF448AFF), Color(0xFF00E5FF)),
)

private object Theming {
    val accent = mutableStateOf(ACCENTS[0].main)
    val accent2 = mutableStateOf(ACCENTS[0].second)
    val black = mutableStateOf(false)
    val size = mutableStateOf(1)           // 0 compact, 1 normal, 2 large
    val haptics = mutableStateOf(true)
    val mode = mutableStateOf(0)           // 0 dark, 1 light, 2 follow the phone
    val systemDark = mutableStateOf(true)
    val light: Boolean get() = mode.value == 1 || (mode.value == 2 && !systemDark.value)

    fun setAccent(i: Int) {
        val a = ACCENTS[i.coerceIn(0, ACCENTS.size - 1)]
        accent.value = a.main
        accent2.value = a.second
    }

    fun load(prefs: SharedPreferences, ctx: Context) {
        mode.value = prefs.getInt("themeMode", 0)
        val night = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        systemDark.value = night == android.content.res.Configuration.UI_MODE_NIGHT_YES
        setAccent(prefs.getInt("accent", 0))
        black.value = prefs.getBoolean("black", false)
        size.value = prefs.getInt("uiSize", 1)
        haptics.value = prefs.getBoolean("haptics", true)
    }
}

private val Ink: Color get() = when {
    Theming.light -> Color(0xFFF3F6FC)
    Theming.black.value -> Color.Black
    else -> Color(0xFF070B14)
}
private val InkSoft: Color get() = when {
    Theming.light -> Color(0xFFE7EDF8)
    Theming.black.value -> Color(0xFF050505)
    else -> Color(0xFF0D1424)
}
private val Panel: Color get() = when {
    Theming.light -> Color(0xE6FFFFFF)
    Theming.black.value -> Color(0xE60C0C0C)
    else -> Color(0xCC111A2C)
}
private val Dock: Color get() = when {
    Theming.light -> Color(0xF5FFFFFF)
    Theming.black.value -> Color(0xFF000000)
    else -> Color(0xF00A1020)
}
private val Cyan: Color get() =
    if (Theming.light) androidx.compose.ui.graphics.lerp(Theming.accent.value, Color.Black, 0.42f) else Theming.accent.value
private val Violet: Color get() =
    if (Theming.light) androidx.compose.ui.graphics.lerp(Theming.accent2.value, Color.Black, 0.22f) else Theming.accent2.value
private val Magenta = Color(0xFFFF2E93)
private val Mint: Color get() = if (Theming.light) Color(0xFF00A152) else Color(0xFF00E676)
private val Dim: Color get() = if (Theming.light) Color(0xFF5A6B88) else Color(0xFF8A9BB8)
private val Hair: Color get() = when {
    Theming.light -> Color(0xFFCBD5E8)
    Theming.black.value -> Color(0xFF262626)
    else -> Color(0xFF22304A)
}
/** Main text colour. */
private val Fg: Color get() = if (Theming.light) Color(0xFF0B1220) else Color.White
private val Soft: Color get() = if (Theming.light) Color(0xFF16213A) else Color(0xFFE6EEFF)
private val DialogBg: Color get() = if (Theming.light) Color.White else Color(0xFF0E1628)
private val Lilac: Color get() = if (Theming.light) Color(0xFF6A4DE0) else Color(0xFFB39DFF)

/** The whole UI is drawn at this fraction of normal size (dp and sp together). */
private const val UI_SCALE = 0.88f

private val Mono: FontFamily get() = Fonts.mono
private val Display: FontFamily get() = Fonts.display

private val NeonBrush: Brush get() = Brush.horizontalGradient(listOf(Cyan, Violet))
private val DangerBrush = Brush.horizontalGradient(listOf(Magenta, Color(0xFFFF5252)))
private val DisabledBrush: Brush get() = Brush.horizontalGradient(listOf(Hair, Hair))
private val BgBrush: Brush get() = Brush.verticalGradient(listOf(Ink, InkSoft, Ink))
private val RingBrush: Brush get() = Brush.sweepGradient(listOf(Cyan, Violet, Magenta, Cyan))

/** Material text styles all use the bundled family, so nothing falls back to the phone's system font. */
private fun typographyWith(f: FontFamily): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = f),
        displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f),
        headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f),
        headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f),
        titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f),
        bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f),
        bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f),
        labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f),
    )
}

private fun schemeFor() = if (Theming.light) {
    lightColorScheme(
        primary = Cyan,
        onPrimary = Ink,
        background = Ink,
        onBackground = Soft,
        surface = Panel,
        onSurface = Soft,
        surfaceVariant = Panel,
        onSurfaceVariant = Dim,
        outline = Hair,
    )
} else {
    darkColorScheme(
        primary = Cyan,
        onPrimary = Ink,
        background = Ink,
        onBackground = Soft,
        surface = Panel,
        onSurface = Soft,
        surfaceVariant = Panel,
        onSurfaceVariant = Dim,
        outline = Hair,
    )
}

/** Gradient, a soft cyan glow from the top and a faint grid. Drawn once, cheap. */
private fun Modifier.techBackdrop(): Modifier = this.drawBehind {
    drawRect(BgBrush)
    val glowCenter = Offset(size.width * 0.5f, 0f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Cyan.copy(alpha = 0.12f), Color.Transparent),
            center = glowCenter,
            radius = size.width * 0.95f,
        ),
        radius = size.width * 0.95f,
        center = glowCenter,
    )
    val step = 36.dp.toPx()
    val gridColor = Cyan.copy(alpha = 0.035f)
    var x = 0f
    while (x < size.width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

/** Press-down squash plus a light haptic tick. Use as the first modifier so scaling covers everything. */
@Composable
private fun Modifier.pressable(enabled: Boolean = true, strong: Boolean = false, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = tween(90),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            if (Theming.haptics.value) {
                haptic.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
}

// ───────────────────────── Helpers ─────────────────────────
private val REQUIRED = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ANSWER_PHONE_CALLS,
    Manifest.permission.READ_CALL_LOG,
)

private fun allGranted(ctx: Context) = REQUIRED.all {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

private fun permsToRequest(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) REQUIRED + Manifest.permission.POST_NOTIFICATIONS else REQUIRED

private class AddResult(val added: List<String>, val invalid: Int, val duplicates: Int)

/** Adds numbers to the queue, skipping invalid ones and (unless allowed) duplicates. */
private fun addChecked(cfg: Config, raw: String): AddResult {
    val added = mutableListOf<String>()
    var invalid = 0
    var duplicates = 0
    raw.split(Regex("[,;\\n]+")).forEach { part ->
        var n = part.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (n.none { it.isDigit() }) return@forEach
        n = normalizeNumber(n, cfg.countryCode)
        if (!isValidNumber(n)) {
            invalid++
            return@forEach
        }
        val key = digitsKey(n)
        if (cfg.numbers.any { digitsKey(it) == key } && !cfg.allowDup) {
            duplicates++
            return@forEach
        }
        cfg.numbers.add(n)
        added.add(n)
    }
    return AddResult(added, invalid, duplicates)
}

private fun reportAdd(ctx: Context, r: AddResult) {
    val parts = mutableListOf("Added ${r.added.size}")
    if (r.invalid > 0) parts += "${r.invalid} invalid skipped"
    if (r.duplicates > 0) parts += "${r.duplicates} duplicate" + (if (r.duplicates > 1) "s" else "") + " skipped"
    Toast.makeText(ctx, parts.joinToString(" · "), Toast.LENGTH_SHORT).show()
}

private fun shareFile(ctx: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun Boolean?.flag(): String = when (this) {
    null -> ""
    true -> "1"
    false -> "0"
}

private fun typeLabel(t: Int) = when (t) {
    CallLog.Calls.INCOMING_TYPE -> "IN"
    CallLog.Calls.OUTGOING_TYPE -> "OUT"
    CallLog.Calls.MISSED_TYPE -> "MISSED"
    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
    CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
    else -> "CALL"
}

private fun typeColor(t: Int) = when (t) {
    CallLog.Calls.OUTGOING_TYPE -> Cyan
    CallLog.Calls.INCOMING_TYPE -> Mint
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> Magenta
    else -> Dim
}

private fun ago(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        s < 172800 -> "yesterday"
        else -> "${s / 86400}d ago"
    }
}

private fun isRunning(s: EngineState) = s !is EngineState.Idle && s !is EngineState.Finished

private fun sendAction(ctx: Context, action: String) {
    ctx.startService(Intent(ctx, RedialService::class.java).setAction(action))
}

/** Editing the queue after a run clears the old "complete" result so new rows don't show as done. */
private fun clearFinished() {
    if (RedialService.status.value is EngineState.Finished) RedialService.status.value = EngineState.Idle
}

private fun openSettings(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action, Uri.parse("package:${ctx.packageName}"))) }
}

private fun clock(totalSec: Long) = "${totalSec / 60}:${"%02d".format(totalSec % 60)}"

private fun spanText(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${maxOf(m, 1)}m"
}

private fun CallOrder.title() = when (this) {
    CallOrder.SEQUENTIAL -> "ONE BY ONE"
    CallOrder.ROUND_ROBIN -> "ROUND ROBIN"
    CallOrder.RANDOM -> "RANDOM"
}

private fun CallOrder.hint() = when (this) {
    CallOrder.SEQUENTIAL -> "Number 1 gets all its calls, then number 2, and so on."
    CallOrder.ROUND_ROBIN -> "One call each in turn: 1, 2, 3, then 1, 2, 3 again."
    CallOrder.RANDOM -> "All calls shuffled. The same number is avoided twice in a row where possible."
}

/** True while the on-screen keyboard is up. */
private object ImeSignal {
    val open = MutableStateFlow(false)
}

/** Text shared into the app (share sheet), waiting for the user to confirm the import. */
private object ShareInbox {
    val pending = MutableStateFlow<String?>(null)
}

/** App lock state. */
private object LockState {
    val locked = MutableStateFlow(false)
    @Volatile var lastUnlock = 0L
}

/** Bumped on every onResume so permission-dependent UI refreshes after returning from system settings. */
private object ResumeSignal {
    val tick = MutableStateFlow(0)
}

/** Per-number settings. A null field follows the global setting. hangUp is in seconds; 0 means no limit. */
private data class NumOverride(
    val calls: Int? = null,
    val hangUp: Int? = null,
    val gap: Int? = null,
    val speaker: Boolean? = null,
    val mute: Boolean? = null,
)

private fun overrideLine(number: String, o: NumOverride) =
    "$number|${o.calls ?: ""}|${o.hangUp ?: ""}|${o.gap ?: ""}|${o.speaker.flag()}|${o.mute.flag()}"

/** Inverse of overrideLine: "number|calls|hangUp|gap|speaker|mute" (the last two are optional). */
private fun parseOverride(line: String): Pair<String, NumOverride>? {
    val f = line.split('|')
    if (f.size < 4 || f[0].isBlank()) return null
    fun flag(i: Int): Boolean? = f.getOrNull(i)?.let { v -> if (v.isEmpty()) null else v == "1" }
    return f[0] to NumOverride(f[1].toIntOrNull(), f[2].toIntOrNull(), f[3].toIntOrNull(), flag(4), flag(5))
}

private class RemovedEntry(val index: Int, val number: String)

/** All user settings in one place; changes only recompose whatever reads that field. */
@Stable
private class Config(private val prefs: SharedPreferences) {
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
    var bubbleLook by mutableStateOf(prefs.getInt("bubbleLook", 0)) // 0 dark, 1 light, 2 neon
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

    fun removeWithUndo(i: Int) {
        if (i !in numbers.indices) return
        val n = numbers.removeAt(i)
        removed = RemovedEntry(i, n)
    }

    fun undoRemove() {
        val r = removed ?: return
        numbers.add(r.index.coerceIn(0, numbers.size), r.number)
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

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_START_LAST = "com.squeve.redail.START_LAST"
        const val ACTION_STOP_RUN = "com.squeve.redail.STOP_RUN"
    }

    private var leftAt = 0L

    override fun onResume() {
        super.onResume()
        ResumeSignal.tick.value = ResumeSignal.tick.value + 1
        val lockOn = getSharedPreferences("redail", Context.MODE_PRIVATE).getBoolean("lockOn", false)
        val now = System.currentTimeMillis()
        if (lockOn && leftAt > 0 && now - leftAt > 20_000 && now - LockState.lastUnlock > 5_000) {
            LockState.locked.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Shortcuts, the tile and the widget start / stop runs here; the share sheet hands over numbers. */
    private fun handleIntent(intent: Intent?) {
        val i = intent ?: return
        when (i.action) {
            ACTION_START_LAST -> {
                i.action = null
                val plan = RunStore.loadLast(this)
                when {
                    plan == null -> Toast.makeText(this, "No previous run yet", Toast.LENGTH_SHORT).show()
                    !allGranted(this) ->
                        Toast.makeText(this, "Open the app and grant permissions first", Toast.LENGTH_LONG).show()
                    isRunning(RedialService.status.value) ->
                        Toast.makeText(this, "Already running", Toast.LENGTH_SHORT).show()
                    else -> RunLauncher.start(this, plan)
                }
            }
            ACTION_STOP_RUN -> {
                i.action = null
                RunLauncher.stop(this)
                finish()
            }
            Intent.ACTION_SEND -> {
                i.action = null
                sharedText(i)?.let { ShareInbox.pending.value = it }
            }
        }
    }

    private fun sharedText(i: Intent): String? {
        val direct = i.getStringExtra(Intent.EXTRA_TEXT)
        if (!direct.isNullOrBlank()) return direct
        @Suppress("DEPRECATION")
        val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        return readUri(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("redail", Context.MODE_PRIVATE)
        Fonts.init(this)
        Theming.load(prefs, this)
        if (prefs.getBoolean("lockOn", false) && savedInstanceState == null) LockState.locked.value = true
        window.statusBarColor = Ink.toArgb()
        window.navigationBarColor = Ink.toArgb()
        val decor = window.decorView
        decor.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            decor.getWindowVisibleDisplayFrame(r)
            val full = decor.rootView.height
            ImeSignal.open.value = full > 0 && (full - r.bottom) > full * 0.15f
        }
        setContent {
            // UI size is a setting; system font scaling beyond 1x is ignored so layouts stay tidy.
            val base = LocalDensity.current
            val factor = when (Theming.size.value) {
                0 -> 0.80f
                2 -> 1.0f
                else -> UI_SCALE
            }
            val scaled = remember(base, factor) { Density(base.density * factor, base.fontScale.coerceAtMost(1f)) }
            val type = remember { typographyWith(Fonts.body) }
            val isLight = Theming.light
            val scheme = remember(Theming.accent.value, Theming.black.value, isLight) { schemeFor() }
            val sysDark = isSystemInDarkTheme()
            SideEffect {
                if (Theming.systemDark.value != sysDark) Theming.systemDark.value = sysDark
                window.statusBarColor = Ink.toArgb()
                window.navigationBarColor = Ink.toArgb()
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Ink.toArgb()))
                val bars = androidx.core.view.WindowCompat.getInsetsController(window, decor)
                bars.isAppearanceLightStatusBars = isLight
                bars.isAppearanceLightNavigationBars = isLight
            }
            CompositionLocalProvider(LocalDensity provides scaled) {
                MaterialTheme(colorScheme = scheme, typography = type) {
                    Box(Modifier.fillMaxSize().techBackdrop()) {
                        Surface(
                            Modifier.fillMaxSize(),
                            color = Color.Transparent,
                            contentColor = Soft,
                        ) { RedialScreen() }
                    }
                }
            }
        }
        handleIntent(intent)
    }
}

// ───────────────────────── Screen host ─────────────────────────
private fun readUri(ctx: Context, uri: Uri): String? =
    runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()

/** Everything the DIAL tab can ask the host to do. */
private class DialActions(
    val add: (String) -> Unit,
    val contacts: () -> Unit,
    val callLog: () -> Unit,
    val paste: () -> Unit,
    val importFile: () -> Unit,
    val edit: (Int) -> Unit,
    val saveList: () -> Unit,
    val lists: () -> Unit,
    val schedule: () -> Unit,
    val cancelSchedule: () -> Unit,
    val resume: () -> Unit,
    val discardResume: () -> Unit,
    val retry: (List<String>) -> Unit,
    val export: () -> Unit,
)

@Composable
private fun RedialScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("redail", Context.MODE_PRIVATE) }
    val cfg = remember { Config(prefs) }
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(0) }
    var permTick by remember { mutableStateOf(0) }
    var uiTick by remember { mutableStateOf(0) }
    val resumeTick by ResumeSignal.tick.collectAsState()
    val imeOpen by ImeSignal.open.collectAsState()
    val locked by LockState.locked.collectAsState()
    val inbox by ShareInbox.pending.collectAsState()

    // Only recompose the host when "running" flips, not on every engine state change.
    val runningFlow = remember { RedialService.status.map { isRunning(it) }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = isRunning(RedialService.status.value))

    val refresh = permTick + resumeTick + uiTick
    val sims by produceState(emptyList<SimOption>(), refresh) {
        value = withContext(Dispatchers.IO) { runCatching { listSims(ctx) }.getOrDefault(emptyList()) }
    }
    val granted = remember(refresh) { allGranted(ctx) }
    val checkpoint = remember(refresh, running) { if (running) null else RunStore.loadCheckpoint(ctx) }
    val scheduled = remember(refresh, running) { RunStore.loadSchedule(ctx) }

    // Settings are remembered between runs; theme changes are applied as they happen.
    LaunchedEffect(Unit) { snapshotFlow { cfg.fingerprint() }.collect { cfg.save() } }
    LaunchedEffect(cfg.accentIdx, cfg.blackOn, cfg.uiSize, cfg.hapticsOn, cfg.themeMode) {
        Theming.mode.value = cfg.themeMode
        Theming.setAccent(cfg.accentIdx)
        Theming.black.value = cfg.blackOn
        Theming.size.value = cfg.uiSize
        Theming.haptics.value = cfg.hapticsOn
    }

    var showLog by remember { mutableStateOf(false) }
    var wantLog by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var showLists by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        // The call log button asked for access: open the picker as soon as it is granted.
        if (wantLog && ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showLog = true
        }
        wantLog = false
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (uri != null) {
            runCatching {
                ctx.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val r = addChecked(cfg, c.getString(0).orEmpty())
                        val name = c.getString(1).orEmpty()
                        if (name.isNotBlank()) r.added.forEach { cfg.names[it] = name }
                        reportAdd(ctx, r)
                        cfg.save()
                        clearFinished()
                    }
                }
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = readUri(ctx, uri)
            if (text.isNullOrBlank()) {
                Toast.makeText(ctx, "Could not read that file", Toast.LENGTH_SHORT).show()
            } else {
                ShareInbox.pending.value = text
            }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val o = readUri(ctx, uri)?.let { t -> runCatching { JSONObject(t) }.getOrNull() }
            if (o == null || !o.has("config")) {
                Toast.makeText(ctx, "That file is not a Squeve Redail backup", Toast.LENGTH_LONG).show()
            } else {
                cfg.applyJson(o.getJSONObject("config"))
                o.optJSONArray("lists")?.let { ListStore.replaceAll(prefs, it) }
                cfg.save()
                Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val unlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            LockState.lastUnlock = System.currentTimeMillis()
            LockState.locked.value = false
        }
    }

    fun requestUnlock() {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val i = km.createConfirmDeviceCredentialIntent("Squeve Redail", "Unlock to continue")
        if (i == null) {
            LockState.locked.value = false
        } else {
            unlockLauncher.launch(i)
        }
    }
    LaunchedEffect(locked) { if (locked) requestUnlock() }

    fun openCallLog() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showLog = true
        } else {
            wantLog = true
            permLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
        }
    }

    fun startRun() {
        if (!allGranted(ctx)) {
            permLauncher.launch(permsToRequest())
            return
        }
        if (cfg.numbers.isEmpty()) {
            Toast.makeText(ctx, "Add at least one number", Toast.LENGTH_SHORT).show()
            tab = 0
            return
        }
        // One-time prompt for the floating controls.
        if (!cfg.dryRun && !Settings.canDrawOverlays(ctx) && !prefs.getBoolean("overlayAsked", false)) {
            prefs.edit().putBoolean("overlayAsked", true).apply()
            Toast.makeText(ctx, "Allow 'Display over other apps', then press START again", Toast.LENGTH_LONG).show()
            openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            return
        }
        cfg.save()
        clearFinished()
        RunLauncher.start(ctx, cfg.buildPlan())
    }

    val actions = DialActions(
        add = { raw ->
            val r = addChecked(cfg, raw)
            reportAdd(ctx, r)
            cfg.save()
            clearFinished()
        },
        contacts = {
            contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        },
        callLog = { openCallLog() },
        paste = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx)?.toString() else null
            if (text.isNullOrBlank()) {
                Toast.makeText(ctx, "The clipboard is empty", Toast.LENGTH_SHORT).show()
            } else {
                ShareInbox.pending.value = text
            }
        },
        importFile = { filePicker.launch("*/*") },
        edit = { editIndex = it },
        saveList = { showSave = true },
        lists = { showLists = true },
        schedule = { showSchedule = true },
        cancelSchedule = {
            Scheduler.cancel(ctx)
            uiTick++
        },
        resume = {
            val plan = checkpoint?.let { RunStore.remainingPlan(it) }
            if (plan != null) RunLauncher.start(ctx, plan)
        },
        discardResume = {
            RunStore.clearCheckpoint(ctx)
            uiTick++
        },
        retry = { nums -> RunLauncher.start(ctx, RunPlan(cfg.buildJobs(nums), cfg.order, cfg.simSlot, false)) },
        export = {
            scope.launch {
                val f = withContext(Dispatchers.IO) { HistoryStore.exportCsv(ctx) }
                shareFile(ctx, f, "text/csv")
            }
        },
    )

    if (locked) {
        LockScreen(onUnlock = { requestUnlock() })
    } else {
        // ───── dialogs ─────
        editIndex?.let { i ->
            val n = cfg.numbers.getOrNull(i)
            if (n != null) {
                TargetDialog(
                    number = n, name = cfg.names[n], current = cfg.overrides[n],
                    defCalls = cfg.calls, defHangUp = if (cfg.limitOn) cfg.durSec else null, defGap = cfg.gapSec,
                    onSave = { ov ->
                        if (ov == null) cfg.overrides.remove(n) else cfg.overrides[n] = ov
                        cfg.save()
                        editIndex = null
                    },
                    onDismiss = { editIndex = null },
                )
            }
        }
        if (showLog) {
            CallLogDialog(
                queued = cfg.numbers,
                onDismiss = { showLog = false },
                onAdd = { picks ->
                    picks.forEach { e ->
                        cfg.numbers.add(e.number)
                        if (e.name.isNotBlank()) cfg.names[e.number] = e.name
                    }
                    cfg.save()
                    clearFinished()
                    showLog = false
                },
            )
        }
        if (showSave) {
            SaveListDialog(
                onSave = { name ->
                    ListStore.put(prefs, name, cfg.toJson(false))
                    Toast.makeText(ctx, "Saved \"$name\"", Toast.LENGTH_SHORT).show()
                    showSave = false
                },
                onDismiss = { showSave = false },
            )
        }
        if (showLists) {
            var listsTick by remember { mutableStateOf(0) }
            val saved = remember(listsTick) { ListStore.all(prefs).map { it.first } }
            ListsDialog(
                lists = saved,
                onLoad = { name ->
                    ListStore.all(prefs).firstOrNull { it.first == name }?.let { cfg.applyJson(it.second) }
                    cfg.save()
                    clearFinished()
                    showLists = false
                },
                onDelete = { name ->
                    ListStore.remove(prefs, name)
                    listsTick++
                },
                onDismiss = { showLists = false },
            )
        }
        if (showSchedule) {
            ScheduleDialog(
                onSchedule = { atMs ->
                    Scheduler.schedule(ctx, atMs, cfg.buildPlan().copy(dry = false))
                    uiTick++
                    showSchedule = false
                    Toast.makeText(ctx, "Scheduled", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showSchedule = false },
            )
        }
        inbox?.let { raw ->
            ImportDialog(
                raw = raw,
                onAdd = { nums ->
                    val r = addChecked(cfg, nums.joinToString(","))
                    reportAdd(ctx, r)
                    cfg.save()
                    clearFinished()
                    ShareInbox.pending.value = null
                },
                onDismiss = { ShareInbox.pending.value = null },
            )
        }

        Column(Modifier.fillMaxSize()) {
            Header(running)
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(170)) + slideInHorizontally(tween(210)) { dir * it / 10 }) togetherWith
                        (fadeOut(tween(110)) + slideOutHorizontally(tween(210)) { -dir * it / 10 })
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> DialTab(cfg, sims, running, checkpoint, scheduled, actions)
                    1 -> LogTab(cfg)
                    2 -> ConfigTab(cfg, running, sims.size)
                    else -> SystemTab(
                        granted = granted, refresh = refresh, running = running,
                        onGrant = { permLauncher.launch(permsToRequest()) },
                        onExportBackup = {
                            scope.launch {
                                val f = withContext(Dispatchers.IO) {
                                    val o = JSONObject()
                                        .put("app", "squeve-redail")
                                        .put("config", cfg.toJson(true))
                                        .put("lists", ListStore.toJson(prefs))
                                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                                    File(dir, "squeve-redail-backup.json").also { it.writeText(o.toString(2)) }
                                }
                                shareFile(ctx, f, "application/json")
                            }
                        },
                        onImportBackup = { backupPicker.launch("*/*") },
                    )
                }
            }
            if (!imeOpen) {
                UndoBar(cfg)
                ControlDock(
                    running = running,
                    startLabel = if (cfg.dryRun) "START DRY RUN" else "START REDIAL",
                    onStart = { startRun() },
                    onPauseResume = { wasPaused ->
                        sendAction(ctx, if (wasPaused) RedialService.ACTION_RESUME else RedialService.ACTION_PAUSE)
                    },
                    onStop = { sendAction(ctx, RedialService.ACTION_STOP) },
                )
                TabBar(tab) { tab = it }
            }
        }
    }
}

// ───────────────────────── Header, tabs, dock ─────────────────────────
@Composable
private fun Header(running: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "SQUEVE REDAIL", fontFamily = Display, fontWeight = FontWeight.Bold,
                fontSize = 20.sp, letterSpacing = 3.sp, color = Fg, maxLines = 1,
            )
            Text(
                "AUTO-DIAL ENGINE", fontFamily = Display, fontWeight = FontWeight.Medium,
                fontSize = 10.sp, letterSpacing = 3.sp, color = Cyan, maxLines = 1,
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, if (running) Mint else Hair, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseDot(running)
            Spacer(Modifier.width(8.dp))
            Text(
                if (running) "ACTIVE" else "STANDBY", fontFamily = Mono,
                fontSize = 11.sp, letterSpacing = 2.sp, color = if (running) Mint else Dim,
            )
        }
    }
}

@Composable
private fun PulseDot(active: Boolean) {
    if (active) {
        val t = rememberInfiniteTransition(label = "pulse")
        val a by t.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
            label = "alpha",
        )
        // Read inside graphicsLayer: animates without recomposing.
        Box(Modifier.size(10.dp).graphicsLayer { alpha = a }.background(Mint, CircleShape))
    } else {
        Box(Modifier.size(10.dp).background(Dim, CircleShape))
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(0 to "DIAL", 1 to "LOG", 2 to "CONFIG", 3 to "SYSTEM")
    Column(Modifier.fillMaxWidth().background(Dock)) {
        Hairline()
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, (kind, label) ->
                val sel = i == selected
                val tint by animateColor(if (sel) Cyan else Dim)
                Column(
                    Modifier.weight(1f).pressable { onSelect(i) }.padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TabIcon(kind, tint, 24.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label, fontFamily = Display, fontSize = 11.sp, letterSpacing = 2.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = tint,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(26.dp).height(2.dp).background(if (sel) Cyan else Color.Transparent))
                }
            }
        }
    }
}

/** Hand-drawn tab icons: 0 keypad, 1 log lines, 2 sliders, 3 power. */
@Composable
private fun TabIcon(kind: Int, tint: Color, dp: Dp = 24.dp) {
    Canvas(Modifier.size(dp)) {
        val s = size.minDimension
        val sw = s * 0.09f
        when (kind) {
            0 -> {
                for (r in 0..3) {
                    for (c in 0..2) {
                        drawCircle(tint, radius = s * 0.07f, center = Offset(s * (0.2f + 0.3f * c), s * (0.14f + 0.24f * r)))
                    }
                }
            }
            1 -> {
                for (r in 0..2) {
                    val y = s * (0.25f + 0.25f * r)
                    drawCircle(tint, radius = s * 0.05f, center = Offset(s * 0.14f, y))
                    drawLine(tint, Offset(s * 0.32f, y), Offset(s * 0.9f, y), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
            2 -> {
                val ys = listOf(0.22f, 0.5f, 0.78f)
                val xs = listOf(0.65f, 0.3f, 0.55f)
                for (r in 0..2) {
                    val y = s * ys[r]
                    drawLine(tint, Offset(s * 0.1f, y), Offset(s * 0.9f, y), strokeWidth = sw, cap = StrokeCap.Round)
                    drawCircle(Dock, radius = s * 0.12f, center = Offset(s * xs[r], y))
                    drawCircle(tint, radius = s * 0.12f, center = Offset(s * xs[r], y), style = Stroke(width = sw))
                }
            }
            else -> {
                drawArc(
                    tint, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(s * 0.15f, s * 0.2f), size = Size(s * 0.7f, s * 0.7f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
                drawLine(tint, Offset(s * 0.5f, s * 0.08f), Offset(s * 0.5f, s * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun animateColor(target: Color): State<Color> =
    androidx.compose.animation.animateColorAsState(target, tween(200), label = "tint")

/** Always visible, whichever tab is open. */
@Composable
private fun ControlDock(
    running: Boolean,
    startLabel: String,
    onStart: () -> Unit,
    onPauseResume: (Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val paused by RedialService.paused.collectAsState()
    Column(Modifier.fillMaxWidth().background(Dock)) {
        Hairline()
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            if (!running) {
                NeonButton(
                    startLabel, strong = true,
                    icon = { Glyph(Icons.Filled.PlayArrow, it, 22.dp) },
                    onClick = onStart,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        if (paused) "RESUME" else "PAUSE",
                        modifier = Modifier.weight(1f).height(48.dp),
                        icon = { if (paused) Glyph(Icons.Filled.PlayArrow, it, 20.dp) else PauseGlyph(it) },
                    ) { onPauseResume(paused) }
                    NeonButton(
                        "STOP", brush = DangerBrush, textColor = Color.White, strong = true,
                        modifier = Modifier.weight(1f),
                        icon = { StopGlyph(it) },
                        onClick = onStop,
                    )
                }
            }
        }
    }
}

// ───────────────────────── DIAL tab ─────────────────────────
@Composable
private fun DialTab(
    cfg: Config, sims: List<SimOption>, running: Boolean,
    checkpoint: Checkpoint?, scheduled: Scheduled?, a: DialActions,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (checkpoint != null) ResumeBanner(checkpoint, a.resume, a.discardResume)
        if (scheduled != null) ScheduleBanner(scheduled, a.cancelSchedule)

        HeroPanel(
            jobCount = cfg.numbers.size,
            totalCalls = cfg.numbers.sumOf { cfg.callsFor(it) },
            names = cfg.names,
        )
        SummaryHost(cfg, a)

        GlassCard {
            SectionTitle("TARGETS")
            AddNumberRow(enabled = !running, onAdd = a.add, a = a)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "QUEUE · ${cfg.numbers.size}",
                    fontFamily = Mono, fontSize = 13.sp, letterSpacing = 3.sp, color = Dim,
                    modifier = Modifier.weight(1f),
                )
                if (!running) {
                    if (cfg.numbers.isNotEmpty()) LinkButton("SAVE LIST") { a.saveList() }
                    LinkButton("LISTS") { a.lists() }
                    if (cfg.numbers.isNotEmpty()) {
                        LinkButton("CLEAR") {
                            cfg.numbers.clear()
                            cfg.save()
                            clearFinished()
                        }
                    }
                }
            }
            if (cfg.numbers.isEmpty()) {
                Text(
                    "No targets yet.", fontFamily = Mono, fontSize = 13.sp, color = Dim,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (!running && cfg.numbers.isNotEmpty()) {
                Caption("Tap a number to give it its own calls, hang-up, interval or audio.")
            }
            QueueList(
                numbers = cfg.numbers, names = cfg.names, cfg = cfg, canEdit = !running,
                onEdit = a.edit,
                onRemove = {
                    cfg.removeWithUndo(it)
                    cfg.save()
                    clearFinished()
                },
                onMove = { from, to ->
                    cfg.move(from, to)
                    cfg.save()
                },
            )
        }

        GlassCard {
            SectionTitle("LAUNCH")

            if (sims.size > 1) {
                FieldLabel("CALL WITH")
                val chosen = sims.firstOrNull { it.slot == cfg.simSlot }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegChip("DEFAULT", chosen == null, !running, Modifier.weight(1f)) { cfg.simSlot = -1 }
                    sims.forEach { s ->
                        SegChip("SIM ${s.slot + 1}", chosen?.slot == s.slot, !running, Modifier.weight(1f)) {
                            cfg.simSlot = s.slot
                        }
                    }
                }
                Caption(chosen?.label ?: "Uses your phone's default calling SIM")
                Spacer(Modifier.height(16.dp))
            }

            FieldLabel("CALL ORDER")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallOrder.values().forEach { o ->
                    SegChip(o.title(), cfg.order == o, !running, Modifier.weight(1f)) { cfg.order = o }
                }
            }
            Caption(cfg.order.hint())

            Spacer(Modifier.height(14.dp))
            Hairline()
            ToggleRow(
                "Dry run",
                "Walk through the order and timing without placing any call.",
                cfg.dryRun, !running,
            ) { cfg.dryRun = it }
            Hairline()
            Spacer(Modifier.height(12.dp))

            val total = cfg.numbers.sumOf { cfg.callsFor(it) }
            val customCount = cfg.numbers.count { cfg.overrides[it] != null }
            if (cfg.numbers.isEmpty()) {
                Text("Add at least one target to begin.", fontFamily = Mono, fontSize = 12.sp, color = Dim)
            } else {
                Text(
                    "${cfg.numbers.size} TARGETS · $total CALLS" +
                        (if (customCount > 0) " · $customCount CUSTOM" else ""),
                    fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp, color = Fg,
                )
                val allLimited = cfg.numbers.all { cfg.hangUpFor(it) != null }
                val timing = buildString {
                    append(if (cfg.limitOn) "${cfg.durSec}s hang-up" else "no hang-up limit")
                    append(if (cfg.gapRandom) " · ${cfg.gapSec}-${maxOf(cfg.gapMaxSec, cfg.gapSec)}s gap" else " · ${cfg.gapSec}s gap")
                    if (allLimited) {
                        val avgGap = if (cfg.gapRandom) (cfg.gapSec + maxOf(cfg.gapMaxSec, cfg.gapSec)) / 2 else cfg.gapSec
                        val secs = cfg.numbers.sumOf { n ->
                            cfg.callsFor(n).toLong() * ((cfg.hangUpFor(n) ?: 0) + avgGap)
                        }
                        append(" · up to ~${spanText(secs)}")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(timing, fontFamily = Mono, fontSize = 12.sp, color = Dim)
            }
            Spacer(Modifier.height(12.dp))
            GhostButton(
                "SCHEDULE START",
                enabled = !running && cfg.numbers.isNotEmpty(),
                icon = { Glyph(Icons.Filled.DateRange, it, 16.dp) },
            ) { a.schedule() }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroPanel(jobCount: Int, totalCalls: Int, names: Map<String, String>) {
    val ctx = LocalContext.current
    val status by RedialService.status.collectAsState()
    val paused by RedialService.paused.collectAsState()
    val running = isRunning(status)
    val p = status.progress()
    val finished = status is EngineState.Finished
    val inCall = status as? EngineState.InCall
    val why by RedialService.waitReason.collectAsState()
    val isDry by RedialService.dry.collectAsState()

    val fraction = when {
        finished -> 1f
        p != null -> (p.step - 1).toFloat() / p.steps.toFloat()
        else -> 0f
    }
    val animated = animateFloatAsState(targetValue = fraction, animationSpec = tween(700), label = "ring")

    // The glow only animates while a run is active, so an idle screen costs nothing.
    val glow: State<Float> = if (running) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0.15f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
            label = "glowAlpha",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val label = when (status) {
        is EngineState.Dialing -> "DIALING"
        is EngineState.InCall -> "IN CALL"
        is EngineState.Cooldown -> "COOLDOWN"
        is EngineState.Paused -> if (why != null) "WAITING" else "PAUSED"
        is EngineState.Finished -> "COMPLETE"
        is EngineState.Idle -> "READY"
    }
    val cooldownUntil = (status as? EngineState.Cooldown)?.untilMs

    GlassCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(172.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sweep = animated.value
                    val strokeW = 12.dp.toPx()
                    val inset = strokeW / 2 + 8.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    rotate(degrees = -90f) {
                        drawArc(
                            color = Hair, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            topLeft = topLeft, size = arcSize, style = Stroke(width = strokeW),
                        )
                        if (sweep > 0.001f) {
                            if (running) {
                                drawArc(
                                    color = Cyan.copy(alpha = glow.value * 0.5f),
                                    startAngle = 0f, sweepAngle = 360f * sweep, useCenter = false,
                                    topLeft = topLeft, size = arcSize,
                                    style = Stroke(width = strokeW * 2.2f, cap = StrokeCap.Round),
                                )
                            }
                            drawArc(
                                brush = RingBrush,
                                startAngle = 0f, sweepAngle = 360f * sweep, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isDry && running) "DRY · $label" else label, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 3.sp,
                        color = if (running) Cyan else Dim,
                    )
                    PercentText(animated)
                }
            }

            Spacer(Modifier.height(12.dp))
            if (p != null) {
                Text(p.number, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Fg)
                val who = names[p.number]
                if (who != null) {
                    Text(who, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Cyan, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "CALL ${p.step}/${p.steps} · THIS NUMBER ${p.attempt}/${p.total}",
                    fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp, color = Dim,
                )
            } else if (finished) {
                Text(
                    "${(status as EngineState.Finished).results.size} CALLS PLACED",
                    fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp, color = Mint,
                )
            } else {
                Text(
                    "$jobCount TARGETS · $totalCalls CALLS",
                    fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp, color = Dim,
                )
            }
            if (cooldownUntil != null) {
                Spacer(Modifier.height(6.dp))
                CooldownText(cooldownUntil)
            }
            if (inCall != null && inCall.hasLimit) {
                Spacer(Modifier.height(12.dp))
                HangUpRow(
                    inCall,
                    onHold = { sendAction(ctx, RedialService.ACTION_HOLD) },
                    onRearm = { sendAction(ctx, RedialService.ACTION_REARM) },
                )
            }
            if (paused && running && status !is EngineState.Paused) {
                Spacer(Modifier.height(6.dp))
                Text("PAUSING AFTER THIS CALL", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp, color = Magenta)
            }
            if (why != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "WAITING · " + why!!.uppercase(), fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp,
                    color = Magenta, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Reads the animated value in its own scope so only this text recomposes while it counts. */
@Composable
private fun PercentText(animated: State<Float>) {
    Text(
        "${(animated.value * 100).roundToInt()}%", fontFamily = Display,
        fontWeight = FontWeight.Bold, fontSize = 38.sp, color = Fg,
    )
}

/** Auto hang-up countdown with the option to let this call run until it ends by itself. */
@Composable
private fun HangUpRow(s: EngineState.InCall, onHold: () -> Unit, onRearm: () -> Unit) {
    val at = s.hangUpAt
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (at != null) {
            HangUpCountdown(at)
            GhostButton("KEEP CALL", modifier = Modifier.width(124.dp).height(40.dp), onClick = onHold)
        } else {
            Text("TIMER OFF", fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp, color = Magenta)
            GhostButton(
                "RE-ARM", modifier = Modifier.width(124.dp).height(40.dp),
                icon = { Glyph(Icons.Filled.Refresh, it, 16.dp) },
                onClick = onRearm,
            )
        }
    }
}

@Composable
private fun HangUpCountdown(at: Long) {
    val now by produceState(System.currentTimeMillis(), at) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val left = ((at - now) / 1000 + 1).coerceAtLeast(0)
    Text("HANGS UP IN ${clock(left)}", fontFamily = Mono, fontSize = 13.sp, letterSpacing = 1.sp, color = Cyan)
}

/** Own composable so the once-per-half-second tick only recomposes this one line. */
@Composable
private fun CooldownText(untilMs: Long) {
    val now by produceState(System.currentTimeMillis(), untilMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val left = ((untilMs - now) / 1000 + 1).coerceAtLeast(0)
    Text("NEXT CALL IN ${left}s", fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp, color = Cyan)
}

// ───────────────────────── Queue ─────────────────────────
@Composable
private fun AddNumberRow(enabled: Boolean, onAdd: (String) -> Unit, a: DialActions) {
    // Keystrokes only recompose this row, not the whole screen. Saved so tab switches keep the text.
    var input by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("Number(s), comma separated") },
        singleLine = false,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        enabled = enabled,
        colors = fieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    NeonButton(
        "ADD TO QUEUE", enabled = enabled && input.isNotBlank(),
        icon = { Glyph(Icons.Filled.Add, it, 20.dp) },
    ) {
        onAdd(input)
        input = ""
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceButton("CONTACTS", Icons.Filled.Person, enabled, Modifier.weight(1f), a.contacts)
        SourceButton("CALL LOG", Icons.Filled.Call, enabled, Modifier.weight(1f), a.callLog)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceButton("PASTE", Icons.Filled.Create, enabled, Modifier.weight(1f), a.paste)
        SourceButton("IMPORT FILE", Icons.Filled.Share, enabled, Modifier.weight(1f), a.importFile)
    }
}

@Composable
private fun SourceButton(
    label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    GhostButton(
        label, enabled = enabled,
        modifier = modifier.height(44.dp),
        icon = { Glyph(icon, it, 16.dp) },
        onClick = onClick,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Hair,
    disabledBorderColor = Hair,
    cursorColor = Cyan,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = Dim,
    disabledLabelColor = Dim,
    focusedTextColor = Fg,
    unfocusedTextColor = Fg,
    disabledTextColor = Dim,
)

@Composable
private fun QueueList(
    numbers: List<String>, names: Map<String, String>, cfg: Config,
    canEdit: Boolean, onEdit: (Int) -> Unit, onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit,
) {
    val status by RedialService.status.collectAsState()
    val finished = status is EngineState.Finished
    val p = status.progress()
    val listState = rememberLazyListState()
    val list = numbers.toList()

    // Keep the number being dialled in view during a run.
    val currentIndex = p?.jobIndex
    LaunchedEffect(currentIndex) {
        if (currentIndex != null && currentIndex in numbers.indices) listState.animateScrollToItem(currentIndex)
    }

    // Stable keys, so each row keeps its own swipe and entry state when the list changes.
    val counts = HashMap<String, Int>()
    val keys = list.map { n ->
        val c = counts[n] ?: 0
        counts[n] = c + 1
        "$n#$c"
    }
    val heights = remember { HashMap<String, Float>() }

    // Drag to reorder: rows slide aside while dragging; the move is applied on release.
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowH by remember { mutableFloatStateOf(150f) }
    val from = dragFrom
    val target = if (from == null) -1 else (from + (dragY / rowH).roundToInt()).coerceIn(0, (list.size - 1).coerceAtLeast(0))

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = if (canEdit) 460.dp else 340.dp),
    ) {
        itemsIndexed(list, key = { i, _ -> keys.getOrElse(i) { "row$i" } }) { i, n ->
            val completed = p?.completed?.getOrNull(i) ?: 0
            val callsEach = cfg.callsFor(n)
            // 0 = queued, 1 = current, 2 = done
            val state = when {
                finished -> 2
                p == null -> 0
                completed >= callsEach -> 2
                i == p.jobIndex -> 1
                else -> 0
            }
            val shiftPx = if (from == null) {
                0f
            } else {
                when {
                    i == from -> dragY
                    from < i && i <= target -> -rowH
                    target <= i && i < from -> rowH
                    else -> 0f
                }
            }
            // New rows ease in.
            val visible = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.zIndex(if (i == from) 1f else 0f).graphicsLayer { translationY = shiftPx },
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            ) {
                SwipeToRemove(
                    enabled = canEdit && from == null,
                    onRemove = { onRemove(i) },
                    modifier = Modifier.onSizeChanged { sz ->
                        if (sz.height > 0) heights[keys.getOrElse(i) { "row$i" }] = sz.height.toFloat()
                    },
                ) {
                    QueueItem(
                        index = i, number = n, name = names[n], custom = cfg.customSummary(n), state = state,
                        doneCount = if (finished) callsEach else completed, total = callsEach,
                        canRemove = canEdit, dragging = i == from,
                        onEdit = { onEdit(i) }, onRemove = { onRemove(i) },
                        onDragStart = {
                            rowH = heights[keys.getOrElse(i) { "row$i" }] ?: rowH
                            dragY = 0f
                            dragFrom = i
                        },
                        onDrag = { dy ->
                            dragY += dy
                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == dragFrom }
                            if (info != null) {
                                val top = info.offset + dragY
                                val bottom = top + info.size
                                val viewport = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).toFloat()
                                val d = when {
                                    top < 0f -> -24f
                                    bottom > viewport -> 24f
                                    else -> 0f
                                }
                                if (d != 0f) dragY += listState.dispatchRawDelta(d)
                            }
                        },
                        onDragEnd = {
                            val f = dragFrom
                            if (f != null) {
                                val to = (f + (dragY / rowH).roundToInt()).coerceIn(0, (numbers.size - 1).coerceAtLeast(0))
                                if (to != f) onMove(f, to)
                            }
                            dragFrom = null
                            dragY = 0f
                        },
                    )
                }
            }
        }
    }
}

/** Swipe a row sideways far enough to remove it. */
@Composable
private fun SwipeToRemove(
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val removeNow by rememberUpdatedState(onRemove)
    var x by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        if (x != 0f) {
            val frac = (abs(x) / width).coerceIn(0f, 1f)
            Box(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Magenta.copy(alpha = 0.18f + 0.5f * frac))
                    .padding(horizontal = 18.dp),
                contentAlignment = if (x > 0f) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text("REMOVE", fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp, color = Color.White)
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = x }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val go = abs(x) > width * 0.4f
                                scope.launch {
                                    if (go) {
                                        val dir = if (x > 0f) 1f else -1f
                                        animate(x, dir * width, animationSpec = tween(140)) { v, _ -> x = v }
                                        if (Theming.haptics.value) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        removeNow()
                                    } else {
                                        animate(x, 0f, animationSpec = tween(160)) { v, _ -> x = v }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { animate(x, 0f, animationSpec = tween(160)) { v, _ -> x = v } }
                            },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                x += dx
                            },
                        )
                    }
                },
        ) { content() }
    }
}

@Composable
private fun DragHandle(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        for (k in 0..2) {
            val y = h * (0.25f + 0.25f * k)
            drawLine(color, Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Appears for a few seconds after a number is removed. */
@Composable
private fun UndoBar(cfg: Config) {
    val r = cfg.removed
    if (r != null) {
        LaunchedEffect(r) {
            delay(5000)
            if (cfg.removed === r) cfg.removed = null
        }
        Row(
            Modifier.fillMaxWidth().background(Dock).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Removed ${r.number}", fontFamily = Mono, fontSize = 13.sp, color = Fg,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            LinkButton("UNDO") { cfg.undoRemove() }
        }
    }
}

@Composable
private fun QueueItem(
    index: Int, number: String, name: String?, custom: String?, state: Int, doneCount: Int, total: Int,
    canRemove: Boolean, dragging: Boolean,
    onEdit: () -> Unit, onRemove: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
) {
    val current = state == 1
    val done = state == 2
    val shape = RoundedCornerShape(12.dp)
    val haptic = LocalHapticFeedback.current
    val startNow by rememberUpdatedState(onDragStart)
    val dragNow by rememberUpdatedState(onDrag)
    val endNow by rememberUpdatedState(onDragEnd)
    Column(
        Modifier
            .pressable(enabled = canRemove, onClick = onEdit)
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(
                when {
                    dragging -> Violet.copy(alpha = 0.22f)
                    current -> Cyan.copy(alpha = 0.10f)
                    else -> Color.Transparent
                },
            )
            .border(1.dp, if (dragging || current) Cyan.copy(alpha = 0.7f) else Hair, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                if (done) {
                    Glyph(Icons.Filled.Check, Mint, 16.dp)
                } else {
                    Text(
                        "%02d".format(index + 1), fontFamily = Mono, fontSize = 13.sp,
                        color = if (current) Cyan else Dim,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    number, fontFamily = Mono, fontSize = 17.sp,
                    color = if (done) Dim else Fg,
                )
                if (name != null) {
                    Text(name, fontSize = 11.sp, color = if (current) Cyan else Dim, maxLines = 1)
                }
                if (custom != null) {
                    Text(
                        custom, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 0.5.sp,
                        color = Lilac, maxLines = 1,
                    )
                }
            }
            if (canRemove) {
                // Press and hold the handle, then drag to reorder.
                Box(
                    Modifier
                        .size(36.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    if (Theming.haptics.value) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    startNow()
                                },
                                onDragEnd = { endNow() },
                                onDragCancel = { endNow() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragNow(amount.y)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { DragHandle(if (dragging) Cyan else Dim) }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).pressable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) { Glyph(Icons.Filled.Close, Dim, 18.dp) }
            } else {
                Text(
                    "$doneCount/$total", fontFamily = Mono, fontSize = 14.sp,
                    color = if (current) Cyan else Dim,
                )
            }
        }
        if (current && total > 0) {
            Spacer(Modifier.height(8.dp))
            MiniBar(doneCount.toFloat() / total)
        }
    }
}

@Composable
private fun MiniBar(fraction: Float) {
    val f by animateFloatAsState(targetValue = fraction.coerceIn(0f, 1f), animationSpec = tween(400), label = "bar")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Hair)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(NeonBrush))
    }
}

// ───────────────────────── CONFIG tab ─────────────────────────
@Composable
private fun ConfigTab(cfg: Config, running: Boolean, simCount: Int) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlassCard {
            SectionTitle("TIMING")
            ToggleRow("Limit call duration", null, cfg.limitOn, !running) { cfg.limitOn = it }
            if (cfg.limitOn) {
                StepperRow("Hang up after", cfg.durSec, 5..600, !running, { "${it / 60}m ${it % 60}s" }) {
                    cfg.durSec = it
                }
                Caption("The countdown shows on the floating control, with a KEEP CALL button to let a call run on.")
            }
            Spacer(Modifier.height(8.dp))
            Hairline()
            StepperRow("Calls per number", cfg.calls, 1..500, !running, { "$it" }) { cfg.calls = it }
            Hairline()
            StepperRow("Interval between calls", cfg.gapSec, 1..300, !running, { "${it}s" }) { cfg.gapSec = it }
            ToggleRow(
                "Random interval", "Pick a different wait each time, between the interval and the limit below.",
                cfg.gapRandom, !running,
            ) { cfg.gapRandom = it }
            if (cfg.gapRandom) {
                StepperRow("Up to", maxOf(cfg.gapMaxSec, cfg.gapSec), cfg.gapSec..600, !running, { "${it}s" }) {
                    cfg.gapMaxSec = it
                }
            }
        }

        GlassCard {
            SectionTitle("ANSWERS")
            StepperRow("Count as answered after", cfg.answerSec, 1..60, !running, { "${it}s" }) { cfg.answerSec = it }
            Caption("Android only reveals call length. If your phone logs ring time as call time, raise this.")
            Spacer(Modifier.height(8.dp))
            Hairline()
            ToggleRow(
                "Skip to next number once answered",
                "Stops calling a number after it has been answered.",
                cfg.skipAnswered, !running,
            ) { cfg.skipAnswered = it }
            if (cfg.skipAnswered) {
                StepperRow("Answers needed", cfg.answersToStop, 1..10, !running, { "$it" }) { cfg.answersToStop = it }
            }
        }

        GlassCard {
            SectionTitle("AUDIO")
            ToggleRow("Start call on speaker", null, cfg.speakerOn, !running) { cfg.speakerOn = it }
            ToggleRow(
                "Mute microphone",
                "Best effort: some phones ignore mute on normal calls.",
                cfg.muteOn, !running,
            ) { cfg.muteOn = it }
        }

        if (simCount > 1) {
            GlassCard {
                SectionTitle("SIM STRATEGY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FIXED", "FALL BACK", "ALTERNATE").forEachIndexed { i, label ->
                        SegChip(label, cfg.simMode == i, !running, Modifier.weight(1f)) { cfg.simMode = i }
                    }
                }
                Caption(
                    when (cfg.simMode) {
                        1 -> "Uses your chosen SIM. If a call can't start, the other SIM tries it."
                        2 -> "Swaps SIM on every call, starting with your chosen one."
                        else -> "Every call uses the SIM chosen on the DIAL tab."
                    },
                )
            }
        }

        GlassCard {
            SectionTitle("SAFETY")
            StepperRow("Daily call cap", cfg.dailyCap, 0..2000, !running, { if (it == 0) "OFF" else "$it" }) {
                cfg.dailyCap = it
            }
            Caption("Ends a run once this many calls have been placed today.")
            Spacer(Modifier.height(8.dp))
            Hairline()
            ToggleRow("Quiet hours", "Waits instead of calling during these hours.", cfg.quietOn, !running) {
                cfg.quietOn = it
            }
            if (cfg.quietOn) {
                StepperRow("From", cfg.quietStart / 15, 0..95, !running, { hm(it * 15) }) { cfg.quietStart = it * 15 }
                StepperRow("Until", cfg.quietEnd / 15, 0..95, !running, { hm(it * 15) }) { cfg.quietEnd = it * 15 }
            }
            Hairline()
            ToggleRow(
                "Pause on low battery", "Waits below 15% until the phone is charging.",
                cfg.batteryPause, !running,
            ) { cfg.batteryPause = it }
            Caption("A run also waits by itself in airplane mode, with no signal, or while you are on another call.")
        }

        GlassCard {
            SectionTitle("BEHAVIOUR")
            ToggleRow("Alert when a run finishes", "Sound and vibration with a notification.", cfg.alertOn, true) {
                cfg.alertOn = it
            }
            ToggleRow("Allow duplicate numbers", "Off: a number already in the queue is skipped.", cfg.allowDup, true) {
                cfg.allowDup = it
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cfg.countryCode,
                onValueChange = { cfg.countryCode = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Country code for local numbers, e.g. 233") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Caption("Numbers starting with 0 become +code… when this is set.")
        }

        GlassCard {
            SectionTitle("FLOATING BUBBLE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DARK", "LIGHT", "NEON").forEachIndexed { i, label ->
                    SegChip(label, cfg.bubbleLook == i, true, Modifier.weight(1f)) { cfg.bubbleLook = i }
                }
            }
            Caption("If the bubble looks washed out on your phone, try another look, then preview it.")
            Spacer(Modifier.height(12.dp))
            GhostButton(
                "PREVIEW BUBBLE",
                icon = { Glyph(Icons.Filled.PlayArrow, it, 16.dp) },
            ) {
                if (Settings.canDrawOverlays(ctx)) {
                    cfg.save()
                    showBubblePreview(ctx)
                } else {
                    Toast.makeText(ctx, "Enable floating control in the SYSTEM tab first", Toast.LENGTH_SHORT).show()
                }
            }
        }

        GlassCard {
            SectionTitle("APPEARANCE")
            FieldLabel("THEME")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DARK", "LIGHT", "SYSTEM").forEachIndexed { i, label ->
                    SegChip(label, cfg.themeMode == i, true, Modifier.weight(1f)) { cfg.themeMode = i }
                }
            }
            Spacer(Modifier.height(14.dp))
            FieldLabel("SIZE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("COMPACT", "NORMAL", "LARGE").forEachIndexed { i, label ->
                    SegChip(label, cfg.uiSize == i, true, Modifier.weight(1f)) { cfg.uiSize = i }
                }
            }
            Spacer(Modifier.height(14.dp))
            FieldLabel("ACCENT")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ACCENTS.forEachIndexed { i, a ->
                    val sel = cfg.accentIdx == i
                    Box(
                        Modifier
                            .size(36.dp)
                            .pressable { cfg.accentIdx = i }
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(a.main, a.second)))
                            .border(if (sel) 3.dp else 1.dp, if (sel) Fg else Hair, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow("Pure black", "Deeper dark background, good on OLED screens.", cfg.blackOn, true) { cfg.blackOn = it }
            ToggleRow("Haptic feedback", "Small vibrations when you tap buttons or drag rows.", cfg.hapticsOn, true) {
                cfg.hapticsOn = it
            }
        }

        GlassCard {
            SectionTitle("PRIVACY")
            ToggleRow(
                "Lock the app",
                "Asks for your phone's PIN, pattern or fingerprint when you come back to the app.",
                cfg.lockOn, true,
            ) { want ->
                val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!want) {
                    cfg.lockOn = false
                } else if (km.isDeviceSecure) {
                    cfg.lockOn = true
                } else {
                    Toast.makeText(ctx, "Set a screen lock on your phone first", Toast.LENGTH_LONG).show()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    // Slider drags only touch local state; the screen is told once, when the finger lifts.
    var live by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
            StepBtn("−", enabled) { onChange((value - 1).coerceIn(range)) }
            Text(
                format(live.roundToInt()), fontFamily = Mono, fontSize = 15.sp, color = Cyan,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 84.dp),
            )
            StepBtn("+", enabled) { onChange((value + 1).coerceIn(range)) }
        }
        Slider(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = { onChange(live.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Cyan,
                activeTrackColor = Cyan,
                inactiveTrackColor = Hair,
                disabledThumbColor = Dim,
                disabledActiveTrackColor = Dim,
                disabledInactiveTrackColor = Hair,
            ),
        )
    }
}

@Composable
private fun StepBtn(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .pressable(enabled, onClick = onClick)
            .clip(CircleShape)
            .border(1.dp, if (enabled) Hair else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontFamily = Display, fontSize = 22.sp, color = if (enabled) Cyan else Dim)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = Dim)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(it)
            },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Cyan,
                checkedBorderColor = Cyan,
                uncheckedThumbColor = Dim,
                uncheckedTrackColor = Hair,
                uncheckedBorderColor = Hair,
            ),
        )
    }
}

// ───────────────────────── SYSTEM tab ─────────────────────────
@Composable
private fun SystemTab(
    granted: Boolean,
    refresh: Int,
    running: Boolean,
    onGrant: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    val ctx = LocalContext.current
    val overlayOk = remember(refresh) { Settings.canDrawOverlays(ctx) }
    val batteryOk = remember(refresh) {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val installOk = remember(refresh) { Updater.canInstall(ctx) }
    val exactOk = remember(refresh) { Scheduler.canBeExact(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlassCard {
            SectionTitle("READINESS")
            StatusRow("Phone & call log", "Place calls, read call state", granted, "GRANT", onGrant)
            Hairline()
            StatusRow(
                "Floating control", "Progress card and bubble over other apps", overlayOk, "ENABLE",
            ) { openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
            Hairline()
            StatusRow(
                "Background running", "Keeps redialing with the screen off", batteryOk, "ALLOW",
            ) { openSettings(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
            Hairline()
            StatusRow(
                "Exact alarms", "Scheduled runs start on the minute", exactOk, "ALLOW",
            ) { openSettings(ctx, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
            Hairline()
            StatusRow(
                "Install updates", "Lets the app update itself", installOk, "ALLOW",
            ) { Updater.openInstallSettings(ctx) }
        }

        GlassCard {
            SectionTitle("BACKUP")
            Text(
                "Save your settings, queue and saved lists to a file, or restore them on this or another phone.",
                fontSize = 12.sp, color = Dim,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("EXPORT", modifier = Modifier.weight(1f).height(48.dp), onClick = onExportBackup)
                GhostButton("RESTORE", modifier = Modifier.weight(1f).height(48.dp), onClick = onImportBackup)
            }
        }

        UpdateCard(running)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusRow(title: String, subtitle: String, ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, if (ok) Mint else Magenta, 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 12.sp, color = Dim)
        }
        if (ok) {
            Text("READY", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp, color = Mint)
        } else {
            GhostButton(actionLabel, modifier = Modifier.width(104.dp).height(38.dp), onClick = onAction)
        }
    }
}

@Composable
private fun UpdateCard(running: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pct by remember { mutableStateOf(-1) }
    var pending by remember { mutableStateOf<Updater.Release?>(null) }
    var notes by remember { mutableStateOf<Updater.Release?>(null) }
    // Looked up once; PackageManager calls are not free inside composition.
    val versionLine = remember { "v${Updater.installedName(ctx)} · build ${Updater.installedBuild(ctx)}" }

    // First tap checks and shows what is new; the second tap downloads and installs.
    fun getUpdate() {
        if (busy) return
        val target = pending
        if (target != null && !Updater.canInstall(ctx)) {
            msg = "Allow installs from Squeve Redail, come back, then tap again."
            Updater.openInstallSettings(ctx)
            return
        }
        busy = true
        pct = -1
        msg = if (target != null) "Downloading build ${target.build}…" else "Checking for updates…"
        scope.launch {
            try {
                if (target != null) {
                    pct = 0
                    val apk = Updater.download(ctx, target.apkUrl) { p -> pct = p }
                    msg = "Opening installer…"
                    Updater.install(ctx, apk)
                } else {
                    val rel = Updater.latest()
                    val have = Updater.installedBuild(ctx)
                    when {
                        rel == null -> msg = "No release found yet."
                        rel.build <= have -> {
                            msg = "You're on the latest version (build $have)."
                            notes = rel
                        }
                        else -> {
                            pending = rel
                            notes = rel
                            msg = "Build ${rel.build} is ready to install."
                        }
                    }
                }
            } catch (e: Exception) {
                msg = "Update failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
                pct = -1
            }
        }
    }

    val pend = pending
    GlassCard {
        SectionTitle("UPDATES")
        Text(versionLine, fontFamily = Mono, fontSize = 13.sp, color = Dim)
        Spacer(Modifier.height(12.dp))
        NeonButton(
            if (busy) "PLEASE WAIT…" else if (pend != null) "INSTALL BUILD ${pend.build}" else "CHECK FOR UPDATES",
            enabled = !busy && !running,
            icon = { Glyph(Icons.Filled.Refresh, it, 20.dp) },
        ) { getUpdate() }
        if (running) {
            Spacer(Modifier.height(6.dp))
            Text("Stop redialing before updating.", fontSize = 12.sp, color = Dim)
        }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, fontFamily = Mono, fontSize = 12.sp, color = Cyan)
        }
        if (pct in 0..100) {
            Spacer(Modifier.height(8.dp))
            MiniBar(pct / 100f)
            Spacer(Modifier.height(4.dp))
            Text("$pct%", fontFamily = Mono, fontSize = 11.sp, color = Dim)
        }
        val n = notes
        if (n != null && n.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(10.dp))
            FieldLabel(if (pend != null) "WHAT IS NEW IN BUILD ${n.build}" else "IN THIS VERSION")
            Text(n.notes, fontSize = 12.sp, color = Dim)
        }
    }
}

// ───────────────────────── Building blocks ─────────────────────────
@Composable
private fun Glyph(icon: ImageVector, tint: Color, size: Dp = 18.dp) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun PauseGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) {
            Box(Modifier.width(4.dp).height(14.dp).background(color, RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
private fun StopGlyph(color: Color) {
    Box(Modifier.size(13.dp).background(color, RoundedCornerShape(3.dp)))
}

@Composable
private fun NeonButton(
    text: String,
    enabled: Boolean = true,
    brush: Brush = NeonBrush,
    textColor: Color = Ink,
    strong: Boolean = false,
    modifier: Modifier = Modifier,
    icon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val ink = if (enabled) textColor else Dim
    Box(
        modifier
            .pressable(enabled, strong, onClick)
            .fillMaxWidth()
            .height(48.dp)
            .drawBehind {
                if (enabled) {
                    // Cheap glow: a few translucent copies of the button, growing outward.
                    val alphas = listOf(0.16f, 0.09f, 0.05f)
                    alphas.forEachIndexed { i, a ->
                        val g = (4 + i * 5).dp.toPx()
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(-g, -g / 2 + 4.dp.toPx()),
                            size = Size(size.width + 2 * g, size.height + g),
                            cornerRadius = CornerRadius(14.dp.toPx() + g),
                            alpha = a,
                        )
                    }
                }
            }
            .clip(shape)
            .background(if (enabled) brush else DisabledBrush),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                icon(ink)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text, fontFamily = Display, fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp, fontSize = 15.sp, color = ink,
            )
        }
    }
}

@Composable
private fun GhostButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth().height(48.dp),
    icon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val ink = if (enabled) Cyan else Dim
    Box(
        modifier
            .pressable(enabled, onClick = onClick)
            .clip(shape)
            .border(1.dp, if (enabled) Cyan else Hair, shape),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                icon(ink)
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontFamily = Display, letterSpacing = 2.sp, fontSize = 13.sp, color = ink)
        }
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp, color = Cyan)
    }
}

/** Segmented choice chip, used for SIM and call order. */
@Composable
private fun SegChip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .pressable(enabled, onClick = onClick)
            .clip(shape)
            .background(if (selected) Cyan.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) Cyan else Hair, shape)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Cyan else Dim,
            maxLines = 1, softWrap = false,
        )
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(Cyan.copy(alpha = 0.55f), Violet.copy(alpha = 0.25f), Color.Transparent)),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(NeonBrush))
        Spacer(Modifier.width(8.dp))
        Text(
            text, fontFamily = Display, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, letterSpacing = 4.sp, color = Cyan,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 3.sp, color = Dim,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, fontSize = 12.sp, color = Dim, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
}

// ───────────────────────── Call log picker ─────────────────────────
@Composable
private fun CallLogDialog(
    queued: List<String>,
    onDismiss: () -> Unit,
    onAdd: (List<RecentCall>) -> Unit,
) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf<List<RecentCall>?>(null) }
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) {
            runCatching { loadRecentCalls(ctx) }.getOrDefault(emptyList())
        }
    }
    var filter by remember { mutableStateOf(0) }              // 0 all, 1 missed, 2 out, 3 in
    val picked = remember { mutableStateListOf<String>() }    // RecentCall.key values
    val queuedKeys = remember(queued.size) {
        queued.map { n -> n.filter { it.isDigit() }.takeLast(9) }.toSet()
    }

    val all = entries
    val shown = (all ?: emptyList()).filter {
        when (filter) {
            1 -> it.type == CallLog.Calls.MISSED_TYPE
            2 -> it.type == CallLog.Calls.OUTGOING_TYPE
            3 -> it.type == CallLog.Calls.INCOMING_TYPE
            else -> true
        }
    }
    val selectable = shown.filter { it.key !in queuedKeys }
    val allPicked = selectable.isNotEmpty() && selectable.all { it.key in picked }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("RECENT CALLS") }
                    if (selectable.isNotEmpty()) {
                        LinkButton(if (allPicked) "CLEAR" else "SELECT ALL") {
                            if (allPicked) {
                                selectable.forEach { picked.remove(it.key) }
                            } else {
                                selectable.forEach { if (it.key !in picked) picked.add(it.key) }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "MISSED", "OUT", "IN").forEachIndexed { i, label ->
                        SegChip(label, filter == i, true, Modifier.weight(1f)) { filter = i }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        all == null ->
                            Text("Loading…", fontFamily = Mono, fontSize = 13.sp, color = Dim)
                        shown.isEmpty() ->
                            Text("No calls to show.", fontFamily = Mono, fontSize = 13.sp, color = Dim)
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.key }) { e ->
                                CallLogRow(e, e.key in picked, e.key in queuedKeys) {
                                    if (e.key in picked) picked.remove(e.key) else picked.add(e.key)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("CANCEL", modifier = Modifier.weight(1f).height(48.dp), onClick = onDismiss)
                    NeonButton(
                        "ADD ${picked.size}", enabled = picked.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        onAdd((all ?: emptyList()).filter { it.key in picked })
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogRow(e: RecentCall, selected: Boolean, queued: Boolean, onToggle: () -> Unit) {
    val hasName = e.name.isNotBlank()
    Row(
        Modifier.fillMaxWidth().pressable(enabled = !queued, onClick = onToggle)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(selected, queued)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (hasName) e.name else e.number,
                fontFamily = if (hasName) Display else Mono, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, color = if (queued) Dim else Fg, maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(typeLabel(e.type), fontFamily = Mono, fontSize = 11.sp, color = typeColor(e.type))
                val rest = (if (hasName) "  ·  ${e.number}" else "") +
                    "  ·  ${ago(e.date)}" + (if (e.count > 1) "  ·  ×${e.count}" else "")
                Text(rest, fontFamily = Mono, fontSize = 11.sp, color = Dim, maxLines = 1)
            }
        }
        if (queued) {
            Text("QUEUED", fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp, color = Mint)
        }
    }
}

@Composable
private fun CheckMark(selected: Boolean, disabled: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(22.dp)
            .clip(shape)
            .background(if (selected) Cyan else Color.Transparent)
            .border(1.5.dp, if (selected) Cyan else if (disabled) Hair else Dim, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Glyph(Icons.Filled.Check, Ink, 16.dp)
    }
}

// ───────────────────────── Per-number settings ─────────────────────────
@Composable
private fun TargetDialog(
    number: String,
    name: String?,
    current: NumOverride?,
    defCalls: Int,
    defHangUp: Int?,
    defGap: Int,
    onSave: (NumOverride?) -> Unit,
    onDismiss: () -> Unit,
) {
    var callsOn by remember { mutableStateOf(current?.calls != null) }
    var calls by remember { mutableStateOf(current?.calls ?: defCalls) }
    // 0 = follow default, 1 = custom seconds, 2 = no limit
    var hangMode by remember {
        mutableStateOf(
            when (current?.hangUp) {
                null -> 0
                0 -> 2
                else -> 1
            },
        )
    }
    var hang by remember { mutableStateOf(current?.hangUp?.takeIf { it > 0 } ?: (defHangUp ?: 30)) }
    var gapOn by remember { mutableStateOf(current?.gap != null) }
    var gap by remember { mutableStateOf(current?.gap ?: defGap) }
    // 0 = follow default, 1 = on, 2 = off
    var spk by remember { mutableStateOf(when (current?.speaker) { null -> 0; true -> 1; false -> 2 }) }
    var mut by remember { mutableStateOf(when (current?.mute) { null -> 0; true -> 1; false -> 2 }) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 620.dp),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("TARGET SETTINGS")
                Text(number, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Fg)
                if (name != null) {
                    Text(name, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Cyan)
                }
                Caption("Anything left on default follows the CONFIG tab.")
                Spacer(Modifier.height(12.dp))
                Hairline()

                ToggleRow("Custom call count", "Default: $defCalls", callsOn, true) { callsOn = it }
                if (callsOn) {
                    StepperRow("Calls", calls, 1..500, true, { "$it" }) { calls = it }
                }
                Hairline()

                Spacer(Modifier.height(12.dp))
                FieldLabel("HANG UP AFTER")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegChip("DEFAULT", hangMode == 0, true, Modifier.weight(1f)) { hangMode = 0 }
                    SegChip("CUSTOM", hangMode == 1, true, Modifier.weight(1f)) { hangMode = 1 }
                    SegChip("NO LIMIT", hangMode == 2, true, Modifier.weight(1f)) { hangMode = 2 }
                }
                Caption("Default: " + (defHangUp?.let { "${it}s" } ?: "no limit"))
                if (hangMode == 1) {
                    StepperRow("Hang up after", hang, 5..600, true, { "${it / 60}m ${it % 60}s" }) { hang = it }
                }
                Spacer(Modifier.height(8.dp))
                Hairline()

                ToggleRow("Custom interval", "Default: ${defGap}s between calls", gapOn, true) { gapOn = it }
                if (gapOn) {
                    StepperRow("Interval", gap, 1..300, true, { "${it}s" }) { gap = it }
                }
                Hairline()

                Spacer(Modifier.height(12.dp))
                FieldLabel("SPEAKER")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegChip("DEFAULT", spk == 0, true, Modifier.weight(1f)) { spk = 0 }
                    SegChip("ON", spk == 1, true, Modifier.weight(1f)) { spk = 1 }
                    SegChip("OFF", spk == 2, true, Modifier.weight(1f)) { spk = 2 }
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("MICROPHONE MUTE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegChip("DEFAULT", mut == 0, true, Modifier.weight(1f)) { mut = 0 }
                    SegChip("MUTED", mut == 1, true, Modifier.weight(1f)) { mut = 1 }
                    SegChip("OPEN", mut == 2, true, Modifier.weight(1f)) { mut = 2 }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("RESET", modifier = Modifier.weight(1f).height(48.dp)) { onSave(null) }
                    NeonButton("SAVE", modifier = Modifier.weight(1f)) {
                        val ov = NumOverride(
                            calls = if (callsOn) calls else null,
                            hangUp = when (hangMode) {
                                1 -> hang
                                2 -> 0
                                else -> null
                            },
                            gap = if (gapOn) gap else null,
                            speaker = when (spk) {
                                1 -> true
                                2 -> false
                                else -> null
                            },
                            mute = when (mut) {
                                1 -> true
                                2 -> false
                                else -> null
                            },
                        )
                        val empty = ov.calls == null && ov.hangUp == null && ov.gap == null &&
                            ov.speaker == null && ov.mute == null
                        onSave(if (empty) null else ov)
                    }
                }
            }
        }
    }
}



// ───────────────────────── Banners and run summary ─────────────────────────
@Composable
private fun ResumeBanner(cp: Checkpoint, onResume: () -> Unit, onDiscard: () -> Unit) {
    GlassCard {
        SectionTitle("UNFINISHED RUN")
        Text(
            "${cp.doneCalls} of ${cp.totalCalls} calls were done when the last run stopped.",
            fontSize = 13.sp, color = Dim,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("DISCARD", modifier = Modifier.weight(1f).height(48.dp), onClick = onDiscard)
            NeonButton(
                "RESUME", modifier = Modifier.weight(1f),
                icon = { Glyph(Icons.Filled.PlayArrow, it, 20.dp) },
                onClick = onResume,
            )
        }
    }
}

@Composable
private fun ScheduleBanner(s: Scheduled, onCancel: () -> Unit) {
    val whenText = remember(s.atMs) {
        SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(s.atMs))
    }
    GlassCard {
        SectionTitle("SCHEDULED")
        Text(whenText, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Fg)
        Text(
            "${s.plan.jobs.size} targets · ${s.plan.jobs.sumOf { it.attempts }} calls",
            fontSize = 12.sp, color = Dim,
        )
        Spacer(Modifier.height(10.dp))
        GhostButton("CANCEL SCHEDULE", modifier = Modifier.fillMaxWidth().height(44.dp), onClick = onCancel)
    }
}

/** Own composable so the rest of the DIAL tab does not recompose on every engine state change. */
@Composable
private fun SummaryHost(cfg: Config, a: DialActions) {
    val status by RedialService.status.collectAsState()
    val isDry by RedialService.dry.collectAsState()
    val s = status
    if (s is EngineState.Finished && s.results.isNotEmpty()) {
        SummaryCard(
            results = s.results, names = cfg.names, answerSec = cfg.answerSec, dry = isDry,
            onRetry = a.retry, onExport = a.export, onDismiss = { clearFinished() },
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = color)
        Text(label, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp, color = Dim)
    }
}

@Composable
private fun SummaryCard(
    results: List<AttemptResult>,
    names: Map<String, String>,
    answerSec: Int,
    dry: Boolean,
    onRetry: (List<String>) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val answered = results.count { it.outcome == AttemptOutcome.CONNECTED }
    val noAnswer = results.count { it.outcome == AttemptOutcome.NO_ANSWER }
    val failed = results.count { it.outcome == AttemptOutcome.FAILED_TO_START }
    val perNumber = results.groupBy { it.number }
    val unanswered = perNumber.filter { e -> e.value.none { it.outcome == AttemptOutcome.CONNECTED } }.keys.toList()

    GlassCard {
        SectionTitle(if (dry) "DRY RUN RESULTS" else "RESULTS")
        if (dry) {
            Row {
                StatTile("CALLS", "${results.size}", Fg, Modifier.weight(1f))
                StatTile("NUMBERS", "${perNumber.size}", Cyan, Modifier.weight(1f))
            }
        } else {
            Row {
                StatTile("PLACED", "${results.size}", Fg, Modifier.weight(1f))
                StatTile("ANSWERED", "$answered", Mint, Modifier.weight(1f))
                StatTile("NO ANSWER", "$noAnswer", Dim, Modifier.weight(1f))
                StatTile("FAILED", "$failed", Magenta, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        perNumber.entries.take(30).forEach { (n, list) ->
            val ok = list.count { it.outcome == AttemptOutcome.CONNECTED }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n, fontFamily = Mono, fontSize = 14.sp, color = Fg)
                    val who = names[n]
                    if (who != null) Text(who, fontSize = 11.sp, color = Dim, maxLines = 1)
                }
                Text(
                    if (dry) "${list.size} calls" else "${list.size} calls · $ok answered",
                    fontFamily = Mono, fontSize = 11.sp, color = if (ok > 0) Mint else Dim,
                )
            }
        }
        if (perNumber.size > 30) Caption("+ ${perNumber.size - 30} more numbers are in the history.")
        if (!dry) Caption("Answered means the call log showed at least ${answerSec}s. Change it in CONFIG.")
        Spacer(Modifier.height(12.dp))
        if (!dry) {
            NeonButton(
                "RETRY ${unanswered.size} UNANSWERED", enabled = unanswered.isNotEmpty(),
                icon = { Glyph(Icons.Filled.Refresh, it, 18.dp) },
            ) { onRetry(unanswered) }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!dry) GhostButton("EXPORT CSV", modifier = Modifier.weight(1f).height(44.dp), onClick = onExport)
            GhostButton("DISMISS", modifier = Modifier.weight(1f).height(44.dp), onClick = onDismiss)
        }
    }
}

// ───────────────────────── LOG tab ─────────────────────────
@Composable
private fun LogTab(cfg: Config) {
    var mode by rememberSaveable { mutableStateOf(0) }   // 0 live, 1 history
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegChip("LIVE", mode == 0, true, Modifier.weight(1f)) { mode = 0 }
            SegChip("HISTORY", mode == 1, true, Modifier.weight(1f)) { mode = 1 }
        }
        Spacer(Modifier.height(12.dp))
        if (mode == 0) {
            EventFeed(Modifier.weight(1f))
        } else {
            HistoryList(cfg, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EventFeed(modifier: Modifier) {
    val lines by EventLog.lines.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Hair, shape)) {
        if (lines.isEmpty()) {
            Text(
                "Nothing yet. What the engine is doing shows up here while a run is going.",
                fontFamily = Mono, fontSize = 12.sp, color = Dim, modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(lines.size) { i ->
                    val line = lines[i]
                    val color = when {
                        line.contains("answered") && !line.contains("no answer") -> Mint
                        line.contains("no answer") || line.contains("Stopped") || line.contains("Waiting") ||
                            line.contains("Could not") || line.contains("did not start") -> Magenta
                        line.contains("Dialing") -> Cyan
                        else -> Dim
                    }
                    Text(line, fontFamily = Mono, fontSize = 11.5.sp, color = color, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryList(cfg: Config, modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) }       // 0 all, 1 answered, 2 no answer, 3 failed
    var confirmClear by remember { mutableStateOf(false) }
    val records by produceState<List<HistoryRecord>?>(null, reload) {
        value = withContext(Dispatchers.IO) { HistoryStore.load(ctx, 600) }
    }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "ANSWERED", "NO ANS", "FAILED").forEachIndexed { i, label ->
                SegChip(label, filter == i, true, Modifier.weight(1f)) { filter = i }
            }
        }
        Spacer(Modifier.height(10.dp))

        val all = records
        val shown = (all ?: emptyList()).filter {
            when (filter) {
                1 -> it.outcome == "CONNECTED"
                2 -> it.outcome == "NO_ANSWER"
                3 -> it.outcome == "FAILED_TO_START"
                else -> true
            }
        }
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.weight(1f).fillMaxWidth().clip(shape).background(Panel).border(1.dp, Hair, shape)) {
            when {
                all == null -> Text("Loading…", fontFamily = Mono, fontSize = 12.sp, color = Dim, modifier = Modifier.padding(16.dp))
                shown.isEmpty() -> Text(
                    "No calls here yet. Every real call is recorded once a run places it.",
                    fontFamily = Mono, fontSize = 12.sp, color = Dim, modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp)) {
                    items(shown) { r -> HistoryRow(r, cfg.names[r.number]) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(
                if (confirmClear) "TAP AGAIN" else "CLEAR",
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                if (confirmClear) {
                    scope.launch {
                        withContext(Dispatchers.IO) { HistoryStore.clear(ctx) }
                        confirmClear = false
                        reload++
                    }
                } else {
                    confirmClear = true
                }
            }
            NeonButton("EXPORT CSV", modifier = Modifier.weight(1f)) {
                scope.launch {
                    val f = withContext(Dispatchers.IO) { HistoryStore.exportCsv(ctx) }
                    shareFile(ctx, f, "text/csv")
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(r: HistoryRecord, name: String?) {
    val fmt = remember { SimpleDateFormat("d MMM · HH:mm:ss", Locale.getDefault()) }
    val (label, color) = when (r.outcome) {
        "CONNECTED" -> "ANSWERED" to Mint
        "NO_ANSWER" -> "NO ANSWER" to Dim
        "FAILED_TO_START" -> "FAILED" to Magenta
        else -> "CALL" to Dim
    }
    val shownName = r.name.ifBlank { name.orEmpty() }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.number, fontFamily = Mono, fontSize = 14.sp, color = Fg)
            if (shownName.isNotBlank()) Text(shownName, fontSize = 11.sp, color = Cyan, maxLines = 1)
            Text(
                fmt.format(Date(r.ts)) + (if (r.sim >= 0) " · SIM ${r.sim + 1}" else ""),
                fontFamily = Mono, fontSize = 10.sp, color = Dim,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(label, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = color)
            Text("${r.durationSec}s", fontFamily = Mono, fontSize = 11.sp, color = Dim)
        }
    }
    Hairline()
}

// ───────────────────────── Dialogs ─────────────────────────
@Composable
private fun SaveListDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f),
        ) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("SAVE LIST")
                Caption("Saves these numbers with their names, custom settings and run settings.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("List name") },
                    singleLine = true,
                    colors = fieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("CANCEL", modifier = Modifier.weight(1f).height(48.dp), onClick = onDismiss)
                    NeonButton("SAVE", enabled = name.isNotBlank(), modifier = Modifier.weight(1f)) {
                        onSave(name.trim())
                    }
                }
            }
        }
    }
}

@Composable
private fun ListsDialog(
    lists: List<String>,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 520.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("SAVED LISTS")
                if (lists.isEmpty()) {
                    Text(
                        "Nothing saved yet. Use SAVE LIST on the DIAL tab.",
                        fontFamily = Mono, fontSize = 12.sp, color = Dim,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Caption("Tap a list to load it. Loading replaces the current queue.")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(lists) { name ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(shape)
                                    .border(1.dp, Hair, shape)
                                    .pressable { onLoad(name) }
                                    .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).pressable { onDelete(name) },
                                    contentAlignment = Alignment.Center,
                                ) { Glyph(Icons.Filled.Close, Dim, 18.dp) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GhostButton("CLOSE", modifier = Modifier.fillMaxWidth().height(48.dp), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ScheduleDialog(onSchedule: (Long) -> Unit, onDismiss: () -> Unit) {
    val nowCal = remember { Calendar.getInstance() }
    var hour by remember { mutableStateOf((nowCal.get(Calendar.HOUR_OF_DAY) + 1) % 24) }
    var minute by remember { mutableStateOf(0) }

    fun target(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 600.dp),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("SCHEDULE START")
                FieldLabel("START IN")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("5 MIN" to 5, "15 MIN" to 15, "30 MIN" to 30, "1 HOUR" to 60).forEach { (label, mins) ->
                        SegChip(label, false, true, Modifier.weight(1f)) {
                            onSchedule(System.currentTimeMillis() + mins * 60_000L)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                FieldLabel("OR AT A TIME")
                StepperRow("Hour", hour, 0..23, true, { "%02d".format(it) }) { hour = it }
                StepperRow("Minute", minute, 0..59, true, { "%02d".format(it) }) { minute = it }
                val at = target()
                Caption(
                    "Starts " + SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(at)) +
                        ". The phone needs to be on; the app does the rest.",
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("CANCEL", modifier = Modifier.weight(1f).height(48.dp), onClick = onDismiss)
                    NeonButton("SCHEDULE", modifier = Modifier.weight(1f)) { onSchedule(at) }
                }
            }
        }
    }
}

@Composable
private fun ImportDialog(raw: String, onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val nums = remember(raw) { extractNumbers(raw) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 560.dp),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("IMPORT NUMBERS")
                if (nums.isEmpty()) {
                    Text("No phone numbers were found in that.", fontSize = 14.sp, color = Dim)
                    Spacer(Modifier.height(14.dp))
                    GhostButton("CLOSE", modifier = Modifier.fillMaxWidth().height(48.dp), onClick = onDismiss)
                } else {
                    Text(
                        "Found ${nums.size} number" + (if (nums.size > 1) "s" else ""),
                        fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Fg,
                    )
                    Spacer(Modifier.height(8.dp))
                    nums.take(8).forEach { Text(it, fontFamily = Mono, fontSize = 14.sp, color = Fg) }
                    if (nums.size > 8) Caption("+ ${nums.size - 8} more")
                    Caption("Invalid numbers are skipped, and so are duplicates unless you allowed them.")
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("CANCEL", modifier = Modifier.weight(1f).height(48.dp), onClick = onDismiss)
                        NeonButton("ADD ${nums.size}", modifier = Modifier.weight(1f)) { onAdd(nums) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(Icons.Filled.Lock, Cyan, 44.dp)
        Spacer(Modifier.height(16.dp))
        Text("LOCKED", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 6.sp, color = Fg)
        Spacer(Modifier.height(6.dp))
        Text(
            "Use your phone's screen lock to continue.",
            fontSize = 13.sp, color = Dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        NeonButton("UNLOCK", onClick = onUnlock)
    }
}
