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

// ───────────────────────── Banners and run summary ─────────────────────────
@Composable
internal fun ResumeBanner(cp: Checkpoint, onResume: () -> Unit, onDiscard: () -> Unit) {
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
internal fun ScheduleBanner(s: Scheduled, onCancel: () -> Unit) {
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
internal fun SummaryHost(cfg: Config, a: DialActions) {
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
internal fun StatTile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = color)
        Text(label, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp, color = Dim)
    }
}

@Composable
internal fun SummaryCard(
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
