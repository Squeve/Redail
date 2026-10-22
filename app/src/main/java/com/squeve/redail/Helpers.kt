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

// ───────────────────────── Helpers ─────────────────────────
internal val REQUIRED = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ANSWER_PHONE_CALLS,
    Manifest.permission.READ_CALL_LOG,
)

internal fun allGranted(ctx: Context) = REQUIRED.all {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

internal fun permsToRequest(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) REQUIRED + Manifest.permission.POST_NOTIFICATIONS else REQUIRED

internal class AddResult(val added: List<String>, val invalid: Int, val duplicates: Int)

/** Adds numbers to the queue, skipping invalid ones and (unless allowed) duplicates. */
internal fun addChecked(cfg: Config, raw: String): AddResult {
    val added = mutableListOf<String>()
    var invalid = 0
    var duplicates = 0
    raw.split(Regex("[,;\\n]+")).forEach { part ->
        var n = part.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (n.none { it.isDigit() }) return@forEach
        n = normalizeNumber(n, cfg.countryCode)
        if (!isValidNumber(n)) {
            invalid++
            return@forEach
        }
        val key = digitsKey(n)
        if (cfg.numbers.any { digitsKey(it) == key } && !cfg.allowDup) {
            duplicates++
            return@forEach
        }
        cfg.numbers.add(n)
        added.add(n)
    }
    return AddResult(added, invalid, duplicates)
}

internal fun reportAdd(ctx: Context, r: AddResult) {
    val parts = mutableListOf("Added ${r.added.size}")
    if (r.invalid > 0) parts += "${r.invalid} invalid skipped"
    if (r.duplicates > 0) parts += "${r.duplicates} duplicate" + (if (r.duplicates > 1) "s" else "") + " skipped"
    Toast.makeText(ctx, parts.joinToString(" · "), Toast.LENGTH_SHORT).show()
}

internal fun shareFile(ctx: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

internal fun Boolean?.flag(): String = when (this) {
    null -> ""
    true -> "1"
    false -> "0"
}

internal fun typeLabel(t: Int) = when (t) {
    CallLog.Calls.INCOMING_TYPE -> "IN"
    CallLog.Calls.OUTGOING_TYPE -> "OUT"
    CallLog.Calls.MISSED_TYPE -> "MISSED"
    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
    CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
    else -> "CALL"
}

internal fun typeColor(t: Int) = when (t) {
    CallLog.Calls.OUTGOING_TYPE -> Cyan
    CallLog.Calls.INCOMING_TYPE -> Mint
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> Magenta
    else -> Dim
}

internal fun ago(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        s < 172800 -> "yesterday"
        else -> "${s / 86400}d ago"
    }
}

internal fun isRunning(s: EngineState) = s !is EngineState.Idle && s !is EngineState.Finished

internal fun sendAction(ctx: Context, action: String) {
    ctx.startService(Intent(ctx, RedialService::class.java).setAction(action))
}

/** Editing the queue after a run clears the old "complete" result so new rows don't show as done. */
internal fun clearFinished() {
    if (RedialService.status.value is EngineState.Finished) RedialService.status.value = EngineState.Idle
}

internal fun openSettings(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action, Uri.parse("package:${ctx.packageName}"))) }
}

internal fun clock(totalSec: Long) = "${totalSec / 60}:${"%02d".format(totalSec % 60)}"

internal fun spanText(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${maxOf(m, 1)}m"
}

internal fun CallOrder.title() = when (this) {
    CallOrder.SEQUENTIAL -> "ONE BY ONE"
    CallOrder.ROUND_ROBIN -> "ROUND ROBIN"
    CallOrder.RANDOM -> "RANDOM"
}

internal fun CallOrder.hint() = when (this) {
    CallOrder.SEQUENTIAL -> "Number 1 gets all its calls, then number 2, and so on."
    CallOrder.ROUND_ROBIN -> "One call each in turn: 1, 2, 3, then 1, 2, 3 again."
    CallOrder.RANDOM -> "All calls shuffled. The same number is avoided twice in a row where possible."
}

/** True while the on-screen keyboard is up. */
internal object ImeSignal {
    val open = MutableStateFlow(false)
}

/** Text shared into the app (share sheet), waiting for the user to confirm the import. */
internal object ShareInbox {
    val pending = MutableStateFlow<String?>(null)
}

/** App lock state. */
internal object LockState {
    val locked = MutableStateFlow(false)
    @Volatile var lastUnlock = 0L
}

/** Bumped on every onResume so permission-dependent UI refreshes after returning from system settings. */
internal object ResumeSignal {
    val tick = MutableStateFlow(0)
}
