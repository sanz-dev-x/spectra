package com.spectra.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/** Navbar gaya Kertas: lembar kertas tebal dengan bayangan jatuh dan tepi bawah yang menebal. Tanpa blur, lensa, atau backdrop. */
internal fun Modifier.paperNav(
    shape: Shape,
    light: Boolean,
): Modifier {
    val top = if (light) Color(0xFFFFFDF7) else Color(0xFF3B3833)
    val bottom = if (light) Color(0xFFF0EADB) else Color(0xFF2C2A26)
    val edgeHi = if (light) Color.White else Color.White.copy(alpha = 0.18f)
    val edgeLo = Color.Black.copy(alpha = if (light) 0.14f else 0.45f)
    return this
        .shadow(
            elevation = 12.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.30f),
            spotColor = Color.Black.copy(alpha = 0.40f),
        ).background(Brush.verticalGradient(listOf(top, bottom)), shape)
        .drawBehind {
            drawRoundRect(
                brush =
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.80f to Color.Transparent,
                        1f to edgeLo,
                    ),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }.border(0.8.dp, Brush.verticalGradient(listOf(edgeHi, edgeLo)), shape)
}

/** Penanda tab aktif saat diam: potongan kertas kecil yang menempel, sedikit timbul. */
internal fun Modifier.paperTab(
    light: Boolean,
    accent: Color,
): Modifier {
    val base = if (light) Color(0xFFF3EDDD) else Color(0xFF4A463F)
    val fill = lerp(base, accent, 0.14f)
    return this
        .drawBehind {
            drawRoundRect(
                color = Color.Black.copy(alpha = if (light) 0.10f else 0.35f),
                topLeft = Offset(0f, 1.5.dp.toPx()),
                size = size,
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }.clip(CircleShape)
        .background(Brush.verticalGradient(listOf(lerp(fill, Color.White, if (light) 0.5f else 0.08f), fill)))
        .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.10f))), CircleShape)
}

private fun jag(
    i: Int,
    seed: Int,
): Float {
    val v = sin(i * 12.9898f + seed * 78.233f) * 43758.547f
    return abs(v - kotlin.math.floor(v))
}

/**
 * Kertas sobekan 3D yang muncul saat navbar digeser: tepi bergerigi (dua lapis, serat putih di belakang),
 * bayangan berlapis di bawahnya, dan sedikit rotasi dari pemanggil. Bentuk dibuat sekali per ukuran.
 */
internal fun Modifier.tornPaper(light: Boolean): Modifier =
    drawWithCache {
        val w = size.width
        val h = size.height
        val step = 7.dp.toPx()
        val inset = h * 0.26f

        fun build(
            seed: Int,
            amp: Float,
            grow: Float,
        ): Path {
            val pts = ArrayList<Offset>()
            var i = 0
            var x = inset
            while (x < w - inset) {
                pts += Offset(x, -grow + (jag(i++, seed) - 0.5f) * 2f * amp)
                x += step
            }
            var y = inset
            while (y < h - inset) {
                pts += Offset(w + grow + (jag(i++, seed) - 0.5f) * 2f * amp, y)
                y += step
            }
            x = w - inset
            while (x > inset) {
                pts += Offset(x, h + grow + (jag(i++, seed) - 0.5f) * 2f * amp)
                x -= step
            }
            y = h - inset
            while (y > inset) {
                pts += Offset(-grow + (jag(i++, seed) - 0.5f) * 2f * amp, y)
                y -= step
            }
            return Path().apply {
                pts.forEachIndexed { index, p -> if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                close()
            }
        }

        val main = build(seed = 1, amp = 2.0.dp.toPx(), grow = 0f)
        val fiber = build(seed = 2, amp = 3.2.dp.toPx(), grow = 1.2.dp.toPx())
        val hi = if (light) Color(0xFFFFFEFA) else Color(0xFF55514A)
        val lo = if (light) Color(0xFFECE5D3) else Color(0xFF3A3731)
        val fiberColor = if (light) Color.White else Color(0xFF6C675D)
        val line = Color.Black.copy(alpha = if (light) 0.16f else 0.50f)
        val shadowK = if (light) 1f else 1.8f
        val stroke = Stroke(width = 0.7.dp.toPx())
        onDrawBehind {
            translate(top = 4.dp.toPx()) { drawPath(main, Color.Black.copy(alpha = 0.08f * shadowK)) }
            translate(top = 2.dp.toPx()) { drawPath(main, Color.Black.copy(alpha = 0.14f * shadowK)) }
            drawPath(fiber, fiberColor)
            drawPath(main, Brush.verticalGradient(listOf(hi, lo)))
            drawPath(main, line, style = stroke)
        }
    }
