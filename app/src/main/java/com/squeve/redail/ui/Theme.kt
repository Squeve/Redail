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

// ───────────────────────── Palette & type ─────────────────────────
/** Accent colours, pure-black mode and UI size, all changeable at runtime. */
internal class Accent(val name: String, val main: Color, val second: Color)

internal val ACCENTS = listOf(
    Accent("CYAN", Color(0xFF00E5FF), Color(0xFF7C4DFF)),
    Accent("MINT", Color(0xFF00E676), Color(0xFF00B8D4)),
    Accent("VIOLET", Color(0xFFB388FF), Color(0xFF536DFE)),
    Accent("AMBER", Color(0xFFFFC400), Color(0xFFFF6D00)),
    Accent("PINK", Color(0xFFFF4081), Color(0xFF7C4DFF)),
    Accent("BLUE", Color(0xFF448AFF), Color(0xFF00E5FF)),
)

internal object Theming {
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

internal val Ink: Color get() = when {
    Theming.light -> Color(0xFFF3F6FC)
    Theming.black.value -> Color.Black
    else -> Color(0xFF070B14)
}
internal val InkSoft: Color get() = when {
    Theming.light -> Color(0xFFE7EDF8)
    Theming.black.value -> Color(0xFF050505)
    else -> Color(0xFF0D1424)
}
internal val Panel: Color get() = when {
    Theming.light -> Color(0xE6FFFFFF)
    Theming.black.value -> Color(0xE60C0C0C)
    else -> Color(0xCC111A2C)
}
internal val Dock: Color get() = when {
    Theming.light -> Color(0xF5FFFFFF)
    Theming.black.value -> Color(0xFF000000)
    else -> Color(0xF00A1020)
}
internal val Cyan: Color get() =
    if (Theming.light) androidx.compose.ui.graphics.lerp(Theming.accent.value, Color.Black, 0.42f) else Theming.accent.value
internal val Violet: Color get() =
    if (Theming.light) androidx.compose.ui.graphics.lerp(Theming.accent2.value, Color.Black, 0.22f) else Theming.accent2.value
internal val Magenta = Color(0xFFFF2E93)
internal val Mint: Color get() = if (Theming.light) Color(0xFF00A152) else Color(0xFF00E676)
internal val Dim: Color get() = if (Theming.light) Color(0xFF5A6B88) else Color(0xFF8A9BB8)
internal val Hair: Color get() = when {
    Theming.light -> Color(0xFFCBD5E8)
    Theming.black.value -> Color(0xFF262626)
    else -> Color(0xFF22304A)
}
/** Main text colour. */
internal val Fg: Color get() = if (Theming.light) Color(0xFF0B1220) else Color.White
internal val Soft: Color get() = if (Theming.light) Color(0xFF16213A) else Color(0xFFE6EEFF)
internal val DialogBg: Color get() = if (Theming.light) Color.White else Color(0xFF0E1628)
internal val Lilac: Color get() = if (Theming.light) Color(0xFF6A4DE0) else Color(0xFFB39DFF)

/** The whole UI is drawn at this fraction of normal size (dp and sp together). */
internal const val UI_SCALE = 0.88f

internal val Mono: FontFamily get() = Fonts.mono
internal val Display: FontFamily get() = Fonts.display

internal val NeonBrush: Brush get() = Brush.horizontalGradient(listOf(Cyan, Violet))
internal val DangerBrush = Brush.horizontalGradient(listOf(Magenta, Color(0xFFFF5252)))
internal val DisabledBrush: Brush get() = Brush.horizontalGradient(listOf(Hair, Hair))
internal val BgBrush: Brush get() = Brush.verticalGradient(listOf(Ink, InkSoft, Ink))
internal val RingBrush: Brush get() = Brush.sweepGradient(listOf(Cyan, Violet, Magenta, Cyan))

/** Material text styles all use the bundled family, so nothing falls back to the phone's system font. */
internal fun typographyWith(f: FontFamily): Typography {
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

internal fun schemeFor() = if (Theming.light) {
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
internal fun Modifier.techBackdrop(): Modifier = this.drawBehind {
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
internal fun Modifier.pressable(enabled: Boolean = true, strong: Boolean = false, onClick: () -> Unit): Modifier {
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
