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
import androidx.compose.foundation.horizontalScroll
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
import com.squeve.redail.data.learnRingOut
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

// ───────────────────────── CONFIG tab ─────────────────────────
@Composable
internal fun ConfigTab(cfg: Config, running: Boolean, simCount: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GroupHeader("CALLING", "How each call is placed")
        CollapsibleCard("timing", "TIMING", "Calls per number, interval and hang-up", startOpen = true) {
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
            Hairline()
            ToggleRow(
                "Smart gaps", "Wait a different time after a busy line and after no answer.",
                cfg.smartGaps, !running,
            ) { cfg.smartGaps = it }
            if (cfg.smartGaps) {
                StepperRow("After busy or declined", cfg.gapBusySec, 1..300, !running, { "${it}s" }) { cfg.gapBusySec = it }
                StepperRow("After no answer", cfg.gapNoAnswerSec, 1..600, !running, { "${it}s" }) { cfg.gapNoAnswerSec = it }
                Caption("A busy line often frees up quickly, so a short wait works. Numbers with their own interval keep it.")
            }
        }

        CollapsibleCard("answers", "ANSWERS", "What counts as answered") {
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
            Hairline()
            ToggleRow(
                "Redial until answered",
                "Keeps calling each number until someone answers, within the limits below.",
                cfg.untilAnswered, !running,
            ) { cfg.untilAnswered = it }
            if (cfg.untilAnswered) {
                StepperRow("Most tries per number", cfg.maxTries, 1..500, !running, { "$it" }) { cfg.maxTries = it }
                StepperRow(
                    "Give up after", cfg.giveUpMin, 0..720, !running,
                    { if (it == 0) "no limit" else if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" },
                ) { cfg.giveUpMin = it }
                Caption("Replaces Calls per number while it is on. A number with its own call count keeps it.")
            }
        }

        CollapsibleCard("audio", "AUDIO", "Speaker and microphone during calls") {
            ToggleRow("Start call on speaker", null, cfg.speakerOn, !running) { cfg.speakerOn = it }
            ToggleRow(
                "Mute microphone",
                "Best effort: some phones ignore mute on normal calls.",
                cfg.muteOn, !running,
            ) { cfg.muteOn = it }
        }

        CollapsibleCard("answerAlert", "ANSWER ALERT", "Notice when someone probably picks up") {
            ToggleRow(
                "Alert when a call is probably answered",
                "Android does not tell apps the moment a call is answered. A call still going after the time below is treated as answered.",
                cfg.answerAlert, !running,
            ) { cfg.answerAlert = it }
            if (cfg.answerAlert) {
                StepperRow("Still going after", cfg.answerGuessSec, 8..180, !running, { "${it}s" }) { cfg.answerGuessSec = it }
                Caption("Set it a little longer than your carrier lets an unanswered call ring, or you will get false alerts.")
                Spacer(Modifier.height(8.dp))
                FieldLabel("WHEN IT HAPPENS")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALERT", "KEEP", "SPEAKER", "EARPIECE").forEachIndexed { i, label ->
                        SegChip(label, cfg.answerAction == i, !running, Modifier.weight(1f)) { cfg.answerAction = i }
                    }
                }
                Caption(
                    when (cfg.answerAction) {
                        0 -> "Sound and vibration only. The hang-up timer keeps running."
                        1 -> "Also cancels the hang-up timer and opens your microphone, so you can talk."
                        2 -> "Also keeps the call, opens the mic and switches to speaker."
                        else -> "Also keeps the call, opens the mic and switches to the earpiece."
                    },
                )
                Spacer(Modifier.height(4.dp))
                GhostButton("LEARN FROM HISTORY", enabled = !running, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    scope.launch {
                        val learned = withContext(Dispatchers.IO) { learnRingOut(HistoryStore.load(ctx, 5000)) }
                        if (learned != null) {
                            cfg.answerGuessSec = learned
                            Toast.makeText(ctx, "Set to ${learned}s from your call history", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "Not enough unanswered calls in the history yet", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        CollapsibleCard("queue", "QUEUE RULES", "Duplicates and country code") {
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

        GroupHeader("SAFETY", "Limits that protect you and your phone")
        CollapsibleCard("safety", "SAFETY", "Daily cap, quiet hours and battery") {
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

        GroupHeader("APP", "Look, feel and privacy")
        CollapsibleCard("appearance", "APPEARANCE", "Style, theme, size and haptics") {
            FieldLabel("STYLE")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeTile(null, cfg.themePack == 0) { cfg.themePack = 0 }
                PACKS.forEachIndexed { i, p -> ThemeTile(p, cfg.themePack == i + 1) { cfg.themePack = i + 1 } }
            }
            val pk = PACKS.getOrNull(cfg.themePack - 1)
            Caption(
                if (pk == null) "Classic uses the dark, light and accent choices below."
                else "${pk.name} · ${pk.tagline}. This style sets its own colours.",
            )
            Spacer(Modifier.height(14.dp))
            if (pk == null) {
                FieldLabel("THEME")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DARK", "LIGHT", "SYSTEM").forEachIndexed { i, label ->
                        SegChip(label, cfg.themeMode == i, true, Modifier.weight(1f)) { cfg.themeMode = i }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            FieldLabel("SIZE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("COMPACT", "NORMAL", "LARGE").forEachIndexed { i, label ->
                    SegChip(label, cfg.uiSize == i, true, Modifier.weight(1f)) { cfg.uiSize = i }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (pk == null) {
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
            }
            ToggleRow("Haptic feedback", "Small vibrations when you tap buttons or drag rows.", cfg.hapticsOn, true) {
                cfg.hapticsOn = it
            }
        }
        CollapsibleCard("bubble", "FLOATING BUBBLE", "The on-screen control during calls") {
            FieldLabel("PREMIUM")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AURORA", "GOLD", "CARBON", "FROST").forEachIndexed { j, label ->
                    SegChip(label, cfg.bubbleLook == j + 3, true, Modifier.weight(1f)) { cfg.bubbleLook = j + 3 }
                }
            }
            Spacer(Modifier.height(12.dp))
            FieldLabel("CLASSIC")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DARK", "LIGHT", "NEON").forEachIndexed { i, label ->
                    SegChip(label, cfg.bubbleLook == i, true, Modifier.weight(1f)) { cfg.bubbleLook = i }
                }
                Spacer(Modifier.weight(1f))
            }
            Caption("Applies to the floating panel and the bubble. Preview shows both for a few seconds.")
            Spacer(Modifier.height(12.dp))
            GhostButton(
                "PREVIEW LOOK",
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

        CollapsibleCard("alerts", "ALERTS", "When a run finishes") {
            ToggleRow("Alert when a run finishes", "Sound and vibration with a notification.", cfg.alertOn, true) {
                cfg.alertOn = it
            }
        }

        CollapsibleCard("privacy", "PRIVACY", "Lock the app") {
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
internal fun StepperRow(
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
internal fun StepBtn(symbol: String, enabled: Boolean, onClick: () -> Unit) {
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
internal fun ToggleRow(
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
                checkedThumbColor = OnAccent,
                checkedTrackColor = Cyan,
                checkedBorderColor = Cyan,
                uncheckedThumbColor = Dim,
                uncheckedTrackColor = Hair,
                uncheckedBorderColor = Hair,
            ),
        )
    }
}
