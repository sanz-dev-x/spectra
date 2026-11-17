package com.spectra.app.ui.theme

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.spectra.app.R
import java.io.File

const val FontBaseSize = 17f
const val FontMinSize = 10f
const val FontMaxSize = 32f
const val FontDefaultWeight = 400

/**
 * Keluarga font SF Pro. Apple tidak mengizinkan SF Pro didistribusikan di dalam aplikasi non-Apple,
 * jadi berkasnya TIDAK dibundel: pengguna mengimpor berkas milik sendiri (.otf/.ttf) dan disalin ke
 * penyimpanan privat aplikasi. Tanpa berkas, font sistem dipakai.
 */
enum class AppFont(
    val id: String,
    @StringRes val label: Int,
) {
    SYSTEM("system", R.string.sx_font_system),
    SF_DISPLAY("sf_display", R.string.sx_font_sf_display),
    SF_TEXT("sf_text", R.string.sx_font_sf_text),
    SF_ROUNDED("sf_rounded", R.string.sx_font_sf_rounded),
    ;

    companion object {
        fun byId(id: String?): AppFont = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

private fun fontFile(context: Context, font: AppFont): File = File(File(context.filesDir, "fonts"), "${font.id}.font")

fun isFontImported(context: Context, font: AppFont): Boolean = font == AppFont.SYSTEM || fontFile(context, font).length() > 0

fun importFont(context: Context, font: AppFont, uri: Uri): Boolean =
    runCatching {
        val dest = fontFile(context, font)
        dest.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } } ?: return false
        // Validasi: berkas harus bisa dibaca sebagai font, kalau tidak dibuang.
        android.graphics.Typeface.Builder(dest).build() != null
    }.getOrDefault(false).also { ok -> if (!ok) fontFile(context, font).delete() }

fun appFontFamily(context: Context, font: AppFont, weight: Int): FontFamily {
    if (!isFontImported(context, font) || font == AppFont.SYSTEM) return FontFamily.SansSerif
    return runCatching { FontFamily(
            Font(
                file = fontFile(context, font),
                weight = FontWeight(weight.coerceIn(100, 900)),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.coerceIn(100, 900))),
            ),
        ) }
        .getOrDefault(FontFamily.SansSerif)
}

private fun TextStyle.applyFont(family: FontFamily, weightShift: Int, scale: Float): TextStyle =
    copy(
        fontFamily = family,
        fontWeight = FontWeight(((fontWeight?.weight ?: 400) + weightShift).coerceIn(100, 900)),
        fontSize = fontSize.scaled(scale),
        lineHeight = lineHeight.scaled(scale),
    )

private fun TextUnit.scaled(k: Float): TextUnit = if (isSpecified) (value * k).sp else this

/** Skala dihitung dari ukuran dasar 17 sp (body), sehingga hierarki tipografi tetap terjaga. */
fun Typography.withAppFont(family: FontFamily, weight: Int, sizeSp: Float): Typography {
    val shift = weight - FontDefaultWeight
    val k = sizeSp.coerceIn(FontMinSize, FontMaxSize) / FontBaseSize
    fun TextStyle.f() = applyFont(family, shift, k)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}
