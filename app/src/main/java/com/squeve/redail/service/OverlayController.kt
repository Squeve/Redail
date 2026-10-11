package com.squeve.redail.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.squeve.redail.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

/**
 * Floating, draggable progress card with Pause/Resume and Stop.
 * Plain Android views so it can live inside the service. Needs "Display over other apps".
 */
class OverlayController(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var scope: CoroutineScope? = null
    private var card: LinearLayout? = null

    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var pauseBtn: Button

    private var lastState: EngineState = EngineState.Idle
    private var lastPaused = false

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun send(action: String) {
        ctx.startService(Intent(ctx, RedialService::class.java).setAction(action))
    }

    fun show() {
        if (card != null || !Settings.canDrawOverlays(ctx)) return

        val c = buildCard()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }
        attachDrag(c, lp)

        val added = runCatching { wm.addView(c, lp) }.isSuccess
        if (!added) return
        card = c

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
            while (true) {          // keeps the "next call in Ns" countdown ticking
                delay(1000)
                render(lastState, lastPaused)
            }
        }
    }

    fun hide() {
        scope?.cancel()
        scope = null
        card?.let { runCatching { wm.removeView(it) } }
        card = null
    }

    private fun render(s: EngineState, paused: Boolean) {
        if (card == null) return

        fun line(p: Progress) =
            "Number ${p.jobIndex + 1}/${p.jobCount} · ${p.number}\nCall ${p.attempt}/${p.total}"

        val (t, d) = when (s) {
            is EngineState.Dialing -> "Dialing…" to line(s.p)
            is EngineState.InCall -> "Calling…" to line(s.p)
            is EngineState.Cooldown -> {
                val left = ((s.untilMs - System.currentTimeMillis()) / 1000 + 1).coerceAtLeast(0)
                "Next call in ${left}s" to line(s.next)
            }
            is EngineState.Paused -> "Paused" to (s.last.progress()?.let { line(it) } ?: "")
            is EngineState.Finished -> "Done" to "${s.results.size} calls placed"
            is EngineState.Idle -> "Starting…" to ""
        }
        // Only touch views when something actually changed; avoids needless layout passes.
        val newTitle =
            if (paused && s !is EngineState.Paused && s !is EngineState.Finished) "$t · pausing" else t
        if (title.text.toString() != newTitle) title.text = newTitle
        if (detail.text.toString() != d) {
            detail.text = d
            detail.visibility = if (d.isEmpty()) View.GONE else View.VISIBLE
        }
        val newPause = if (paused) "Resume" else "Pause"
        if (pauseBtn.text.toString() != newPause) pauseBtn.text = newPause
        val canPause = s !is EngineState.Finished
        if (pauseBtn.isEnabled != canPause) {
            pauseBtn.isEnabled = canPause
            pauseBtn.alpha = if (canPause) 1f else 0.4f
        }
    }

    private fun buildCard(): LinearLayout {
        title = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            text = "Starting…"
        }
        detail = TextView(ctx).apply {
            setTextColor(Color.parseColor("#8A9BB8"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(8))
        }
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

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(240)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#EE0A1020"))
                setStroke(dp(2), Color.parseColor("#00E5FF"))
            }
            addView(title)
            addView(detail)
            addView(row)
        }
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
            setOnClickListener { onClick() }
        }

    private fun attachDrag(view: View, lp: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = e.rawX
                    touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt()
                    lp.y = startY + (e.rawY - touchY).toInt()
                    runCatching { wm.updateViewLayout(view, lp) }
                    true
                }
                else -> false
            }
        }
    }
}
