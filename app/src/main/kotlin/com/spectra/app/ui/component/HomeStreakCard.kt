package com.spectra.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.app.LocalDatabase
import com.spectra.app.R
import com.spectra.app.utils.computeListeningStreak
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun HomeStreakCard(modifier: Modifier = Modifier) {
    val database = LocalDatabase.current
    val today = remember { LocalDate.now() }
    val rawDays by remember(database) { database.listeningDays() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val streak =
        remember(rawDays, today) {
            computeListeningStreak(rawDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }, today)
        }
    val lit = streak.listenedToday && streak.current > 0
    val scheme = MaterialTheme.colorScheme

    val count = remember { Animatable(0f) }

    LaunchedEffect(streak.current) {
        count.snapTo(streak.current.toFloat())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = HomeSideInset)
                .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp),
        ) {
            StreakFlame(lit = lit, modifier = Modifier.size(48.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = count.value.roundToInt().toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (lit) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = stringResource(R.string.home_streak_card_days),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (lit) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                text =
                    when {
                        streak.current == 0 -> stringResource(R.string.home_streak_card_start)
                        lit -> stringResource(R.string.home_streak_card_lit)
                        else -> stringResource(R.string.home_streak_card_resting)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private fun flamePath(
    w: Float,
    h: Float,
    sway: Float,
    stretch: Float,
): Path {
    val top = h * (1f - stretch)
    fun x(v: Float) = v * w
    fun y(v: Float) = top + v * (h - top)
    val tipX = 0.52f + sway
    return Path().apply {
        moveTo(x(tipX), y(0.0f))
        cubicTo(x(0.44f + sway * 0.6f), y(0.24f), x(0.10f), y(0.38f), x(0.13f), y(0.68f))
        cubicTo(x(0.14f), y(0.88f), x(0.30f), y(1.0f), x(0.50f), y(1.0f))
        cubicTo(x(0.70f), y(1.0f), x(0.87f), y(0.88f), x(0.87f), y(0.66f))
        cubicTo(x(0.87f), y(0.46f), x(0.64f + sway * 0.6f), y(0.30f), x(tipX), y(0.0f))
        close()
    }
}

private fun wave(t: Float, phase: Float = 0f): Float = sin(((t + phase) % 1f) * 2f * PI.toFloat())

@Composable
fun StreakFlame(
    lit: Boolean,
    modifier: Modifier = Modifier,
) {
    val t1 = 0f
    val t2 = 0f
    val t3 = 0f
    val colorLerp by androidx.compose.animation.core.animateFloatAsState(if (lit) 1f else 0f, tween(600), label = "flameLit")

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val litF = colorLerp
        val breathe = 1f + 0.035f * wave(t1)

        val outerTop = lerpColor(Color(0xFF8C9099), Color(0xFFFFB02E), litF)
        val outerBottom = lerpColor(Color(0xFF5E626B), Color(0xFFFF5A1F), litF)
        val midTop = lerpColor(Color(0xFFA7ABB3), Color(0xFFFFD23F), litF)
        val midBottom = lerpColor(Color(0xFF7C808A), Color(0xFFFF9F1C), litF)
        val coreTop = lerpColor(Color(0xFFD2D5DB), Color(0xFFFFF6BF), litF)
        val coreBottom = lerpColor(Color(0xFFB4B8C0), Color(0xFFFFD166), litF)

        val swayAmp = 0.05f * litF + 0.006f
        scale(breathe, breathe, pivot = Offset(w / 2f, h)) {
            rotate(degrees = 2.2f * wave(t1) * litF, pivot = Offset(w / 2f, h)) {
                drawFlameLayer(w, h, 1f, 1f + 0.05f * wave(t2, 0.1f) * litF, swayAmp * wave(t1), outerTop, outerBottom)
                drawFlameLayer(w, h, 0.66f, 1f + 0.08f * wave(t2, 0.45f) * litF, swayAmp * 1.2f * wave(t2, 0.3f), midTop, midBottom)
                drawFlameLayer(w, h, 0.38f, 1f + 0.12f * wave(t3, 0.7f) * litF, swayAmp * 1.4f * wave(t3), coreTop, coreBottom)
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, f: Float): Color = androidx.compose.ui.graphics.lerp(a, b, f.coerceIn(0f, 1f))

private fun DrawScope.drawFlameLayer(
    w: Float,
    h: Float,
    sizeFactor: Float,
    stretch: Float,
    sway: Float,
    top: Color,
    bottom: Color,
) {
    val lw = w * sizeFactor
    val lh = h * sizeFactor
    translate(left = (w - lw) / 2f, top = h - lh) {
        drawPath(
            flamePath(lw, lh, sway, stretch),
            brush = Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = lh),
        )
    }
}
