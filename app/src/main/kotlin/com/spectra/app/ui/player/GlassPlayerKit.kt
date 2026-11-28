package com.spectra.app.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.spectra.app.ui.component.GlassQuality
import com.spectra.app.ui.component.LocalGlassQuality
import com.spectra.app.ui.component.glassPanel
import com.spectra.app.ui.glass.GlassLensGate
import com.spectra.app.ui.glass.GlassTouchLiquid
import com.spectra.app.ui.glass.GlassTouchLiquidSpec
import com.spectra.app.ui.glass.GlassTouchLiquidState
import com.spectra.app.ui.glass.glassTouchLiquidTrack
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode

/** Sumber backdrop milik kartu pemutar: panel kaca di atasnya mem-blur dan membiaskan latar ini. */
val LocalPlayerBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Latar kartu pemutar: mengikuti tema (hitam lembut atau putih-abu lembut), tidak memakai warna lagu. */
@Composable
fun PlayerThemedBackground(modifier: Modifier = Modifier) {
    val dark = LocalGlassMode.current == GlassMode.DARK
    Box(
        modifier =
            modifier.fillMaxSize().drawWithCache {
                val base =
                    Brush.verticalGradient(
                        if (dark) {
                            listOf(Color(0xFF1C1C1F), Color(0xFF0F0F11), Color(0xFF080809))
                        } else {
                            listOf(Color(0xFFF7F7F9), Color(0xFFEAEAEE), Color(0xFFDDDDE3))
                        },
                    )
                onDrawBehind {
                    drawRect(base)
                }
            },
    )
}

/**
 * Kaca cair untuk kartu pemutar: dominan refraksi/lensing dari backdrop Kyant (Android 13+), blur hanya tipis
 * agar latar tetap jernih. Distorsi cair kecil mengikuti sentuhan lalu mereda; specular tipis mengikuti sudut.
 * Di bawah Android 13 atau kualitas LOW memakai panel kaca biasa.
 */
@Composable
fun Modifier.playerGlass(
    shape: Shape,
    strength: Float = 1f,
): Modifier {
    val mode = LocalGlassMode.current
    val dark = mode == GlassMode.DARK
    val backdrop = LocalPlayerBackdrop.current
    if (backdrop == null || Build.VERSION.SDK_INT < 33 || LocalGlassQuality.current == GlassQuality.LOW) {
        return this.glassPanel(shape, strength = strength)
    }
    val fill = if (dark) Color.White.copy(alpha = 0.05f * strength) else Color.White.copy(alpha = 0.28f * strength)
    val liquidState = remember { GlassTouchLiquidState() }
    val liquid = remember { GlassTouchLiquid() }
    val specK = GlassTouchLiquidSpec.specK.pick(dark)
    return this.glassTouchLiquidTrack(liquidState).drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.15f)
            blur(2.dp.toPx())
            if (GlassLensGate.enabled) {
                liquid
                    .effect(
                        w = size.width,
                        h = size.height,
                        touchX = liquidState.x,
                        touchY = liquidState.y,
                        radius = minOf(GlassTouchLiquidSpec.RADIUS_DP.dp.toPx(), minOf(size.width, size.height) * 0.95f),
                        peak = GlassTouchLiquidSpec.PEAK_DP.dp.toPx(),
                        energy = liquidState.energy.value,
                        lightX = -0.55f,
                        lightY = -0.75f,
                        specK = specK,
                    )?.let { effect(it) }
            }
            lens(refractionHeight = 18.dp.toPx(), refractionAmount = 36.dp.toPx(), chromaticAberration = false)
        },
        highlight = { Highlight.Default.copy(alpha = 0.8f) },
        shadow = { Shadow(radius = 12.dp, alpha = if (dark) 0.35f else 0.14f) },
        innerShadow = { InnerShadow(radius = 6.dp, alpha = if (dark) 0.4f else 0.25f) },
        onDrawSurface = { drawRect(fill) },
    )
}

data class GlassBarItem(
    val icon: Int,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val label: String? = null,
    val onClick: () -> Unit,
)

/** Bar aksi bawah: hanya ikon transparan; item aktif ditandai titik kecil di bawah ikon. */
@Composable
fun GlassActionBar(
    items: List<GlassBarItem>,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        items.forEach { item ->
            val src = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .size(48.dp)
                        .alpha(if (item.enabled) 1f else 0.35f)
                        .clickable(interactionSource = src, indication = null, enabled = item.enabled, onClick = item.onClick),
            ) {
                if (item.label != null) {
                    Text(item.label, color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                } else {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = null,
                        tint = if (item.active) ink else ink.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box(Modifier.padding(top = 3.dp).size(4.dp).background(if (item.active) ink else Color.Transparent, CircleShape))
            }
        }
    }
}

/** Ukuran rongga kontrol transport, dipakai IosPlayerControls mode kaca. */
internal val GlassPlayButtonSize: Dp = 68.dp

