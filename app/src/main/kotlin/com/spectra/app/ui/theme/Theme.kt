/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.spectra.app.constants.GlassModeKey
import com.spectra.app.constants.GlassRefractionKey
import com.spectra.app.ui.component.GlassIndication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.spectra.app.utils.dataStore
import com.spectra.app.utils.rememberPreference
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

private val IosShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(14.dp),
    )

private fun ios(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

/** SF-style type scale: 17pt body, 34/28/22/20 titles, 13/12/11 captions. */
private val IosTypography =
    Typography(
        displayLarge = ios(38, 45, FontWeight.Bold, 0.4f),
        displayMedium = ios(38, 45, FontWeight.Bold, 0.4f),
        displaySmall = ios(32, 38, FontWeight.Bold, 0.36f),
        headlineLarge = ios(32, 38, FontWeight.Bold, 0.36f),
        headlineMedium = ios(27, 33, FontWeight.Bold),
        headlineSmall = ios(23, 28, FontWeight.Bold, 0.38f),
        titleLarge = ios(26, 31, FontWeight.SemiBold),
        titleMedium = ios(19, 24, FontWeight.SemiBold, -0.4f),
        titleSmall = ios(17, 22, FontWeight.SemiBold, -0.41f),
        bodyLarge = ios(17, 22, FontWeight.Normal, -0.41f),
        bodyMedium = ios(15, 20, FontWeight.Normal, -0.24f),
        bodySmall = ios(13, 18, FontWeight.Normal, -0.08f),
        labelLarge = ios(17, 22, FontWeight.Medium, -0.41f),
        labelMedium = ios(15, 20, FontWeight.Medium, -0.24f),
        labelSmall = ios(12, 16, FontWeight.Medium, 0f),
    )

val DefaultThemeColor = Color(0xFFED5564)
val BrandSeedColor = Color(0xFFF0904F)

@Composable
fun SpectraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val stored = remember { runBlocking { context.dataStore.data.first() } }
    val glassModeId by rememberPreference(GlassModeKey, defaultValue = stored[GlassModeKey] ?: "dark")
    val effDark = darkTheme
    val useSystemDynamicColor = false

    val baseColorScheme = if (useSystemDynamicColor) {
        if (effDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = if (themeColor == DefaultThemeColor) BrandSeedColor else themeColor,
            isDark = effDark,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot
        )
    }

    val glassMode = GlassMode.resolve(glassModeId, effDark)
    val refractionId by rememberPreference(GlassRefractionKey, defaultValue = "normal")
    val frostBlur by rememberPreference(com.spectra.app.constants.GlassFrostBlurKey, defaultValue = 0.2f)
    val navBlur by rememberPreference(com.spectra.app.constants.GlassNavBlurKey, defaultValue = 1f)
    val fontId by rememberPreference(com.spectra.app.constants.AppFontFamilyKey, defaultValue = stored[com.spectra.app.constants.AppFontFamilyKey] ?: AppFont.SYSTEM.id)
    val fontWeight by rememberPreference(com.spectra.app.constants.AppFontWeightKey, defaultValue = stored[com.spectra.app.constants.AppFontWeightKey] ?: FontDefaultWeight)
    val fontSize by rememberPreference(com.spectra.app.constants.AppFontSizeKey, defaultValue = stored[com.spectra.app.constants.AppFontSizeKey] ?: FontBaseSize)
    val typography =
        remember(fontId, fontWeight, fontSize) {
            IosTypography.withAppFont(appFontFamily(context, AppFont.byId(fontId), fontWeight), fontWeight, fontSize)
        }
    val refraction =
        when (refractionId) {
            "off" -> 0f
            "soft" -> 0.6f
            "strong" -> 1.4f
            else -> 1f
        }
    val softSeed = if (themeColor == DefaultThemeColor) MatchaSeed else themeColor
    val soft = remember(softSeed, effDark) { softPalette(softSeed, effDark) }
    val softSurface = soft.animatedSurface()
    val style =
        remember(glassMode, softSurface.accent, softSurface.bg, softSurface.card, softSurface.ink) {
            when (glassMode) {
                GlassMode.DARK -> AppStyles.LiquidDark.tinted(softSurface)
                GlassMode.BRIGHT -> AppStyles.LiquidBright.tinted(softSurface)
                GlassMode.OFF -> AppStyles.Paper.tinted(softSurface)
            }
        }
    val glass = if (glassMode != GlassMode.OFF) GlassMaterial.CLEAR else style.glass
    val largeNav = false

    val blendedScheme = remember(baseColorScheme, effDark, style) {
        if (style.overrides) {
            style.scheme(baseColorScheme, effDark)
        } else if (effDark) {
            baseColorScheme.copy(
                tertiary = Color(0xFFFFB68A),
                onTertiary = Color(0xFF4E2300),
                tertiaryContainer = Color(0xFF703610),
                onTertiaryContainer = Color(0xFFFFDBC8),
            )
        } else {
            baseColorScheme.copy(
                tertiary = Color(0xFF9A4A1A),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFFDBC8),
                onTertiaryContainer = Color(0xFF351000),
            )
        }
    }

    val colorScheme = remember(blendedScheme, pureBlack, effDark, glassMode) {
        if (effDark && ((pureBlack && glassMode == GlassMode.OFF) || glassMode == GlassMode.DARK)) {
            blendedScheme.pureBlack(true)
        } else {
            blendedScheme
        }
    }

    val glassQuality = remember(context) { com.spectra.app.ui.component.detectGlassQuality(context) }

    val backdropLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val contentLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backdropState = remember(backdropLayer, contentLayer) { com.spectra.app.ui.component.GlassBackdropState(backdropLayer, contentLayer) }

    val ambientGlow =
        remember(baseColorScheme.primary, baseColorScheme.secondary, baseColorScheme.tertiary) {
            com.spectra.app.ui.component.AmbientGlowColors(baseColorScheme.primary, baseColorScheme.secondary, baseColorScheme.tertiary)
        }

    val songGradient =
        if (glassMode != GlassMode.OFF && backdropState.songColor.isSpecified) {
            com.spectra.app.ui.component.songGradient(backdropState.songColor, glassMode == GlassMode.DARK)
        } else {
            null
        }

    val kyantBackdrop =
        if (android.os.Build.VERSION.SDK_INT >= 33 && glassMode != GlassMode.OFF) {
            com.spectra.app.ui.component.rememberKyantBackdrop(
                glassMode,
                colorScheme.background,
                baseTop = songGradient?.top ?: Color.Unspecified,
                baseMid = songGradient?.mid ?: Color.Unspecified,
                baseBottom = songGradient?.bottom ?: Color.Unspecified,
            )
        } else {
            null
        }

    if (kyantBackdrop != null && glassMode != GlassMode.OFF) {
        com.spectra.app.ui.component.GlassTiltEffect()
        com.spectra.app.ui.glass.GlassLensGateEffect()
    }

    MaterialTheme(colorScheme = colorScheme, shapes = IosShapes, typography = typography) {
        CompositionLocalProvider(
            com.spectra.app.ui.component.LocalAmbientGlow provides ambientGlow,
            com.spectra.app.ui.component.LocalKyantBackdrop provides kyantBackdrop,
            com.spectra.app.ui.component.LocalGlassBackdrop provides backdropState,
            com.spectra.app.ui.component.LocalGlassRefraction provides refraction,
            com.spectra.app.ui.component.LocalGlassFrostBlur provides frostBlur.dp,
            com.spectra.app.ui.component.LocalGlassNavBlur provides navBlur,
            LocalIndication provides GlassIndication,
            LocalGlassMaterial provides glass,
            LocalGlassAccent provides style.accent(effDark),
            LocalLargeNav provides largeNav,
            LocalGlassMode provides glassMode,
            com.spectra.app.ui.component.LocalGlassQuality provides glassQuality,
            content = content,
        )
    }
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF2A2A2D),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF111113),
        surfaceContainer = Color(0xFF1A1A1D),
        surfaceContainerHigh = Color(0xFF1E1E21),
        surfaceContainerHighest = Color(0xFF28282C),
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
