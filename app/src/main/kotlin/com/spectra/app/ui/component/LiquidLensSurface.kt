/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal fun lensScaleX(liquid: LiquidLensPhysics, itemWidthPx: Float, insetPx: Float): Float =
    if (liquid.ready) (liquid.rightPx - liquid.leftPx + insetPx) / (itemWidthPx + insetPx).coerceAtLeast(1f) else 1f

internal fun DrawScope.drawNavSilhouette(
    liquid: LiquidLensPhysics,
    itemWidthPx: Float,
    navWidthPx: Float,
    navHeightPx: Float,
    lensInsetPx: Float,
    edgePadPx: Float,
    squeeze: Float,
    tint: Color,
    scaled: Boolean = true,
) {
    if (!liquid.ready) return
    val sx = if (scaled) lensScaleX(liquid, itemWidthPx, lensInsetPx) else 1f
    val sy = if (scaled) liquid.heightScale else 1f
    val lensLeft = liquid.leftPx - lensInsetPx / 2f
    val lensRight = liquid.rightPx + lensInsetPx / 2f
    val navLeft = -edgePadPx
    val navRight = navWidthPx + edgePadPx
    val bandLeft = maxOf(lensLeft, navLeft)
    val bandRight = minOf(lensRight, navRight)
    if (bandRight <= bandLeft) return

    val radius = navHeightPx * squeeze / 2f
    val corner = CornerRadius(radius / sx, radius / sy)
    val flat = CornerRadius.Zero
    val roundLeft = navLeft > lensLeft
    val roundRight = navRight < lensRight
    val cy = size.height / 2f
    val halfH = radius / sy
    val rect =
        RoundRect(
            left = (bandLeft - lensLeft) / sx,
            top = cy - halfH,
            right = (bandRight - lensLeft) / sx,
            bottom = cy + halfH,
            topLeftCornerRadius = if (roundLeft) corner else flat,
            topRightCornerRadius = if (roundRight) corner else flat,
            bottomRightCornerRadius = if (roundRight) corner else flat,
            bottomLeftCornerRadius = if (roundLeft) corner else flat,
        )
    val path = Path().apply { addRoundRect(rect) }
    drawPath(path, tint)
    drawPath(path, Color.White.copy(alpha = 0.2f), style = Stroke(width = 1.dp.toPx()))
}

private val RainbowColors =
    listOf(
        Color(0xFFFF4D6D),
        Color(0xFFFF9F43),
        Color(0xFFFFE066),
        Color(0xFF5CF29A),
        Color(0xFF3DC8FF),
        Color(0xFF8B7BFF),
    )

internal fun DrawScope.drawLensRainbow(
    liquid: LiquidLensPhysics,
    itemWidthPx: Float,
    insetPx: Float,
    verticalPadPx: Float,
    intensity: Float,
) {
    if (!liquid.ready || intensity <= 0.01f) return
    val left = liquid.leftPx - insetPx / 2f
    val width = liquid.rightPx - liquid.leftPx + insetPx
    val height = (size.height - verticalPadPx) * liquid.heightScale
    val top = (size.height - height) / 2f
    val baseRadius = (size.height - verticalPadPx) / 2f
    val rx = (baseRadius * lensScaleX(liquid, itemWidthPx, insetPx)).coerceAtMost(width / 2f)
    val ry = (baseRadius * liquid.heightScale).coerceAtMost(height / 2f)
    val band = 0.2f
    val topAlpha = 0.18f * intensity.coerceIn(0f, 1f)
    val bottomAlpha = 0.62f * intensity.coerceIn(0f, 1f)
    val last = RainbowColors.size - 1
    val stops = ArrayList<Pair<Float, Color>>()
    RainbowColors.forEachIndexed { i, c -> stops.add(band * i / last to c.copy(alpha = topAlpha * (1f - 0.7f * i / last))) }
    stops.add(band + 0.07f to RainbowColors[last].copy(alpha = 0f))
    stops.add(1f - band - 0.07f to RainbowColors[last].copy(alpha = 0f))
    RainbowColors.reversed().forEachIndexed { i, c -> stops.add(1f - band + band * i / last to c.copy(alpha = bottomAlpha * (0.3f + 0.7f * i / last))) }
    val brush =
        Brush.verticalGradient(
            colorStops = stops.toTypedArray(),
            startY = top,
            endY = top + height,
        )
    val shape = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + width, top + height, CornerRadius(rx, ry))) }
    clipPath(shape) {
        drawRect(brush = brush, topLeft = Offset(left, top), size = Size(width, height))
        // Pelangi tipis di sisi kiri dan kanan dari pantulan liquid glass.
        val sideBand = (rx * 0.9f / width).coerceIn(0.05f, 0.3f)
        val sideAlpha = 0.24f * intensity.coerceIn(0f, 1f)
        val sideStops = ArrayList<Pair<Float, Color>>()
        RainbowColors.forEachIndexed { i, c -> sideStops.add(sideBand * i / last to c.copy(alpha = sideAlpha * (1f - 0.75f * i / last))) }
        sideStops.add(sideBand + 0.05f to RainbowColors[last].copy(alpha = 0f))
        sideStops.add(1f - sideBand - 0.05f to RainbowColors[last].copy(alpha = 0f))
        RainbowColors.reversed().forEachIndexed { i, c -> sideStops.add(1f - sideBand + sideBand * i / last to c.copy(alpha = sideAlpha * (0.25f + 0.75f * i / last))) }
        drawRect(
            brush = Brush.horizontalGradient(colorStops = sideStops.toTypedArray(), startX = left, endX = left + width),
            topLeft = Offset(left, top),
            size = Size(width, height),
        )
        // Pantulan putih liquid glass di sisi atas.
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f * intensity.coerceIn(0f, 1f)), Color.Transparent),
                    startY = top,
                    endY = top + height * 0.22f,
                ),
            topLeft = Offset(left, top),
            size = Size(width, height * 0.22f),
        )
        // Cahaya light tipis di tepi kiri dan kanan.
        val glow = 0.30f * intensity.coerceIn(0f, 1f)
        drawRect(
            brush =
                Brush.horizontalGradient(
                    colorStops =
                        arrayOf(
                            0f to Color.White.copy(alpha = glow),
                            0.10f to Color.Transparent,
                            0.90f to Color.Transparent,
                            1f to Color.White.copy(alpha = glow),
                        ),
                    startX = left,
                    endX = left + width,
                ),
            topLeft = Offset(left, top),
            size = Size(width, height),
        )
    }
}

private fun roundedPolygon(points: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = points.size
    for (i in 0 until n) {
        val v = points[i]
        val p = points[(i + n - 1) % n]
        val q = points[(i + 1) % n]
        val toP = p - v
        val toQ = q - v
        val r = minOf(radius, toP.getDistance() / 2f, toQ.getDistance() / 2f)
        val a = v + toP * (r / toP.getDistance().coerceAtLeast(0.001f))
        val b = v + toQ * (r / toQ.getDistance().coerceAtLeast(0.001f))
        if (i == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticTo(v.x, v.y, b.x, b.y)
    }
    path.close()
    return path
}

private fun rainbowLoop(alpha: Float): List<Color> =
    (RainbowColors + RainbowColors.first()).map { it.copy(alpha = alpha) }
