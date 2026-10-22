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

// ───────────────────────── SYSTEM tab ─────────────────────────
@Composable
internal fun SystemTab(
    granted: Boolean,
    refresh: Int,
    running: Boolean,
    onGrant: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    val ctx = LocalContext.current
    val overlayOk = remember(refresh) { Settings.canDrawOverlays(ctx) }
    val batteryOk = remember(refresh) {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val installOk = remember(refresh) { Updater.canInstall(ctx) }
    val exactOk = remember(refresh) { Scheduler.canBeExact(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GlassCard {
            SectionTitle("READINESS", bottom = 4.dp)
            Text("Everything the app needs to dial reliably.", fontSize = 12.sp, color = Dim)
            Spacer(Modifier.height(6.dp))
            StatusRow("Phone & call log", "Place calls, read call state", granted, "GRANT", onGrant)
            Hairline()
            StatusRow(
                "Floating control", "Progress card and bubble over other apps", overlayOk, "ENABLE",
            ) { openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
            Hairline()
            StatusRow(
                "Background running", "Keeps redialing with the screen off", batteryOk, "ALLOW",
            ) { openSettings(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
            Hairline()
            StatusRow(
                "Exact alarms", "Scheduled runs start on the minute", exactOk, "ALLOW",
            ) { openSettings(ctx, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
            Hairline()
            StatusRow(
                "Install updates", "Lets the app update itself", installOk, "ALLOW",
            ) { Updater.openInstallSettings(ctx) }
        }

        GlassCard {
            SectionTitle("DATA")
            Text(
                "Save your settings, queue and saved lists to a file, or restore them on this or another phone.",
                fontSize = 12.sp, color = Dim,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("EXPORT", modifier = Modifier.weight(1f).height(48.dp), onClick = onExportBackup)
                GhostButton("RESTORE", modifier = Modifier.weight(1f).height(48.dp), onClick = onImportBackup)
            }
        }

        UpdateCard(running)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun StatusRow(title: String, subtitle: String, ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, if (ok) Mint else Magenta, 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 12.sp, color = Dim)
        }
        if (ok) {
            Text("READY", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp, color = Mint)
        } else {
            GhostButton(actionLabel, modifier = Modifier.width(104.dp).height(38.dp), onClick = onAction)
        }
    }
}

@Composable
internal fun UpdateCard(running: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pct by remember { mutableStateOf(-1) }
    var pending by remember { mutableStateOf<Updater.Release?>(null) }
    var notes by remember { mutableStateOf<Updater.Release?>(null) }
    // Looked up once; PackageManager calls are not free inside composition.
    val versionLine = remember { "v${Updater.installedName(ctx)} · build ${Updater.installedBuild(ctx)}" }

    // First tap checks and shows what is new; the second tap downloads and installs.
    fun getUpdate() {
        if (busy) return
        val target = pending
        if (target != null && !Updater.canInstall(ctx)) {
            msg = "Allow installs from Squeve Redail, come back, then tap again."
            Updater.openInstallSettings(ctx)
            return
        }
        busy = true
        pct = -1
        msg = if (target != null) "Downloading build ${target.build}…" else "Checking for updates…"
        scope.launch {
            try {
                if (target != null) {
                    pct = 0
                    val apk = Updater.download(ctx, target.apkUrl) { p -> pct = p }
                    msg = "Opening installer…"
                    Updater.install(ctx, apk)
                } else {
                    val rel = Updater.latest()
                    val have = Updater.installedBuild(ctx)
                    when {
                        rel == null -> msg = "No release found yet."
                        rel.build <= have -> {
                            msg = "You're on the latest version (build $have)."
                            notes = rel
                        }
                        else -> {
                            pending = rel
                            notes = rel
                            msg = "Build ${rel.build} is ready to install."
                        }
                    }
                }
            } catch (e: Exception) {
                msg = "Update failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
                pct = -1
            }
        }
    }

    val pend = pending
    GlassCard {
        SectionTitle("UPDATES")
        Text(versionLine, fontFamily = Mono, fontSize = 13.sp, color = Dim)
        Spacer(Modifier.height(12.dp))
        NeonButton(
            if (busy) "PLEASE WAIT…" else if (pend != null) "INSTALL BUILD ${pend.build}" else "CHECK FOR UPDATES",
            enabled = !busy && !running,
            icon = { Glyph(Icons.Filled.Refresh, it, 20.dp) },
        ) { getUpdate() }
        if (running) {
            Spacer(Modifier.height(6.dp))
            Text("Stop redialing before updating.", fontSize = 12.sp, color = Dim)
        }
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, fontFamily = Mono, fontSize = 12.sp, color = Cyan)
        }
        if (pct in 0..100) {
            Spacer(Modifier.height(8.dp))
            MiniBar(pct / 100f)
            Spacer(Modifier.height(4.dp))
            Text("$pct%", fontFamily = Mono, fontSize = 11.sp, color = Dim)
        }
        val n = notes
        if (n != null && n.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(10.dp))
            FieldLabel(if (pend != null) "WHAT IS NEW IN BUILD ${n.build}" else "IN THIS VERSION")
            Text(n.notes, fontSize = 12.sp, color = Dim)
        }
    }
}
