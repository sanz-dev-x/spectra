/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.spectra.app.ui.glass.GlassLensGate

private const val LENS_OPTICS_AGSL = """
uniform float2 size;
uniform float radius;
uniform float bezel;
uniform float2 lightDir;
uniform float strength;
uniform float pass;

float surfaceSlope(float x) {
    float u = 1.0 - x;
    float u2 = u * u;
    float inner = max(1.0 - u2 * u2, 1e-4);
    return min(u2 * u * pow(inner, -0.75), 6.0);
}

half4 main(float2 frag) {
    float2 hs = size * 0.5;
    float2 p = frag - hs;
    float2 q = abs(p) - hs + float2(radius);
    float2 qm = max(q, float2(0.0));
    float sd = length(qm) + min(max(q.x, q.y), 0.0) - radius;
    float inside = -sd;
    if (inside <= 0.0) {
        return pass < 0.5 ? half4(0.0) : half4(1.0);
    }

    float2 outward;
    if (q.x > 0.0 || q.y > 0.0) {
        outward = normalize(qm * sign(p) + float2(1e-4, 0.0));
    } else if (q.x > q.y) {
        outward = float2(sign(p.x), 0.0);
    } else {
        outward = float2(0.0, sign(p.y));
    }

    float x = clamp(inside / bezel, 0.0, 1.0);
    float slope = surfaceSlope(x);
    float cosV = 1.0 / sqrt(1.0 + slope * slope);
    float edge = 1.0 - smoothstep(0.0, 1.0, x);

    if (pass > 0.5) {
        float path = (1.0 - x * 0.6) * 0.6 + 0.2;
        float3 absorb = exp(-float3(0.050, 0.028, 0.018) * path * strength);
        return half4(half3(absorb), 1.0);
    }

    float3 N = normalize(float3(outward * slope * cosV, cosV));
    float fres = 0.04 + 0.96 * pow(1.0 - cosV, 5.0);
    float3 V = float3(0.0, 0.0, 1.0);
    float3 L1 = normalize(float3(lightDir, 0.55));
    float3 L2 = normalize(float3(-lightDir, 0.55));
    float s1 = pow(max(dot(N, normalize(L1 + V)), 0.0), 70.0);
    float s2 = pow(max(dot(N, normalize(L2 + V)), 0.0), 45.0) * 0.45;
    float spec = (s1 + s2) * edge;
    float rim = 1.0 - smoothstep(0.0, 1.2, inside);

    float3 light = float3(0.90, 0.95, 1.0) * fres * 0.8 + float3(spec * 0.9) + float3(rim * 0.08);
    light *= strength;
    float a = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
    return half4(half3(clamp(light, 0.0, a)), half(a));
}
"""

/**
 * Lapisan optik di atas lensa: serapan warna kaca (Beer-Lambert), pantulan Fresnel (Schlick), dan
 * specular dari normal permukaan squircle cembung. Dihitung analitik per piksel di GPU dan tidak
 * membaca isi layar. Uniform hanya di-set saat nilainya berubah.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LensOptics {
    private val light: RuntimeShader? = create(0f)
    private val tint: RuntimeShader? = create(1f)
    private var lastW = -1f
    private var lastH = -1f
    private var lastBezel = -1f
    private var lastLightX = Float.NaN
    private var lastLightY = Float.NaN
    private var lastStrength = -1f

    private fun create(pass: Float): RuntimeShader? =
        runCatching { RuntimeShader(LENS_OPTICS_AGSL) }.getOrNull()?.also { it.setFloatUniform("pass", pass) }

    fun draw(
        scope: DrawScope,
        bezel: Float,
        lightX: Float,
        lightY: Float,
        strength: Float,
    ) {
        if (!GlassLensGate.enabled) return
        val l = light ?: return
        val t = tint ?: return
        val w = scope.size.width
        val h = scope.size.height
        if (w <= 1f || h <= 1f) return
        val radius = minOf(w, h) / 2f
        val b = bezel.coerceIn(1f, radius)
        if (w != lastW || h != lastH || b != lastBezel || lightX != lastLightX || lightY != lastLightY || strength != lastStrength) {
            for (s in arrayOf(l, t)) {
                s.setFloatUniform("size", w, h)
                s.setFloatUniform("radius", radius)
                s.setFloatUniform("bezel", b)
                s.setFloatUniform("lightDir", lightX, lightY)
                s.setFloatUniform("strength", strength)
            }
            lastW = w
            lastH = h
            lastBezel = b
            lastLightX = lightX
            lastLightY = lightY
            lastStrength = strength
        }
        scope.drawRect(brush = ShaderBrush(t), blendMode = BlendMode.Multiply)
        scope.drawRect(brush = ShaderBrush(l), blendMode = BlendMode.Plus)
    }
}
