@file:android.annotation.SuppressLint("NewApi")

package com.spectra.app.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.android.awaitFrame

/** Efek air halus khusus mini player. Hanya menggeser sampel latar (setelah blur), bukan kontrol di atasnya. */
object GlassWater {
    const val ENABLED = true
    const val AMPLITUDE_DP = 1.6f
    const val STEP_MS = 50L
    const val PERIOD_S = 14f
}

private const val WATER_RIPPLE_AGSL =
    """
    uniform shader content;
    uniform float2 size;
    uniform float t;
    uniform float amp;

    half4 main(float2 p) {
        float2 uv = p / size;
        float a = sin(uv.x * 9.0 + t) * 0.6 + sin(uv.x * 17.0 - t * 1.3 + uv.y * 6.0) * 0.4;
        float b = cos(uv.y * 11.0 + t * 0.9) * 0.6 + cos(uv.x * 7.0 + uv.y * 13.0 - t * 1.1) * 0.4;
        float edge = min(min(uv.x, 1.0 - uv.x) * size.x, min(uv.y, 1.0 - uv.y) * size.y);
        float fade = clamp(edge / 8.0, 0.0, 1.0);
        float2 q = clamp(p + float2(a, b) * amp * fade, float2(0.0), size - float2(1.0));
        return content.eval(q);
    }
    """

/** Satu shader per pemakai. Efek dibuat ulang hanya saat waktu (dikuantisasi) atau ukuran berubah. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GlassWaterRipple {
    private val shader: RuntimeShader? = runCatching { RuntimeShader(WATER_RIPPLE_AGSL) }.getOrNull()
    private var lastT = Float.NaN
    private var lastW = -1f
    private var lastH = -1f
    private var cached: ComposeRenderEffect? = null

    fun effect(w: Float, h: Float, t: Float, amp: Float): ComposeRenderEffect? {
        val s = shader ?: return null
        if (w <= 1f || h <= 1f) return null
        val prev = cached
        if (prev != null && t == lastT && w == lastW && h == lastH) return prev
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("t", t)
        s.setFloatUniform("amp", amp)
        val fx = RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
        cached = fx
        lastT = t; lastW = w; lastH = h
        return fx
    }
}

/**
 * Waktu ripple dalam radian, maju ~20 fps hanya saat [active], aplikasi RESUMED, dan layar tampil.
 * Saat tidak aktif nilai membeku, jadi tidak ada frame tambahan dan tidak ada biaya baterai.
 */
@Composable
fun rememberGlassWaterClock(active: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner) {
        if (!active) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = awaitFrame()
            var acc = 0L
            while (true) {
                val now = awaitFrame()
                acc += (now - last) / 1_000_000L
                last = now
                if (acc >= GlassWater.STEP_MS) {
                    val step = (acc / GlassWater.STEP_MS) * GlassWater.STEP_MS
                    acc -= step
                    val next = time.floatValue + (step / 1000f) * (6.2831855f / GlassWater.PERIOD_S)
                    time.floatValue = if (next > 6.2831855f * 64f) next - 6.2831855f * 64f else next
                }
            }
        }
    }
    return time
}
