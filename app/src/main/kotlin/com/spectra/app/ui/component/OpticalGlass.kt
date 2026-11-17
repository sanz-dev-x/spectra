@file:android.annotation.SuppressLint("NewApi")

package com.spectra.app.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.spectra.app.ui.utils.resize
import kotlin.math.roundToInt
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode

class GlassBackdropState(
    val layer: GraphicsLayer,
    val contentLayer: GraphicsLayer? = null,
) {
    var contentOrigin: Offset = Offset.Zero
    var contentReady: Boolean = false
    var origin: Offset = Offset.Zero
    var ready: Boolean = false
    var view: View? = null
    var artwork by mutableStateOf<ImageBitmap?>(null)
    var songColor by mutableStateOf(Color.Unspecified)
}

class SongGradient(val top: Color, val mid: Color, val bottom: Color)

fun songGradient(color: Color, dark: Boolean): SongGradient {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    val h = hsv[0]
    fun c(sat: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat.coerceIn(0f, 1f), v)))
    // Merah pekat menjadi merah pudar: saturasi dipangkas, kecerahan dinaikkan. Ujung gradasi tetap putih khas liquid glass.
    val soft = (hsv[1] * 0.42f).coerceIn(0.10f, 0.26f)
    return SongGradient(c(soft * 0.5f, 0.94f), c((soft * 1.2f).coerceAtMost(0.30f), 0.87f), c(soft * 0.5f, 0.95f))
}

val LocalGlassFrostBlur = staticCompositionLocalOf { 0.2.dp }

/** Frosted blur strength for navbar and mini player (0.1 to 2.0; 1.0 is about 12dp of blur). */
val LocalGlassNavBlur = staticCompositionLocalOf { 1f }

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }

val LocalGlassRefraction = staticCompositionLocalOf { 1f }

private const val REFRACT_AGSL =
    """
    uniform shader content;
    uniform float2 size;
    uniform float cr;
    uniform float depth;
    uniform float zoom;

    float sdf(float2 p) {
        float2 c = size * 0.5;
        float2 q = abs(p - c) - (c - float2(cr));
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cr;
    }

    half4 main(float2 p) {
        float2 z = size * 0.5 + (p - size * 0.5) / zoom;
        float sd = sdf(p);
        float d = -sd;
        if (d >= depth) {
            return content.eval(z);
        }
        float e = 1.0;
        float2 n = float2(sdf(p + float2(e, 0.0)) - sdf(p - float2(e, 0.0)),
                          sdf(p + float2(0.0, e)) - sdf(p - float2(0.0, e)));
        float len = length(n);
        n = len > 0.0001 ? n / len : float2(0.0, -1.0);
        float t = clamp(1.0 - d / depth, 0.0, 1.0);
        float bend = pow(t, 1.6) * depth * 1.15;
        float2 hi = size - float2(1.0);
        float2 ps = clamp(z - n * bend, float2(0.0), hi);
        float2 pr = clamp(z - n * bend * 1.14, float2(0.0), hi);
        float2 pb = clamp(z - n * bend * 0.86, float2(0.0), hi);
        half4 base = content.eval(ps);
        half r = content.eval(pr).r;
        half b = content.eval(pb).b;
        half3 col = half3(r, base.g, b);
        return half4(col, 1.0);
    }
    """

private class RefractionHolder {
    var layer: GraphicsLayer? = null
    var shader: RuntimeShader? = null
    var w = -1f
    var h = -1f
    var cr = -1f
    var depth = -1f
    var frost = -1f
    var zoom = -1f

    fun obtain(context: GraphicsContext): GraphicsLayer = layer ?: context.createGraphicsLayer().also { layer = it }

    fun configure(
        l: GraphicsLayer,
        size: Size,
        corner: Float,
        depthPx: Float,
        frostPx: Float,
        zoomAmount: Float,
    ) {
        if (shader == null) shader = runCatching { RuntimeShader(REFRACT_AGSL) }.getOrNull()
        val s = shader ?: return
        if (size.width == w && size.height == h && corner == cr && depthPx == depth && frostPx == frost && zoomAmount == zoom) return
        s.setFloatUniform("size", size.width, size.height)
        s.setFloatUniform("cr", corner)
        s.setFloatUniform("depth", depthPx)
        s.setFloatUniform("zoom", zoomAmount.coerceAtLeast(1f))
        w = size.width
        h = size.height
        cr = corner
        depth = depthPx
        frost = frostPx
        zoom = zoomAmount
        val refract = RenderEffect.createRuntimeShaderEffect(s, "content")
        l.renderEffect =
            if (frostPx > 0f) {
                val soften = RenderEffect.createBlurEffect(frostPx, frostPx, android.graphics.Shader.TileMode.CLAMP)
                RenderEffect.createChainEffect(refract, soften).asComposeRenderEffect()
            } else {
                refract.asComposeRenderEffect()
            }
    }

    fun release(context: GraphicsContext) {
        layer?.let { context.releaseGraphicsLayer(it) }
        layer = null
    }
}

@Composable
private fun GlassArtworkSource(
    state: GlassBackdropState,
    playerConnection: com.spectra.app.playback.PlayerConnection?,
) {
    val context = LocalContext.current
    val url = playerConnection?.let { it.mediaMetadata.collectAsStateWithLifecycle().value?.thumbnailUrl }
    LaunchedEffect(url) {
        if (url == null) {
            state.artwork = null
            state.songColor = Color.Unspecified
            return@LaunchedEffect
        }
        val request =
            ImageRequest
                .Builder(context)
                .data(url.resize(1080, 1080))
                .size(1080)
                .allowHardware(false)
                .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
        if (bitmap != null) {
            state.artwork = bitmap.asImageBitmap()
            val picked =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching {
                        val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                        val dominant = palette.dominantSwatch
                        val hsv = dominant?.hsl
                        val usable =
                            if (dominant != null && hsv != null && hsv[1] >= 0.12f) {
                                dominant
                            } else {
                                palette.vibrantSwatch ?: palette.mutedSwatch ?: dominant
                            }
                        usable?.rgb?.let { Color(it) }
                    }.getOrNull()
                }
            if (picked != null) state.songColor = picked
        }
    }
}

/** Merekam isi NavHost (tanpa navbar/mini player) agar bisa di-blur di belakang kaca. API 33+. */
@Composable
fun Modifier.glassContentCapture(): Modifier {
    val state = LocalGlassBackdrop.current ?: return this
    val content = state.contentLayer ?: return this
    if (Build.VERSION.SDK_INT < 33) return this
    if (LocalGlassMode.current == GlassMode.OFF) return this
    if (LocalGlassQuality.current == GlassQuality.LOW) return this
    return this
        .onGloballyPositioned { state.contentOrigin = it.positionInWindow() }
        .drawWithContent {
            content.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                this@drawWithContent.drawContent()
            }
            state.contentReady = true
            drawLayer(content)
        }
}

@Composable
fun GlassBackdropSource(
    mode: GlassMode,
    playerConnection: com.spectra.app.playback.PlayerConnection?,
) {
    val state = LocalGlassBackdrop.current
    val view = LocalView.current
    if (state != null && mode != GlassMode.OFF) GlassArtworkSource(state, playerConnection)
    if (state == null || mode == GlassMode.OFF || Build.VERSION.SDK_INT < 33) {
        LiquidBackdropLayer(mode)
        return
    }
    val ambientGlow = rememberAmbientGlow(mode)
    val song = state.songColor
    val gradient = if (song.isSpecified) songGradient(song, mode == GlassMode.DARK) else null
    val tween900 = androidx.compose.animation.core.tween<Color>(durationMillis = 900)
    val gTop = animateColorAsState(gradient?.top ?: Color.Transparent, tween900, label = "songTop")
    val gMid = animateColorAsState(gradient?.mid ?: Color.Transparent, tween900, label = "songMid")
    val gBottom = animateColorAsState(gradient?.bottom ?: Color.Transparent, tween900, label = "songBottom")
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    state.origin = it.positionInWindow()
                    state.view = view
                }.drawWithContent {
                    // Diagonal kanan atas -> kiri bawah: putih, warna artwork pudar di tengah, putih lagi; stop perantara menghaluskan transisi.
                    val songBrush =
                        Brush.linearGradient(
                            colorStops =
                                arrayOf(
                                    0f to gTop.value,
                                    0.22f to gTop.value,
                                    0.38f to androidx.compose.ui.graphics.lerp(gTop.value, gMid.value, 0.6f),
                                    0.5f to gMid.value,
                                    0.62f to androidx.compose.ui.graphics.lerp(gBottom.value, gMid.value, 0.6f),
                                    0.78f to gBottom.value,
                                    1f to gBottom.value,
                                ),
                            start = Offset(size.width, 0f),
                            end = Offset(0f, size.height),
                        )
                    state.layer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                        if (gradient != null) {
                            drawRect(songBrush)
                        } else {
                            this@drawWithContent.drawContent()
                        }
                    }
                    state.ready = true
                    drawLayer(state.layer)
                    if (gradient == null) {
                        drawRect(
                            Color(if (mode == GlassMode.DARK) 0xFF0E0C0B else 0xFFF3EEE8),
                            alpha = 0.9f,
                        )
                        drawAmbientGlow(ambientGlow)
                    }
                }.liquidBackdrop(mode),
    ) {
        LiquidBackdropLayer(mode)
    }
}

private fun DrawScope.drawCoverImage(image: ImageBitmap) {
    val dstW = size.width
    val dstH = size.height
    val scale = maxOf(dstW / image.width, dstH / image.height)
    val srcW = (dstW / scale).roundToInt().coerceIn(1, image.width)
    val srcH = (dstH / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

@Composable
internal fun Modifier.glassCoverBackground(image: ImageBitmap): Modifier {
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val quality = LocalGlassQuality.current
    val mode = LocalGlassMode.current
    val blurPx =
        if (Build.VERSION.SDK_INT >= 31 && quality != GlassQuality.LOW) with(density) { LocalGlassFrostBlur.current.toPx() } else 0f
    val scrim = if (mode == GlassMode.DARK) Color.Black.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.30f)
    return this.drawWithContent {
        layer.renderEffect =
            if (blurPx > 0f) {
                androidx.compose.ui.graphics.BlurEffect(blurPx, blurPx, androidx.compose.ui.graphics.TileMode.Clamp)
            } else {
                null
            }
        layer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
            drawCoverImage(image)
            drawRect(scrim)
        }
        drawLayer(layer)
        drawContent()
    }
}

@Composable
internal fun rememberOpticalBackdrop(): GlassBackdropState? {
    val state = LocalGlassBackdrop.current ?: return null
    if (Build.VERSION.SDK_INT < 33) return null
    if (LocalGlassRefraction.current <= 0f) return null
    if (LocalGlassQuality.current == GlassQuality.LOW) return null
    if (LocalGlassMode.current == GlassMode.OFF) return null
    if (state.view != null && state.view !== LocalView.current) return null
    return state
}

@Composable
internal fun Modifier.opticalRefraction(
    backdrop: GlassBackdropState,
    shape: Shape,
    strength: Float = 1f,
    blurred: Boolean = false,
    zoom: Float = 1f,
): Modifier {
    val context = LocalGraphicsContext.current
    // Frosted glass: when blurred, the shared backdrop is softened by the user's blur setting instead of swapping in artwork.
    val frostDp = glassBlur((LocalGlassNavBlur.current.coerceIn(0.1f, 2f) * 12f).dp)
    val holder = remember { RefractionHolder() }
    val scrim = if (LocalGlassMode.current == GlassMode.DARK) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.22f)
    var pos by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(holder) { onDispose { holder.release(context) } }
    return this
        .onGloballyPositioned { pos = it.positionInWindow() }
        .drawWithContent {
            val w = size.width
            val h = size.height
            val minDim = minOf(w, h)
            if (backdrop.ready && minDim >= 24.dp.toPx()) {
                val outline = shape.createOutline(size, layoutDirection, this)
                val corner =
                    when (outline) {
                        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                        else -> minDim / 2f
                    }.coerceIn(0f, minDim / 2f)
                val depthPx = (minOf(minDim * 0.34f, 26.dp.toPx()) * strength.coerceIn(0.3f, 1.5f)).coerceAtMost(minDim * 0.5f)
                val layer = holder.obtain(context)
                holder.configure(layer, size, corner, depthPx, if (blurred) frostDp.toPx() else 0f, zoom)
                layer.record(this, layoutDirection, IntSize(w.toInt(), h.toInt())) {
                    translate(backdrop.origin.x - pos.x, backdrop.origin.y - pos.y) {
                        drawLayer(backdrop.layer)
                    }
                    if (blurred) drawRect(scrim)
                }
                drawLayer(layer)
            }
            drawContent()
        }
}
