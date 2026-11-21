package com.spectra.app.ui.glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.spectra.app.ui.component.GlassQuality
import com.spectra.app.ui.component.LocalGlassQuality
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode

private val EdgeFadeHeight = 28.dp

/** Dimatikan: mask DstIn di strip tepi menghasilkan pita gelap di atas mini player. Nyalakan lagi setelah diperbaiki. */
private const val EDGE_FADE_ENABLED = false
private const val EdgeFadeMs = 250

/** Status ujung daftar: true bila scroll maju sudah tidak punya sisa konten. */
@Stable
class ScrollEdgeState {
    var atEnd by mutableStateOf(false)
}

@Composable
fun rememberScrollEdgeState(key: Any?): ScrollEdgeState = remember(key) { ScrollEdgeState() }

private class EdgeConnection(
    private val state: ScrollEdgeState,
) : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y < -1f) {
            state.atEnd = true
        } else if (consumed.y > 0f) {
            state.atEnd = false
        }
        return Offset.Zero
    }
}

/**
 * Fade opacity konten tepat di atas navbar/mini player. Mask DstIn hanya di strip tepi (saveLayer terbatas),
 * tanpa blur baru: blur di belakang bar tetap dari backdrop kaca yang sudah ada. Nonaktif di Kertas/OFF,
 * API < 33, kualitas LOW, dan saat daftar sudah di ujung.
 */
@Composable
fun Modifier.scrollEdge(
    state: ScrollEdgeState,
    insets: WindowInsets,
    enabled: Boolean = true,
): Modifier {
    val active =
        EDGE_FADE_ENABLED &&
            enabled &&
            Build.VERSION.SDK_INT >= 33 &&
            LocalGlassMode.current != GlassMode.OFF &&
            LocalGlassQuality.current != GlassQuality.LOW
    if (!active) return this
    val strength = remember { Animatable(1f) }
    LaunchedEffect(state) {
        snapshotFlow { state.atEnd }.collect { strength.animateTo(if (it) 0f else 1f, tween(EdgeFadeMs)) }
    }
    val connection = remember(state) { EdgeConnection(state) }
    return this
        .nestedScroll(connection)
        .drawWithContent {
            val s = strength.value
            // Batas dibulatkan ke piksel utuh: tepi non-integer membuat garis gelap tipis di sambungan dua clip.
            val bottomY = kotlin.math.floor(size.height - insets.getBottom(this)).coerceAtLeast(0f)
            val topY = kotlin.math.floor(bottomY - EdgeFadeHeight.toPx())
            if (s <= 0.001f || topY <= 0f) {
                drawContent()
                return@drawWithContent
            }
            val width = size.width
            clipRect(0f, topY, width, bottomY, ClipOp.Difference) { this@drawWithContent.drawContent() }
            clipRect(0f, topY, width, bottomY) {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(0f, topY, width, bottomY), Paint())
                    this@drawWithContent.drawContent()
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - s)),
                                startY = topY,
                                endY = bottomY,
                            ),
                        topLeft = Offset(0f, topY),
                        size = Size(width, bottomY - topY),
                        blendMode = BlendMode.DstIn,
                    )
                    canvas.restore()
                }
            }
        }
}
