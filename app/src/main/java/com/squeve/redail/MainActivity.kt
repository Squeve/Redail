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

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_START_LAST = "com.squeve.redail.START_LAST"
        const val ACTION_STOP_RUN = "com.squeve.redail.STOP_RUN"
    }

    private var leftAt = 0L

    override fun onResume() {
        super.onResume()
        ResumeSignal.tick.value = ResumeSignal.tick.value + 1
        val lockOn = getSharedPreferences("redail", Context.MODE_PRIVATE).getBoolean("lockOn", false)
        val now = System.currentTimeMillis()
        if (lockOn && leftAt > 0 && now - leftAt > 20_000 && now - LockState.lastUnlock > 5_000) {
            LockState.locked.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Shortcuts, the tile and the widget start / stop runs here; the share sheet hands over numbers. */
    private fun handleIntent(intent: Intent?) {
        val i = intent ?: return
        when (i.action) {
            ACTION_START_LAST -> {
                i.action = null
                val plan = RunStore.loadLast(this)
                when {
                    plan == null -> Toast.makeText(this, "No previous run yet", Toast.LENGTH_SHORT).show()
                    !allGranted(this) ->
                        Toast.makeText(this, "Open the app and grant permissions first", Toast.LENGTH_LONG).show()
                    isRunning(RedialService.status.value) ->
                        Toast.makeText(this, "Already running", Toast.LENGTH_SHORT).show()
                    else -> RunLauncher.start(this, plan)
                }
            }
            ACTION_STOP_RUN -> {
                i.action = null
                RunLauncher.stop(this)
                finish()
            }
            Intent.ACTION_SEND -> {
                i.action = null
                sharedText(i)?.let { ShareInbox.pending.value = it }
            }
        }
    }

    private fun sharedText(i: Intent): String? {
        val direct = i.getStringExtra(Intent.EXTRA_TEXT)
        if (!direct.isNullOrBlank()) return direct
        @Suppress("DEPRECATION")
        val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        return readUri(this, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("redail", Context.MODE_PRIVATE)
        Fonts.init(this)
        Theming.load(prefs, this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Ink.toArgb()))
        if (prefs.getBoolean("lockOn", false) && savedInstanceState == null) LockState.locked.value = true
        window.statusBarColor = Ink.toArgb()
        window.navigationBarColor = Ink.toArgb()
        val decor = window.decorView
        decor.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            decor.getWindowVisibleDisplayFrame(r)
            val full = decor.rootView.height
            ImeSignal.open.value = full > 0 && (full - r.bottom) > full * 0.15f
        }
        setContent {
            // UI size is a setting; system font scaling beyond 1x is ignored so layouts stay tidy.
            val base = LocalDensity.current
            val factor = when (Theming.size.value) {
                0 -> 0.80f
                2 -> 1.0f
                else -> UI_SCALE
            }
            val scaled = remember(base, factor) { Density(base.density * factor, base.fontScale.coerceAtMost(1f)) }
            val type = remember { typographyWith(Fonts.body) }
            val isLight = Theming.light
            val scheme = remember(Theming.accent.value, Theming.black.value, isLight) { schemeFor() }
            val sysDark = isSystemInDarkTheme()
            SideEffect {
                if (Theming.systemDark.value != sysDark) Theming.systemDark.value = sysDark
                window.statusBarColor = Ink.toArgb()
                window.navigationBarColor = Ink.toArgb()
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Ink.toArgb()))
                val bars = androidx.core.view.WindowCompat.getInsetsController(window, decor)
                bars.isAppearanceLightStatusBars = isLight
                bars.isAppearanceLightNavigationBars = isLight
            }
            CompositionLocalProvider(LocalDensity provides scaled) {
                MaterialTheme(colorScheme = scheme, typography = type) {
                    Box(Modifier.fillMaxSize().techBackdrop()) {
                        Surface(
                            Modifier.fillMaxSize(),
                            color = Color.Transparent,
                            contentColor = Soft,
                        ) { RedialScreen() }
                    }
                }
            }
        }
        handleIntent(intent)
    }
}
