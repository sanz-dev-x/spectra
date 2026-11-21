package com.spectra.app.ui.screens.wrapped.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

enum class ShapeType {
    Circle, Rect, Line
}

private data class AnimatedElement(
    val shapeType: ShapeType,
    val initialX: Float,
    val initialY: Float,
    val targetX: Float,
    val targetY: Float,
    val size: Float, // radius for circle, width/height for rect, length multiplier for line
    val alpha: Float,
    val duration: Int
)

@Composable
internal fun AnimatedBackground(
    elementCount: Int = 20,
    shapeTypes: List<ShapeType> = listOf(ShapeType.Circle),
) {
    // Latar kini satu lapisan global (WrappedThemedBackground) yang tidak berhenti saat pager di-scroll.
}
