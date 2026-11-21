@file:android.annotation.SuppressLint("NewApi")

package com.spectra.app.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Distorsi cair lokal di sekitar titik sentuh. false mematikan seluruh efek tanpa menyentuh bagian kaca lain. */
const val GlassTouchLiquidEnabled = true

object GlassTouchLiquidSpec {
    const val PEAK_DP = 3.2f
    const val RADIUS_DP = 40f
    const val MIN_ENERGY = 0.015f

    /** Terang: putih lembut tipis. Gelap: glossy, sedikit lebih kuat agar terbaca di atas latar gelap. */
    val specK = GlassValue(0.26f, 0.34f)
}

/**
 * Posisi sentuh (px, relatif ke permukaan) dan energi 0..1 per permukaan. Energi naik cepat saat jari turun
 * dan mereda dengan spring halus saat dilepas; setelah diam, nilainya 0 dan efek tidak digambar sama sekali.
 */
@Stable
internal class GlassTouchLiquidState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    val energy = Animatable(0f)
}

/** Mengamati sentuhan tanpa mengonsumsi event, jadi clickable/drag di bawahnya tetap bekerja. */
internal fun Modifier.glassTouchLiquidTrack(state: GlassTouchLiquidState): Modifier =
    pointerInput(state) {
        coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.x = down.position.x
                state.y = down.position.y
                scope.launch { state.energy.animateTo(1f, GlassMotion.press) }
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Final)
                    ev.changes.firstOrNull { it.pressed }?.let {
                        state.x = it.position.x
                        state.y = it.position.y
                    }
                } while (ev.changes.any { it.pressed })
                scope.launch { state.energy.animateTo(0f, GlassMotion.settle) }
            }
        }
    }

/**
 * Menerima konten latar (hasil blur backdrop) lalu menggesernya secara radial di sekitar titik sentuh seperti
 * tetesan lensa cembung kecil: nol di pusat dan di batas radius, maksimum di sekitar 0.45 radius. Dari profil
 * tinggi yang sama dihitung normal permukaan untuk specular tipis (dengan sedikit dispersi), jadi pantulan
 * mengikuti bentuk distorsi dan arah cahaya, bukan pola tempelan. Di luar radius piksel dikembalikan apa adanya.
 */
private const val TOUCH_LIQUID_AGSL =
    """
    uniform shader content;
    uniform float2 size;
    uniform float2 touch;
    uniform float radius;
    uniform float peak;
    uniform float energy;
    uniform float2 lightDir;
    uniform float specK;

    half4 main(float2 p) {
        float2 d = p - touch;
        float r = length(d);
        if (r >= radius) {
            return content.eval(p);
        }
        float k = r / radius;
        float k2 = k * k;
        float fall = (1.0 - k2) * (1.0 - k2);
        float2 dir = r > 1e-3 ? d / r : float2(0.0, 0.0);

        float shift = peak * energy * 3.5 * k * fall;
        float2 hi = size - float2(1.0);
        float2 ps = clamp(p - dir * shift, float2(0.0), hi);
        float2 pr = clamp(p - dir * shift * 1.12, float2(0.0), hi);
        float2 pb = clamp(p - dir * shift * 0.88, float2(0.0), hi);
        half4 base = content.eval(ps);
        half3 col = half3(content.eval(pr).r, base.g, content.eval(pb).b);

        float slope = 4.0 * k * (1.0 - k2) * 0.9 * energy;
        float3 N = normalize(float3(dir * slope, 1.0));
        float3 H = normalize(normalize(float3(lightDir, 0.55)) + float3(0.0, 0.0, 1.0));
        float spec = pow(max(dot(N, H), 0.0), 38.0) * specK * energy;
        float3 tone = mix(float3(col), float3(1.0), 0.55);
        float3 outc = float3(col) + tone * spec;
        return half4(half3(clamp(outc, 0.0, 1.0)), base.a);
    }
    """

private fun q(v: Float, steps: Float): Float = (v * steps).roundToInt() / steps

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassTouchLiquid {
    private val shader: RuntimeShader? = runCatching { RuntimeShader(TOUCH_LIQUID_AGSL) }.getOrNull()
    private var cached: ComposeRenderEffect? = null
    private val last = FloatArray(10) { Float.NaN }
    private val next = FloatArray(10)

    /** Mengembalikan null saat energi hampir nol, sehingga pemanggil tidak menambah efek apa pun ke rantai backdrop. */
    fun effect(
        w: Float,
        h: Float,
        touchX: Float,
        touchY: Float,
        radius: Float,
        peak: Float,
        energy: Float,
        lightX: Float,
        lightY: Float,
        specK: Float,
    ): ComposeRenderEffect? {
        val s = shader ?: return null
        if (w <= 1f || h <= 1f || radius <= 1f || energy < GlassTouchLiquidSpec.MIN_ENERGY) return null
        next[0] = w
        next[1] = h
        next[2] = touchX.coerceIn(0f, w).roundToInt().toFloat()
        next[3] = touchY.coerceIn(0f, h).roundToInt().toFloat()
        next[4] = radius
        next[5] = peak
        next[6] = q(energy.coerceIn(0f, 1.2f), 100f)
        next[7] = q(lightX, 50f)
        next[8] = q(lightY, 50f)
        next[9] = specK
        val prev = cached
        if (prev != null && next.contentEquals(last)) return prev
        s.setFloatUniform("size", next[0], next[1])
        s.setFloatUniform("touch", next[2], next[3])
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("peak", peak)
        s.setFloatUniform("energy", next[6])
        s.setFloatUniform("lightDir", next[7], next[8])
        s.setFloatUniform("specK", specK)
        val fx = RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
        cached = fx
        next.copyInto(last)
        return fx
    }
}
