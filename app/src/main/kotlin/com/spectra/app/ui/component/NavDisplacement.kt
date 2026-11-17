/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.abs

private const val NAV_DISPLACE_AGSL = """
uniform shader content;
uniform float4 lens;
uniform float radius;
uniform float push;
uniform float band;

half4 main(float2 p) {
    float2 q = p - lens.xy;
    float2 d = abs(q) - (lens.zw - float2(radius));
    float2 o = max(d, float2(0.0));
    float sdf = length(o) + min(max(d.x, d.y), 0.0) - radius;
    if (sdf <= 0.0 || sdf >= band) {
        return content.eval(p);
    }
    float t = 1.0 - sdf / band;
    t = t * t * (3.0 - 2.0 * t);
    float2 n = normalize(sign(q) * o + float2(0.0001, 0.0));
    float2 shift = n * float2(1.0, 0.5) * (push * t);
    return content.eval(p - shift);
}
"""

/** Posisi dan fisika lensa terhadap navbar: g = besar regangan (0..1), arah = tepi depan lensa. */
internal class LensKinematics(
    val g: Float,
    val dirRight: Boolean,
)

internal fun lensKinematics(
    dragging: Boolean,
    shownIndex: Int,
    itemWidthPx: Float,
    x: Float,
    v: Float,
): LensKinematics {
    val gap = if (dragging || shownIndex < 0) 0f else shownIndex * itemWidthPx - x
    val gapFrac = (abs(gap) / itemWidthPx.coerceAtLeast(1f)).coerceAtMost(1f)
    val velFrac = (abs(v) / 4500f).coerceAtMost(1f)
    val dir = if (abs(gap) > 1f) gap else v
    return LensKinematics(maxOf(gapFrac, velFrac), dir >= 0f)
}

/** Displacement AGSL: isi navbar di sekitar lensa terdorong menjauh dan termampatkan, mengikuti sisi lensa. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class NavDisplacement {
    private val shader = RuntimeShader(NAV_DISPLACE_AGSL)

    fun effect(
        cx: Float,
        cy: Float,
        halfW: Float,
        halfH: Float,
        radius: Float,
        push: Float,
        band: Float,
    ): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("lens", cx, cy, halfW, halfH)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("push", push)
        shader.setFloatUniform("band", band)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
