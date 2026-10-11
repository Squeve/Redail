package com.squeve.redail.service

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.squeve.redail.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating control. Two looks in one window:
 *  - card: live progress, auto hang-up countdown (tap to keep the call), Pause/Resume, Stop
 *  - bubble: a small draggable ring with the countdown / percent; tap to expand, snaps to the screen edge
 * Plain Android views so it can live inside the service. Needs "Display over other apps".
 */
class OverlayController(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = ctx.getSharedPreferences("redail", Context.MODE_PRIVATE)
    private var scope: CoroutineScope? = null
    private var root: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null

    private lateinit var card: LinearLayout
    private lateinit var bubble: BubbleView
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var timerBtn: Button
    private lateinit var pauseBtn: Button

    private var collapsed = prefs.getBoolean("overlayCollapsed", false)
    private var lastState: EngineState = EngineState.Idle
    private var lastPaused = false
    private var timerArmedStyle: Boolean? = null

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun send(action: String) {
        ctx.startService(Intent(ctx, RedialService::class.java).setAction(action))
    }

    fun show() {
        if (root != null || !Settings.canDrawOverlays(ctx)) return

        buildViews()
        val frame = FrameLayout(ctx).apply {
            addView(card)
            addView(bubble, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        card.visibility = if (collapsed) View.GONE else View.VISIBLE
        bubble.visibility = if (collapsed) View.VISIBLE else View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (collapsed) ctx.resources.displayMetrics.widthPixels - dp(84) else dp(16)
            y = dp(160)
        }
        attachDrag(frame, params)

        if (runCatching { wm.addView(frame, params) }.isFailure) return
        root = frame
        lp = params

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            combine(RedialService.status, RedialService.paused) { st, p -> st to p }
                .collect { (st, p) ->
                    lastState = st
                    lastPaused = p
                    render(st, p)
                }
        }
        s.launch {
            while (true) {          // keeps the countdowns ticking
                delay(500)
                render(lastState, lastPaused)
            }
        }
    }

    fun hide() {
        scope?.cancel()
        scope = null
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        lp = null
        timerArmedStyle = null
    }

    private fun setCollapsed(c: Boolean) {
        collapsed = c
        prefs.edit().putBoolean("overlayCollapsed", c).apply()
        card.visibility = if (c) View.GONE else View.VISIBLE
        bubble.visibility = if (c) View.VISIBLE else View.GONE
        val l = lp ?: return
        if (!c) {   // keep the expanded card on screen
            val maxX = ctx.resources.displayMetrics.widthPixels - dp(280)
            l.x = l.x.coerceIn(0, maxOf(0, maxX))
        }
        root?.let { runCatching { wm.updateViewLayout(it, l) } }
    }

    private fun clock(totalSec: Long) = "${totalSec / 60}:${"%02d".format(totalSec % 60)}"

    private fun render(s: EngineState, paused: Boolean) {
        if (root == null) return
        val now = System.currentTimeMillis()

        fun line(p: Progress) =
            "${p.number}\nCall ${p.step}/${p.steps} · this number ${p.attempt}/${p.total}"

        val (t, d) = when (s) {
            is EngineState.Dialing -> "Dialing…" to line(s.p)
            is EngineState.InCall -> "In call" to line(s.p)
            is EngineState.Cooldown -> {
                val left = ((s.untilMs - now) / 1000 + 1).coerceAtLeast(0)
                "Next call in ${left}s" to line(s.next)
            }
            is EngineState.Paused -> "Paused" to (s.last.progress()?.let { line(it) } ?: "")
            is EngineState.Finished -> "Done" to "${s.results.size} calls placed"
            is EngineState.Idle -> "Starting…" to ""
        }

        // ── card: only touch views when something actually changed ──
        val newTitle =
            if (paused && s !is EngineState.Paused && s !is EngineState.Finished) "$t · pausing" else t
        if (title.text.toString() != newTitle) title.text = newTitle
        if (detail.text.toString() != d) {
            detail.text = d
            detail.visibility = if (d.isEmpty()) View.GONE else View.VISIBLE
        }

        val inCall = s as? EngineState.InCall
        val armedLeft: Long? = inCall?.hangUpAt?.let { ((it - now) / 1000 + 1).coerceAtLeast(0) }

        if (inCall != null && inCall.hasLimit) {
            val armed = armedLeft != null
            val label = if (armedLeft != null) {
                "Hangs up in ${clock(armedLeft)}  ·  KEEP CALL"
            } else {
                "Timer off, call runs free  ·  RE-ARM"
            }
            if (timerBtn.text.toString() != label) timerBtn.text = label
            if (timerArmedStyle != armed) {
                timerArmedStyle = armed
                (timerBtn.background as? GradientDrawable)
                    ?.setColor(Color.parseColor(if (armed) "#00838F" else "#37474F"))
            }
            if (timerBtn.visibility != View.VISIBLE) timerBtn.visibility = View.VISIBLE
        } else if (timerBtn.visibility != View.GONE) {
            timerBtn.visibility = View.GONE
        }

        val newPause = if (paused) "Resume" else "Pause"
        if (pauseBtn.text.toString() != newPause) pauseBtn.text = newPause
        val canPause = s !is EngineState.Finished
        if (pauseBtn.isEnabled != canPause) {
            pauseBtn.isEnabled = canPause
            pauseBtn.alpha = if (canPause) 1f else 0.4f
        }

        // ── bubble ──
        val p = s.progress()
        val frac = when {
            s is EngineState.Finished -> 1f
            p != null -> (p.step - 1).toFloat() / p.steps.toFloat()
            else -> 0f
        }
        val label = armedLeft?.let { clock(it) } ?: "${(frac * 100).roundToInt()}%"
        val sub = when (s) {
            is EngineState.Dialing -> "DIAL"
            is EngineState.InCall -> if (armedLeft != null) "HANG UP" else "CALL"
            is EngineState.Cooldown -> "WAIT"
            is EngineState.Paused -> "PAUSED"
            is EngineState.Finished -> "DONE"
            is EngineState.Idle -> "START"
        }
        val ring = when {
            paused || s is EngineState.Paused -> "#FF2E93"
            s is EngineState.Finished -> "#00E676"
            else -> "#00E5FF"
        }
        bubble.update(frac, label, sub, Color.parseColor(ring))
    }

    private fun toggleTimer() {
        val ic = RedialService.status.value as? EngineState.InCall ?: return
        send(if (ic.hangUpAt != null) RedialService.ACTION_HOLD else RedialService.ACTION_REARM)
    }

    private fun buildViews() {
        title = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            text = "Starting…"
        }
        val collapseBtn = TextView(ctx).apply {
            text = "–"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                setCollapsed(true)
            }
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(collapseBtn)
        }
        detail = TextView(ctx).apply {
            setTextColor(Color.parseColor("#8A9BB8"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(8))
        }
        timerBtn = makeButton("", "#00838F") { toggleTimer() }.apply { visibility = View.GONE }
        pauseBtn = makeButton("Pause", "#00838F") {
            send(if (RedialService.paused.value) RedialService.ACTION_RESUME else RedialService.ACTION_PAUSE)
        }
        val stopBtn = makeButton("Stop", "#D50057") { send(RedialService.ACTION_STOP) }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pauseBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            })
            addView(stopBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(6)
            })
        }

        card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(260)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#EE0A1020"))
                setStroke(dp(2), Color.parseColor("#00E5FF"))
            }
            addView(header)
            addView(detail)
            addView(timerBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
            addView(row)
        }

        bubble = BubbleView(ctx)
    }

    private fun makeButton(label: String, color: String, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            setAllCaps(false)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor(color))
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

    /** Drag anywhere that isn't a button. In bubble mode: tap expands, release snaps to the nearest edge. */
    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        val slop = ViewConfiguration.get(ctx).scaledTouchSlop

        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (collapsed) {
                        if (moved) snapToEdge(view, params) else {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            setCollapsed(false)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val dm = ctx.resources.displayMetrics
        val w = if (bubble.width > 0) bubble.width else dp(76)
        val target = if (params.x + w / 2 < dm.widthPixels / 2) dp(6) else dm.widthPixels - w - dp(6)
        params.y = params.y.coerceIn(0, maxOf(0, dm.heightPixels - w - dp(24)))
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 180
            addUpdateListener {
                params.x = it.animatedValue as Int
                runCatching { wm.updateViewLayout(view, params) }
            }
            start()
        }
    }
}

/**
 * Round readout: opaque dark disc with a white hairline, a thick progress ring and a pulsing glow,
 * so it stands out on dark and light screens alike. Redraws on value changes and while it pulses.
 */
private class BubbleView(ctx: Context) : View(ctx) {
    private var fraction = 0f
    private var label = ""
    private var sub = ""
    private var ringColor = Color.parseColor("#00E5FF")
    private var pulse = 1f
    private var pulser: ValueAnimator? = null

    private val d = ctx.resources.displayMetrics.density
    private val core = 30 * d          // radius of the dark disc
    private val glowPad = 8 * d        // room around the disc for the glow

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF0A1020") }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * d
        color = Color.parseColor("#E6FFFFFF")
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * d
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f * d
        color = Color.parseColor("#2E4066")
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 14 * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B7C4DD")
        textAlign = Paint.Align.CENTER
        textSize = 7.5f * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val box = RectF()
    private val glowAlphas = intArrayOf(95, 55, 28)

    fun update(f: Float, l: String, s: String, c: Int) {
        if (f != fraction || l != label || s != sub || c != ringColor) {
            fraction = f
            label = l
            sub = s
            ringColor = c
            invalidate()
        }
    }

    // Pulse only while the bubble is actually on screen.
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) startPulse() else stopPulse()
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    private fun startPulse() {
        if (pulser != null) return
        pulser = ValueAnimator.ofFloat(0.45f, 1f).apply {
            duration = 1300
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        pulser?.cancel()
        pulser = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = ((core + glowPad) * 2).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        // glow halo in the ring colour
        for (i in glowAlphas.indices) {
            glow.color = ringColor
            glow.alpha = (glowAlphas[i] * pulse).toInt()
            canvas.drawCircle(cx, cy, core + (1.5f + i * 2.5f) * d, glow)
        }

        canvas.drawCircle(cx, cy, core, fill)
        canvas.drawCircle(cx, cy, core, rim)

        val r = core - 8 * d
        box.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(box, 0f, 360f, false, track)
        if (fraction > 0.001f) {
            arc.color = ringColor
            canvas.drawArc(box, -90f, 360f * fraction, false, arc)
        }

        canvas.drawText(label, cx, cy + 1 * d, big)
        canvas.drawText(sub, cx, cy + 12 * d, small)
    }
}
