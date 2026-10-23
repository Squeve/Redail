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

// ───────────────────────── DIAL tab ─────────────────────────
@Composable
internal fun DialTab(
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

        var query by remember { mutableStateOf("") }
        var selecting by remember { mutableStateOf(false) }
        var bulkDialog by remember { mutableStateOf(false) }
        val selected = remember { mutableStateListOf<Int>() }
        LaunchedEffect(running) {
            if (running) {
                selecting = false
                selected.clear()
            }
        }

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
                if (!running && !selecting) {
                    if (cfg.numbers.size > 1) LinkButton("SELECT") { selecting = true }
                    if (cfg.numbers.isNotEmpty()) LinkButton("SAVE LIST") { a.saveList() }
                    LinkButton("LISTS") { a.lists() }
                    if (cfg.numbers.isNotEmpty()) {
                        LinkButton("CLEAR") {
                            cfg.removeManyWithUndo(cfg.numbers.indices.toList())
                            cfg.save()
                            clearFinished()
                        }
                    }
                }
            }
            if (selecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selected.size} SELECTED", fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp,
                        color = Cyan, modifier = Modifier.weight(1f),
                    )
                    LinkButton("ALL") {
                        selected.clear()
                        selected.addAll(cfg.numbers.indices)
                    }
                    if (selected.isNotEmpty()) {
                        LinkButton("SETTINGS") { bulkDialog = true }
                        LinkButton("DELETE") {
                            cfg.removeManyWithUndo(selected.toList())
                            selected.clear()
                            selecting = false
                            cfg.save()
                            clearFinished()
                        }
                    }
                    LinkButton("DONE") {
                        selecting = false
                        selected.clear()
                    }
                }
            }
            if (cfg.numbers.size >= 6) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search name or number", color = Dim, fontSize = 13.sp) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Box(Modifier.size(36.dp).clip(CircleShape).pressable { query = "" }, contentAlignment = Alignment.Center) {
                                Glyph(Icons.Filled.Close, Dim, 18.dp)
                            }
                        }
                    },
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (cfg.numbers.isEmpty()) {
                EmptyState(
                    0, "Your queue is empty",
                    "Type a number above, paste a list, or pick from your contacts or call log.",
                )
            }
            if (!running && cfg.numbers.isNotEmpty() && !selecting) {
                Caption("Tap a number to name it or give it its own calls, hang-up, interval or audio.")
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
                query = query,
                selecting = selecting,
                selected = selected.toSet(),
                onToggleSelect = { i -> if (i in selected) selected.remove(i) else selected.add(i) },
            )
        }

        if (bulkDialog) {
            TargetDialog(
                number = "${selected.size} numbers", name = null, current = null,
                defCalls = cfg.calls, defHangUp = if (cfg.limitOn) cfg.durSec else null, defGap = cfg.gapSec,
                onSave = { ov, _ ->
                    selected.mapNotNull { cfg.numbers.getOrNull(it) }.forEach { n ->
                        if (ov == null) cfg.overrides.remove(n) else cfg.overrides[n] = ov
                    }
                    cfg.save()
                    bulkDialog = false
                    selecting = false
                    selected.clear()
                },
                onDismiss = { bulkDialog = false },
                bulk = true,
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
                Spacer(Modifier.height(12.dp))
                FieldLabel("SIM STRATEGY")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FIXED", "FALL BACK", "ALTERNATE").forEachIndexed { i, label ->
                        SegChip(label, cfg.simMode == i, !running, Modifier.weight(1f)) { cfg.simMode = i }
                    }
                }
                Caption(
                    when (cfg.simMode) {
                        1 -> "Uses your chosen SIM. If a call can't start, the other SIM tries it."
                        2 -> "Swaps SIM on every call, starting with your chosen one."
                        else -> "Every call uses the SIM chosen above."
                    },
                )
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
internal fun HeroPanel(jobCount: Int, totalCalls: Int, names: Map<String, String>) {
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
            if (inCall != null && inCall.micMuted) {
                Spacer(Modifier.height(10.dp))
                GhostButton("UNMUTE MIC", modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    sendAction(ctx, RedialService.ACTION_UNMUTE)
                }
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
internal fun PercentText(animated: State<Float>) {
    Text(
        "${(animated.value * 100).roundToInt()}%", fontFamily = Display,
        fontWeight = FontWeight.Bold, fontSize = 38.sp, color = Fg,
    )
}

/** Auto hang-up countdown with the option to let this call run until it ends by itself. */
@Composable
internal fun HangUpRow(s: EngineState.InCall, onHold: () -> Unit, onRearm: () -> Unit) {
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
internal fun HangUpCountdown(at: Long) {
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
internal fun CooldownText(untilMs: Long) {
    val now by produceState(System.currentTimeMillis(), untilMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val left = ((untilMs - now) / 1000 + 1).coerceAtLeast(0)
    Text("NEXT CALL IN ${left}s", fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp, color = Cyan)
}
