package com.spectra.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

/** Benih default Liquid Glass bila belum ada lagu: hijau matcha (kuning-kehijauan, dilunakkan oleh softPalette). */
val MatchaSeed = Color(0xFF85B350)

private val WarmCream = Color(0xFFF8F5EC)
private val WarmWhite = Color(0xFFFFFDF8)

/**
 * Palet lembut turunan satu warna benih. Warna mentah tidak pernah dipakai langsung: hanya hue yang
 * dipertahankan, saturasi dan kecerahan dipaksa masuk pita pastel. Cover abu-abu atau hitam
 * menghasilkan palet netral hangat. Latar terang selalu krem/putih hangat dengan sapuan tipis hue.
 */
class SoftPalette(
    val accent: Color,
    val bg: Color,
    val card: Color,
    val ink: Color,
    val baseTop: Color,
    val baseMid: Color,
    val baseBottom: Color,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
)

private fun hsv(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

fun softPalette(seed: Color, dark: Boolean): SoftPalette {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val h = hsv[0]
    // Desaturasi + lightening: saturasi dipangkas dan dibatasi agar merah pekat menjadi merah muda lembut.
    val k = (((hsv[1] * 0.55f).coerceAtMost(0.34f) - 0.06f) / 0.32f).coerceIn(0f, 1f)
    return if (dark) {
        SoftPalette(
            accent = hsv(h, 0.08f + 0.28f * k, 0.80f),
            bg = hsv(h, 0.05f + 0.09f * k, 0.09f),
            card = hsv(h, 0.05f + 0.09f * k, 0.15f),
            ink = hsv(h, 0.04f, 0.95f),
            baseTop = hsv(h, 0.08f + 0.16f * k, 0.22f),
            baseMid = hsv(h, 0.06f + 0.12f * k, 0.12f),
            baseBottom = hsv(h, 0.05f + 0.10f * k, 0.07f),
            glowA = hsv(h, 0.10f + 0.22f * k, 0.64f),
            glowB = hsv(h + 12f, 0.10f + 0.20f * k, 0.60f),
            glowC = hsv(h + 35f, 0.10f + 0.18f * k, 0.56f),
        )
    } else {
        val wash = hsv(h, 0.06f + 0.24f * k, 0.96f)
        SoftPalette(
            accent = hsv(h, 0.10f + 0.30f * k, 0.52f),
            bg = lerp(WarmCream, wash, 0.30f),
            card = lerp(WarmWhite, wash, 0.04f),
            ink = hsv(h, 0.06f + 0.08f * k, 0.17f),
            baseTop = lerp(WarmCream, wash, 0.65f),
            baseMid = lerp(WarmCream, wash, 0.32f),
            baseBottom = lerp(WarmWhite, wash, 0.12f),
            glowA = hsv(h, 0.08f + 0.24f * k, 0.90f),
            glowB = hsv(h + 12f, 0.08f + 0.22f * k, 0.92f),
            glowC = hsv(h + 35f, 0.08f + 0.20f * k, 0.94f),
        )
    }
}

fun SoftPalette.neutralSurface(dark: Boolean): SoftPalette =
    if (dark) {
        SoftPalette(accent, Color.Black, Color(0xFF141416), Color(0xFFF5F5F7), Color.Black, Color.Black, Color.Black, glowA, glowB, glowC)
    } else {
        SoftPalette(accent, Color(0xFFF5F6F1), Color.White, Color(0xFF1C1C1E), Color(0xFFF5F6F1), Color(0xFFF5F6F1), Color(0xFFF5F6F1), glowA, glowB, glowC)
    }

/**
 * Salinan palet dengan warna permukaan (accent, bg, card, ink) yang bertransisi halus saat lagu
 * berganti. Warna backdrop tidak ikut dianimasikan di sini karena rememberKyantBackdrop sudah
 * menganimasikannya di fase gambar, tanpa rekomposisi.
 */
@Composable
fun SoftPalette.animatedSurface(): SoftPalette {
    val spec = tween<Color>(durationMillis = 800, easing = FastOutSlowInEasing)
    val a by animateColorAsState(accent, spec, label = "softAccent")
    val b by animateColorAsState(bg, spec, label = "softBg")
    val c by animateColorAsState(card, spec, label = "softCard")
    val i by animateColorAsState(ink, spec, label = "softInk")
    return SoftPalette(a, b, c, i, baseTop, baseMid, baseBottom, glowA, glowB, glowC)
}

fun AppStyle.tinted(p: SoftPalette): AppStyle =
    AppStyle(
        id = id,
        label = label,
        note = note,
        ios = ios,
        overrides = overrides,
        glass = glass,
        accentLight = p.accent,
        accentDark = p.accent,
        bgLight = p.bg,
        cardLight = p.card,
        inkLight = p.ink,
        bgDark = p.bg,
        cardDark = p.card,
        inkDark = p.ink,
    )

/** Warna dominan menurut populasi piksel; softPalette yang melembutkannya menjadi pastel. */
fun Bitmap.extractDominantColor(fallback: Color): Color {
    val palette = Palette.from(this).maximumColorCount(16).generate()
    val swatch = palette.swatches.maxByOrNull { it.population } ?: return fallback
    return Color(swatch.rgb)
}

/** Desaturasi dan lightening: warna pekat/gelap menjadi pastel lembut dengan hue yang sama. */
fun Color.toSoftPastel(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return hsv(hsv[0], (hsv[1] * 0.45f).coerceIn(0.08f, 0.32f), (0.62f + hsv[2] * 0.30f).coerceIn(0.72f, 0.92f))
}
