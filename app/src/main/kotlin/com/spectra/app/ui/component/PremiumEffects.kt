package com.spectra.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.spectra.app.constants.FxEdgeGlowKey
import com.spectra.app.constants.FxGrainKey
import com.spectra.app.constants.FxSpotlightKey
import com.spectra.app.constants.FxVignetteKey
import com.spectra.app.ui.theme.LocalGlassAccent
import com.spectra.app.utils.rememberPreference
import kotlin.math.hypot
import kotlin.random.Random

/** Posisi sentuhan terakhir; hanya dibaca saat menggambar sehingga tidak memicu recomposition. */
@Stable
class SpotlightState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var down by mutableStateOf(false)
}

/** Mengamati sentuhan tanpa mengonsumsi event, jadi semua gestur di bawahnya tetap bekerja. */
fun Modifier.observeSpotlight(state: SpotlightState): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val p = event.changes.firstOrNull { it.pressed }
                if (p != null) {
                    state.x = p.position.x
                    state.y = p.position.y
                    state.down = true
                } else {
                    state.down = false
                }
            }
        }
    }

private fun noiseTile(): androidx.compose.ui.graphics.ImageBitmap {
    val size = 128
    val rnd = Random(7)
    val px = IntArray(size * size) {
        val v = rnd.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Lapisan efek premium di atas seluruh aplikasi: grain film, vignette sinematik, cahaya tepi, dan cahaya sentuhan.
 * Murni gambar, tidak menerima sentuhan. Semua bernilai 0 (mati) secara bawaan.
 */
@Composable
fun PremiumEffectsOverlay(spot: SpotlightState) {
    val (grain) = rememberPreference(FxGrainKey, defaultValue = 0f)
    val (vignette) = rememberPreference(FxVignetteKey, defaultValue = 0f)
    val (edge) = rememberPreference(FxEdgeGlowKey, defaultValue = 0f)
    val (spotlight) = rememberPreference(FxSpotlightKey, defaultValue = 0f)
    if (grain <= 0f && vignette <= 0f && edge <= 0f && spotlight <= 0f) return

    val accent = LocalGlassAccent.current
    val tile = remember { if (grain > 0f) noiseTile() else null }
    val brush = remember(tile) { tile?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) } }
    val pulse: State<Float>? =
        if (edge > 0f) {
            rememberInfiniteTransition(label = "edgeGlow").animateFloat(
                initialValue = 0.78f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "edgePulse",
            )
        } else {
            null
        }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(spotlight) {
        if (spotlight <= 0f) return@LaunchedEffect
        snapshotFlow { spot.down }.collect { down ->
            fade.animateTo(if (down) 1f else 0f, tween(if (down) 140 else 520, easing = LinearEasing))
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val reach = hypot(w, h) / 2f
        if (vignette > 0f) {
            drawRect(
                Brush.radialGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.62f * vignette.coerceIn(0f, 1f)),
                    center = Offset(w / 2f, h / 2f),
                    radius = reach,
                ),
            )
        }
        if (edge > 0f) {
            val a = 0.50f * edge.coerceIn(0f, 1f) * (pulse?.value ?: 1f)
            drawRect(
                Brush.radialGradient(
                    0.62f to Color.Transparent,
                    1f to accent.copy(alpha = a),
                    center = Offset(w / 2f, h / 2f),
                    radius = reach,
                ),
            )
        }
        if (spotlight > 0f && fade.value > 0.001f) {
            val a = 0.30f * spotlight.coerceIn(0f, 1f) * fade.value
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = a),
                    1f to Color.Transparent,
                    center = Offset(spot.x, spot.y),
                    radius = minOf(w, h) * 0.55f,
                ),
            )
        }
        if (grain > 0f && brush != null) {
            drawRect(brush, alpha = 0.14f * grain.coerceIn(0f, 1f))
        }
    }
}
