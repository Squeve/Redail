package com.squeve.redail.service

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button

/** Colours for the expanded floating card in one look. The bubble has its own drawing code. */
internal class CardStyle(
    val bgFrom: Int, val bgTo: Int, val stroke: Int, val strokeDp: Float, val radiusDp: Int,
    val title: Int, val detail: Int, val collapse: Int,
    val primFrom: Int, val primTo: Int, val primText: Int,
    val stopFrom: Int, val stopTo: Int, val stopText: Int,
    val idleFrom: Int, val idleTo: Int, val idleText: Int,
)

private fun c(v: Long): Int = v.toInt()

/** 0 dark, 1 light, 2 neon, 3 aurora, 4 gold, 5 carbon, 6 frost. */
internal val CARD_STYLES = listOf(
    // dark
    CardStyle(
        c(0xEE0A1020), c(0xEE0A1020), c(0xFF00E5FF), 2f, 14,
        c(0xFFFFFFFF), c(0xFF8A9BB8), c(0xFF00E5FF),
        c(0xFF00838F), c(0xFF00838F), c(0xFFFFFFFF),
        c(0xFFD50057), c(0xFFD50057), c(0xFFFFFFFF),
        c(0xFF37474F), c(0xFF37474F), c(0xFFFFFFFF),
    ),
    // light
    CardStyle(
        c(0xF2F3F7FF), c(0xF2E4ECFF), c(0xFF0A1020), 1.5f, 16,
        c(0xFF0A1020), c(0xFF2E4066), c(0xFF007C91),
        c(0xFF00838F), c(0xFF00838F), c(0xFFFFFFFF),
        c(0xFFD50057), c(0xFFD50057), c(0xFFFFFFFF),
        c(0xFF455A64), c(0xFF455A64), c(0xFFFFFFFF),
    ),
    // neon (same card as dark)
    CardStyle(
        c(0xEE0A1020), c(0xEE0A1020), c(0xFF00E5FF), 2.5f, 14,
        c(0xFFFFFFFF), c(0xFF8A9BB8), c(0xFF00E5FF),
        c(0xFF00838F), c(0xFF00838F), c(0xFFFFFFFF),
        c(0xFFD50057), c(0xFFD50057), c(0xFFFFFFFF),
        c(0xFF37474F), c(0xFF37474F), c(0xFFFFFFFF),
    ),
    // aurora
    CardStyle(
        c(0xF0141A40), c(0xF02A1058), c(0xFF7C4DFF), 1.5f, 20,
        c(0xFFFFFFFF), c(0xFFB8C4E8), c(0xFF00E5FF),
        c(0xFF0097B2), c(0xFF6A3DFF), c(0xFFFFFFFF),
        c(0xFFFF2E93), c(0xFFC2185B), c(0xFFFFFFFF),
        c(0xFF3A3F66), c(0xFF2B2F52), c(0xFFFFFFFF),
    ),
    // gold
    CardStyle(
        c(0xF2181206), c(0xF2050403), c(0xFFD4AF37), 1.5f, 20,
        c(0xFFFFF1C9), c(0xFFC8AE62), c(0xFFD4AF37),
        c(0xFFF0D27A), c(0xFFB8892A), c(0xFF1A1405),
        c(0xFF7D2234), c(0xFF4A1020), c(0xFFFFD9A0),
        c(0xFF3A3220), c(0xFF2A2416), c(0xFFFFF1C9),
    ),
    // carbon
    CardStyle(
        c(0xF01E222B), c(0xF00B0D11), c(0xFF8A94A6), 1.5f, 18,
        c(0xFFEAF0FA), c(0xFF8D98AD), c(0xFF00E5FF),
        c(0xFF2C3342), c(0xFF1B202B), c(0xFF00E5FF),
        c(0xFF3A2430), c(0xFF24141C), c(0xFFFF5C9A),
        c(0xFF2A303C), c(0xFF1C212B), c(0xFFEAF0FA),
    ),
    // frost
    CardStyle(
        c(0xF5FFFFFF), c(0xF5DCE6FA), c(0xFFB8C6E4), 1.5f, 22,
        c(0xFF0A1020), c(0xFF2E4066), c(0xFF00838F),
        c(0xFF00B8D4), c(0xFF0091EA), c(0xFFFFFFFF),
        c(0xFFFF4081), c(0xFFD50057), c(0xFFFFFFFF),
        c(0xFF78909C), c(0xFF607D8B), c(0xFFFFFFFF),
    ),
)

internal fun styleFor(look: Int): CardStyle = CARD_STYLES[look.coerceIn(0, CARD_STYLES.size - 1)]

internal fun paintButton(ctx: Context, b: Button, from: Int, to: Int, text: Int) {
    val d = ctx.resources.displayMetrics.density
    b.setTextColor(text)
    b.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(from, to)).apply {
        cornerRadius = 12 * d
    }
}

internal fun paintCard(ctx: Context, card: View, st: CardStyle) {
    val d = ctx.resources.displayMetrics.density
    card.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(st.bgFrom, st.bgTo)).apply {
        cornerRadius = st.radiusDp * d
        setStroke(maxOf(1, (st.strokeDp * d).toInt()), st.stroke)
    }
}
