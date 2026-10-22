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

// ───────────────────────── Queue ─────────────────────────
@Composable
internal fun AddNumberRow(enabled: Boolean, onAdd: (String) -> Unit, a: DialActions) {
    // Keystrokes only recompose this row, not the whole screen. Saved so tab switches keep the text.
    var input by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("Number(s), comma separated") },
        singleLine = false,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        enabled = enabled,
        colors = fieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    NeonButton(
        "ADD TO QUEUE", enabled = enabled && input.isNotBlank(),
        icon = { Glyph(Icons.Filled.Add, it, 20.dp) },
    ) {
        onAdd(input)
        input = ""
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceButton("CONTACTS", Icons.Filled.Person, enabled, Modifier.weight(1f), a.contacts)
        SourceButton("CALL LOG", Icons.Filled.Call, enabled, Modifier.weight(1f), a.callLog)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceButton("PASTE", Icons.Filled.Create, enabled, Modifier.weight(1f), a.paste)
        SourceButton("IMPORT FILE", Icons.Filled.Share, enabled, Modifier.weight(1f), a.importFile)
    }
}

@Composable
internal fun SourceButton(
    label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    GhostButton(
        label, enabled = enabled,
        modifier = modifier.height(44.dp),
        icon = { Glyph(icon, it, 16.dp) },
        onClick = onClick,
    )
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Hair,
    disabledBorderColor = Hair,
    cursorColor = Cyan,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = Dim,
    disabledLabelColor = Dim,
    focusedTextColor = Fg,
    unfocusedTextColor = Fg,
    disabledTextColor = Dim,
)

@Composable
internal fun QueueList(
    numbers: List<String>, names: Map<String, String>, cfg: Config,
    canEdit: Boolean, onEdit: (Int) -> Unit, onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit,
) {
    val status by RedialService.status.collectAsState()
    val finished = status is EngineState.Finished
    val p = status.progress()
    val listState = rememberLazyListState()
    val list = numbers.toList()

    // Keep the number being dialled in view during a run.
    val currentIndex = p?.jobIndex
    LaunchedEffect(currentIndex) {
        if (currentIndex != null && currentIndex in numbers.indices) listState.animateScrollToItem(currentIndex)
    }

    // Stable keys, so each row keeps its own swipe and entry state when the list changes.
    val counts = HashMap<String, Int>()
    val keys = list.map { n ->
        val c = counts[n] ?: 0
        counts[n] = c + 1
        "$n#$c"
    }
    val heights = remember { HashMap<String, Float>() }

    // Drag to reorder: rows slide aside while dragging; the move is applied on release.
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowH by remember { mutableFloatStateOf(150f) }
    val from = dragFrom
    val target = if (from == null) -1 else (from + (dragY / rowH).roundToInt()).coerceIn(0, (list.size - 1).coerceAtLeast(0))

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = if (canEdit) 460.dp else 340.dp),
    ) {
        itemsIndexed(list, key = { i, _ -> keys.getOrElse(i) { "row$i" } }) { i, n ->
            val completed = p?.completed?.getOrNull(i) ?: 0
            val callsEach = cfg.callsFor(n)
            // 0 = queued, 1 = current, 2 = done
            val state = when {
                finished -> 2
                p == null -> 0
                completed >= callsEach -> 2
                i == p.jobIndex -> 1
                else -> 0
            }
            val shiftPx = if (from == null) {
                0f
            } else {
                when {
                    i == from -> dragY
                    from < i && i <= target -> -rowH
                    target <= i && i < from -> rowH
                    else -> 0f
                }
            }
            // New rows ease in.
            val visible = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.zIndex(if (i == from) 1f else 0f).graphicsLayer { translationY = shiftPx },
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            ) {
                SwipeToRemove(
                    enabled = canEdit && from == null,
                    onRemove = { onRemove(i) },
                    modifier = Modifier.onSizeChanged { sz ->
                        if (sz.height > 0) heights[keys.getOrElse(i) { "row$i" }] = sz.height.toFloat()
                    },
                ) {
                    QueueItem(
                        index = i, number = n, name = names[n], custom = cfg.customSummary(n), state = state,
                        doneCount = if (finished) callsEach else completed, total = callsEach,
                        canRemove = canEdit, dragging = i == from,
                        onEdit = { onEdit(i) }, onRemove = { onRemove(i) },
                        onDragStart = {
                            rowH = heights[keys.getOrElse(i) { "row$i" }] ?: rowH
                            dragY = 0f
                            dragFrom = i
                        },
                        onDrag = { dy ->
                            dragY += dy
                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == dragFrom }
                            if (info != null) {
                                val top = info.offset + dragY
                                val bottom = top + info.size
                                val viewport = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).toFloat()
                                val d = when {
                                    top < 0f -> -24f
                                    bottom > viewport -> 24f
                                    else -> 0f
                                }
                                if (d != 0f) dragY += listState.dispatchRawDelta(d)
                            }
                        },
                        onDragEnd = {
                            val f = dragFrom
                            if (f != null) {
                                val to = (f + (dragY / rowH).roundToInt()).coerceIn(0, (numbers.size - 1).coerceAtLeast(0))
                                if (to != f) onMove(f, to)
                            }
                            dragFrom = null
                            dragY = 0f
                        },
                    )
                }
            }
        }
    }
}

/** Swipe a row sideways far enough to remove it. */
@Composable
internal fun SwipeToRemove(
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val removeNow by rememberUpdatedState(onRemove)
    var x by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        if (x != 0f) {
            val frac = (abs(x) / width).coerceIn(0f, 1f)
            Box(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Magenta.copy(alpha = 0.18f + 0.5f * frac))
                    .padding(horizontal = 18.dp),
                contentAlignment = if (x > 0f) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text("REMOVE", fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp, color = Color.White)
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = x }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val go = abs(x) > width * 0.4f
                                scope.launch {
                                    if (go) {
                                        val dir = if (x > 0f) 1f else -1f
                                        animate(x, dir * width, animationSpec = tween(140)) { v, _ -> x = v }
                                        if (Theming.haptics.value) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        removeNow()
                                    } else {
                                        animate(x, 0f, animationSpec = tween(160)) { v, _ -> x = v }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { animate(x, 0f, animationSpec = tween(160)) { v, _ -> x = v } }
                            },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                x += dx
                            },
                        )
                    }
                },
        ) { content() }
    }
}

@Composable
internal fun DragHandle(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        for (k in 0..2) {
            val y = h * (0.25f + 0.25f * k)
            drawLine(color, Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Appears for a few seconds after a number is removed. */
@Composable
internal fun UndoBar(cfg: Config) {
    val r = cfg.removed
    if (r != null) {
        LaunchedEffect(r) {
            delay(5000)
            if (cfg.removed === r) cfg.removed = null
        }
        Row(
            Modifier.fillMaxWidth().background(Dock).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Removed ${r.number}", fontFamily = Mono, fontSize = 13.sp, color = Fg,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            LinkButton("UNDO") { cfg.undoRemove() }
        }
    }
}

@Composable
internal fun QueueItem(
    index: Int, number: String, name: String?, custom: String?, state: Int, doneCount: Int, total: Int,
    canRemove: Boolean, dragging: Boolean,
    onEdit: () -> Unit, onRemove: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
) {
    val current = state == 1
    val done = state == 2
    val shape = RoundedCornerShape(12.dp)
    val haptic = LocalHapticFeedback.current
    val startNow by rememberUpdatedState(onDragStart)
    val dragNow by rememberUpdatedState(onDrag)
    val endNow by rememberUpdatedState(onDragEnd)
    Column(
        Modifier
            .pressable(enabled = canRemove, onClick = onEdit)
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(
                when {
                    dragging -> Violet.copy(alpha = 0.22f)
                    current -> Cyan.copy(alpha = 0.10f)
                    else -> Color.Transparent
                },
            )
            .border(1.dp, if (dragging || current) Cyan.copy(alpha = 0.7f) else Hair, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                if (done) {
                    Glyph(Icons.Filled.Check, Mint, 16.dp)
                } else {
                    Text(
                        "%02d".format(index + 1), fontFamily = Mono, fontSize = 13.sp,
                        color = if (current) Cyan else Dim,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    number, fontFamily = Mono, fontSize = 17.sp,
                    color = if (done) Dim else Fg,
                )
                if (name != null) {
                    Text(name, fontSize = 11.sp, color = if (current) Cyan else Dim, maxLines = 1)
                }
                if (custom != null) {
                    Text(
                        custom, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 0.5.sp,
                        color = Lilac, maxLines = 1,
                    )
                }
            }
            if (canRemove) {
                // Press and hold the handle, then drag to reorder.
                Box(
                    Modifier
                        .size(36.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    if (Theming.haptics.value) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    startNow()
                                },
                                onDragEnd = { endNow() },
                                onDragCancel = { endNow() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragNow(amount.y)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { DragHandle(if (dragging) Cyan else Dim) }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).pressable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) { Glyph(Icons.Filled.Close, Dim, 18.dp) }
            } else {
                Text(
                    "$doneCount/$total", fontFamily = Mono, fontSize = 14.sp,
                    color = if (current) Cyan else Dim,
                )
            }
        }
        if (current && total > 0) {
            Spacer(Modifier.height(8.dp))
            MiniBar(doneCount.toFloat() / total)
        }
    }
}

@Composable
internal fun MiniBar(fraction: Float) {
    val f by animateFloatAsState(targetValue = fraction.coerceIn(0f, 1f), animationSpec = tween(400), label = "bar")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Hair)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(NeonBrush))
    }
}
