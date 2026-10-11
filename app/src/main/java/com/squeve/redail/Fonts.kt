package com.squeve.redail

import android.content.Context
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Bundled families, so the app looks the same whatever system font the phone uses.
 * Replace the files in app/src/main/assets/fonts/ (same names) to restyle.
 */
object Fonts {
    /** Headings, buttons and body text. */
    var body: FontFamily = FontFamily.SansSerif
        private set

    /** Same family; headings are just set bold with wide letter spacing. */
    val display: FontFamily get() = body

    /** Numbers and data. */
    var mono: FontFamily = FontFamily.Monospace
        private set

    @OptIn(ExperimentalTextApi::class)
    fun init(ctx: Context) {
        val am = ctx.assets
        body = runCatching {
            FontFamily(
                Font(am, "fonts/sans_regular.ttf", FontWeight.Normal),
                Font(am, "fonts/sans_medium.ttf", FontWeight.Medium),
                Font(am, "fonts/sans_bold.ttf", FontWeight.Bold),
            )
        }.getOrDefault(FontFamily.SansSerif)
        mono = runCatching {
            FontFamily(
                Font(am, "fonts/mono_regular.ttf", FontWeight.Normal),
                Font(am, "fonts/mono_bold.ttf", FontWeight.Bold),
            )
        }.getOrDefault(FontFamily.Monospace)
    }
}
