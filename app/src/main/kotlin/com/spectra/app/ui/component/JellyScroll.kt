package com.spectra.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

@Stable
class JellyScroll {
    var lean by mutableFloatStateOf(0f)
        private set

    val connection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val dx = if (consumed.x != 0f) consumed.x else available.x
                if (dx != 0f) lean = (lean * 0.6f + dx * 0.03f).coerceIn(-1f, 1f)
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                lean = 0f
                return Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                lean = 0f
                return Velocity.Zero
            }
        }
}

@Composable
fun Modifier.jellyScroll(jelly: JellyScroll?): Modifier {
    val lean by animateFloatAsState(
        targetValue = jelly?.lean ?: 0f,
        animationSpec = spring(dampingRatio = 0.28f, stiffness = 170f),
        label = "jellyLean",
    )
    return graphicsLayer {
        val a = abs(lean)
        scaleX = 1f + 0.12f * a
        scaleY = 1f - 0.09f * a
        rotationZ = lean * 9f
        translationX = lean * 18f * density
    }
}

@Composable
fun Modifier.jellyCard(
    touch: InteractionSource,
    jelly: JellyScroll?,
): Modifier {
    val pressed by touch.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.26f, stiffness = 360f),
        label = "jellyPress",
    )
    val lean by animateFloatAsState(
        targetValue = jelly?.lean ?: 0f,
        animationSpec = spring(dampingRatio = 0.28f, stiffness = 170f),
        label = "jellyLean",
    )
    return graphicsLayer {
        val a = abs(lean)
        scaleX = 1f - 0.10f * press + 0.12f * a
        scaleY = 1f + 0.07f * press - 0.09f * a
        rotationZ = lean * 9f
        translationX = lean * 18f * density
    }
}
