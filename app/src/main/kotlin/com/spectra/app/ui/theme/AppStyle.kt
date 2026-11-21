package com.spectra.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.spectra.app.R

enum class GlassMaterial(
    val id: String,
    @StringRes val label: Int,
    @StringRes val note: Int,
) {
    CLEAR("clear", R.string.spectra_glass_clear, R.string.spectra_glass_clear_note),
    FROSTED("frosted", R.string.spectra_glass_frosted, R.string.spectra_glass_frosted_note),
    TINTED("tinted", R.string.spectra_glass_tinted, R.string.spectra_glass_tinted_note),
    METAL("metal", R.string.spectra_glass_metal, R.string.spectra_glass_metal_note),
    MATTE("matte", R.string.spectra_glass_matte, R.string.spectra_glass_matte_note),
    OUTLINE("outline", R.string.spectra_glass_outline, R.string.spectra_glass_outline_note),
    INK("ink", R.string.spectra_glass_ink, R.string.spectra_glass_ink_note),
    ;

    companion object {
        fun byId(id: String): GlassMaterial? = entries.firstOrNull { it.id == id }
    }
}

val DefaultGlassAccent = Color(0xFFF0904F)
val LocalSongAccent = compositionLocalOf { Color(0xFFED5564) }

val LocalGlassMaterial = staticCompositionLocalOf { GlassMaterial.CLEAR }
val LocalGlassAccent = compositionLocalOf { DefaultGlassAccent }
val LocalLargeNav = staticCompositionLocalOf { false }

enum class GlassMode(
    val id: String,
) {
    OFF("off"),
    DARK("dark"),
    BRIGHT("bright"),
    ;

    companion object {
        fun byId(id: String?): GlassMode = entries.firstOrNull { it.id == id } ?: OFF

        /** Hanya ada dua gaya: Kertas (OFF) dan Liquid Glass, yang gelap/terangnya mengikuti sistem. */
        fun resolve(id: String?, systemDark: Boolean): GlassMode =
            if (byId(id) == OFF) OFF else if (systemDark) DARK else BRIGHT
    }
}

val LocalGlassMode = staticCompositionLocalOf { GlassMode.OFF }

class AppStyle(
    val id: String,
    @StringRes val label: Int,
    @StringRes val note: Int,
    val ios: Boolean,
    val overrides: Boolean,
    val glass: GlassMaterial,
    val accentLight: Color,
    val accentDark: Color,
    val bgLight: Color,
    val cardLight: Color,
    val inkLight: Color,
    val bgDark: Color,
    val cardDark: Color,
    val inkDark: Color,
) {
    fun accent(dark: Boolean): Color = if (dark) accentDark else accentLight

    fun bg(dark: Boolean): Color = if (dark) bgDark else bgLight

    fun card(dark: Boolean): Color = if (dark) cardDark else cardLight

    fun ink(dark: Boolean): Color = if (dark) inkDark else inkLight

    fun scheme(
        base: ColorScheme,
        dark: Boolean,
    ): ColorScheme {
        if (!overrides) return base
        val bg = bg(dark)
        val card = card(dark)
        val ink = ink(dark)
        val accent = accent(dark)
        val onDarkBg = bg.luminance() < 0.4f
        val text = if (onDarkBg) Color(0xFFF2EDE7) else Color(0xFF1E1A17)
        val textMuted = if (onDarkBg) Color(0xFFA8A29B) else Color(0xFF6B645C)
        val onAccent = if (accent.luminance() > 0.55f) Color(0xFF111111) else Color.White
        val secondary = textMuted
        val onSecondary = if (secondary.luminance() > 0.5f) Color(0xFF111111) else Color.White
        val secondaryContainer = lerp(bg, accent, if (dark) 0.20f else 0.12f)
        return base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(bg, accent, if (dark) 0.28f else 0.16f),
            onPrimaryContainer = if (dark) lerp(accent, Color.White, 0.55f) else lerp(accent, Color.Black, 0.45f),
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = text,
            tertiary = secondary,
            onTertiary = onSecondary,
            tertiaryContainer = secondaryContainer,
            onTertiaryContainer = text,
            background = bg,
            onBackground = text,
            surface = bg,
            onSurface = text,
            surfaceVariant = lerp(bg, ink, 0.08f),
            onSurfaceVariant = textMuted,
            surfaceTint = accent,
            inverseSurface = text,
            inverseOnSurface = bg,
            outline = lerp(bg, ink, 0.45f),
            outlineVariant = lerp(bg, ink, 0.16f),
            surfaceDim = lerp(bg, Color.Black, 0.05f),
            surfaceBright = card,
            surfaceContainerLowest = if (dark) lerp(bg, Color.Black, 0.4f) else card,
            surfaceContainerLow = lerp(bg, card, 0.5f),
            surfaceContainer = card,
            surfaceContainerHigh = lerp(card, ink, 0.05f),
            surfaceContainerHighest = lerp(card, ink, 0.10f),
        )
    }
}

object AppStyles {
    /** Kertas: permukaan solid, datar, tanpa transparansi; aksen dan warna latar diturunkan dari SoftPalette. */
    val Paper =
        AppStyle(
            id = "paper",
            label = R.string.spectra_glassmode_paper,
            note = R.string.spectra_glassmode_paper,
            ios = false,
            overrides = true,
            glass = GlassMaterial.MATTE,
            accentLight = DefaultGlassAccent,
            accentDark = DefaultGlassAccent,
            bgLight = Color(0xFFF6F3EC),
            cardLight = Color(0xFFFFFFFF),
            inkLight = Color(0xFF1E1A17),
            bgDark = Color(0xFF121110),
            cardDark = Color(0xFF1D1B19),
            inkDark = Color(0xFFF2EDE7),
        )

    val LiquidDark =
        AppStyle(
            id = "liquid_dark",
            label = R.string.spectra_style_default,
            note = R.string.spectra_style_default_note,
            ios = false,
            overrides = true,
            glass = GlassMaterial.CLEAR,
            accentLight = Color(0xFFF0904F),
            accentDark = Color(0xFFF0904F),
            bgLight = Color(0xFF0E0C0B),
            cardLight = Color(0xFF1B1816),
            inkLight = Color(0xFFF2EDE7),
            bgDark = Color(0xFF0E0C0B),
            cardDark = Color(0xFF1B1816),
            inkDark = Color(0xFFF2EDE7),
        )

    val LiquidBright =
        AppStyle(
            id = "liquid_bright",
            label = R.string.spectra_style_default,
            note = R.string.spectra_style_default_note,
            ios = false,
            overrides = true,
            glass = GlassMaterial.CLEAR,
            accentLight = Color(0xFFAD4A12),
            accentDark = Color(0xFFAD4A12),
            bgLight = Color(0xFFF3EEE8),
            cardLight = Color(0xFFFFFFFF),
            inkLight = Color(0xFF1E1A17),
            bgDark = Color(0xFFF3EEE8),
            cardDark = Color(0xFFFFFFFF),
            inkDark = Color(0xFF1E1A17),
        )
}
