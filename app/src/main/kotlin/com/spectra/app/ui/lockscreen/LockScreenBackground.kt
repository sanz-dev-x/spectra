package com.spectra.app.ui.lockscreen

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.min

/**
 * Warna latar mode "teks di tengah layar".
 * [primary] adalah warna paling banyak di cover (dipakai di tengah layar), [secondary] warna kedua yang
 * berbeda jelas dari yang pertama (dipakai di bagian atas), [shade] versi lebih gelap dari [primary] untuk bagian bawah.
 * Semuanya sudah dilembutkan: warna asli (mis. putih) tidak dipakai mentah.
 */
class LockPalette(val primary: Color, val secondary: Color, val shade: Color)

internal val LockPaletteFallback = LockPalette(Color(0xFF3A3A42), Color(0xFF34343C), Color(0xFF1E1E24))

/** Ambil dua warna terbanyak dari cover lalu lembutkan. Mengembalikan null bila cover kosong. */
fun Bitmap.toLockPalette(): LockPalette? {
    val palette = Palette.from(this).maximumColorCount(24).clearFilters().generate()
    val ranked = palette.swatches.sortedByDescending { it.population }
    val first = ranked.firstOrNull() ?: return null
    val second = ranked.drop(1).firstOrNull { distinctColors(first.rgb, it.rgb) } ?: ranked.getOrNull(1) ?: first
    val primary = softLock(first.rgb)
    val secondary = softLock(second.rgb)
    return LockPalette(primary = primary, secondary = secondary, shade = darkenLock(lerp(primary, secondary, 0.5f)))
}

/** Dua warna dianggap berbeda bila hue-nya berjauhan, atau yang satu berwarna dan yang lain abu/putih/hitam. */
private fun distinctColors(a: Int, b: Int): Boolean {
    val ha = FloatArray(3).also { ColorUtils.colorToHSL(a, it) }
    val hb = FloatArray(3).also { ColorUtils.colorToHSL(b, it) }
    val colorA = ha[1] > 0.2f && ha[2] in 0.12f..0.88f
    val colorB = hb[1] > 0.2f && hb[2] in 0.12f..0.88f
    return when {
        colorA && colorB -> {
            val d = abs(ha[0] - hb[0])
            min(d, 360f - d) >= 40f
        }
        colorA != colorB -> true
        else -> abs(ha[2] - hb[2]) >= 0.3f
    }
}

/** Saturasi dipangkas dan kecerahan dipaksa ke pita 0.26-0.46: lembut, tidak pernah menyilaukan, teks putih tetap terbaca. */
private fun softLock(rgb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = (hsl[1] * 0.8f).coerceAtMost(0.5f)
    hsl[2] = 0.26f + 0.20f * hsl[2]
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Lebih gelap tapi tidak hitam. */
private fun darkenLock(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = (hsl[2] * 0.55f).coerceAtLeast(0.12f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Gradasi vertikal: warna kedua di 1/5 atas layar, warna utama lembut di tengah, lalu makin gelap ke bawah.
 */
@Composable
fun LockGradientBackground(palette: LockPalette?, modifier: Modifier = Modifier) {
    val p = palette ?: LockPaletteFallback
    val spec = tween<Color>(durationMillis = 700)
    val top by animateColorAsState(p.secondary, spec, label = "lockTop")
    val mid by animateColorAsState(p.primary, spec, label = "lockMid")
    val bottom by animateColorAsState(p.shade, spec, label = "lockBottom")
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to top,
                        0.20f to top,
                        0.50f to mid,
                        0.62f to mid,
                        1f to bottom,
                    ),
                startY = 0f,
                endY = size.height,
            ),
        )
    }
}
