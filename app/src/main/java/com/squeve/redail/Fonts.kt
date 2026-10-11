package com.squeve.redail

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

/**
 * Display and monospace families. Put display.ttf / mono.ttf in app/src/main/assets/fonts/ to override;
 * otherwise the system condensed and monospace fonts are used.
 */
object Fonts {
    var display: FontFamily = FontFamily.SansSerif
        private set
    var mono: FontFamily = FontFamily.Monospace
        private set

    fun init(ctx: Context) {
        display = fromAsset(ctx, "fonts/display.ttf")
            ?: FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
        mono = fromAsset(ctx, "fonts/mono.ttf") ?: FontFamily.Monospace
    }

    private fun fromAsset(ctx: Context, path: String): FontFamily? =
        runCatching { FontFamily(Typeface.createFromAsset(ctx.assets, path)) }.getOrNull()
}
