package com.squeve.redail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.squeve.redail.engine.listSims
import com.squeve.redail.model.*
import com.squeve.redail.service.RedialService
import com.squeve.redail.update.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

private val Bg = Color(0xFF303030)
private val CardBg = Color(0xFF424242)
private val Brand = Color(0xFF7E57C2)
private val Danger = Color(0xFFD32F2F)
private val Line = Color(0xFF616161)

private val Scheme = darkColorScheme(
    primary = Color(0xFFB39DDB),
    onPrimary = Color(0xFF1E1233),
    background = Bg,
    onBackground = Color.White,
    surface = CardBg,
    onSurface = Color.White,
    surfaceVariant = CardBg,
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = Color(0xFF9E9E9E),
)

private val REQUIRED = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ANSWER_PHONE_CALLS,
    Manifest.permission.READ_CALL_LOG,
)

private fun allGranted(ctx: Context) = REQUIRED.all {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

private fun permsToRequest(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) REQUIRED + Manifest.permission.POST_NOTIFICATIONS else REQUIRED

private fun addNumbers(target: MutableList<String>, raw: String) {
    raw.split(Regex("[,;\\n]+")).forEach { part ->
        val cleaned = part.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleaned.any { it.isDigit() }) target.add(cleaned)
    }
}

/** Bumped on every onResume so permission-dependent UI refreshes after returning from system settings. */
private object ResumeSignal {
    val tick = MutableStateFlow(0)
}

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        ResumeSignal.tick.value = ResumeSignal.tick.value + 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                Surface(Modifier.fillMaxSize(), color = Bg) { RedialScreen() }
            }
        }
    }
}

@Composable
private fun RedialScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("redail", Context.MODE_PRIVATE) }

    val numbers = remember {
        mutableStateListOf<String>().apply {
            prefs.getString("numbers", "")?.split("\n")?.filter { it.isNotBlank() }?.let { addAll(it) }
        }
    }
    var input by remember { mutableStateOf("") }
    var calls by remember { mutableStateOf(prefs.getInt("calls", 5)) }
    var gapSec by remember { mutableStateOf(prefs.getInt("gap", 5)) }
    var limitOn by remember { mutableStateOf(prefs.getBoolean("limitOn", true)) }
    var durSec by remember { mutableStateOf(prefs.getInt("dur", 30)) }
    var skipAnswered by remember { mutableStateOf(prefs.getBoolean("skip", false)) }
    var speakerOn by remember { mutableStateOf(prefs.getBoolean("speaker", false)) }
    var muteOn by remember { mutableStateOf(prefs.getBoolean("mute", false)) }
    var simIndex by remember { mutableStateOf(prefs.getInt("sim", 0)) }
    var permTick by remember { mutableStateOf(0) }
    val resumeTick by ResumeSignal.tick.collectAsState()

    val status by RedialService.status.collectAsState()
    val paused by RedialService.paused.collectAsState()
    val running = status !is EngineState.Idle && status !is EngineState.Finished
    val finished = status is EngineState.Finished
    val progress = status.progress()
    val sims = remember(permTick, resumeTick) { listSims(ctx) }
    val granted = remember(permTick, resumeTick) { allGranted(ctx) }
    val overlayOk = remember(permTick, resumeTick) { Settings.canDrawOverlays(ctx) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (uri != null) {
            runCatching {
                ctx.contentResolver.query(
                    uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) addNumbers(numbers, c.getString(0).orEmpty())
                }
            }
        }
    }

    fun send(action: String) {
        ctx.startService(Intent(ctx, RedialService::class.java).setAction(action))
    }

    fun startRun() {
        if (!allGranted(ctx)) {
            permLauncher.launch(permsToRequest())
            return
        }
        if (numbers.isEmpty()) {
            Toast.makeText(ctx, "Add at least one number", Toast.LENGTH_SHORT).show()
            return
        }
        // One-time prompt for the floating progress controls.
        if (!Settings.canDrawOverlays(ctx) && !prefs.getBoolean("overlayAsked", false)) {
            prefs.edit().putBoolean("overlayAsked", true).apply()
            Toast.makeText(ctx, "Allow \"Display over other apps\", then press START again", Toast.LENGTH_LONG).show()
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
            )
            return
        }
        prefs.edit()
            .putString("numbers", numbers.joinToString("\n"))
            .putInt("calls", calls).putInt("gap", gapSec).putInt("dur", durSec)
            .putBoolean("limitOn", limitOn).putBoolean("skip", skipAnswered)
            .putBoolean("speaker", speakerOn).putBoolean("mute", muteOn).putInt("sim", simIndex)
            .apply()
        RedialService.pendingQueue = numbers.map {
            RedialJob(
                number = it,
                attempts = calls,
                gapMs = gapSec * 1000L,
                hangUpAfterMs = if (limitOn) durSec * 1000L else null,
                stopOnConnected = skipAnswered,
                speaker = speakerOn,
                muteMic = muteOn,
            )
        }
        RedialService.pendingSim = if (sims.size > 1) sims.getOrNull(simIndex)?.handle else null
        ctx.startForegroundService(Intent(ctx, RedialService::class.java).setAction(RedialService.ACTION_START))
    }

    val scope = rememberCoroutineScope()
    var updateMsg by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }

    fun getUpdate() {
        if (updating) return
        if (!Updater.canInstall(ctx)) {
            updateMsg = "Allow installs from Squeve Redail, come back, then tap again."
            Updater.openInstallSettings(ctx)
            return
        }
        updating = true
        updateMsg = "Checking for updates…"
        scope.launch {
            try {
                val rel = Updater.latest()
                val have = Updater.installedBuild(ctx)
                when {
                    rel == null -> updateMsg = "No release found yet."
                    rel.build <= have -> updateMsg = "You're on the latest version (build $have)."
                    else -> {
                        updateMsg = "Downloading build ${rel.build}… 0%"
                        val apk = Updater.download(ctx, rel.apkUrl) { pct ->
                            updateMsg = "Downloading build ${rel.build}… $pct%"
                        }
                        updateMsg = "Opening installer…"
                        Updater.install(ctx, apk)
                    }
                }
            } catch (e: Exception) {
                updateMsg = "Update failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                updating = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Brand).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("Squeve Redail", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }

        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            if (status !is EngineState.Idle) StatusCard(status, paused)

            // ---------- Numbers + start ----------
            Section {
                if (sims.size > 1) {
                    Text("SIM", fontSize = 14.sp, color = Color(0xFFD0D0D0))
                    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sims.forEachIndexed { i, s ->
                            if (i == simIndex) {
                                Button(onClick = { simIndex = i }, enabled = !running) { Text(s.label) }
                            } else {
                                OutlinedButton(onClick = { simIndex = i }, enabled = !running) { Text(s.label) }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Number(s), comma separated") },
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        enabled = !running,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            contactPicker.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                            )
                        },
                        enabled = !running,
                    ) { Text("Contacts") }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { addNumbers(numbers, input); input = "" },
                    enabled = !running && input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add to queue") }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Queue (${numbers.size})", fontSize = 18.sp, modifier = Modifier.weight(1f))
                    if (!running && numbers.isNotEmpty()) {
                        TextButton(onClick = { numbers.clear() }) { Text("Clear") }
                    }
                }
                if (numbers.isEmpty()) {
                    Text("No numbers yet.", fontSize = 14.sp, color = Color(0xFFB0B0B0))
                }
                numbers.forEachIndexed { i, n ->
                    val cur = progress?.jobIndex
                    val tag = when {
                        finished -> "✓"
                        cur == null -> "${i + 1}."
                        i < cur -> "✓"
                        i == cur -> "●"
                        else -> "${i + 1}."
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tag, fontSize = 16.sp, modifier = Modifier.width(30.dp))
                        Text(n, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (running && progress != null && i == progress.jobIndex) {
                            Text("${progress.attempt}/${progress.total}", fontSize = 15.sp)
                        } else if (!running) {
                            TextButton(onClick = { numbers.removeAt(i) }) { Text("✕") }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (!granted) {
                    OutlinedButton(
                        onClick = { permLauncher.launch(permsToRequest()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Grant permissions") }
                    Spacer(Modifier.height(8.dp))
                }
                if (!running) {
                    Button(
                        onClick = { startRun() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
                    ) { Text("START", fontSize = 18.sp) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { send(if (paused) RedialService.ACTION_RESUME else RedialService.ACTION_PAUSE) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text(if (paused) "RESUME" else "PAUSE", fontSize = 16.sp) }
                        Button(
                            onClick = { send(RedialService.ACTION_STOP) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
                        ) { Text("STOP", fontSize = 16.sp) }
                    }
                }
            }

            // ---------- Settings ----------
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Limit call duration", fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Switch(checked = limitOn, onCheckedChange = { limitOn = it }, enabled = !running)
                }
                if (limitOn) {
                    StepperRow(
                        title = "Hang up after",
                        valueText = "${durSec / 60} min ${durSec % 60} sec",
                        value = durSec, range = 5..600, enabled = !running,
                    ) { durSec = it }
                }
                Divider()
                StepperRow(
                    title = "Calls per number",
                    valueText = "$calls",
                    value = calls, range = 1..500, enabled = !running,
                ) { calls = it }
                Divider()
                StepperRow(
                    title = "Interval between calls",
                    valueText = "$gapSec sec",
                    value = gapSec, range = 1..300, enabled = !running,
                ) { gapSec = it }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Start call on speaker", fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Switch(checked = speakerOn, onCheckedChange = { speakerOn = it }, enabled = !running)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mute microphone", fontSize = 18.sp)
                        Text(
                            "Best effort: some phones ignore mute on normal calls.",
                            fontSize = 12.sp, color = Color(0xFFB0B0B0),
                        )
                    }
                    Switch(checked = muteOn, onCheckedChange = { muteOn = it }, enabled = !running)
                }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Skip to next number if answered", fontSize = 18.sp)
                        Text(
                            "Off by default: some phones log ring time as call time, so this can skip too early.",
                            fontSize = 12.sp, color = Color(0xFFB0B0B0),
                        )
                    }
                    Switch(checked = skipAnswered, onCheckedChange = { skipAnswered = it }, enabled = !running)
                }
                Divider()
                if (!overlayOk) {
                    TextButton(onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                            )
                        }
                    }) { Text("Enable floating controls (display over other apps)") }
                }
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                        )
                    }
                }) { Text("Allow background running (battery)") }
                Divider()
                Spacer(Modifier.height(8.dp))
                Text("App updates", fontSize = 18.sp)
                Text(
                    "Installed: version ${Updater.installedName(ctx)} (build ${Updater.installedBuild(ctx)})",
                    fontSize = 13.sp, color = Color(0xFFB0B0B0),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { getUpdate() },
                    enabled = !updating && !running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (updating) "Please wait…" else "Get latest version") }
                if (running) {
                    Text("Stop redialing before updating.", fontSize = 12.sp, color = Color(0xFFB0B0B0))
                }
                if (updateMsg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(updateMsg, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = CardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StepperRow(
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = enabled) {
                Text("−", fontSize = 24.sp)
            }
            Text(valueText, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 80.dp))
            TextButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = enabled) {
                Text("+", fontSize = 24.sp)
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun StatusCard(status: EngineState, paused: Boolean) {
    val now by produceState(System.currentTimeMillis(), status) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }

    fun line(p: Progress) = "Number ${p.jobIndex + 1} of ${p.jobCount} · ${p.number}\nCall ${p.attempt} of ${p.total}"

    val (title, detail) = when (val s = status) {
        is EngineState.Dialing -> "Dialing…" to line(s.p)
        is EngineState.InCall -> "Calling…" to line(s.p)
        is EngineState.Cooldown -> {
            val left = ((s.untilMs - now) / 1000 + 1).coerceAtLeast(0)
            "Next call in ${left}s" to line(s.next)
        }
        is EngineState.Paused -> "Paused" to (s.last.progress()?.let { line(it) } ?: "")
        is EngineState.Finished -> "Done" to "${s.results.size} calls placed"
        is EngineState.Idle -> "" to ""
    }
    val shownTitle =
        if (paused && status !is EngineState.Paused && status !is EngineState.Finished) "$title · pausing after this call"
        else title

    Surface(color = Brand, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(shownTitle, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(detail, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}
