package com.spectra.app.ui.component

import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectra.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration

val SpectraSoftWhite = Color(0xFFF6F7F9)

/**
 * Pembuka aplikasi: zoom masuk, logo naik dari garis paling bawah sampai atas, lalu teks "Spectra".
 */
@Composable
fun OpeningIntro(onFinished: () -> Unit) {
    val zoom = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        zoom.animateTo(1f, tween(550, easing = LinearOutSlowInEasing))
        reveal.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        text.animateTo(1f, tween(520, easing = LinearOutSlowInEasing))
        delay(650)
        exit.animateTo(1f, tween(280))
        onFinished()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - exit.value }
                .background(SpectraSoftWhite)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.graphicsLayer {
                    val z = 0.55f + 0.45f * zoom.value
                    scaleX = z
                    scaleY = z
                    alpha = zoom.value
                },
        ) {
            Image(
                painter = painterResource(R.drawable.spectra_logo),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(width = 220.dp, height = 109.dp)
                        .drawWithContent {
                            val top = size.height * (1f - reveal.value)
                            clipRect(top = top) { this@drawWithContent.drawContent() }
                        },
            )
            Spacer(Modifier.height(30.dp))
            Text(
                text = "Spectra",
                color = Color(0xFF0B0B0D),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier =
                    Modifier.graphicsLayer {
                        alpha = text.value
                        translationY = (1f - text.value) * 14.dp.toPx()
                    },
            )
        }
    }
}
