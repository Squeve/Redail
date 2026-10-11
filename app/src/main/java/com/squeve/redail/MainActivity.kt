package com.squeve.redail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.squeve.redail.engine.SimOption
import com.squeve.redail.engine.listSims
import com.squeve.redail.model.*
import com.squeve.redail.service.RedialService
import com.squeve.redail.update.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ───────────────────────── Palette & type ─────────────────────────
private val Ink = Color(0xFF070B14)
private val InkSoft = Color(0xFF0D1424)
private val Panel = Color(0xCC111A2C)
private val Dock = Color(0xF00A1020)
private val Cyan = Color(0xFF00E5FF)
private val Violet = Color(0xFF7C4DFF)
private val Magenta = Color(0xFFFF2E93)
private val Mint = Color(0xFF00E676)
private val Dim = Color(0xFF8A9BB8)
private val Hair = Color(0xFF22304A)

/** The whole UI is drawn at this fraction of normal size (dp and sp together). */
private const val UI_SCALE = 0.88f

private val Mono: FontFamily get() = Fonts.mono
private val Display: FontFamily get() = Fonts.display

private val NeonBrush = Brush.horizontalGradient(listOf(Cyan, Violet))
private val DangerBrush = Brush.horizontalGradient(listOf(Magenta, Color(0xFFFF5252)))
private val DisabledBrush = Brush.horizontalGradient(listOf(Hair, Hair))
private val BgBrush = Brush.verticalGradient(listOf(Ink, InkSoft, Ink))
private val RingBrush = Brush.sweepGradient(listOf(Cyan, Violet, Magenta, Cyan))

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

private val Scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Ink,
    background = Ink,
    onBackground = Color(0xFFE6EEFF),
    surface = Panel,
    onSurface = Color(0xFFE6EEFF),
    surfaceVariant = Panel,
    onSurfaceVariant = Dim,
    outline = Hair,
)

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
            haptic.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
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

private fun addNumbers(target: MutableList<String>, raw: String) {
    raw.split(Regex("[,;\\n]+")).forEach { part ->
        val cleaned = part.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleaned.any { it.isDigit() }) target.add(cleaned)
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

/** Bumped on every onResume so permission-dependent UI refreshes after returning from system settings. */
private object ResumeSignal {
    val tick = MutableStateFlow(0)
}

/** All user settings in one place; changes only recompose whatever reads that field. */
@Stable
private class Config(private val prefs: SharedPreferences) {
    val numbers = mutableStateListOf<String>().apply {
        prefs.getString("numbers", "")?.split("\n")?.filter { it.isNotBlank() }?.let { addAll(it) }
    }
    var calls by mutableStateOf(prefs.getInt("calls", 5))
    var gapSec by mutableStateOf(prefs.getInt("gap", 5))
    var limitOn by mutableStateOf(prefs.getBoolean("limitOn", true))
    var durSec by mutableStateOf(prefs.getInt("dur", 30))
    var skipAnswered by mutableStateOf(prefs.getBoolean("skip", false))
    var speakerOn by mutableStateOf(prefs.getBoolean("speaker", false))
    var muteOn by mutableStateOf(prefs.getBoolean("mute", false))
    var simSlot by mutableStateOf(prefs.getInt("simSlot", -1))     // -1 = phone default
    var order by mutableStateOf(
        CallOrder.values().getOrElse(prefs.getInt("order", 0)) { CallOrder.SEQUENTIAL },
    )

    fun save() {
        prefs.edit()
            .putString("numbers", numbers.joinToString("\n"))
            .putInt("calls", calls).putInt("gap", gapSec).putInt("dur", durSec)
            .putBoolean("limitOn", limitOn).putBoolean("skip", skipAnswered)
            .putBoolean("speaker", speakerOn).putBoolean("mute", muteOn)
            .putInt("simSlot", simSlot).putInt("order", order.ordinal)
            .apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        ResumeSignal.tick.value = ResumeSignal.tick.value + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Fonts.init(this)
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
            // Uniformly smaller UI, and ignore oversized system font scaling so layouts stay tidy.
            val base = LocalDensity.current
            val scaled = remember(base) { Density(base.density * UI_SCALE, base.fontScale.coerceAtMost(1f)) }
            val type = remember { typographyWith(Fonts.body) }
            CompositionLocalProvider(LocalDensity provides scaled) {
                MaterialTheme(colorScheme = Scheme, typography = type) {
                    Box(Modifier.fillMaxSize().techBackdrop()) {
                        Surface(
                            Modifier.fillMaxSize(),
                            color = Color.Transparent,
                            contentColor = Color(0xFFE6EEFF),
                        ) { RedialScreen() }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Screen host ─────────────────────────
@Composable
private fun RedialScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("redail", Context.MODE_PRIVATE) }
    val cfg = remember { Config(prefs) }

    var tab by rememberSaveable { mutableStateOf(0) }
    var permTick by remember { mutableStateOf(0) }
    val resumeTick by ResumeSignal.tick.collectAsState()
    val imeOpen by ImeSignal.open.collectAsState()

    // Only recompose the host when "running" flips, not on every engine state change.
    val runningFlow = remember { RedialService.status.map { isRunning(it) }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = isRunning(RedialService.status.value))

    val refresh = permTick + resumeTick
    val sims = remember(refresh) { listSims(ctx) }
    val granted = remember(refresh) { allGranted(ctx) }

    // Settings are remembered between runs.
    LaunchedEffect(
        cfg.calls, cfg.gapSec, cfg.limitOn, cfg.durSec, cfg.skipAnswered,
        cfg.speakerOn, cfg.muteOn, cfg.simSlot, cfg.order,
    ) { cfg.save() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (uri != null) {
            runCatching {
                ctx.contentResolver.query(
                    uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        addNumbers(cfg.numbers, c.getString(0).orEmpty())
                        cfg.save()
                        clearFinished()
                    }
                }
            }
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
        if (!Settings.canDrawOverlays(ctx) && !prefs.getBoolean("overlayAsked", false)) {
            prefs.edit().putBoolean("overlayAsked", true).apply()
            Toast.makeText(ctx, "Allow 'Display over other apps', then press START again", Toast.LENGTH_LONG).show()
            openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            return
        }
        cfg.save()
        RedialService.pendingQueue = cfg.numbers.map {
            RedialJob(
                number = it,
                attempts = cfg.calls,
                gapMs = cfg.gapSec * 1000L,
                hangUpAfterMs = if (cfg.limitOn) cfg.durSec * 1000L else null,
                stopOnConnected = cfg.skipAnswered,
                speaker = cfg.speakerOn,
                muteMic = cfg.muteOn,
            )
        }
        RedialService.pendingOrder = cfg.order
        RedialService.pendingSim = sims.firstOrNull { it.slot == cfg.simSlot }   // null = phone default
        ctx.startForegroundService(Intent(ctx, RedialService::class.java).setAction(RedialService.ACTION_START))
    }

    Column(Modifier.fillMaxSize()) {
        Header(running)
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { dir * it / 8 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(260)) { -dir * it / 8 })
            },
            label = "tabs",
        ) { t ->
            when (t) {
                0 -> DialTab(
                    cfg = cfg, sims = sims, running = running,
                    onContacts = {
                        contactPicker.launch(
                            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                        )
                    },
                )
                1 -> ConfigTab(cfg, running)
                else -> SystemTab(
                    granted = granted, refresh = refresh, running = running,
                    onGrant = { permLauncher.launch(permsToRequest()) },
                )
            }
        }
        if (!imeOpen) {
            ControlDock(
                running = running,
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
                fontSize = 20.sp, letterSpacing = 3.sp, color = Color.White, maxLines = 1,
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
    val items = listOf(
        Icons.Filled.Call to "DIAL",
        Icons.Filled.Settings to "CONFIG",
        Icons.Filled.CheckCircle to "SYSTEM",
    )
    Column(Modifier.fillMaxWidth().background(Dock)) {
        Hairline()
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, (icon, label) ->
                val sel = i == selected
                val tint by animateColor(if (sel) Cyan else Dim)
                Column(
                    Modifier.weight(1f).pressable { onSelect(i) }.padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Glyph(icon, tint, 22.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label, fontFamily = Display, fontSize = 12.sp, letterSpacing = 3.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = tint,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(26.dp).height(2.dp).background(if (sel) Cyan else Color.Transparent))
                }
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
                    "START REDIAL", strong = true,
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
private fun DialTab(cfg: Config, sims: List<SimOption>, running: Boolean, onContacts: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroPanel(jobCount = cfg.numbers.size, callsEach = cfg.calls)

        GlassCard {
            SectionTitle("TARGETS")
            AddNumberRow(
                enabled = !running,
                onAdd = { addNumbers(cfg.numbers, it); cfg.save(); clearFinished() },
                onContacts = onContacts,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "QUEUE · ${cfg.numbers.size}",
                    fontFamily = Mono, fontSize = 13.sp, letterSpacing = 3.sp, color = Dim,
                    modifier = Modifier.weight(1f),
                )
                if (!running && cfg.numbers.isNotEmpty()) {
                    LinkButton("CLEAR") { cfg.numbers.clear(); cfg.save(); clearFinished() }
                }
            }
            if (cfg.numbers.isEmpty()) {
                Text(
                    "No targets yet.", fontFamily = Mono, fontSize = 13.sp, color = Dim,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            QueueList(
                numbers = cfg.numbers, callsEach = cfg.calls, canRemove = !running,
                onRemove = { cfg.numbers.removeAt(it); cfg.save(); clearFinished() },
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
            Spacer(Modifier.height(12.dp))

            val total = cfg.numbers.size * cfg.calls
            if (cfg.numbers.isEmpty()) {
                Text("Add at least one target to begin.", fontFamily = Mono, fontSize = 12.sp, color = Dim)
            } else {
                Text(
                    "${cfg.numbers.size} TARGETS × ${cfg.calls} CALLS = $total CALLS",
                    fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp, color = Color.White,
                )
                val timing = buildString {
                    if (cfg.limitOn) append("${cfg.durSec}s hang-up · ")
                    append("${cfg.gapSec}s gap")
                    if (cfg.limitOn) {
                        append(" · up to ~${spanText(total.toLong() * (cfg.durSec + cfg.gapSec))}")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(timing, fontFamily = Mono, fontSize = 12.sp, color = Dim)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroPanel(jobCount: Int, callsEach: Int) {
    val ctx = LocalContext.current
    val status by RedialService.status.collectAsState()
    val paused by RedialService.paused.collectAsState()
    val running = isRunning(status)
    val p = status.progress()
    val finished = status is EngineState.Finished
    val inCall = status as? EngineState.InCall

    val fraction = when {
        finished -> 1f
        p != null -> (p.step - 1).toFloat() / p.steps.toFloat()
        else -> 0f
    }
    val animated = animateFloatAsState(targetValue = fraction, animationSpec = tween(700), label = "ring")

    val glow = rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
        label = "glowAlpha",
    )

    val label = when (status) {
        is EngineState.Dialing -> "DIALING"
        is EngineState.InCall -> "IN CALL"
        is EngineState.Cooldown -> "COOLDOWN"
        is EngineState.Paused -> "PAUSED"
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
                        label, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 3.sp,
                        color = if (running) Cyan else Dim,
                    )
                    PercentText(animated)
                }
            }

            Spacer(Modifier.height(12.dp))
            if (p != null) {
                Text(p.number, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
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
                    "$jobCount TARGETS · $callsEach CALLS EACH",
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
        }
    }
}

/** Reads the animated value in its own scope so only this text recomposes while it counts. */
@Composable
private fun PercentText(animated: State<Float>) {
    Text(
        "${(animated.value * 100).roundToInt()}%", fontFamily = Display,
        fontWeight = FontWeight.Bold, fontSize = 38.sp, color = Color.White,
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
private fun AddNumberRow(enabled: Boolean, onAdd: (String) -> Unit, onContacts: () -> Unit) {
    // Keystrokes only recompose this row, not the whole screen. Saved so tab switches keep the text.
    var input by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
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
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        GhostButton(
            "CONTACTS", enabled = enabled, modifier = Modifier.width(120.dp).height(56.dp),
            icon = { Glyph(Icons.Filled.Person, it, 16.dp) },
            onClick = onContacts,
        )
    }
    Spacer(Modifier.height(10.dp))
    NeonButton(
        "ADD TO QUEUE", enabled = enabled && input.isNotBlank(),
        icon = { Glyph(Icons.Filled.Add, it, 20.dp) },
    ) {
        onAdd(input)
        input = ""
    }
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
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Dim,
)

@Composable
private fun QueueList(numbers: List<String>, callsEach: Int, canRemove: Boolean, onRemove: (Int) -> Unit) {
    val status by RedialService.status.collectAsState()
    val finished = status is EngineState.Finished
    val p = status.progress()
    numbers.forEachIndexed { i, n ->
        val completed = p?.completed?.getOrNull(i) ?: 0
        // 0 = queued, 1 = current, 2 = done
        val state = when {
            finished -> 2
            p == null -> 0
            completed >= callsEach -> 2
            i == p.jobIndex -> 1
            else -> 0
        }
        // New rows ease in.
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
        ) {
            QueueItem(
                index = i, number = n, state = state,
                doneCount = if (finished) callsEach else completed, total = callsEach,
                canRemove = canRemove, onRemove = { onRemove(i) },
            )
        }
    }
}

@Composable
private fun QueueItem(
    index: Int, number: String, state: Int, doneCount: Int, total: Int,
    canRemove: Boolean, onRemove: () -> Unit,
) {
    val current = state == 1
    val done = state == 2
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (current) Cyan.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (current) Cyan.copy(alpha = 0.7f) else Hair, shape)
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
            Text(
                number, fontFamily = Mono, fontSize = 17.sp,
                color = if (done) Dim else Color.White,
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
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
private fun ConfigTab(cfg: Config, running: Boolean) {
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

        GlassCard {
            SectionTitle("BEHAVIOUR")
            ToggleRow(
                "Skip to next number if answered",
                "Off by default: some phones log ring time as call time, so this can skip too early.",
                cfg.skipAnswered, !running,
            ) { cfg.skipAnswered = it }
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
private fun SystemTab(granted: Boolean, refresh: Int, running: Boolean, onGrant: () -> Unit) {
    val ctx = LocalContext.current
    val overlayOk = remember(refresh) { Settings.canDrawOverlays(ctx) }
    val batteryOk = remember(refresh) {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val installOk = remember(refresh) { Updater.canInstall(ctx) }

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
                "Install updates", "Lets the app update itself", installOk, "ALLOW",
            ) { Updater.openInstallSettings(ctx) }
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
    // Looked up once; PackageManager calls are not free inside composition.
    val versionLine = remember { "v${Updater.installedName(ctx)} · build ${Updater.installedBuild(ctx)}" }

    fun getUpdate() {
        if (busy) return
        if (!Updater.canInstall(ctx)) {
            msg = "Allow installs from Squeve Redail, come back, then tap again."
            Updater.openInstallSettings(ctx)
            return
        }
        busy = true
        pct = -1
        msg = "Checking for updates…"
        scope.launch {
            try {
                val rel = Updater.latest()
                val have = Updater.installedBuild(ctx)
                when {
                    rel == null -> msg = "No release found yet."
                    rel.build <= have -> msg = "You're on the latest version (build $have)."
                    else -> {
                        msg = "Downloading build ${rel.build}…"
                        pct = 0
                        val apk = Updater.download(ctx, rel.apkUrl) { p -> pct = p }
                        msg = "Opening installer…"
                        Updater.install(ctx, apk)
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

    GlassCard {
        SectionTitle("UPDATES")
        Text(versionLine, fontFamily = Mono, fontSize = 13.sp, color = Dim)
        Spacer(Modifier.height(12.dp))
        NeonButton(
            if (busy) "PLEASE WAIT…" else "GET LATEST VERSION",
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
