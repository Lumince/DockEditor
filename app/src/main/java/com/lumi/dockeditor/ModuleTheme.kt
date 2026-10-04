package com.lumi.dockeditor

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * The background, text and accent colours set in UX Patcher.
 * UX Patcher mirrors them to Settings.Global, which any app can read.
 * UX Patcher not installed, or nothing set = the normal look.
 */
object ModuleTheme {

    private const val MODULE_PKG = "com.lumi.uxpatcher"
    private const val G_BG = "uxpatcher_bg_color"
    private const val G_TEXT = "uxpatcher_text_color"
    private const val G_ACCENT = "uxpatcher_accent_color"

    /** Opaque ARGB colours, null = not set. */
    data class Custom(val bg: Int?, val text: Int?, val accent: Int?)

    /** Compose reads this, so a change recomposes the screen. */
    var current: Custom? by mutableStateOf(null)
        private set

    /** Reads the colours and restyles the window. Call from onCreate and onResume. */
    fun refresh(activity: Activity) {
        val read = read(activity)
        if (read != current) current = read
        applyWindow(activity, read)
    }

    /** The settings stay behind if UX Patcher is uninstalled, so check it is really there. */
    @Suppress("DEPRECATION")
    private fun moduleInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(MODULE_PKG, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun read(context: Context): Custom? {
        if (!moduleInstalled(context)) return null
        val cr = context.contentResolver
        // a number is a colour (RGB); anything else ("off", missing) is not set
        fun get(key: String): Int? = try {
            Settings.Global.getString(cr, key)?.trim()?.toInt()
                ?.takeIf { it != -1 }
                ?.let { it or 0xFF000000.toInt() }
        } catch (e: Exception) {
            null
        }
        val c = Custom(get(G_BG), get(G_TEXT), get(G_ACCENT))
        return if (c.bg == null && c.text == null && c.accent == null) null else c
    }

    private fun applyWindow(activity: Activity, c: Custom?) {
        val bg = c?.bg ?: return
        val window = activity.window
        window.setBackgroundDrawable(ColorDrawable(bg))
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val lightBg = Color(bg).luminance() > 0.5f
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = lightBg
        controller.isAppearanceLightNavigationBars = lightBg
    }

    /** Colours for the plain-view dialog: background, text, muted text, button. Null = not set. */
    class DialogColors(val bg: Int, val text: Int, val muted: Int, val button: Int)

    fun dialogColors(): DialogColors? {
        val c = current ?: return null
        val bg = Color(c.bg ?: return null)
        val text = c.text?.let { Color(it) } ?: onColor(bg)
        val accent = c.accent?.let { Color(it) } ?: text
        return DialogColors(
            lerp(bg, text, 0.10f).toArgb(),
            text.toArgb(),
            text.copy(alpha = 0.7f).toArgb(),
            accent.toArgb()
        )
    }

    private fun onColor(on: Color): Color = if (on.luminance() < 0.5f) Color.White else Color.Black

    /** The Material scheme: the system one, with the UX Patcher colours on top. */
    @Composable
    fun colorScheme(): ColorScheme {
        val custom = current
        val context = LocalContext.current
        val systemDark = isSystemInDarkTheme()
        // a custom background decides dark or light; otherwise follow the system
        val dark = custom?.bg?.let { Color(it).luminance() < 0.5f } ?: systemDark
        val base = if (custom?.bg == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) darkColorScheme() else lightColorScheme()
        }
        return if (custom == null) base else withCustom(base, custom)
    }

    private fun withCustom(base: ColorScheme, c: Custom): ColorScheme {
        var s = base
        val bg = c.bg?.let { Color(it) }
        val text = c.text?.let { Color(it) }
        if (bg != null) {
            val on = text ?: onColor(bg)
            s = s.copy(
                background = bg,
                surface = bg,
                surfaceContainerLowest = bg,
                surfaceContainerLow = lerp(bg, on, 0.04f),
                surfaceContainer = lerp(bg, on, 0.07f),
                surfaceContainerHigh = lerp(bg, on, 0.10f),
                surfaceContainerHighest = lerp(bg, on, 0.14f),
                surfaceVariant = lerp(bg, on, 0.12f),
                outline = lerp(bg, on, 0.50f),
                outlineVariant = lerp(bg, on, 0.20f),
                onBackground = on,
                onSurface = on,
                onSurfaceVariant = on.copy(alpha = 0.7f),
                onPrimaryContainer = on,
                onSecondaryContainer = on
            )
        } else if (text != null) {
            s = s.copy(
                onBackground = text,
                onSurface = text,
                onSurfaceVariant = text.copy(alpha = 0.7f),
                onPrimaryContainer = text,
                onSecondaryContainer = text
            )
        }
        c.accent?.let { a ->
            val accent = Color(a)
            val under = bg ?: s.background
            s = s.copy(
                primary = accent,
                onPrimary = onColor(accent),
                primaryContainer = lerp(under, accent, 0.30f),
                secondary = accent,
                onSecondary = onColor(accent),
                secondaryContainer = lerp(under, accent, 0.20f),
                inversePrimary = accent,
                surfaceTint = accent
            )
        }
        return s
    }
}
