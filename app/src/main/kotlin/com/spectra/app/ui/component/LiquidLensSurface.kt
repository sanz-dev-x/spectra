/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
