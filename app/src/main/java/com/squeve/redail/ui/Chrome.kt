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

// ───────────────────────── Header, tabs, dock ─────────────────────────
@Composable
internal fun Header(running: Boolean) {
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
internal fun PulseDot(active: Boolean) {
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
internal fun TabBar(selected: Int, attention: Boolean = false, onSelect: (Int) -> Unit) {
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
                    Box {
                        TabIcon(kind, tint, 24.dp)
                        if (attention && i == 3) {
                            Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Magenta))
                        }
                    }
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
internal fun TabIcon(kind: Int, tint: Color, dp: Dp = 24.dp) {
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
internal fun animateColor(target: Color): State<Color> =
    androidx.compose.animation.animateColorAsState(target, tween(200), label = "tint")

/** Always visible, whichever tab is open. */
@Composable
internal fun ControlDock(
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
