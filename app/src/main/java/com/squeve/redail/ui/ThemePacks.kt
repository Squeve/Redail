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

// ───────────────────────── Theme styles ─────────────────────────
/** A complete look: surfaces, text, accents and background art. CLASSIC (index 0) is not one of these. */
internal class ThemePack(
    val name: String, val tagline: String, val light: Boolean,
    val ink: Color, val inkSoft: Color, val panel: Color, val dock: Color, val hair: Color,
    val dim: Color, val fg: Color, val soft: Color, val dialog: Color,
    val accent: Color, val accent2: Color, val ok: Color, val lilac: Color,
    val glowA: Color, val glowB: Color,
    /** 0 grid, 1 dots, 2 diagonal lines, 3 rings. */
    val pattern: Int,
)

internal val PACKS = listOf(
    ThemePack(
        "ROYAL GOLD", "Black and molten gold", false,
        Color(0xFF0A0804),
        Color(0xFF14100A),
        Color(0xD9201808),
        Color(0xF0100C06),
        Color(0xFF3D3015),
        Color(0xFFB9A57A),
        Color(0xFFFFF4D9),
        Color(0xFFF3E3BC),
        Color(0xFF1A1409),
        Color(0xFFFFD166),
        Color(0xFFE09F3E),
        Color(0xFF7BE495),
        Color(0xFFF2B880),
        Color(0xFFFFC857),
        Color(0xFFB5651D),
        2,
    ),
    ThemePack(
        "EMERALD NOIR", "Deep green with jade light", false,
        Color(0xFF03100B),
        Color(0xFF07201A),
        Color(0xD90B2A21),
        Color(0xF0061711),
        Color(0xFF14503C),
        Color(0xFF86B8A4),
        Color(0xFFE9FFF5),
        Color(0xFFCDF5E4),
        Color(0xFF08201A),
        Color(0xFF2EF2A0),
        Color(0xFF12B5CB),
        Color(0xFF2EF2A0),
        Color(0xFF8FF0D0),
        Color(0xFF00D68F),
        Color(0xFF0A6E78),
        1,
    ),
    ThemePack(
        "CRIMSON VELVET", "Wine red and ember", false,
        Color(0xFF12050A),
        Color(0xFF210A14),
        Color(0xD9331019),
        Color(0xF0180710),
        Color(0xFF5C1A2E),
        Color(0xFFD09BAA),
        Color(0xFFFFEAF0),
        Color(0xFFFAD2DC),
        Color(0xFF2A0C16),
        Color(0xFFFF4D6D),
        Color(0xFFFF9E5E),
        Color(0xFF6EE7B7),
        Color(0xFFFFA8B8),
        Color(0xFFFF2E63),
        Color(0xFF8A1538),
        3,
    ),
    ThemePack(
        "OCEAN ABYSS", "Midnight blue, electric aqua", false,
        Color(0xFF020B1C),
        Color(0xFF06203D),
        Color(0xD90A2A4D),
        Color(0xF0041429),
        Color(0xFF13497A),
        Color(0xFF8DB5D9),
        Color(0xFFE8F6FF),
        Color(0xFFCDE6FA),
        Color(0xFF071E38),
        Color(0xFF3DD9FF),
        Color(0xFF3D7BFF),
        Color(0xFF3EE6B0),
        Color(0xFFA0C4FF),
        Color(0xFF0AA3E8),
        Color(0xFF1B4DB3),
        0,
    ),
    ThemePack(
        "SUNSET BLAZE", "Plum sky, orange to pink", false,
        Color(0xFF130820),
        Color(0xFF24103A),
        Color(0xD9381A57),
        Color(0xF0190B2B),
        Color(0xFF5D2E86),
        Color(0xFFD0A8E0),
        Color(0xFFFFF1E6),
        Color(0xFFFFDCC7),
        Color(0xFF2A1244),
        Color(0xFFFF8A1F),
        Color(0xFFFF2E93),
        Color(0xFF7FE9A8),
        Color(0xFFFFB085),
        Color(0xFFFF6A00),
        Color(0xFFC2186B),
        2,
    ),
    ThemePack(
        "CYBER VIOLET", "Neon violet and cyan", false,
        Color(0xFF0A0516),
        Color(0xFF150B2E),
        Color(0xD91F1145),
        Color(0xF0100824),
        Color(0xFF3F2488),
        Color(0xFFB9A6E8),
        Color(0xFFF3EBFF),
        Color(0xFFDDD0F8),
        Color(0xFF1A0E3A),
        Color(0xFFB65CFF),
        Color(0xFF00E5FF),
        Color(0xFF39F5B5),
        Color(0xFFD2A8FF),
        Color(0xFF9B3DFF),
        Color(0xFF0AB7D6),
        0,
    ),
    ThemePack(
        "OBSIDIAN SILVER", "Black glass, platinum", false,
        Color(0xFF08080A),
        Color(0xFF121215),
        Color(0xD91C1C21),
        Color(0xF00D0D10),
        Color(0xFF35353C),
        Color(0xFF9A9AA5),
        Color(0xFFFFFFFF),
        Color(0xFFE6E6EB),
        Color(0xFF16161A),
        Color(0xFFF2F4F8),
        Color(0xFF8F98A8),
        Color(0xFF8CE99A),
        Color(0xFFC9CED8),
        Color(0xFFC8CCD6),
        Color(0xFF4A4F5C),
        3,
    ),
    ThemePack(
        "AURORA", "Northern lights green and violet", false,
        Color(0xFF050B14),
        Color(0xFF0A1A24),
        Color(0xD90E2A33),
        Color(0xF0061018),
        Color(0xFF1B5A5E),
        Color(0xFF8FC1B8),
        Color(0xFFEAFFF8),
        Color(0xFFD2F7EC),
        Color(0xFF0B2229),
        Color(0xFF5CFFC8),
        Color(0xFFB07CFF),
        Color(0xFF5CFFC8),
        Color(0xFFC9B0FF),
        Color(0xFF3DFFB5),
        Color(0xFF8A4DFF),
        1,
    ),
    ThemePack(
        "ROSE QUARTZ", "Light, blush pink and plum", true,
        Color(0xFFFFF4F7),
        Color(0xFFFFE6EE),
        Color(0xE6FFFFFF),
        Color(0xF5FFFFFF),
        Color(0xFFF1C4D5),
        Color(0xFF8F6A7C),
        Color(0xFF2B1020),
        Color(0xFF4A2036),
        Color(0xFFFFFFFF),
        Color(0xFFD6336C),
        Color(0xFF7048E8),
        Color(0xFF23874F),
        Color(0xFF8B4DD8),
        Color(0xFFFF8FB5),
        Color(0xFFB197FC),
        1,
    ),
    ThemePack(
        "ARCTIC FROST", "Light, ice blue and teal", true,
        Color(0xFFF1F8FF),
        Color(0xFFE0EEFB),
        Color(0xE6FFFFFF),
        Color(0xF5FFFFFF),
        Color(0xFFC4DAF0),
        Color(0xFF57708C),
        Color(0xFF0A1E33),
        Color(0xFF1B3550),
        Color(0xFFFFFFFF),
        Color(0xFF0B7285),
        Color(0xFF1864AB),
        Color(0xFF2B8A3E),
        Color(0xFF5F3DC4),
        Color(0xFF74C0FC),
        Color(0xFF66D9E8),
        0,
    ),
)

/** A small preview card for the style picker. Reads the pack's own colours, not the live theme. */
@Composable
internal fun ThemeTile(pack: ThemePack?, selected: Boolean, onClick: () -> Unit) {
    val ink = pack?.ink ?: Color(0xFF070B14)
    val soft = pack?.inkSoft ?: Color(0xFF0D1424)
    val panel = pack?.panel ?: Color(0xCC111A2C)
    val hair = pack?.hair ?: Color(0xFF22304A)
    val a1 = pack?.accent ?: ACCENTS[0].main
    val a2 = pack?.accent2 ?: ACCENTS[0].second
    val glow = pack?.glowA ?: ACCENTS[0].main
    val fg = pack?.fg ?: Color.White
    val dim = pack?.dim ?: Color(0xFF8A9BB8)
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .width(96.dp)
            .height(128.dp)
            .pressable(onClick = onClick)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ink, soft)))
            .drawBehind {
                val c = Offset(size.width * 0.2f, 0f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(glow.copy(alpha = 0.40f), Color.Transparent), center = c, radius = size.width),
                    radius = size.width,
                    center = c,
                )
            }
            .border(if (selected) 2.5.dp else 1.dp, if (selected) a1 else hair, shape),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(panel).border(1.dp, hair, RoundedCornerShape(8.dp)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(a1, a2))))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(dim.copy(alpha = 0.5f)))
            }
            Text(pack?.name ?: "CLASSIC", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, color = fg, maxLines = 1)
        }
    }
}
