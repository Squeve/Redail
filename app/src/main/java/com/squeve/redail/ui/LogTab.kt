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

// ───────────────────────── LOG tab ─────────────────────────
@Composable
internal fun LogTab(cfg: Config) {
    var mode by rememberSaveable { mutableStateOf(0) }   // 0 live, 1 history
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegChip("LIVE", mode == 0, true, Modifier.weight(1f)) { mode = 0 }
            SegChip("HISTORY", mode == 1, true, Modifier.weight(1f)) { mode = 1 }
        }
        Spacer(Modifier.height(12.dp))
        if (mode == 0) {
            EventFeed(Modifier.weight(1f))
        } else {
            HistoryList(cfg, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun EventFeed(modifier: Modifier) {
    val lines by EventLog.lines.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Hair, shape)) {
        if (lines.isEmpty()) {
            EmptyState(1, "Nothing yet", "What the engine is doing shows up here while a run is going.")
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(lines.size) { i ->
                    val line = lines[i]
                    val color = when {
                        line.contains("answered") && !line.contains("no answer") -> Mint
                        line.contains("no answer") || line.contains("Stopped") || line.contains("Waiting") ||
                            line.contains("Could not") || line.contains("did not start") -> Magenta
                        line.contains("Dialing") -> Cyan
                        else -> Dim
                    }
                    Text(line, fontFamily = Mono, fontSize = 11.5.sp, color = color, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
internal fun HistoryList(cfg: Config, modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(0) }       // 0 all, 1 answered, 2 no answer, 3 failed
    var confirmClear by remember { mutableStateOf(false) }
    val records by produceState<List<HistoryRecord>?>(null, reload) {
        value = withContext(Dispatchers.IO) { HistoryStore.load(ctx, 600) }
    }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL", "ANSWERED", "NO ANS", "FAILED").forEachIndexed { i, label ->
                SegChip(label, filter == i, true, Modifier.weight(1f)) { filter = i }
            }
        }
        Spacer(Modifier.height(10.dp))

        val all = records
        val shown = (all ?: emptyList()).filter {
            when (filter) {
                1 -> it.outcome == "CONNECTED"
                2 -> it.outcome == "NO_ANSWER"
                3 -> it.outcome == "FAILED_TO_START"
                else -> true
            }
        }
        val shape = RoundedCornerShape(16.dp)
        Box(Modifier.weight(1f).fillMaxWidth().clip(shape).background(Panel).border(1.dp, Hair, shape)) {
            when {
                all == null -> Text("Loading…", fontFamily = Mono, fontSize = 12.sp, color = Dim, modifier = Modifier.padding(16.dp))
                shown.isEmpty() -> EmptyState(1, "No calls here yet", "Every real call is recorded once a run places it.")
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp)) {
                    items(shown) { r -> HistoryRow(r, cfg.names[r.number]) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(
                if (confirmClear) "TAP AGAIN" else "CLEAR",
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                if (confirmClear) {
                    scope.launch {
                        withContext(Dispatchers.IO) { HistoryStore.clear(ctx) }
                        confirmClear = false
                        reload++
                    }
                } else {
                    confirmClear = true
                }
            }
            NeonButton("EXPORT CSV", modifier = Modifier.weight(1f)) {
                scope.launch {
                    val f = withContext(Dispatchers.IO) { HistoryStore.exportCsv(ctx) }
                    shareFile(ctx, f, "text/csv")
                }
            }
        }
    }
}

@Composable
internal fun HistoryRow(r: HistoryRecord, name: String?) {
    val fmt = remember { SimpleDateFormat("d MMM · HH:mm:ss", Locale.getDefault()) }
    val (label, color) = when (r.outcome) {
        "CONNECTED" -> "ANSWERED" to Mint
        "NO_ANSWER" -> "NO ANSWER" to Dim
        "FAILED_TO_START" -> "FAILED" to Magenta
        else -> "CALL" to Dim
    }
    val shownName = r.name.ifBlank { name.orEmpty() }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.number, fontFamily = Mono, fontSize = 14.sp, color = Fg)
            if (shownName.isNotBlank()) Text(shownName, fontSize = 11.sp, color = Cyan, maxLines = 1)
            Text(
                fmt.format(Date(r.ts)) + (if (r.sim >= 0) " · SIM ${r.sim + 1}" else ""),
                fontFamily = Mono, fontSize = 10.sp, color = Dim,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(label, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp, color = color)
            Text("${r.durationSec}s", fontFamily = Mono, fontSize = 11.sp, color = Dim)
        }
    }
    Hairline()
}
