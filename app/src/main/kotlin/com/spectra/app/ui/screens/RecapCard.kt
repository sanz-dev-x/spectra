/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spectra.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

private const val Tau = (2.0 * PI).toFloat()

/**
 * Kartu rekap musik. Latar Canvas berupa pita spektrum lembut dan tiga cahaya yang melayang pelan,
 * hanya memakai warna tema. Fase animasi dibaca di blok gambar saja, jadi tidak ada recomposition per frame.
 * Animasi jalan selama kartu terlihat dan app aktif, melambat sampai diam saat list di-scroll, lalu
 * menyala lagi dengan mulus.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapCard(
    isReady: Boolean,
    isVisible: Boolean,
    isScrolling: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var started by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) started = true
            if (e == Lifecycle.Event.ON_STOP) started = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val running = isReady && isVisible && started
    val speed = remember { Animatable(0f) }
    val phase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(running, isScrolling) {
        speed.animateTo(if (running && !isScrolling) 1f else 0f, tween(450))
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            if (speed.value <= 0.001f) {
                snapshotFlow { speed.value > 0.001f }.first { it }
                last = androidx.compose.runtime.withFrameNanos { it }
            }
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            phase.floatValue = (phase.floatValue + dt * speed.value * 0.05f) % 1f
        }
    }

    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    val glowA = scheme.primary
    val glowB = scheme.tertiary
    val glowC = scheme.secondary
    val ink = scheme.onSurface
    val shape = RoundedCornerShape(28.dp)
    val font =
        remember {
            runCatching { FontFamily(Font(R.font.bbh_bartle_regular)) }.getOrDefault(FontFamily.Default)
        }

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clip(shape)) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(base)
            val t = phase.floatValue * Tau
            val w = size.width
            val h = size.height
            val m = maxOf(w, h)
            listOf(glowA to 0f, glowB to 2.1f, glowC to 4.2f).forEach { (c, o) ->
                val cx = w * (0.5f + 0.34f * sin(t + o))
                val cy = h * (0.5f + 0.30f * cos(t + o * 1.3f))
                val r = m * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(listOf(c.copy(alpha = 0.20f), Color.Transparent), Offset(cx, cy), r),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            val stroke = Stroke(width = 1.4.dp.toPx())
            for (i in 0 until 5) {
                val path = Path()
                val baseY = h * (0.30f + 0.10f * i)
                val amp = h * (0.05f + 0.012f * i)
                var x = 0f
                path.moveTo(0f, baseY)
                while (x <= w) {
                    val y = baseY + amp * sin(x / w * Tau * 1.5f + t * (1 + i % 2) + i * 0.8f)
                    path.lineTo(x, y)
                    x += 12.dp.toPx()
                }
                drawPath(path, ink.copy(alpha = 0.05f + 0.02f * i), style = stroke)
            }
        }
        if (isReady) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.wrapped_ready_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = font),
                        color = ink,
                    )
                    Text(
                        text = stringResource(R.string.wrapped_ready_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink.copy(alpha = 0.72f),
                    )
                }
                FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
        }
    }
}
