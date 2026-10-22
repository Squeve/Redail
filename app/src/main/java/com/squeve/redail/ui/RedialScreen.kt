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

// ───────────────────────── Screen host ─────────────────────────
internal fun readUri(ctx: Context, uri: Uri): String? =
    runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()

/** Everything the DIAL tab can ask the host to do. */
internal class DialActions(
    val add: (String) -> Unit,
    val contacts: () -> Unit,
    val callLog: () -> Unit,
    val paste: () -> Unit,
    val importFile: () -> Unit,
    val edit: (Int) -> Unit,
    val saveList: () -> Unit,
    val lists: () -> Unit,
    val schedule: () -> Unit,
    val cancelSchedule: () -> Unit,
    val resume: () -> Unit,
    val discardResume: () -> Unit,
    val retry: (List<String>) -> Unit,
    val export: () -> Unit,
)

@Composable
internal fun RedialScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("redail", Context.MODE_PRIVATE) }
    val cfg = remember { Config(prefs) }
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(0) }
    var permTick by remember { mutableStateOf(0) }
    var uiTick by remember { mutableStateOf(0) }
    val resumeTick by ResumeSignal.tick.collectAsState()
    val imeOpen by ImeSignal.open.collectAsState()
    val locked by LockState.locked.collectAsState()
    val inbox by ShareInbox.pending.collectAsState()

    // Only recompose the host when "running" flips, not on every engine state change.
    val runningFlow = remember { RedialService.status.map { isRunning(it) }.distinctUntilChanged() }
    val running by runningFlow.collectAsState(initial = isRunning(RedialService.status.value))

    val refresh = permTick + resumeTick + uiTick
    val sims by produceState(emptyList<SimOption>(), refresh) {
        value = withContext(Dispatchers.IO) { runCatching { listSims(ctx) }.getOrDefault(emptyList()) }
    }
    val granted = remember(refresh) { allGranted(ctx) }
    val notReady = remember(refresh) {
        !granted || !Settings.canDrawOverlays(ctx) || !Scheduler.canBeExact(ctx) ||
            !(ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val checkpoint = remember(refresh, running) { if (running) null else RunStore.loadCheckpoint(ctx) }
    val scheduled = remember(refresh, running) { RunStore.loadSchedule(ctx) }

    // Settings are remembered between runs; theme changes are applied as they happen.
    LaunchedEffect(Unit) { snapshotFlow { cfg.fingerprint() }.collect { cfg.save() } }
    LaunchedEffect(cfg.accentIdx, cfg.blackOn, cfg.uiSize, cfg.hapticsOn, cfg.themeMode) {
        Theming.mode.value = cfg.themeMode
        Theming.setAccent(cfg.accentIdx)
        Theming.black.value = cfg.blackOn
        Theming.size.value = cfg.uiSize
        Theming.haptics.value = cfg.hapticsOn
    }

    var showLog by remember { mutableStateOf(false) }
    var wantLog by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var showLists by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        // The call log button asked for access: open the picker as soon as it is granted.
        if (wantLog && ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showLog = true
        }
        wantLog = false
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (uri != null) {
            runCatching {
                ctx.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val r = addChecked(cfg, c.getString(0).orEmpty())
                        val name = c.getString(1).orEmpty()
                        if (name.isNotBlank()) r.added.forEach { cfg.names[it] = name }
                        reportAdd(ctx, r)
                        cfg.save()
                        clearFinished()
                    }
                }
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = readUri(ctx, uri)
            if (text.isNullOrBlank()) {
                Toast.makeText(ctx, "Could not read that file", Toast.LENGTH_SHORT).show()
            } else {
                ShareInbox.pending.value = text
            }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val o = readUri(ctx, uri)?.let { t -> runCatching { JSONObject(t) }.getOrNull() }
            if (o == null || !o.has("config")) {
                Toast.makeText(ctx, "That file is not a Squeve Redail backup", Toast.LENGTH_LONG).show()
            } else {
                cfg.applyJson(o.getJSONObject("config"))
                o.optJSONArray("lists")?.let { ListStore.replaceAll(prefs, it) }
                cfg.save()
                Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val unlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            LockState.lastUnlock = System.currentTimeMillis()
            LockState.locked.value = false
        }
    }

    fun requestUnlock() {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val i = km.createConfirmDeviceCredentialIntent("Squeve Redail", "Unlock to continue")
        if (i == null) {
            LockState.locked.value = false
        } else {
            unlockLauncher.launch(i)
        }
    }
    LaunchedEffect(locked) { if (locked) requestUnlock() }

    fun openCallLog() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showLog = true
        } else {
            wantLog = true
            permLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
        }
    }

    fun startRun() {
        if (!allGranted(ctx)) {
            permLauncher.launch(permsToRequest())
            return
        }
        if (cfg.numbers.isEmpty()) {
            Toast.makeText(ctx, "Add at least one number", Toast.LENGTH_SHORT).show()
            tab = 0
            return
        }
        // One-time prompt for the floating controls.
        if (!cfg.dryRun && !Settings.canDrawOverlays(ctx) && !prefs.getBoolean("overlayAsked", false)) {
            prefs.edit().putBoolean("overlayAsked", true).apply()
            Toast.makeText(ctx, "Allow 'Display over other apps', then press START again", Toast.LENGTH_LONG).show()
            openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            return
        }
        cfg.save()
        clearFinished()
        RunLauncher.start(ctx, cfg.buildPlan())
    }

    val actions = DialActions(
        add = { raw ->
            val r = addChecked(cfg, raw)
            reportAdd(ctx, r)
            cfg.save()
            clearFinished()
        },
        contacts = {
            contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        },
        callLog = { openCallLog() },
        paste = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx)?.toString() else null
            if (text.isNullOrBlank()) {
                Toast.makeText(ctx, "The clipboard is empty", Toast.LENGTH_SHORT).show()
            } else {
                ShareInbox.pending.value = text
            }
        },
        importFile = { filePicker.launch("*/*") },
        edit = { editIndex = it },
        saveList = { showSave = true },
        lists = { showLists = true },
        schedule = { showSchedule = true },
        cancelSchedule = {
            Scheduler.cancel(ctx)
            uiTick++
        },
        resume = {
            val plan = checkpoint?.let { RunStore.remainingPlan(it) }
            if (plan != null) RunLauncher.start(ctx, plan)
        },
        discardResume = {
            RunStore.clearCheckpoint(ctx)
            uiTick++
        },
        retry = { nums -> RunLauncher.start(ctx, RunPlan(cfg.buildJobs(nums), cfg.order, cfg.simSlot, false)) },
        export = {
            scope.launch {
                val f = withContext(Dispatchers.IO) { HistoryStore.exportCsv(ctx) }
                shareFile(ctx, f, "text/csv")
            }
        },
    )

    if (locked) {
        LockScreen(onUnlock = { requestUnlock() })
    } else {
        // ───── dialogs ─────
        editIndex?.let { i ->
            val n = cfg.numbers.getOrNull(i)
            if (n != null) {
                TargetDialog(
                    number = n, name = cfg.names[n], current = cfg.overrides[n],
                    defCalls = cfg.calls, defHangUp = if (cfg.limitOn) cfg.durSec else null, defGap = cfg.gapSec,
                    onSave = { ov ->
                        if (ov == null) cfg.overrides.remove(n) else cfg.overrides[n] = ov
                        cfg.save()
                        editIndex = null
                    },
                    onDismiss = { editIndex = null },
                )
            }
        }
        if (showLog) {
            CallLogDialog(
                queued = cfg.numbers,
                onDismiss = { showLog = false },
                onAdd = { picks ->
                    picks.forEach { e ->
                        cfg.numbers.add(e.number)
                        if (e.name.isNotBlank()) cfg.names[e.number] = e.name
                    }
                    cfg.save()
                    clearFinished()
                    showLog = false
                },
            )
        }
        if (showSave) {
            SaveListDialog(
                onSave = { name ->
                    ListStore.put(prefs, name, cfg.toJson(false))
                    Toast.makeText(ctx, "Saved \"$name\"", Toast.LENGTH_SHORT).show()
                    showSave = false
                },
                onDismiss = { showSave = false },
            )
        }
        if (showLists) {
            var listsTick by remember { mutableStateOf(0) }
            val saved = remember(listsTick) { ListStore.all(prefs).map { it.first } }
            ListsDialog(
                lists = saved,
                onLoad = { name ->
                    ListStore.all(prefs).firstOrNull { it.first == name }?.let { cfg.applyJson(it.second) }
                    cfg.save()
                    clearFinished()
                    showLists = false
                },
                onDelete = { name ->
                    ListStore.remove(prefs, name)
                    listsTick++
                },
                onDismiss = { showLists = false },
            )
        }
        if (showSchedule) {
            ScheduleDialog(
                onSchedule = { atMs ->
                    Scheduler.schedule(ctx, atMs, cfg.buildPlan().copy(dry = false))
                    uiTick++
                    showSchedule = false
                    Toast.makeText(ctx, "Scheduled", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showSchedule = false },
            )
        }
        inbox?.let { raw ->
            ImportDialog(
                raw = raw,
                onAdd = { nums ->
                    val r = addChecked(cfg, nums.joinToString(","))
                    reportAdd(ctx, r)
                    cfg.save()
                    clearFinished()
                    ShareInbox.pending.value = null
                },
                onDismiss = { ShareInbox.pending.value = null },
            )
        }

        Column(Modifier.fillMaxSize()) {
            Header(running)
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(170)) + slideInHorizontally(tween(210)) { dir * it / 10 }) togetherWith
                        (fadeOut(tween(110)) + slideOutHorizontally(tween(210)) { -dir * it / 10 })
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> DialTab(cfg, sims, running, checkpoint, scheduled, actions)
                    1 -> LogTab(cfg)
                    2 -> ConfigTab(cfg, running, sims.size)
                    else -> SystemTab(
                        granted = granted, refresh = refresh, running = running,
                        onGrant = { permLauncher.launch(permsToRequest()) },
                        onExportBackup = {
                            scope.launch {
                                val f = withContext(Dispatchers.IO) {
                                    val o = JSONObject()
                                        .put("app", "squeve-redail")
                                        .put("config", cfg.toJson(true))
                                        .put("lists", ListStore.toJson(prefs))
                                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                                    File(dir, "squeve-redail-backup.json").also { it.writeText(o.toString(2)) }
                                }
                                shareFile(ctx, f, "application/json")
                            }
                        },
                        onImportBackup = { backupPicker.launch("*/*") },
                    )
                }
            }
            if (!imeOpen) {
                UndoBar(cfg)
                ControlDock(
                    running = running,
                    startLabel = if (cfg.dryRun) "START DRY RUN" else "START REDIAL",
                    onStart = { startRun() },
                    onPauseResume = { wasPaused ->
                        sendAction(ctx, if (wasPaused) RedialService.ACTION_RESUME else RedialService.ACTION_PAUSE)
                    },
                    onStop = { sendAction(ctx, RedialService.ACTION_STOP) },
                )
                TabBar(tab, attention = notReady) { tab = it }
            }
        }
    }
}
