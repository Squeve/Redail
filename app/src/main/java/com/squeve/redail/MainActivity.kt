package com.squeve.redail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

// ───────────────────────── Palette ─────────────────────────
private val Ink = Color(0xFF070B14)
private val InkSoft = Color(0xFF0D1424)
private val Panel = Color(0xCC111A2C)
private val Cyan = Color(0xFF00E5FF)
private val Violet = Color(0xFF7C4DFF)
private val Magenta = Color(0xFFFF2E93)
private val Mint = Color(0xFF00E676)
private val Dim = Color(0xFF8A9BB8)
private val Hair = Color(0xFF22304A)
private val Mono = FontFamily.Monospace

private val NeonBrush = Brush.horizontalGradient(listOf(Cyan, Violet))
private val DangerBrush = Brush.horizontalGradient(listOf(Magenta, Color(0xFFFF5252)))
private val DisabledBrush = Brush.horizontalGradient(listOf(Hair, Hair))
private val BgBrush = Brush.verticalGradient(listOf(Ink, InkSoft, Ink))
private val RingBrush = Brush.sweepGradient(listOf(Cyan, Violet, Magenta, Cyan))

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

/** Bumped on every onResume so permission-dependent UI refreshes after returning from system settings. */
private object ResumeSignal {
    val tick = MutableStateFlow(0)
}

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        ResumeSignal.tick.value = ResumeSignal.tick.value + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ink.toArgb()
        window.navigationBarColor = Ink.toArgb()
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                Box(Modifier.fillMaxSize().background(BgBrush)) {
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

// ───────────────────────── Screen ─────────────────────────
@Composable
private fun RedialScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("redail", Context.MODE_PRIVATE) }

    val numbers = remember {
        mutableStateListOf<String>().apply {
            prefs.getString("numbers", "")?.split("\n")?.filter { it.isNotBlank() }?.let { addAll(it) }
        }
    }
    var calls by remember { mutableStateOf(prefs.getInt("calls", 5)) }
    var gapSec by remember { mutableStateOf(prefs.getInt("gap", 5)) }
    var limitOn by remember { mutableStateOf(prefs.getBoolean("limitOn", true)) }
    var durSec by remember { mutableStateOf(prefs.getInt("dur", 30)) }
    var skipAnswered by remember { mutableStateOf(prefs.getBoolean("skip", false)) }
    var speakerOn by remember { mutableStateOf(prefs.getBoolean("speaker", false)) }
    var muteOn by remember { mutableStateOf(prefs.getBoolean("mute", false)) }
    var simIndex by remember { mutableStateOf(prefs.getInt("sim", 0)) }
    var permTick by remember { mutableStateOf(0) }
    val resumeTick by ResumeSignal.tick.collectAsState()

    // Only recompose the whole screen when "running" flips, not on every engine state change.
    val runningFlow = remember { RedialService.status.map { isRunning(it) }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = isRunning(RedialService.status.value))

    val sims = remember(permTick, resumeTick) { listSims(ctx) }
    val granted = remember(permTick, resumeTick) { allGranted(ctx) }
    val overlayOk = remember(permTick, resumeTick) { Settings.canDrawOverlays(ctx) }

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
                    if (c.moveToFirst()) addNumbers(numbers, c.getString(0).orEmpty())
                }
            }
        }
    }

    fun send(action: String) {
        ctx.startService(Intent(ctx, RedialService::class.java).setAction(action))
    }

    fun startRun() {
        if (!allGranted(ctx)) {
            permLauncher.launch(permsToRequest())
            return
        }
        if (numbers.isEmpty()) {
            Toast.makeText(ctx, "Add at least one number", Toast.LENGTH_SHORT).show()
            return
        }
        // One-time prompt for the floating progress controls.
        if (!Settings.canDrawOverlays(ctx) && !prefs.getBoolean("overlayAsked", false)) {
            prefs.edit().putBoolean("overlayAsked", true).apply()
            Toast.makeText(ctx, "Allow 'Display over other apps', then press START again", Toast.LENGTH_LONG).show()
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
            )
            return
        }
        prefs.edit()
            .putString("numbers", numbers.joinToString("\n"))
            .putInt("calls", calls).putInt("gap", gapSec).putInt("dur", durSec)
            .putBoolean("limitOn", limitOn).putBoolean("skip", skipAnswered)
            .putBoolean("speaker", speakerOn).putBoolean("mute", muteOn)
            .putInt("sim", simIndex)
            .apply()
        RedialService.pendingQueue = numbers.map {
            RedialJob(
                number = it,
                attempts = calls,
                gapMs = gapSec * 1000L,
                hangUpAfterMs = if (limitOn) durSec * 1000L else null,
                stopOnConnected = skipAnswered,
                speaker = speakerOn,
                muteMic = muteOn,
            )
        }
        RedialService.pendingSim = if (sims.size > 1) sims.getOrNull(simIndex)?.handle else null
        ctx.startForegroundService(Intent(ctx, RedialService::class.java).setAction(RedialService.ACTION_START))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(running)

        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroPanel(jobCount = numbers.size, callsEach = calls)

            // ───── Targets ─────
            GlassCard {
                SectionTitle("TARGETS")

                if (sims.size > 1) {
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sims.forEachIndexed { i, s ->
                            SimChip(s.label, selected = i == simIndex, enabled = !running) { simIndex = i }
                        }
                    }
                }

                AddNumberRow(
                    enabled = !running,
                    onAdd = { addNumbers(numbers, it) },
                    onContacts = {
                        contactPicker.launch(
                            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                        )
                    },
                )

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "QUEUE · ${numbers.size}",
                        fontFamily = Mono, fontSize = 13.sp, letterSpacing = 3.sp, color = Dim,
                        modifier = Modifier.weight(1f),
                    )
                    if (!running && numbers.isNotEmpty()) {
                        LinkButton("CLEAR") { numbers.clear() }
                    }
                }
                if (numbers.isEmpty()) {
                    Text("No targets yet.", fontFamily = Mono, fontSize = 13.sp, color = Dim,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
                QueueList(numbers = numbers, canRemove = !running, onRemove = { numbers.removeAt(it) })

                Spacer(Modifier.height(16.dp))
                if (!granted) {
                    GhostButton("GRANT PERMISSIONS") { permLauncher.launch(permsToRequest()) }
                    Spacer(Modifier.height(10.dp))
                }
                Controls(
                    running = running,
                    onStart = { startRun() },
                    onPauseResume = { wasPaused ->
                        send(if (wasPaused) RedialService.ACTION_RESUME else RedialService.ACTION_PAUSE)
                    },
                    onStop = { send(RedialService.ACTION_STOP) },
                )
            }

            // ───── Parameters ─────
            GlassCard {
                SectionTitle("PARAMETERS")
                ToggleRow("Limit call duration", null, limitOn, !running) { limitOn = it }
                if (limitOn) {
                    StepperRow("Hang up after", durSec, 5..600, !running, { "${it / 60}m ${it % 60}s" }) { durSec = it }
                }
                Hairline()
                StepperRow("Calls per number", calls, 1..500, !running, { "$it" }) { calls = it }
                Hairline()
                StepperRow("Interval between calls", gapSec, 1..300, !running, { "${it}s" }) { gapSec = it }
                Hairline()
                ToggleRow("Start call on speaker", null, speakerOn, !running) { speakerOn = it }
                ToggleRow(
                    "Mute microphone",
                    "Best effort: some phones ignore mute on normal calls.",
                    muteOn, !running,
                ) { muteOn = it }
                ToggleRow(
                    "Skip to next number if answered",
                    "Off by default: some phones log ring time as call time.",
                    skipAnswered, !running,
                ) { skipAnswered = it }
                Hairline()
                LinkButton("ALLOW BACKGROUND RUNNING (BATTERY)") {
                    runCatching {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${ctx.packageName}"),
                            ),
                        )
                    }
                }
                if (!overlayOk) {
                    LinkButton("ENABLE FLOATING CONTROLS (DISPLAY OVER OTHER APPS)") {
                        runCatching {
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${ctx.packageName}"),
                                ),
                            )
                        }
                    }
                }
            }

            UpdateCard(running)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ───────────────────────── Header / hero ─────────────────────────
@Composable
private fun Header(running: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "SQUEVE", fontFamily = Mono, fontWeight = FontWeight.Black,
                fontSize = 26.sp, letterSpacing = 6.sp, color = Color.White,
            )
            Text(
                "REDAIL · AUTO-DIAL ENGINE", fontFamily = Mono,
                fontSize = 11.sp, letterSpacing = 3.sp, color = Cyan,
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
private fun HeroPanel(jobCount: Int, callsEach: Int) {
    val status by RedialService.status.collectAsState()
    val paused by RedialService.paused.collectAsState()
    val running = isRunning(status)
    val p = status.progress()
    val finished = status is EngineState.Finished

    val fraction = when {
        finished -> 1f
        p != null -> (p.jobIndex * p.total + p.attempt - 1).toFloat() / (p.jobCount * p.total).toFloat()
        else -> 0f
    }
    val animated by animateFloatAsState(targetValue = fraction, animationSpec = tween(600), label = "ring")

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
            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val strokeW = 12.dp.toPx()
                    val inset = strokeW / 2 + 8.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    rotate(degrees = -90f) {
                        drawArc(
                            color = Hair, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            topLeft = topLeft, size = arcSize, style = Stroke(width = strokeW),
                        )
                        if (animated > 0.001f) {
                            if (running) {
                                drawArc(
                                    color = Cyan.copy(alpha = glow.value * 0.5f),
                                    startAngle = 0f, sweepAngle = 360f * animated, useCenter = false,
                                    topLeft = topLeft, size = arcSize,
                                    style = Stroke(width = strokeW * 2.2f, cap = StrokeCap.Round),
                                )
                            }
                            drawArc(
                                brush = RingBrush,
                                startAngle = 0f, sweepAngle = 360f * animated, useCenter = false,
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
                    Text(
                        "${(fraction * 100).roundToInt()}%", fontFamily = Mono,
                        fontWeight = FontWeight.Bold, fontSize = 38.sp, color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            if (p != null) {
                Text(p.number, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(
                    "NUMBER ${p.jobIndex + 1}/${p.jobCount} · CALL ${p.attempt}/${p.total}",
                    fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp, color = Dim,
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
            if (paused && running && status !is EngineState.Paused) {
                Spacer(Modifier.height(6.dp))
                Text("PAUSING AFTER THIS CALL", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp, color = Magenta)
            }
        }
    }
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
    // Keystrokes only recompose this row, not the whole screen.
    var input by remember { mutableStateOf("") }
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
        GhostButton("CONTACTS", enabled = enabled, modifier = Modifier.width(112.dp).height(56.dp), onClick = onContacts)
    }
    Spacer(Modifier.height(10.dp))
    NeonButton("ADD TO QUEUE", enabled = enabled && input.isNotBlank()) {
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
private fun QueueList(numbers: List<String>, canRemove: Boolean, onRemove: (Int) -> Unit) {
    val status by RedialService.status.collectAsState()
    val finished = status is EngineState.Finished
    val p = status.progress()
    numbers.forEachIndexed { i, n ->
        // 0 = queued, 1 = current, 2 = done
        val state = when {
            finished -> 2
            p == null -> 0
            i < p.jobIndex -> 2
            i == p.jobIndex -> 1
            else -> 0
        }
        QueueItem(
            index = i, number = n, state = state,
            attempt = p?.attempt ?: 0, total = p?.total ?: 0,
            canRemove = canRemove, onRemove = { onRemove(i) },
        )
    }
}

@Composable
private fun QueueItem(
    index: Int, number: String, state: Int, attempt: Int, total: Int,
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
            Text(
                if (done) "✓" else "%02d".format(index + 1),
                fontFamily = Mono, fontSize = 13.sp,
                color = if (done) Mint else if (current) Cyan else Dim,
                modifier = Modifier.width(30.dp),
            )
            Text(
                number, fontFamily = Mono, fontSize = 17.sp,
                color = if (done) Dim else Color.White,
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
                Text(
                    "✕", color = Dim, fontSize = 16.sp,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onRemove).padding(8.dp),
                )
            } else if (current) {
                Text("$attempt/$total", fontFamily = Mono, fontSize = 14.sp, color = Cyan)
            }
        }
        if (current && total > 0) {
            Spacer(Modifier.height(8.dp))
            MiniBar(attempt.toFloat() / total)
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

@Composable
private fun SimChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Cyan.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) Cyan else Hair, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontFamily = Mono, fontSize = 12.sp, color = if (selected) Cyan else Dim)
    }
}

// ───────────────────────── Controls ─────────────────────────
@Composable
private fun Controls(
    running: Boolean,
    onStart: () -> Unit,
    onPauseResume: (Boolean) -> Unit,
    onStop: () -> Unit,
) {
    val paused by RedialService.paused.collectAsState()
    if (!running) {
        NeonButton("START REDIAL", onClick = onStart)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(
                if (paused) "RESUME" else "PAUSE",
                modifier = Modifier.weight(1f).height(52.dp),
            ) { onPauseResume(paused) }
            NeonButton(
                "STOP", brush = DangerBrush, textColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = onStop,
            )
        }
    }
}

@Composable
private fun NeonButton(
    text: String,
    enabled: Boolean = true,
    brush: Brush = NeonBrush,
    textColor: Color = Ink,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) brush else DisabledBrush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontFamily = Mono, fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp, fontSize = 14.sp,
            color = if (enabled) textColor else Dim,
        )
    }
}

@Composable
private fun GhostButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth().height(52.dp),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, if (enabled) Cyan else Hair, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontFamily = Mono, letterSpacing = 2.sp, fontSize = 13.sp,
            color = if (enabled) Cyan else Dim,
        )
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp, color = Cyan)
    }
}

// ───────────────────────── Parameters ─────────────────────────
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
            .clip(CircleShape)
            .border(1.dp, if (enabled) Hair else Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontFamily = Mono, fontSize = 20.sp, color = if (enabled) Cyan else Dim)
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
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = Dim)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
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

// ───────────────────────── Updates ─────────────────────────
@Composable
private fun UpdateCard(running: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
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
        msg = "Checking for updates…"
        scope.launch {
            try {
                val rel = Updater.latest()
                val have = Updater.installedBuild(ctx)
                when {
                    rel == null -> msg = "No release found yet."
                    rel.build <= have -> msg = "You're on the latest version (build $have)."
                    else -> {
                        msg = "Downloading build ${rel.build}… 0%"
                        val apk = Updater.download(ctx, rel.apkUrl) { pct ->
                            msg = "Downloading build ${rel.build}… $pct%"
                        }
                        msg = "Opening installer…"
                        Updater.install(ctx, apk)
                    }
                }
            } catch (e: Exception) {
                msg = "Update failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    GlassCard {
        SectionTitle("SYSTEM")
        Text(versionLine, fontFamily = Mono, fontSize = 13.sp, color = Dim)
        Spacer(Modifier.height(12.dp))
        NeonButton(
            if (busy) "PLEASE WAIT…" else "GET LATEST VERSION",
            enabled = !busy && !running,
        ) { getUpdate() }
        if (running) {
            Spacer(Modifier.height(6.dp))
            Text("Stop redialing before updating.", fontSize = 12.sp, color = Dim)
        }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, fontFamily = Mono, fontSize = 12.sp, color = Cyan)
        }
    }
}

// ───────────────────────── Building blocks ─────────────────────────
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
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontFamily = Mono, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, letterSpacing = 4.sp, color = Cyan,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
}
