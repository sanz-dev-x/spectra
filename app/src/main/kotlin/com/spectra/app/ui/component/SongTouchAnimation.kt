package com.spectra.app.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.spectra.app.constants.SongTouchAnimationKey
import com.spectra.app.utils.rememberPreference

/** Animasi sentuh kartu lagu. 0 = jelly, 1 = tenggelam, 2 = denyut. */
@Composable
fun Modifier.songTouchAnimation(interactionSource: InteractionSource): Modifier {
    val (style) = rememberPreference(SongTouchAnimationKey, defaultValue = 0)
    val pressed by interactionSource.collectIsPressedAsState()
    val spec =
        when (style) {
            1 -> spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)
            2 -> spring<Float>(dampingRatio = 0.35f, stiffness = 900f)
            else -> spring<Float>(dampingRatio = 0.32f, stiffness = 420f)
        }
    val amount by animateFloatAsState(targetValue = if (pressed) 1f else 0f, animationSpec = spec, label = "songTouch")
    return graphicsLayer {
        when (style) {
            1 -> {
                val s = 1f - 0.05f * amount
                scaleX = s
                scaleY = s
                translationY = 3f * density * amount
                alpha = 1f - 0.12f * amount.coerceIn(0f, 1f)
            }
            2 -> {
                val s = 1f + 0.04f * amount
                scaleX = s
                scaleY = s
            }
            else -> {
                scaleX = 1f - 0.07f * amount
                scaleY = 1f + 0.05f * amount
            }
        }
    }
}
