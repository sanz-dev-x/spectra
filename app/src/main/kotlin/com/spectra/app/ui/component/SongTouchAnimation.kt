package com.spectra.app.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.spectra.app.constants.SongTouchAnimation
import com.spectra.app.constants.SongTouchAnimationKey
import com.spectra.app.utils.rememberEnumPreference

/** Animasi saat lagu disentuh. Hanya mengamati sentuhan, klik tetap ditangani komponen aslinya. */
@Composable
fun Modifier.songTouchAnimation(): Modifier {
    val (style) = rememberEnumPreference(SongTouchAnimationKey, defaultValue = SongTouchAnimation.JELLY)
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val jelly = style == SongTouchAnimation.JELLY
    val sink = style == SongTouchAnimation.SINK
    val tilt = style == SongTouchAnimation.TILT

    val wobble = spring<Float>(dampingRatio = 0.32f, stiffness = 420f)
    val scaleX by animateFloatAsState(if (pressed && jelly) 1.05f else 1f, wobble, label = "songJellyX")
    val scaleY by animateFloatAsState(if (pressed && jelly) 0.9f else 1f, wobble, label = "songJellyY")
    val sinkScale by animateFloatAsState(if (pressed && sink) 0.95f else 1f, tween(110), label = "songSink")
    val sinkAlpha by animateFloatAsState(if (pressed && sink) 0.82f else 1f, tween(110), label = "songSinkAlpha")

    val tiltSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val nx = if (size.width > 0) ((touch.x / size.width) * 2f - 1f).coerceIn(-1f, 1f) else 0f
    val ny = if (size.height > 0) ((touch.y / size.height) * 2f - 1f).coerceIn(-1f, 1f) else 0f
    val rotY by animateFloatAsState(if (pressed && tilt) nx * 7f else 0f, tiltSpring, label = "songTiltY")
    val rotX by animateFloatAsState(if (pressed && tilt) -ny * 7f else 0f, tiltSpring, label = "songTiltX")
    val tiltScale by animateFloatAsState(if (pressed && tilt) 0.97f else 1f, tiltSpring, label = "songTiltScale")

    return this
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop * 1.5f
            awaitPointerEventScope {
                var down = Offset.Zero
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
                    if (change.pressed) {
                        if (!change.previousPressed) {
                            down = change.position
                            pressed = true
                        } else if ((change.position - down).getDistance() > slop) {
                            pressed = false
                        }
                        touch = change.position
                    } else {
                        pressed = false
                    }
                }
            }
        }.graphicsLayer {
            transformOrigin = TransformOrigin.Center
            cameraDistance = 14f * density
            this.scaleX = scaleX * sinkScale * tiltScale
            this.scaleY = scaleY * sinkScale * tiltScale
            this.alpha = sinkAlpha
            rotationX = rotX
            rotationY = rotY
        }
}
