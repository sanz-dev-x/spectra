package com.spectra.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.spectra.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INTRO_TEXT = "Spectra"
private const val LETTER_STEP_MS = 70L
private const val LETTER_MS = 420

private val IntroBase = Color(0xFF0A0A0B)
private val IntroTop = Color(0xFF000000)
private val IntroBottom = Color(0xFF2E2E33)
private val IntroGlow = Color(0xFF8A8A92)
private val IntroInk = Color(0xFFF2F2F4)

/**
 * Opening bergaya Canva: latar hitam ke abu-abu dengan glow yang bergeser pelan, logo kecil
 * muncul dengan pantulan pegas lalu tergambar dari bawah ke atas, teks "Spectra" naik huruf
 * demi huruf, garis tipis melebar, lalu seluruh isi memudar keluar.
 */
@Composable
fun OpeningIntro(onFinished: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
        onDispose {}
    }

    val bg = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.55f) }
    val logoAlpha = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val letters = remember { INTRO_TEXT.map { Animatable(0f) } }
    val line = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { bg.animateTo(1f, tween(500, easing = LinearEasing)) }
        launch { glow.animateTo(1f, tween(3200, easing = LinearEasing)) }
        delay(250)
        launch { logoAlpha.animateTo(1f, tween(280, easing = LinearEasing)) }
        launch {
            logoScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
        launch { reveal.animateTo(1f, tween(750, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))) }
        delay(600)
        letters.forEachIndexed { i, a ->
            launch {
                delay(i * LETTER_STEP_MS)
                a.animateTo(1f, tween(LETTER_MS, easing = FastOutSlowInEasing))
            }
        }
        delay(INTRO_TEXT.length * LETTER_STEP_MS)
        line.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(550)
        exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(IntroBase)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(IntroTop, IntroBottom)),
                        alpha = bg.value,
                    )
                    val g = glow.value
                    val center = Offset(size.width * (0.3f + 0.4f * g), size.height * (0.62f - 0.22f * g))
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(IntroGlow.copy(alpha = 0.22f * bg.value), Color.Transparent),
                                center = center,
                                radius = size.minDimension * 0.75f,
                            ),
                        radius = size.minDimension * 0.75f,
                        center = center,
                    )
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.graphicsLayer {
                    val e = exit.value
                    alpha = 1f - e
                    scaleX = 1f + 0.06f * e
                    scaleY = 1f + 0.06f * e
                },
        ) {
            Image(
                painter = painterResource(R.drawable.spectra_intro_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier =
                    Modifier
                        .width(112.dp)
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                            alpha = logoAlpha.value
                        }
                        .drawWithContent {
                            val top = size.height * (1f - reveal.value)
                            clipRect(top = top) { this@drawWithContent.drawContent() }
                        },
            )
            Spacer(Modifier.height(22.dp))
            Row {
                INTRO_TEXT.forEachIndexed { i, ch ->
                    val a = letters[i]
                    Text(
                        text = ch.toString(),
                        color = IntroInk,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 4.sp,
                        modifier =
                            Modifier.graphicsLayer {
                                alpha = a.value
                                translationY = (1f - a.value) * 16.dp.toPx()
                            },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier =
                    Modifier
                        .width(44.dp * line.value)
                        .height(2.dp)
                        .background(IntroInk.copy(alpha = 0.55f)),
            )
        }
    }
}
