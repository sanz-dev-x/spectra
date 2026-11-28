package com.spectra.app.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spectra.app.ui.component.ChromeOrange
import com.spectra.app.ui.component.GlassQuality
import com.spectra.app.ui.component.GlassTilt
import com.spectra.app.ui.component.LENS_SDF_AGSL
import com.spectra.app.ui.component.LocalAmbientGlow
import com.spectra.app.ui.component.LocalGlassQuality
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode
import kotlin.math.roundToInt

/** Saklar: false mematikan refleksi dan specular HD (kembali ke energi tahap sebelumnya). */
const val GlassHdEnabled = true

private const val EDGE_COMMON =
    """
    uniform float2 size;
    uniform float cr;
    uniform float bezel;
    uniform float specW;
    uniform float2 lightDir;
    uniform float3 env;
    uniform float envK;
    uniform float specK;
    uniform float specExp;

    float edgeFresnel(float t) {
        float cosV = 1.0 - 0.9 * pow(t, 1.5);
        return 0.04 + 0.96 * pow(1.0 - cosV, 5.0);
    }

    float edgeSpec(float2 n, float t, float d) {
        float edge = 1.0 - smoothstep(0.0, specW, d);
        float3 N = normalize(float3(n * t, 0.6));
        float3 H1 = normalize(normalize(float3(lightDir, 0.55)) + float3(0.0, 0.0, 1.0));
        float3 H2 = normalize(normalize(float3(-lightDir, 0.55)) + float3(0.0, 0.0, 1.0));
        float s = pow(max(dot(N, H1), 0.0), specExp) + 0.35 * pow(max(dot(N, H2), 0.0), specExp * 0.7);
        return s * edge;
    }
    """

/** B2: membaca backdrop (konten) dan memantulkannya di tepi dengan Fresnel, ditambah specular tipis. Hanya pita tepi yang dihitung. */
private const val EDGE_SAMPLE_AGSL =
    LENS_SDF_AGSL + EDGE_COMMON +
        """
    uniform shader content;

    half4 main(float2 p) {
        half4 base = content.eval(p);
        float2 c = size * 0.5;
        float d = -lensSd(p - c, c, cr);
        if (d >= bezel || d < 0.0) {
            return base;
        }
        float2 n = lensGrad(p - c, c, cr);
        float t = clamp(1.0 - d / bezel, 0.0, 1.0);
        float2 rp = clamp(p - n * (bezel * (0.5 + 1.5 * t)), float2(1.0), size - float2(1.0));
        float3 refl = mix(float3(content.eval(rp).rgb), env, 0.35);
        float3 col = mix(float3(base.rgb), refl, clamp(edgeFresnel(t) * envK, 0.0, 0.7));
        col += float3(edgeSpec(n, t, d) * specK);
        return half4(half3(clamp(col, 0.0, 1.0)), base.a);
    }
    """

/** Tanpa sampling backdrop: dipakai di permukaan non-Kyant (tombol full player). Digambar dengan BlendMode.Plus. */
private const val EDGE_OVERLAY_AGSL =
    LENS_SDF_AGSL + EDGE_COMMON +
        """
    half4 main(float2 p) {
        float2 c = size * 0.5;
        float d = -lensSd(p - c, c, cr);
        if (d >= bezel || d < 0.0) {
            return half4(0.0);
        }
        float2 n = lensGrad(p - c, c, cr);
        float t = clamp(1.0 - d / bezel, 0.0, 1.0);
        float3 light = env * (edgeFresnel(t) * envK) + float3(edgeSpec(n, t, d) * specK);
        float a = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
        return half4(half3(clamp(light, 0.0, a)), half(a));
    }
    """

private fun q(v: Float): Float = (v * 50f).roundToInt() / 50f

/** RenderEffect HD untuk backdrop Kyant. Satu shader per permukaan; efek dibuat ulang hanya bila parameter (terkuantisasi 0.02) berubah. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassEdgeHd {
    private val shader: RuntimeShader? = runCatching { RuntimeShader(EDGE_SAMPLE_AGSL) }.getOrNull()
    private var cached: ComposeRenderEffect? = null
    private val last = FloatArray(14) { Float.NaN }
    private val next = FloatArray(14)

    fun effect(
        w: Float,
        h: Float,
        cr: Float,
        bezel: Float,
        specW: Float,
        lightX: Float,
        lightY: Float,
        env: Color,
        envK: Float,
        specK: Float,
        specExp: Float,
    ): ComposeRenderEffect? {
        val s = shader ?: return null
        if (w <= 1f || h <= 1f) return null
        next[0] = w; next[1] = h; next[2] = cr; next[3] = bezel; next[4] = specW
        next[5] = q(lightX); next[6] = q(lightY)
        next[7] = env.red; next[8] = env.green; next[9] = env.blue
        next[10] = q(envK); next[11] = q(specK); next[12] = q(specExp / 100f) * 100f
        val prev = cached
        if (prev != null && next.contentEquals(last)) return prev
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("cr", cr)
        s.setFloatUniform("bezel", bezel)
        s.setFloatUniform("specW", specW)
        s.setFloatUniform("lightDir", next[5], next[6])
        s.setFloatUniform("env", env.red, env.green, env.blue)
        s.setFloatUniform("envK", next[10])
        s.setFloatUniform("specK", next[11])
        s.setFloatUniform("specExp", next[12])
        val fx = RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
        cached = fx
        next.copyInto(last)
        return fx
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassEdgeOverlay {
    private val shader: RuntimeShader? = runCatching { RuntimeShader(EDGE_OVERLAY_AGSL) }.getOrNull()
    private val last = FloatArray(13) { Float.NaN }
    private val next = FloatArray(13)

    fun draw(
        scope: DrawScope,
        shape: CornerBasedShape,
        bezelDp: Dp,
        lightX: Float,
        lightY: Float,
        env: Color,
        envK: Float,
        specK: Float,
        specExp: Float,
    ) {
        val s = shader ?: return
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 1f || h <= 1f) return
        val half = minOf(w, h) * 0.5f
        val cr = shape.topStart.toPx(scope.size, scope).coerceIn(0f, half)
        val bezel = with(scope) { bezelDp.toPx() }.coerceAtMost(half)
        val specW = with(scope) { GlassHd.SPEC_WIDTH_DP.dp.toPx() }
        next[0] = w; next[1] = h; next[2] = cr; next[3] = bezel; next[4] = specW
        next[5] = q(lightX); next[6] = q(lightY)
        next[7] = env.red; next[8] = env.green; next[9] = env.blue
        next[10] = q(envK); next[11] = q(specK); next[12] = q(specExp / 100f) * 100f
        if (!next.contentEquals(last)) {
            s.setFloatUniform("size", w, h)
            s.setFloatUniform("cr", cr)
            s.setFloatUniform("bezel", bezel)
            s.setFloatUniform("specW", specW)
            s.setFloatUniform("lightDir", next[5], next[6])
            s.setFloatUniform("env", env.red, env.green, env.blue)
            s.setFloatUniform("envK", next[10])
            s.setFloatUniform("specK", next[11])
            s.setFloatUniform("specExp", next[12])
            next.copyInto(last)
        }
        scope.drawRect(brush = ShaderBrush(s), blendMode = BlendMode.Plus)
    }
}

/**
 * Specular + refleksi tepi untuk permukaan non-Kyant. API 33+ dan gate aktif: AGSL overlay.
 * Selain itu (API <33, LOW, gate mati): rim sederhana, tetap Liquid Glass tetapi lebih ringan. Kertas/OFF: overlay tipis.
 */
@Composable
fun Modifier.glassEdgeHdOverlay(
    shape: Shape,
    bezel: Dp = GlassHd.bezelButton,
    energy: () -> Float,
): Modifier {
    val mode = LocalGlassMode.current
    val capable =
        GlassHdEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            shape is CornerBasedShape && LocalGlassQuality.current != GlassQuality.LOW
    if (!capable) return this.glassEnergyRim(shape, energy)
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val dark = mode == GlassMode.DARK
    val env = (LocalAmbientGlow.current?.a ?: ChromeOrange).copy(alpha = 1f)
    val specK = GlassHd.specK.pick(dark)
    val envK = GlassHd.envK.pick(dark)
    val overlay = remember { GlassEdgeOverlay() }
    val cbShape = shape as CornerBasedShape
    return this
        .glassEnergyRim(shape) { if (GlassLensGate.enabled) 0f else energy() }
        .drawWithContent {
            drawContent()
            if (GlassLensGate.enabled) {
                val e = energy().coerceIn(0f, 1f)
                overlay.draw(
                    scope = this,
                    shape = cbShape,
                    bezelDp = bezel,
                    lightX = -0.55f + 0.25f * GlassTilt.x + 0.9f * GlassTouch.x * e,
                    lightY = -0.75f + 0.25f * GlassTilt.y + 0.9f * GlassTouch.y * e,
                    env = env,
                    envK = envK * (1f + 0.27f * e),
                    specK = specK * (1f + 0.64f * e),
                    specExp = GlassHd.SPEC_EXP + GlassHd.SPEC_EXP_GAIN * e,
                )
            }
        }
}
