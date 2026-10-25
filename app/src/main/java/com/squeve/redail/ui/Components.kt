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

// ───────────────────────── Building blocks ─────────────────────────
@Composable
internal fun Glyph(icon: ImageVector, tint: Color, size: Dp = 18.dp) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
internal fun PauseGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) {
            Box(Modifier.width(4.dp).height(14.dp).background(color, RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
internal fun StopGlyph(color: Color) {
    Box(Modifier.size(13.dp).background(color, RoundedCornerShape(3.dp)))
}

@Composable
internal fun NeonButton(
    text: String,
    enabled: Boolean = true,
    brush: Brush = NeonBrush,
    textColor: Color = OnAccent,
    strong: Boolean = false,
    modifier: Modifier = Modifier,
    icon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val radius = Theming.pack?.btnRadius ?: 14
    val shape = RoundedCornerShape(radius.dp)
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
                            cornerRadius = CornerRadius(radius.dp.toPx() + g),
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
internal fun GhostButton(
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
internal fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 1.sp, color = Cyan)
    }
}

/** Segmented choice chip, used for SIM and call order. */
@Composable
internal fun SegChip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
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

/** A settings card that folds away. Its open or closed state is remembered between launches. */
@Composable
internal fun CollapsibleCard(
    id: String,
    title: String,
    subtitle: String,
    startOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("redail", Context.MODE_PRIVATE) }
    var open by remember { mutableStateOf(prefs.getBoolean("open_$id", startOpen)) }
    val angle by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "chevron")
    GlassCard {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable {
                    open = !open
                    prefs.edit().putBoolean("open_$id", open).apply()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SectionTitle(title, bottom = 4.dp)
                Text(subtitle, fontSize = 12.sp, color = Dim)
            }
            Box(Modifier.graphicsLayer { rotationZ = angle }) {
                Glyph(Icons.Filled.KeyboardArrowDown, Dim, 24.dp)
            }
        }
        AnimatedVisibility(visible = open, enter = expandVertically(tween(200)) + fadeIn(tween(200))) {
            Column {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
internal fun GroupHeader(title: String, hint: String) {
    Column(Modifier.padding(start = 4.dp, top = 6.dp)) {
        Text(title, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 4.sp, color = Fg)
        Text(hint, fontSize = 12.sp, color = Dim)
    }
}

@Composable
internal fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val p = Theming.pack
    if (p == null) {
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
    } else {
        // Glass, soft-drop or raised, depending on the style.
        val radius = p.cardRadius.dp
        val shape = RoundedCornerShape(radius)
        val opaque = p.cardTo.copy(alpha = 1f)
        var m = Modifier.fillMaxWidth()
        when (p.cardShadow) {
            1 -> m = m.softShadow(p.shadowColor, 18.dp, 0.dp, 10.dp, radius, opaque)
            2 -> m = m
                .softShadow(p.shadowLight, 14.dp, (-7).dp, (-7).dp, radius, opaque)
                .softShadow(p.shadowColor, 14.dp, 7.dp, 7.dp, radius, opaque)
        }
        val gloss = Color.White.copy(alpha = if (p.light) 0.35f else 0.07f)
        Box(
            m
                .clip(shape)
                .background(Brush.linearGradient(listOf(p.cardFrom, p.cardTo)))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(gloss, Color.Transparent), endY = size.height * 0.45f))
                }
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(p.cardRim, p.cardRim.copy(alpha = 0.08f), p.cardRim.copy(alpha = 0.30f))),
                    shape,
                ),
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
internal fun SectionTitle(text: String, bottom: Dp = 12.dp) {
    Row(Modifier.padding(bottom = bottom), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(NeonBrush))
        Spacer(Modifier.width(8.dp))
        Text(
            text, fontFamily = Display, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, letterSpacing = 4.sp, color = Cyan,
        )
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 3.sp, color = Dim,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
internal fun Caption(text: String) {
    Text(text, fontSize = 12.sp, color = Dim, modifier = Modifier.padding(top = 8.dp))
}

@Composable
internal fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
}

/** A calm placeholder for lists with nothing in them yet. */
@Composable
internal fun EmptyState(kind: Int, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TabIcon(kind, Dim.copy(alpha = 0.55f), 40.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontFamily = Display, fontSize = 15.sp, letterSpacing = 1.sp, color = Fg)
        Spacer(Modifier.height(4.dp))
        Text(hint, fontSize = 12.sp, color = Dim, textAlign = TextAlign.Center)
    }
}
