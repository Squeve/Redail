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

// ───────────────────────── Call log picker ─────────────────────────
@Composable
internal fun CallLogDialog(
    queued: List<String>,
    onDismiss: () -> Unit,
    onAdd: (List<RecentCall>) -> Unit,
) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf<List<RecentCall>?>(null) }
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) {
            runCatching { loadRecentCalls(ctx) }.getOrDefault(emptyList())
        }
    }
    var filter by remember { mutableStateOf(0) }              // 0 all, 1 missed, 2 out, 3 in
    val picked = remember { mutableStateListOf<String>() }    // RecentCall.key values
    val queuedKeys = remember(queued.size) {
        queued.map { n -> n.filter { it.isDigit() }.takeLast(9) }.toSet()
    }

    val all = entries
    val shown = (all ?: emptyList()).filter {
        when (filter) {
            1 -> it.type == CallLog.Calls.MISSED_TYPE
            2 -> it.type == CallLog.Calls.OUTGOING_TYPE
            3 -> it.type == CallLog.Calls.INCOMING_TYPE
            else -> true
        }
    }
    val selectable = shown.filter { it.key !in queuedKeys }
    val allPicked = selectable.isNotEmpty() && selectable.all { it.key in picked }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("RECENT CALLS") }
                    if (selectable.isNotEmpty()) {
                        LinkButton(if (allPicked) "CLEAR" else "SELECT ALL") {
                            if (allPicked) {
                                selectable.forEach { picked.remove(it.key) }
                            } else {
                                selectable.forEach { if (it.key !in picked) picked.add(it.key) }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "MISSED", "OUT", "IN").forEachIndexed { i, label ->
                        SegChip(label, filter == i, true, Modifier.weight(1f)) { filter = i }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        all == null ->
                            Text("Loading…", fontFamily = Mono, fontSize = 13.sp, color = Dim)
                        shown.isEmpty() ->
                            Text("No calls to show.", fontFamily = Mono, fontSize = 13.sp, color = Dim)
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.key }) { e ->
                                CallLogRow(e, e.key in picked, e.key in queuedKeys) {
                                    if (e.key in picked) picked.remove(e.key) else picked.add(e.key)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("CANCEL", modifier = Modifier.weight(1f).height(48.dp), onClick = onDismiss)
                    NeonButton(
                        "ADD ${picked.size}", enabled = picked.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        onAdd((all ?: emptyList()).filter { it.key in picked })
                    }
                }
            }
        }
    }
}

@Composable
internal fun CallLogRow(e: RecentCall, selected: Boolean, queued: Boolean, onToggle: () -> Unit) {
    val hasName = e.name.isNotBlank()
    Row(
        Modifier.fillMaxWidth().pressable(enabled = !queued, onClick = onToggle)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(selected, queued)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (hasName) e.name else e.number,
                fontFamily = if (hasName) Display else Mono, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, color = if (queued) Dim else Fg, maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(typeLabel(e.type), fontFamily = Mono, fontSize = 11.sp, color = typeColor(e.type))
                val rest = (if (hasName) "  ·  ${e.number}" else "") +
                    "  ·  ${ago(e.date)}" + (if (e.count > 1) "  ·  ×${e.count}" else "")
                Text(rest, fontFamily = Mono, fontSize = 11.sp, color = Dim, maxLines = 1)
            }
        }
        if (queued) {
            Text("QUEUED", fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp, color = Mint)
        }
    }
}

@Composable
internal fun CheckMark(selected: Boolean, disabled: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(22.dp)
            .clip(shape)
            .background(if (selected) Cyan else Color.Transparent)
            .border(1.5.dp, if (selected) Cyan else if (disabled) Hair else Dim, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Glyph(Icons.Filled.Check, Ink, 16.dp)
    }
}

// ───────────────────────── Per-number settings ─────────────────────────
@Composable
internal fun TargetDialog(
    number: String,
    name: String?,
    current: NumOverride?,
    defCalls: Int,
    defHangUp: Int?,
    defGap: Int,
    onSave: (NumOverride?) -> Unit,
    onDismiss: () -> Unit,
) {
    var callsOn by remember { mutableStateOf(current?.calls != null) }
    var calls by remember { mutableStateOf(current?.calls ?: defCalls) }
    // 0 = follow default, 1 = custom seconds, 2 = no limit
    var hangMode by remember {
        mutableStateOf(
            when (current?.hangUp) {
                null -> 0
                0 -> 2
                else -> 1
            },
        )
    }
    var hang by remember { mutableStateOf(current?.hangUp?.takeIf { it > 0 } ?: (defHangUp ?: 30)) }
    var gapOn by remember { mutableStateOf(current?.gap != null) }
    var gap by remember { mutableStateOf(current?.gap ?: defGap) }
    // 0 = follow default, 1 = on, 2 = off
    var spk by remember { mutableStateOf(when (current?.speaker) { null -> 0; true -> 1; false -> 2 }) }
    var mut by remember { mutableStateOf(when (current?.mute) { null -> 0; true -> 1; false -> 2 }) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 620.dp),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("TARGET SETTINGS")
                Text(number, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Fg)
                if (name != null) {
                    Text(name, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Cyan)
                }
                Caption("Anything left on default follows the CONFIG tab.")
                Spacer(Modifier.height(12.dp))
                Hairline()

                ToggleRow("Custom call count", "Default: $defCalls", callsOn, true) { callsOn = it }
                if (callsOn) {
                    StepperRow("Calls", calls, 1..500, true, { "$it" }) { calls = it }
                }
                Hairline()

                Spacer(Modifier.height(12.dp))
                FieldLabel("HANG UP AFTER")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegChip("DEFAULT", hangMode == 0, true, Modifier.weight(1f)) { hangMode = 0 }
                    SegChip("CUSTOM", hangMode == 1, true, Modifier.weight(1f)) { hangMode = 1 }
                    SegChip("NO LIMIT", hangMode == 2, true, Modifier.weight(1f)) { hangMode = 2 }
                }
                Caption("Default: " + (defHangUp?.let { "${it}s" } ?: "no limit"))
                if (hangMode == 1) {
                    StepperRow("Hang up after", hang, 5..600, true, { "${it / 60}m ${it % 60}s" }) { hang = it }
                }
                Spacer(Modifier.height(8.dp))
                Hairline()

                ToggleRow("Custom interval", "Default: ${defGap}s between calls", gapOn, true) { gapOn = it }
                if (gapOn) {
                    StepperRow("Interval", gap, 1..300, true, { "${it}s" }) { gap = it }
                }
                Hairline()

                Spacer(Modifier.height(12.dp))
                FieldLabel("SPEAKER")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegChip("DEFAULT", spk == 0, true, Modifier.weight(1f)) { spk = 0 }
                    SegChip("ON", spk == 1, true, Modifier.weight(1f)) { spk = 1 }
                    SegChip("OFF", spk == 2, true, Modifier.weight(1f)) { spk = 2 }
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("MICROPHONE MUTE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegChip("DEFAULT", mut == 0, true, Modifier.weight(1f)) { mut = 0 }
                    SegChip("MUTED", mut == 1, true, Modifier.weight(1f)) { mut = 1 }
                    SegChip("OPEN", mut == 2, true, Modifier.weight(1f)) { mut = 2 }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("RESET", modifier = Modifier.weight(1f).height(48.dp)) { onSave(null) }
                    NeonButton("SAVE", modifier = Modifier.weight(1f)) {
                        val ov = NumOverride(
                            calls = if (callsOn) calls else null,
                            hangUp = when (hangMode) {
                                1 -> hang
                                2 -> 0
                                else -> null
                            },
                            gap = if (gapOn) gap else null,
                            speaker = when (spk) {
                                1 -> true
                                2 -> false
                                else -> null
                            },
                            mute = when (mut) {
                                1 -> true
                                2 -> false
                                else -> null
                            },
                        )
                        val empty = ov.calls == null && ov.hangUp == null && ov.gap == null &&
                            ov.speaker == null && ov.mute == null
                        onSave(if (empty) null else ov)
                    }
                }
            }
        }
    }
}

// ───────────────────────── Dialogs ─────────────────────────
@Composable
internal fun SaveListDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f),
        ) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("SAVE LIST")
                Caption("Saves these numbers with their names, custom settings and run settings.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("List name") },
                    singleLine = true,
                    colors = fieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("CANCEL", modifier = Modifier.weight(1f).height(48.dp), onClick = onDismiss)
                    NeonButton("SAVE", enabled = name.isNotBlank(), modifier = Modifier.weight(1f)) {
                        onSave(name.trim())
                    }
                }
            }
        }
    }
}

@Composable
internal fun ListsDialog(
    lists: List<String>,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 520.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("SAVED LISTS")
                if (lists.isEmpty()) {
                    Text(
                        "Nothing saved yet. Use SAVE LIST on the DIAL tab.",
                        fontFamily = Mono, fontSize = 12.sp, color = Dim,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Caption("Tap a list to load it. Loading replaces the current queue.")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(lists) { name ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(shape)
                                    .border(1.dp, Hair, shape)
                                    .pressable { onLoad(name) }
                                    .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).pressable { onDelete(name) },
                                    contentAlignment = Alignment.Center,
                                ) { Glyph(Icons.Filled.Close, Dim, 18.dp) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GhostButton("CLOSE", modifier = Modifier.fillMaxWidth().height(48.dp), onClick = onDismiss)
            }
        }
    }
}

@Composable
internal fun ScheduleDialog(onSchedule: (Long) -> Unit, onDismiss: () -> Unit) {
    val nowCal = remember { Calendar.getInstance() }
    var hour by remember { mutableStateOf((nowCal.get(Calendar.HOUR_OF_DAY) + 1) % 24) }
    var minute by remember { mutableStateOf(0) }

    fun target(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 600.dp),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("SCHEDULE START")
                FieldLabel("START IN")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("5 MIN" to 5, "15 MIN" to 15, "30 MIN" to 30, "1 HOUR" to 60).forEach { (label, mins) ->
                        SegChip(label, false, true, Modifier.weight(1f)) {
                            onSchedule(System.currentTimeMillis() + mins * 60_000L)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                FieldLabel("OR AT A TIME")
                StepperRow("Hour", hour, 0..23, true, { "%02d".format(it) }) { hour = it }
                StepperRow("Minute", minute, 0..59, true, { "%02d".format(it) }) { minute = it }
                val at = target()
                Caption(
                    "Starts " + SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(at)) +
                        ". The phone needs to be on; the app does the rest.",
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("CANCEL", modifier = Modifier.weight(1f).height(48.dp), onClick = onDismiss)
                    NeonButton("SCHEDULE", modifier = Modifier.weight(1f)) { onSchedule(at) }
                }
            }
        }
    }
}

@Composable
internal fun ImportDialog(raw: String, onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val nums = remember(raw) { extractNumbers(raw) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DialogBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 560.dp),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("IMPORT NUMBERS")
                if (nums.isEmpty()) {
                    Text("No phone numbers were found in that.", fontSize = 14.sp, color = Dim)
                    Spacer(Modifier.height(14.dp))
                    GhostButton("CLOSE", modifier = Modifier.fillMaxWidth().height(48.dp), onClick = onDismiss)
                } else {
                    Text(
                        "Found ${nums.size} number" + (if (nums.size > 1) "s" else ""),
                        fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Fg,
                    )
                    Spacer(Modifier.height(8.dp))
                    nums.take(8).forEach { Text(it, fontFamily = Mono, fontSize = 14.sp, color = Fg) }
                    if (nums.size > 8) Caption("+ ${nums.size - 8} more")
                    Caption("Invalid numbers are skipped, and so are duplicates unless you allowed them.")
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("CANCEL", modifier = Modifier.weight(1f).height(48.dp), onClick = onDismiss)
                        NeonButton("ADD ${nums.size}", modifier = Modifier.weight(1f)) { onAdd(nums) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(Icons.Filled.Lock, Cyan, 44.dp)
        Spacer(Modifier.height(16.dp))
        Text("LOCKED", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 6.sp, color = Fg)
        Spacer(Modifier.height(6.dp))
        Text(
            "Use your phone's screen lock to continue.",
            fontSize = 13.sp, color = Dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        NeonButton("UNLOCK", onClick = onUnlock)
    }
}
