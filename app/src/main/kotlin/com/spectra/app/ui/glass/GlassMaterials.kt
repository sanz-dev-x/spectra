package com.spectra.app.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import com.spectra.app.ui.component.LocalKyantBackdrop
import com.spectra.app.ui.component.clearGlassNav
import com.spectra.app.ui.component.controlGlass3D
import com.spectra.app.ui.component.kyantChrome
import com.spectra.app.ui.component.tintedGlass3D
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode
import kotlin.math.ceil

enum class GlassState { Default, Pressed, Selected, Disabled }

/** Alpha scrim hitam untuk glassClear; diisi sumber luminance artwork, default 0. */
val LocalGlassClearScrim = staticCompositionLocalOf<() -> Float> { { 0f } }

private const val DISABLED_ALPHA = 0.38f
private const val PRESSED_ALPHA = 0.12f
private const val SELECTED_ALPHA_PAPER = 0.16f

private fun quantizeScrim(alpha: Float): Float = (ceil(alpha / GlassScrim.STEP - 0.001f) * GlassScrim.STEP).coerceIn(0f, GlassScrim.MAX_ALPHA)

@Composable
private fun isLightSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

/** Material Regular: tab bar, navbar, mini player ([thick]), dan area teks. */
@Composable
fun Modifier.glassRegular(
    shape: Shape,
    state: GlassState = GlassState.Default,
    thick: Boolean = false,
    hd: Boolean = false,
    baseEnergy: Float = 0f,
): Modifier {
    val mode = LocalGlassMode.current
    val light = isLightSurface()
    val energy = animateGlassEnergy(maxOf(stateEnergy(state), baseEnergy))
    val e = remember(energy) { { energy.value } }
    val inScreen = LocalKyantBackdrop.current == null
    val surface =
        if (inScreen) {
            Modifier.controlGlass3D(shape, light).glassEdgeHdOverlay(shape) { e() }
        } else if (thick) {
            Modifier.tintedGlass3D(shape, light, hd, e)
        } else {
            Modifier.clearGlassNav(shape, light, hd, e)
        }
    return this
        .then(glassDisabledLayer(state))
        .then(surface)
        .then(Modifier.glassStateOverlay(shape, state, mode, light))
}

/** Material Clear: hanya untuk kontrol di atas artwork/full-screen player. */
@Composable
fun Modifier.glassClear(
    shape: Shape,
    state: GlassState = GlassState.Default,
): Modifier {
    val mode = LocalGlassMode.current
    val light = isLightSurface()
    val scrim = LocalGlassClearScrim.current
    val extra = remember(scrim) { { quantizeScrim(scrim()) } }
    val energy = animateGlassEnergy(stateEnergy(state))
    val e = remember(energy) { { energy.value } }
    val surface =
        Modifier.kyantChrome(
            shape,
            light,
            intense = false,
            fillScale = GlassClear.FILL_SCALE,
            extraScrim = extra,
            energy = e,
        ) ?: Modifier.clearGlassNav(shape, light, true, e)
    return this
        .then(glassDisabledLayer(state))
        .then(surface)
        .then(Modifier.glassStateOverlay(shape, state, mode, light))
}

private fun stateEnergy(state: GlassState): Float =
    when (state) {
        GlassState.Pressed -> 1f
        GlassState.Selected -> 0.6f
        else -> 0f
    }

private fun glassDisabledLayer(state: GlassState): Modifier =
    if (state == GlassState.Disabled) Modifier.graphicsLayer { alpha = DISABLED_ALPHA } else Modifier

@Composable
private fun Modifier.glassStateOverlay(
    shape: Shape,
    state: GlassState,
    mode: GlassMode,
    light: Boolean,
): Modifier {
    val pressedTarget = state == GlassState.Pressed
    val pressed =
        animateFloatAsState(
            targetValue = if (pressedTarget) 1f else 0f,
            animationSpec = if (pressedTarget) GlassMotion.press else GlassMotion.release,
            label = "glassPress",
        )
    val selected =
        animateFloatAsState(
            targetValue = if (state == GlassState.Selected) 1f else 0f,
            animationSpec = GlassMotion.settle,
            label = "glassSelected",
        )
    val dark =
        when (mode) {
            GlassMode.DARK -> true
            GlassMode.BRIGHT -> false
            GlassMode.OFF -> !light
        }
    val selectedColor =
        when {
            mode != GlassMode.OFF -> GlassRegular.sheen.pick(dark)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ALPHA_PAPER)
        }
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            val s = selected.value
            val p = pressed.value
            if (s > 0.001f) drawOutline(outline, selectedColor, alpha = s)
            if (p > 0.001f) drawOutline(outline, Color.White, alpha = PRESSED_ALPHA * p, blendMode = BlendMode.Plus)
            drawContent()
        }
    }
}
