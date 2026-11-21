/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Posisi tiga blok teks/kontrol (koordinat window) dan ukuran latar; hanya dibaca pada fase draw. */
class PlayerScrimState {
    var title by mutableStateOf(Rect.Zero)
    var progress by mutableStateOf(Rect.Zero)
    var controls by mutableStateOf(Rect.Zero)
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
}

private const val GRID = 64
private const val STEP = 0.05f
private const val MAX_STEPS = 11 // 0,55
private const val MIN_RATIO = 4.5f
private const val CACHE_SIZE = 64

private val LINEAR = FloatArray(256) { (it / 255f).toDouble().pow(2.2).toFloat() }

/** LRU 64 entri: peta luminance linear 64x64 per lagu. */
private object ScrimCache {
    private val lru =
        object : LinkedHashMap<String, FloatArray>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean = size > CACHE_SIZE
        }

    @Synchronized fun get(key: String): FloatArray? = lru[key]

    @Synchronized fun put(
        key: String,
        value: FloatArray,
    ) {
        lru[key] = value
    }
}

private suspend fun loadLuma(
    context: Context,
    url: String,
): FloatArray? {
    val request = ImageRequest.Builder(context).data(url).size(GRID).allowHardware(false).build()
    val source = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
    val side = minOf(source.width, source.height)
    if (side <= 0) return null
    val square =
        if (source.width == source.height) source else Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val scaled = if (square.width == GRID) square else Bitmap.createScaledBitmap(square, GRID, GRID, true)
    val pixels = IntArray(GRID * GRID)
    scaled.getPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
    if (scaled !== square) scaled.recycle()
    if (square !== source) square.recycle()
    return FloatArray(pixels.size) {
        val p = pixels[it]
        0.2126f * LINEAR[(p shr 16) and 0xFF] + 0.7152f * LINEAR[(p shr 8) and 0xFF] + 0.0722f * LINEAR[p and 0xFF]
    }
}

/** Pecahan tinggi latar (0..1), dikuantisasi 0,01; t1 <= t0 berarti pita tidak ada. */
private data class Fracs(
    val w: Float,
    val h: Float,
    val t0: Float,
    val t1: Float,
    val p0: Float,
    val p1: Float,
    val c0: Float,
    val c1: Float,
)

private fun q(v: Float) = (v * 100f).roundToInt() / 100f

private fun fracs(state: PlayerScrimState): Fracs? {
    val w = state.size.width.toFloat()
    val h = state.size.height.toFloat()
    if (w <= 0f || h <= 0f) return null
    val oy = state.origin.y
    fun top(r: Rect) = if (r.height > 0f) q((r.top - oy) / h) else 0f
    fun bottom(r: Rect) = if (r.height > 0f) q((r.bottom - oy) / h) else 0f
    return Fracs(w, h, top(state.title), bottom(state.title), top(state.progress), bottom(state.progress), top(state.controls), bottom(state.controls))
}

/** Langkah alpha (0..11, kelipatan 0,05) agar teks dengan luminance [textLum] mencapai 4,5:1 di atas P75 luminance pita. */
private fun stepsFor(
    luma: FloatArray,
    f: Fracs,
    f0: Float,
    f1: Float,
    textLum: Float,
): Int {
    if (f1 <= f0) return 0
    val m = max(f.w, f.h)
    val visRows = GRID * f.h / m
    val visCols = GRID * f.w / m
    val rowStart = (GRID - visRows) / 2f
    val colStart = (GRID - visCols) / 2f
    val r0 = (rowStart + f0.coerceIn(0f, 1f) * visRows).toInt().coerceIn(0, GRID - 1)
    val r1 = ceil(rowStart + f1.coerceIn(0f, 1f) * visRows).toInt().coerceIn(r0 + 1, GRID)
    val c0 = colStart.toInt().coerceIn(0, GRID - 1)
    val c1 = ceil(colStart + visCols).toInt().coerceIn(c0 + 1, GRID)
    val buf = FloatArray((r1 - r0) * (c1 - c0))
    var i = 0
    for (r in r0 until r1) for (c in c0 until c1) buf[i++] = luma[r * GRID + c]
    buf.sort()
    val p75 = buf[((buf.size - 1) * 0.75f).toInt()]
    val required = max((textLum + 0.05f) / MIN_RATIO - 0.05f, 0.001f)
    if (p75 <= required) return 0
    // Overlay hitam dicampur di ruang sRGB: luminance turun kira-kira (1 - a)^2,2.
    val a = 1f - (required / p75).pow(1f / 2.2f)
    return ceil(a / STEP - 0.001f).toInt().coerceIn(0, MAX_STEPS)
}

private fun DrawScope.drawBand(
    rect: Rect,
    origin: Offset,
    alpha: Float,
) {
    if (alpha <= 0f || rect.height <= 0f) return
    val pad = 28.dp.toPx()
    val y0 = rect.top - origin.y - pad
    val h = rect.height + 2f * pad
    val edge = pad / h
    val c = Color.Black.copy(alpha = alpha)
    drawRect(
        brush = Brush.verticalGradient(0f to Color.Transparent, edge to c, 1f - edge to c, 1f to Color.Transparent, startY = y0, endY = y0 + h),
        topLeft = Offset(0f, y0),
        size = Size(size.width, h),
    )
}

/**
 * Scrim adaptif untuk latar artwork nyata (BLUR/GRADIENT): gradient hitam lokal di belakang judul/artis, progress,
 * dan kontrol. Alpha dihitung dari P75 luminance bitmap 64x64 per pita, dianimasikan 350 ms, dan dibaca di fase draw.
 */
@Composable
fun PlayerAdaptiveScrim(
    state: PlayerScrimState,
    songId: String?,
    thumbnailUrl: String?,
    textColor: Color,
    alphaScale: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textLum = remember(textColor) { textColor.luminance() }
    var luma by remember { mutableStateOf<FloatArray?>(null) }
    val key = thumbnailUrl?.let { "${songId.orEmpty()}|$it" }
    val aTitle = remember { Animatable(0f) }
    val aProgress = remember { Animatable(0f) }
    val aControls = remember { Animatable(0f) }

    // Debounce 120 ms: pergantian lagu cepat membatalkan efek sebelumnya sebelum decode.
    LaunchedEffect(key) {
        if (key == null || thumbnailUrl == null) {
            luma = null
            return@LaunchedEffect
        }
        delay(120)
        luma =
            ScrimCache.get(key)
                ?: withContext(Dispatchers.Default) { runCatching { loadLuma(context, thumbnailUrl) }.getOrNull() }
                    ?.also { ScrimCache.put(key, it) }
    }

    LaunchedEffect(luma, textLum) {
        val map = luma
        val spec = tween<Float>(durationMillis = 350)
        if (map == null) {
            // Analisis gagal atau belum ada: alpha 0.
            coroutineScope {
                launch { aTitle.animateTo(0f, spec) }
                launch { aProgress.animateTo(0f, spec) }
                launch { aControls.animateTo(0f, spec) }
            }
            return@LaunchedEffect
        }
        snapshotFlow { fracs(state) }
            .distinctUntilChanged()
            .collectLatest { f ->
                val steps =
                    if (f == null) {
                        Triple(0, 0, 0)
                    } else {
                        withContext(Dispatchers.Default) {
                            Triple(
                                stepsFor(map, f, f.t0, f.t1, textLum),
                                stepsFor(map, f, f.p0, f.p1, textLum),
                                stepsFor(map, f, f.c0, f.c1, textLum),
                            )
                        }
                    }
                coroutineScope {
                    launch { aTitle.animateTo(steps.first * STEP, spec) }
                    launch { aProgress.animateTo(steps.second * STEP, spec) }
                    launch { aControls.animateTo(steps.third * STEP, spec) }
                }
            }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                state.origin = it.positionInWindow()
                state.size = it.size
            }.drawBehind {
                val s = alphaScale.coerceIn(0f, 1f)
                drawBand(state.title, state.origin, aTitle.value * s)
                drawBand(state.progress, state.origin, aProgress.value * s)
                drawBand(state.controls, state.origin, aControls.value * s)
            },
    )
}
