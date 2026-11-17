package com.spectra.app.ui.component

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Highlight yang mengikuti jari: spring posisi (damping 0,5 / stiffness 300) dan pulse saat dilepas,
 * mengikuti resep InteractiveHighlight Auralys/Kyant. Hanya mengamati sentuhan (pass Initial, tidak mengonsumsi).
 */
@Stable
class GlassTouchHighlight(
    private val scope: CoroutineScope,
    private val strength: Float = 1f,
) {
    private val press = Animatable(0f, 0.001f)
    private val pos = Animatable(Offset.Zero, Offset.VectorConverter)
    val pressProgress: Float get() = press.value

    val observe: Modifier =
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { pos.snapTo(down.position) }
                scope.launch { press.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull { it.id == down.id }
                    if (c == null || c.changedToUpIgnoreConsumed()) break
                    scope.launch { pos.animateTo(c.position, spring(0.5f, 300f)) }
                }
                scope.launch { press.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
            }
        }

    val draw: Modifier =
        Modifier.drawWithContent {
            drawContent()
            val p = press.value
            if (p > 0.001f) {
                val o = pos.value
                val c = Offset(o.x.fastCoerceIn(0f, size.width), o.y.fastCoerceIn(0f, size.height))
                val r = size.minDimension * 1.5f
                drawRect(Color.White.copy(0.08f * p * strength), blendMode = BlendMode.Plus)
                drawRect(
                    Brush.radialGradient(
                        0f to Color.White.copy(0.15f * p * strength),
                        1f to Color.Transparent,
                        center = c,
                        radius = r,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        }
}

@Composable
fun rememberGlassTouchHighlight(scope: CoroutineScope, strength: Float = 1f): GlassTouchHighlight =
    remember(scope, strength) { GlassTouchHighlight(scope, strength) }

/**
 * Gerak squash-stretch ala DampedDragAnimation Auralys: scaleX (0,6 / 250), scaleY (0,7 / 250).
 * [press] 0..1 menentukan bentuk tekan; [velocityPx] menambah rentang saat bergerak cepat.
 */
@Stable
class DampedPillMotion {
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)

    suspend fun pressTo(pressed: Boolean, velocityPx: Float = 0f) {
        val stretch = (kotlin.math.abs(velocityPx) / 9000f).coerceAtMost(0.16f)
        val tx = if (pressed) 1.3f + stretch else 1f
        val ty = if (pressed) 1.14f - stretch * 0.5f else 1f
        kotlinx.coroutines.coroutineScope {
            launch { scaleX.animateTo(tx, spring(0.6f, 250f)) }
            launch { scaleY.animateTo(ty, spring(0.7f, 250f)) }
        }
    }
}

@Composable
fun rememberDampedPillMotion(): DampedPillMotion = remember { DampedPillMotion() }

/**
 * Efek gooey: blur lalu ambang alpha, sehingga bentuk solid yang berdekatan menyatu seperti cairan.
 * Isi grup harus berwarna solid (alpha 1); transparansi akhir diatur lewat [alpha].
 * Android 12+; di bawahnya tidak melakukan apa-apa.
 */
fun Modifier.gooey(blurPx: Float, alpha: () -> Float): Modifier =
    if (Build.VERSION.SDK_INT < 31) {
        this
    } else {
        this.graphicsLayer {
            val a = alpha()
            this.alpha = a
            renderEffect = if (a > 0.01f) gooeyEffect(blurPx) else null
        }
    }

private val gooeyCache = HashMap<Float, ComposeRenderEffect>()

private fun gooeyEffect(blurPx: Float): ComposeRenderEffect =
    gooeyCache.getOrPut(blurPx) {
        val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL)
        val threshold =
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 24f, -11f * 255f,
                ),
            )
        RenderEffect
            .createChainEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(threshold)), blur)
            .asComposeRenderEffect()
    }

private const val LENS_FX_AGSL =
    """
    uniform shader content;
    uniform float4 lens;
    uniform float radius;
    uniform float amount;
    float sdRoundBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }
    half4 main(float2 fc) {
        float2 p = fc - lens.xy;
        float d = sdRoundBox(p, lens.zw, radius);
        if (d > 0.0) return content.eval(fc);
        float w = 0.50 * min(lens.z, lens.w);
        float t = clamp(-d / w, 0.0, 1.0);
        float bend = pow(1.0 - t, 2.0);
        float e = 1.0;
        float2 n = normalize(float2(
            sdRoundBox(p + float2(e, 0.0), lens.zw, radius) - sdRoundBox(p - float2(e, 0.0), lens.zw, radius),
            sdRoundBox(p + float2(0.0, e), lens.zw, radius) - sdRoundBox(p - float2(0.0, e), lens.zw, radius)
        ) + float2(0.0001));
        float zoom = 1.0 + 0.32 * amount;
        float2 base = lens.xy + p / zoom;
        float2 s = base + n * (bend * w * 3.4 * amount);
        float disp = bend * 9.0 * amount;
        half4 cr = content.eval(s + n * disp);
        half4 cg = content.eval(s);
        half4 cb = content.eval(s - n * disp);
        return half4(cr.r, cg.g, cb.b, max(cr.a, max(cg.a, cb.a)));
    }
    """

@androidx.annotation.RequiresApi(33)
private class LensFxHolder {
    val shader: android.graphics.RuntimeShader? = runCatching { android.graphics.RuntimeShader(LENS_FX_AGSL) }.getOrNull()
}

/**
 * Lensa Auralys (LensFx): isi di bawah pill diperbesar, tepinya dibengkokkan dengan dispersi RGB.
 * Kotak lensa: pusat ([centerX], tengah vertikal), setengah lebar [halfWidth], setengah tinggi [halfHeight].
 * Android 13+ dan kualitas kaca bukan LOW; selain itu tidak berefek.
 */
@Composable
fun Modifier.lensFx(
    centerX: () -> Float,
    halfWidth: () -> Float,
    halfHeight: (Float) -> Float,
    amount: () -> Float,
): Modifier {
    if (Build.VERSION.SDK_INT < 33 || LocalGlassQuality.current == GlassQuality.LOW) return this
    val holder = remember { LensFxHolder() }
    return this.graphicsLayer {
        val sh = holder.shader
        val a = amount()
        val hw = if (sh != null && a > 0.01f) halfWidth() else 0f
        val hh = if (sh != null && a > 0.01f) halfHeight(size.height) else 0f
        // Ukuran lensa belum terukur (0 atau negatif) membuat normalize/sdf di shader menghasilkan NaN; lewati sampai valid.
        if (sh != null && a > 0.01f && hw > 1f && hh > 1f) {
            sh.setFloatUniform("lens", centerX(), size.height / 2f, hw, hh)
            sh.setFloatUniform("radius", minOf(hw, hh))
            sh.setFloatUniform("amount", a.coerceIn(0f, 1f))
            renderEffect = RenderEffect.createRuntimeShaderEffect(sh, "content").asComposeRenderEffect()
        } else {
            renderEffect = null
        }
    }
}
