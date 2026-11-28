package com.spectra.app.ui.screens.wrapped.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalWrappedInk = compositionLocalOf { Color.White }
val LocalWrappedInkInverse = compositionLocalOf { Color.Black }

val WrappedInk: Color
    @Composable @ReadOnlyComposable get() = LocalWrappedInk.current

val WrappedInkInverse: Color
    @Composable @ReadOnlyComposable get() = LocalWrappedInkInverse.current

private class Palette(
    val base: List<Color>,
    val blobs: List<Color>,
    val ink: Color,
    val inkInverse: Color,
    val dark: Boolean,
)

private val DarkPalette =
    Palette(
        base = listOf(Color(0xFF15110F), Color(0xFF0C0A09)),
        blobs = listOf(Color(0xFF6B3F2A), Color(0xFF3B3A63), Color(0xFF2F5A57), Color(0xFF63343F)),
        ink = Color.White,
        inkInverse = Color(0xFF0C0A09),
        dark = true,
    )

private val LightPalette =
    Palette(
        base = listOf(Color(0xFFFBF8F6), Color(0xFFF1EBE8)),
        blobs = listOf(Color(0xFFF2B8B0), Color(0xFFB9C9F2), Color(0xFFBFE3D2), Color(0xFFF5DDA8)),
        ink = Color(0xFF1C1816),
        inkInverse = Color.White,
        dark = false,
    )

private const val TAU = (2.0 * PI).toFloat()

/**
 * Latar rekap yang mengikuti tema (hitam lembut / putih) dan berjalan terus.
 * Satu fase loop untuk semua elemen, tidak bergantung pada halaman pager, jadi animasi
 * tidak berhenti saat di-scroll. Fase hanya dibaca di blok gambar sehingga tidak memicu recomposition.
 */
@Composable
fun WrappedThemedBackground(content: @Composable () -> Unit) {
    val mode = LocalGlassMode.current
    val palette =
        when (mode) {
            GlassMode.DARK -> DarkPalette
            GlassMode.BRIGHT -> LightPalette
        }
    val transition = rememberInfiniteTransition(label = "wrapped_backdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart),
        label = "wrapped_phase",
    )

    CompositionLocalProvider(
        LocalWrappedInk provides palette.ink,
        LocalWrappedInkInverse provides palette.inkInverse,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(palette.base))
                val t = phase * TAU
                drawLiquidGlass(palette, t)
            }
            content()
        }
    }
}

private fun DrawScope.drawLiquidGlass(
    p: Palette,
    t: Float,
) {
    val w = size.width
    val h = size.height
    val m = minOf(w, h)
    val glow = if (p.dark) 0.34f else 0.55f
    for (i in p.blobs.indices) {
        val k = i + 1
        val cx = w * (0.5f + 0.38f * sin(t * k + i * 1.7f))
        val cy = h * (0.5f + 0.40f * cos(t * (k % 3 + 1) + i * 2.3f))
        val r = m * (0.75f + 0.12f * sin(t * 2f + i))
        drawCircle(
            brush = Brush.radialGradient(listOf(p.blobs[i].copy(alpha = glow), Color.Transparent), center = Offset(cx, cy), radius = r),
            radius = r,
            center = Offset(cx, cy),
        )
    }
    for (i in 0 until 7) {
        val cx = w * (0.5f + 0.42f * sin(t * (1 + i % 3) + i * 0.9f))
        val cy = h * (0.5f + 0.44f * cos(t * (1 + (i + 1) % 3) + i * 1.3f))
        drawGlassDroplet(Offset(cx, cy), m * (0.07f + 0.018f * i), p.dark, t + i)
    }
}

private fun DrawScope.drawGlassDroplet(
    c: Offset,
    r: Float,
    dark: Boolean,
    t: Float,
) {
    val rimA = if (dark) 0.55f else 0.95f
    drawCircle(
        brush =
            Brush.radialGradient(
                0.0f to Color.White.copy(alpha = if (dark) 0.03f else 0.10f),
                0.78f to Color.White.copy(alpha = if (dark) 0.07f else 0.22f),
                1.0f to Color.White.copy(alpha = if (dark) 0.26f else 0.55f),
                center = c,
                radius = r,
            ),
        radius = r,
        center = c,
    )
    drawCircle(
        brush =
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = rimA), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = rimA * 0.5f)),
                start = Offset(c.x - r, c.y - r),
                end = Offset(c.x + r, c.y + r),
            ),
        radius = r,
        center = c,
        style = Stroke(width = r * 0.045f + 1.2f),
    )
    rotate(degrees = 18f * sin(t), pivot = c) {
        drawArc(
            color = Color.White.copy(alpha = rimA),
            startAngle = 205f,
            sweepAngle = 48f,
            useCenter = false,
            topLeft = Offset(c.x - r * 0.82f, c.y - r * 0.82f),
            size = Size(r * 1.64f, r * 1.64f),
            style = Stroke(width = r * 0.07f + 1f),
        )
    }
}


