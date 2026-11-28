package com.spectra.app.ui.glass

import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode

/** Saklar cepat: false mengembalikan semua efek energi ke perilaku sebelumnya. */
const val GlassEnergyEnabled = true

/** Energi interaksi 0..1: 0 idle, 1 ditekan. Spring berhenti saat diam, jadi idle tidak menggambar ulang. */
@Composable
fun animateGlassEnergy(target: Float): State<Float> {
    val t = if (GlassEnergyEnabled) target.coerceIn(0f, 1f) else 0f
    return animateFloatAsState(
        targetValue = t,
        animationSpec = if (t > 0.5f) GlassMotion.press else GlassMotion.release,
        label = "glassEnergy",
    )
}

/** Specular tepi tambahan yang tumbuh dengan energi. Terang/Gelap: rim putih/ink; Kertas: overlay tipis. */
@Composable
fun Modifier.glassEnergyRim(
    shape: Shape,
    energy: () -> Float,
): Modifier {
    if (!GlassEnergyEnabled) return this
    val mode = LocalGlassMode.current
    val ink = MaterialTheme.colorScheme.onSurface
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val dark =
        when (mode) {
            GlassMode.DARK -> true
            GlassMode.BRIGHT -> false
        }
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(1.dp.toPx())
        val rim =
            if (dark) {
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.60f), 0.5f to Color.Transparent, 1f to Color.White.copy(alpha = 0.25f))
            } else {
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.95f), 0.5f to Color.Transparent, 1f to ink.copy(alpha = 0.14f))
            }
        onDrawWithContent {
            drawContent()
            val e = energy()
            if (e > 0.001f) {
                drawOutline(outline, rim, alpha = e.coerceAtMost(1f), style = stroke)
            }
        }
    }
}

/** Mengamati tekan tanpa mengonsumsi event, jadi clickable di bawahnya tetap bekerja. */
fun Modifier.glassPressObserver(onDown: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onDown(true)
            do {
                val ev = awaitPointerEvent(PointerEventPass.Final)
            } while (ev.changes.any { it.pressed })
            onDown(false)
        }
    }

/** Progres expand sheet player (0 = mini, 1 = penuh). Dipakai mini player untuk menaikkan refraksi dan bayangan. */
val LocalGlassExpand = staticCompositionLocalOf<() -> Float> { { 0f } }

/** Posisi sentuh terakhir (-1..1 relatif ke elemen) sebagai arah cahaya specular. Pengaruhnya diskalakan energi, jadi hilang halus saat lepas. */
object GlassTouch {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set

    internal fun set(nx: Float, ny: Float) {
        x = nx.coerceIn(-1f, 1f)
        y = ny.coerceIn(-1f, 1f)
    }
}

/** Mengamati posisi sentuh tanpa mengonsumsi event. */
fun Modifier.glassTouchLight(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val w = size.width.coerceAtLeast(1)
            val h = size.height.coerceAtLeast(1)
            GlassTouch.set(down.position.x / w * 2f - 1f, down.position.y / h * 2f - 1f)
            do {
                val ev = awaitPointerEvent(PointerEventPass.Final)
                ev.changes.firstOrNull { it.pressed }?.let { GlassTouch.set(it.position.x / w * 2f - 1f, it.position.y / h * 2f - 1f) }
            } while (ev.changes.any { it.pressed })
        }
    }
