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
import com.squeve.redail.data.computeStats

// ───────────────────────── Stats ─────────────────────────
@Composable
internal fun StatsPanel(cfg: Config, modifier: Modifier) {
    val ctx = LocalContext.current
    val records by produceState<List<HistoryRecord>?>(null) {
        value = withContext(Dispatchers.IO) { HistoryStore.load(ctx, 20_000) }
    }
    val all = records
    val stats = remember(all) { all?.let { computeStats(it) } }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Hair, shape)) {
        when {
            stats == null -> Text("Loading…", fontFamily = Mono, fontSize = 12.sp, color = Dim, modifier = Modifier.padding(16.dp))
            stats.calls == 0 -> EmptyState(1, "No stats yet", "Answer rates build up as real calls are recorded.")
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                item {
                    Row {
                        StatTile("CALLS", "${stats.calls}", Fg, Modifier.weight(1f))
                        StatTile("ANSWERED", "${stats.ratePct}%", Mint, Modifier.weight(1f))
                        StatTile("BEST HOUR", stats.bestHour()?.let { "%02d:00".format(it.hour) } ?: "-", Cyan, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Caption(
                        "${stats.answered} answered · ${stats.busy} busy or declined · " +
                            "${stats.noAnswer} no answer · ${stats.failed} failed",
                    )
                    Spacer(Modifier.height(8.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("BY TIME OF DAY")
                }
                items(
                    listOf("NIGHT 00-06" to 0..5, "MORNING 06-12" to 6..11, "AFTERNOON 12-18" to 12..17, "EVENING 18-24" to 18..23),
                ) { (label, hours) ->
                    val inBlock = stats.perHour.filter { it.hour in hours }
                    RateBar(label, inBlock.sumOf { it.calls }, inBlock.sumOf { it.answered })
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("BY NUMBER")
                }
                items(stats.perNumber.take(25)) { n ->
                    val who = cfg.names[n.number]
                    RateBar(if (who != null) "$who · ${n.number}" else n.number, n.calls, n.answered)
                }
                if (stats.perNumber.size > 25) item { Caption("+ ${stats.perNumber.size - 25} more numbers.") }
            }
        }
    }
}

@Composable
private fun RateBar(label: String, calls: Int, answered: Int) {
    val pct = if (calls == 0) 0 else answered * 100 / calls
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontFamily = Mono, fontSize = 12.sp, color = Fg, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                if (calls == 0) "no calls" else "$pct% · $calls calls",
                fontFamily = Mono, fontSize = 11.sp, color = if (pct >= 50) Mint else Dim,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Hair)) {
            Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(Mint))
        }
    }
}
