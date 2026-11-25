package com.spectra.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val TrackW = 56.dp
private val TrackH = 32.dp
private val RestW = 30.dp
private val RestH = 24.dp
private val LensW = 46.dp
private val LensH = 34.dp

/**
 * Toggle liquid glass. Diam: bulatan kaca kecil. Disentuh atau digeser: lensa kaca lebar yang
 * membesarkan trek di bawahnya, dengan tepi bias tipis dan kilau spekular. Parameter [colors],
 * [thumbContent], dan [interactionSource] dipertahankan hanya demi kompatibilitas pemanggil lama.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val onTrack = MaterialTheme.colorScheme.primary
    val offTrack = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val travelPx = with(density) { (TrackW - LensW - 4.dp).toPx().coerceAtLeast(1f) }
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    var pressed by remember { mutableStateOf(false) }
    val latestChecked by rememberUpdatedState(checked)
    val latestChange by rememberUpdatedState(onCheckedChange)
    val spring = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)

    androidx.compose.runtime.LaunchedEffect(checked) {
        if (!pressed) pos.animateTo(if (checked) 1f else 0f, spring)
    }
    val lens by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "switchLens",
    )

    Canvas(
        modifier =
            modifier
                .size(TrackW, TrackH)
                .alpha(if (enabled) 1f else 0.4f)
                .semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                }.pointerInput(enabled, travelPx) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        var moved = false
                        horizontalDrag(down.id) { change ->
                            moved = true
                            change.consume()
                            val next = (pos.value + change.positionChange().x / travelPx).coerceIn(0f, 1f)
                            scope.launch { pos.snapTo(next) }
                        }
                        pressed = false
                        val target =
                            if (moved) pos.value > 0.5f else !latestChecked
                        scope.launch { pos.animateTo(if (target) 1f else 0f, spring) }
                        if (target != latestChecked) latestChange?.invoke(target)
                    }
                },
    ) {
        val p = pos.value
        val trackColor = lerp(offTrack, onTrack.copy(alpha = 0.85f), p)
        val tr = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, size = size, cornerRadius = tr)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)),
            size = size,
            cornerRadius = tr,
        )
        drawRoundRect(Color.Black.copy(alpha = 0.10f), size = size, cornerRadius = tr, style = Stroke(1.dp.toPx()))

        val w = lerpF(RestW.toPx(), LensW.toPx(), lens)
        val h = lerpF(RestH.toPx(), LensH.toPx(), lens)
        val pad = 2.dp.toPx()
        val cx = pad + LensW.toPx() / 2f + travelPx * p
        val left = cx - w / 2f
        val top = (size.height - h) / 2f
        val r = CornerRadius(h / 2f)
        val tl = Offset(left, top)
        val sz = Size(w, h)

        // Bayangan jatuh lembut
        drawRoundRect(Color.Black.copy(alpha = 0.16f * (0.5f + lens)), tl + Offset(0f, 1.5.dp.toPx()), sz, r)
        // Badan kaca: putih bening, makin transparan saat jadi lensa
        drawRoundRect(Color.White.copy(alpha = lerpF(0.96f, 0.34f, lens)), tl, sz, r)
        // Pembesaran trek di dalam lensa
        if (lens > 0.02f) {
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(trackColor.copy(alpha = 0.55f * lens), Color.White.copy(alpha = 0.10f * lens), trackColor.copy(alpha = 0.55f * lens)),
                    startX = left,
                    endX = left + w,
                ),
                tl,
                sz,
                r,
            )
        }
        // Kilau spekular atas
        drawRoundRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                startY = top,
                endY = top + h * 0.55f,
            ),
            tl + Offset(w * 0.12f, h * 0.07f),
            Size(w * 0.76f, h * 0.42f),
            CornerRadius(h * 0.21f),
        )
        // Tepi bias: pelangi tipis yang hanya terlihat saat lensa aktif
        drawRoundRect(
            Brush.sweepGradient(
                listOf(
                    Color(0xFFFFB3C7), Color(0xFFFFE9A8), Color(0xFFB5F0C8),
                    Color(0xFFA8D8FF), Color(0xFFD9B8FF), Color(0xFFFFB3C7),
                ),
                center = Offset(cx, top + h / 2f),
            ),
            tl, sz, r, alpha = 0.25f + 0.55f * lens, style = Stroke(1.4.dp.toPx()),
        )
        drawRoundRect(Color.White.copy(alpha = 0.7f), tl, sz, r, style = Stroke(0.6.dp.toPx()))
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
