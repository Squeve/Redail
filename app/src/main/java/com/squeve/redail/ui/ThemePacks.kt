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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

// ───────────────────────── Theme styles ─────────────────────────
/** A soft coloured glow in the background. Position and size are fractions of the screen width and height. */
internal class Blob(val color: Color, val x: Float, val y: Float, val r: Float, val alpha: Float)

/**
 * A complete visual style: a rich gradient background with glows and scenery, glass or raised cards,
 * and its own text, accent and button colours. CLASSIC (index 0) is not one of these.
 */
internal class ThemePack(
    val name: String, val tagline: String, val light: Boolean,
    val bg: List<Color>, val blobs: List<Blob>,
    /** 0 none, 1 layered mountains, 2 flowing waves. */
    val scenery: Int,
    val ink: Color, val inkSoft: Color, val panel: Color, val dock: Color, val hair: Color,
    val dim: Color, val fg: Color, val soft: Color, val dialog: Color,
    val accent: Color, val accent2: Color, val onAccent: Color, val ok: Color, val lilac: Color,
    val cardFrom: Color, val cardTo: Color, val cardRim: Color, val cardRadius: Int,
    /** 0 none (glass), 1 soft drop shadow, 2 raised neumorphic. */
    val cardShadow: Int, val shadowColor: Color, val shadowLight: Color,
    val btnRadius: Int,
)

internal val PACKS = listOf(
    ThemePack(
        name = "DREAMSCAPE", tagline = "Violet dusk, peach glow, glass tiles", light = false,
        bg = listOf(Color(0xFF1F174F), Color(0xFF3A2278), Color(0xFF5B2F82)),
        blobs = listOf(
            Blob(Color(0xFFFFA07A), 0.12f, 0.02f, 0.95f, 0.38f),
            Blob(Color(0xFF2EE6C5), 0.98f, 0.45f, 0.55f, 0.20f),
            Blob(Color(0xFFB79CFF), 0.50f, 1.00f, 0.90f, 0.30f),
        ),
        scenery = 1,
        ink = Color(0xFF2A1F66), inkSoft = Color(0xFF3A2A7A), panel = Color(0xCC3A2A78), dock = Color(0xEE2B2070), hair = Color(0x40FFFFFF),
        dim = Color(0xFFE6DCFA), fg = Color(0xFFFFFFFF), soft = Color(0xFFF3ECFF), dialog = Color(0xFF33277A),
        accent = Color(0xFFFFB892), accent2 = Color(0xFFD07BFF), onAccent = Color(0xFF2A1450), ok = Color(0xFF62F5C8), lilac = Color(0xFFFFD0E4),
        cardFrom = Color(0x2EFFFFFF), cardTo = Color(0x14FFFFFF), cardRim = Color(0x66FFFFFF), cardRadius = 26,
        cardShadow = 0, shadowColor = Color(0x66150A3A), shadowLight = Color(0x00FFFFFF),
        btnRadius = 20,
    ),
    ThemePack(
        name = "COBALT CORAL", tagline = "Electric blue fading to coral red", light = false,
        bg = listOf(Color(0xFF071B55), Color(0xFF0F3A9A), Color(0xFF2A63D8), Color(0xFFC22E48)),
        blobs = listOf(
            Blob(Color(0xFFBFD8FF), 0.15f, 0.00f, 0.90f, 0.28f),
            Blob(Color(0xFFFF5A68), 0.95f, 0.95f, 0.80f, 0.35f),
        ),
        scenery = 2,
        ink = Color(0xFF071B55), inkSoft = Color(0xFF0F3A9A), panel = Color(0xD9102F7A), dock = Color(0xF00A2166), hair = Color(0x40FFFFFF),
        dim = Color(0xFFD6E2FF), fg = Color(0xFFFFFFFF), soft = Color(0xFFE6EEFF), dialog = Color(0xFF0F2C78),
        accent = Color(0xFFFF4D63), accent2 = Color(0xFF1F7AE8), onAccent = Color(0xFFFFFFFF), ok = Color(0xFF5BF0AE), lilac = Color(0xFFFFB0B8),
        cardFrom = Color(0xE0102A74), cardTo = Color(0xD0163F9E), cardRim = Color(0x66CFE0FF), cardRadius = 28,
        cardShadow = 0, shadowColor = Color(0x66050F3A), shadowLight = Color(0x00FFFFFF),
        btnRadius = 22,
    ),
    ThemePack(
        name = "BLACK AMBER", tagline = "Black glass with molten amber", light = false,
        bg = listOf(Color(0xFF050508), Color(0xFF0D0D13), Color(0xFF17120A)),
        blobs = listOf(
            Blob(Color(0xFFFFB800), 0.95f, 0.02f, 0.75f, 0.26f),
            Blob(Color(0xFFFF8A00), 0.02f, 0.98f, 0.70f, 0.16f),
        ),
        scenery = 0,
        ink = Color(0xFF050508), inkSoft = Color(0xFF0D0D13), panel = Color(0xEE15151C), dock = Color(0xF00A0A0F), hair = Color(0xFF2B2A26),
        dim = Color(0xFFB5A67C), fg = Color(0xFFFFF8E6), soft = Color(0xFFF0E6C8), dialog = Color(0xFF14141A),
        accent = Color(0xFFFFC21A), accent2 = Color(0xFFFF9500), onAccent = Color(0xFF1A1200), ok = Color(0xFF8FE388), lilac = Color(0xFFFFD27A),
        cardFrom = Color(0xFF1D1D26), cardTo = Color(0xFF121218), cardRim = Color(0x66FFC21A), cardRadius = 26,
        cardShadow = 1, shadowColor = Color(0xCC000000), shadowLight = Color(0x00FFFFFF),
        btnRadius = 24,
    ),
    ThemePack(
        name = "PORCELAIN", tagline = "Soft light, raised graphite buttons", light = true,
        bg = listOf(Color(0xFFF5F6F8), Color(0xFFECEEF2)),
        blobs = emptyList(),
        scenery = 0,
        ink = Color(0xFFF0F1F4), inkSoft = Color(0xFFE6E8ED), panel = Color(0xF2F6F7F9), dock = Color(0xFFF2F3F6), hair = Color(0xFFD5D8E0),
        dim = Color(0xFF5C6170), fg = Color(0xFF15161A), soft = Color(0xFF2A2C33), dialog = Color(0xFFF6F7F9),
        accent = Color(0xFF1B1C20), accent2 = Color(0xFF4A4C56), onAccent = Color(0xFFFFFFFF), ok = Color(0xFF157A42), lilac = Color(0xFF4A4C56),
        cardFrom = Color(0xFFF7F8FA), cardTo = Color(0xFFEDEFF3), cardRim = Color(0xCCFFFFFF), cardRadius = 28,
        cardShadow = 2, shadowColor = Color(0x59A3AEC4), shadowLight = Color(0xFFFFFFFF),
        btnRadius = 26,
    ),
    ThemePack(
        name = "GLASS NAVY", tagline = "Frosted glass over deep blue, gold accent", light = false,
        bg = listOf(Color(0xFF1B3F78), Color(0xFF132E5C), Color(0xFF0B1B3C)),
        blobs = listOf(
            Blob(Color(0xFF7FB2FF), 0.10f, 0.00f, 0.90f, 0.28f),
            Blob(Color(0xFFF6C453), 0.98f, 0.60f, 0.50f, 0.10f),
            Blob(Color(0xFF4F8CFF), 0.40f, 1.00f, 0.80f, 0.22f),
        ),
        scenery = 2,
        ink = Color(0xFF0F2348), inkSoft = Color(0xFF18396D), panel = Color(0xCC1B3F78), dock = Color(0xF00F2348), hair = Color(0x40FFFFFF),
        dim = Color(0xFFD3E0F5), fg = Color(0xFFFFFFFF), soft = Color(0xFFE8F0FF), dialog = Color(0xFF173565),
        accent = Color(0xFFF6C453), accent2 = Color(0xFFE8A33A), onAccent = Color(0xFF2A1D00), ok = Color(0xFF7CF0B8), lilac = Color(0xFFC9D8FF),
        cardFrom = Color(0x38FFFFFF), cardTo = Color(0x14FFFFFF), cardRim = Color(0x73FFFFFF), cardRadius = 28,
        cardShadow = 0, shadowColor = Color(0x66061530), shadowLight = Color(0x00FFFFFF),
        btnRadius = 22,
    ),
    ThemePack(
        name = "ROSE NOIR", tagline = "Black wine, rose gold and garnet", light = false,
        bg = listOf(Color(0xFF0C0408), Color(0xFF1D0A15), Color(0xFF2C1020)),
        blobs = listOf(
            Blob(Color(0xFFF0B3A0), 0.90f, 0.05f, 0.80f, 0.26f),
            Blob(Color(0xFFC0184F), 0.05f, 0.95f, 0.80f, 0.30f),
        ),
        scenery = 0,
        ink = Color(0xFF0C0408), inkSoft = Color(0xFF1D0A15), panel = Color(0xE6241020), dock = Color(0xF0120610), hair = Color(0x40F0B8A8),
        dim = Color(0xFFD2A8A0), fg = Color(0xFFFFF0EC), soft = Color(0xFFF7DAD2), dialog = Color(0xFF1E0B16),
        accent = Color(0xFFF4B8A4), accent2 = Color(0xFFD9877B), onAccent = Color(0xFF2B0F0C), ok = Color(0xFF8CE8B8), lilac = Color(0xFFFFC9BA),
        cardFrom = Color(0xB3341526), cardTo = Color(0x99200C17), cardRim = Color(0x66F0B8A8), cardRadius = 24,
        cardShadow = 0, shadowColor = Color(0x66000000), shadowLight = Color(0x00FFFFFF),
        btnRadius = 22,
    ),
    ThemePack(
        name = "PEARL AURORA", tagline = "Light pastel glass, lilac and peach", light = true,
        bg = listOf(Color(0xFFF4EEFF), Color(0xFFE6F1FF), Color(0xFFFFEFE8)),
        blobs = listOf(
            Blob(Color(0xFFC4A8FF), 0.10f, 0.05f, 0.80f, 0.40f),
            Blob(Color(0xFFFFB79A), 0.95f, 0.90f, 0.80f, 0.38f),
            Blob(Color(0xFF9ADBFF), 0.90f, 0.25f, 0.50f, 0.25f),
        ),
        scenery = 1,
        ink = Color(0xFFF4EEFF), inkSoft = Color(0xFFE6F1FF), panel = Color(0xE6FFFFFF), dock = Color(0xF2FFFFFF), hair = Color(0xFFD9D2F0),
        dim = Color(0xFF6B6490), fg = Color(0xFF241B4A), soft = Color(0xFF3A3166), dialog = Color(0xFFFFFFFF),
        accent = Color(0xFF6B47F0), accent2 = Color(0xFFB84FE6), onAccent = Color(0xFFFFFFFF), ok = Color(0xFF14805A), lilac = Color(0xFF7B4FD8),
        cardFrom = Color(0xD9FFFFFF), cardTo = Color(0xA6FFFFFF), cardRim = Color(0xFFFFFFFF), cardRadius = 26,
        cardShadow = 1, shadowColor = Color(0x4D7B5CFF), shadowLight = Color(0x00FFFFFF),
        btnRadius = 24,
    ),
)

/** A soft blurred shadow under a rounded shape. [fill] must be opaque so the shadow shows. */
internal fun Modifier.softShadow(
    color: Color, blur: Dp, dx: Dp, dy: Dp, radius: Dp, fill: Color,
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = fill.toArgb()
            setShadowLayer(blur.toPx(), dx.toPx(), dy.toPx(), color.toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius.toPx(), radius.toPx(), paint)
    }
}

/** Layered mountains or waves along the bottom of the screen, tinted from the style's own colours. */
internal fun DrawScope.drawScenery(p: ThemePack) {
    val w = size.width
    val h = size.height
    val far = p.accent2
    val mid = p.accent
    val near = if (p.light) Color.White else p.ink
    when (p.scenery) {
        1 -> {
            val layers = listOf(
                Triple(far.copy(alpha = 0.16f), 0.80f, listOf(0f to 0.80f, 0.18f to 0.68f, 0.34f to 0.78f, 0.52f to 0.62f, 0.70f to 0.77f, 0.86f to 0.66f, 1f to 0.76f)),
                Triple(mid.copy(alpha = 0.14f), 0.86f, listOf(0f to 0.84f, 0.22f to 0.76f, 0.40f to 0.86f, 0.58f to 0.74f, 0.78f to 0.85f, 1f to 0.78f)),
                Triple(near.copy(alpha = if (p.light) 0.55f else 0.40f), 0.92f, listOf(0f to 0.92f, 0.25f to 0.86f, 0.50f to 0.94f, 0.75f to 0.87f, 1f to 0.93f)),
            )
            layers.forEach { (c, _, pts) ->
                val path = Path().apply {
                    moveTo(0f, h)
                    pts.forEach { (fx, fy) -> lineTo(w * fx, h * fy) }
                    lineTo(w, h)
                    close()
                }
                drawPath(path, c)
            }
        }
        2 -> {
            val waves = listOf(
                Triple(far.copy(alpha = 0.14f), 0.86f, 1.3f),
                Triple(mid.copy(alpha = 0.12f), 0.90f, 1.8f),
                Triple(near.copy(alpha = if (p.light) 0.5f else 0.30f), 0.95f, 2.4f),
            )
            waves.forEachIndexed { k, (c, base, freq) ->
                val path = Path().apply {
                    moveTo(0f, h)
                    val steps = 28
                    for (i in 0..steps) {
                        val fx = i / steps.toFloat()
                        val y = h * base + Math.sin(fx * 2.0 * Math.PI * freq + k * 1.7).toFloat() * h * 0.014f
                        lineTo(w * fx, y)
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(path, c)
            }
        }
    }
}

/** A preview card for the style picker. Reads the style's own colours, not the live theme. */
@Composable
internal fun ThemeTile(pack: ThemePack?, selected: Boolean, onClick: () -> Unit) {
    val bg = pack?.bg ?: listOf(Color(0xFF070B14), Color(0xFF0D1424))
    val hair = pack?.hair ?: Color(0xFF22304A)
    val a1 = pack?.accent ?: ACCENTS[0].main
    val a2 = pack?.accent2 ?: ACCENTS[0].second
    val fg = pack?.fg ?: Color.White
    val dim = pack?.dim ?: Color(0xFF8A9BB8)
    val cardFrom = pack?.cardFrom ?: Color(0xCC111A2C)
    val cardTo = pack?.cardTo ?: Color(0xCC111A2C)
    val rim = pack?.cardRim ?: Color(0x5500E5FF)
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .width(108.dp)
            .height(150.dp)
            .pressable(onClick = onClick)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.linearGradient(bg, start = Offset.Zero, end = Offset(size.width, size.height)))
                if (pack != null) {
                    pack.blobs.forEach { b ->
                        val c = Offset(size.width * b.x, size.height * b.y)
                        val r = size.width * b.r
                        drawCircle(
                            brush = Brush.radialGradient(listOf(b.color.copy(alpha = b.alpha), Color.Transparent), center = c, radius = r),
                            radius = r, center = c,
                        )
                    }
                    drawScenery(pack)
                } else {
                    val c = Offset(size.width * 0.5f, 0f)
                    drawCircle(
                        brush = Brush.radialGradient(listOf(a1.copy(alpha = 0.25f), Color.Transparent), center = c, radius = size.width),
                        radius = size.width, center = c,
                    )
                }
            }
            .border(if (selected) 2.5.dp else 1.dp, if (selected) a1 else hair, shape),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                val cardShape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(cardShape)
                        .background(Brush.linearGradient(listOf(cardFrom, cardTo)))
                        .border(1.dp, rim, cardShape),
                ) {
                    Column(Modifier.padding(7.dp)) {
                        Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.85f)))
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(dim.copy(alpha = 0.6f)))
                    }
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(a1, a2))),
                )
            }
            Text(pack?.name ?: "CLASSIC", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, color = fg, maxLines = 1)
        }
    }
}
