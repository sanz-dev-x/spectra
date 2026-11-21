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
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.spectra.app.ui.component.GlassQuality
import com.spectra.app.ui.component.LocalGlassQuality
import com.spectra.app.ui.component.glassPanel
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
                val w = size.width
                val h = size.height
                val base =
                    Brush.verticalGradient(
                        if (dark) {
                            listOf(Color(0xFF1C1C1F), Color(0xFF0F0F11), Color(0xFF080809))
                        } else {
                            listOf(Color(0xFFF7F7F9), Color(0xFFEAEAEE), Color(0xFFDDDDE3))
                        },
                    )
                val glowA =
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.08f else 0.9f), Color.Transparent),
                        center = Offset(w * 0.18f, h * 0.10f),
                        radius = w * 0.95f,
                    )
                val glowB =
                    Brush.radialGradient(
                        listOf((if (dark) Color(0xFF8C93A6) else Color(0xFFC9CCD6)).copy(alpha = if (dark) 0.10f else 0.45f), Color.Transparent),
                        center = Offset(w * 0.92f, h * 0.60f),
                        radius = w * 0.9f,
                    )
                onDrawBehind {
                    drawRect(base)
                    drawRect(glowA)
                    drawRect(glowB)
                }
            },
    )
}

/**
 * Kaca cair untuk kartu pemutar: blur, saturasi, lensa pembias, highlight tepi, dan bayangan dalam,
 * semuanya dari backdrop Kyant (Android 13+). Di bawah itu atau kualitas LOW memakai panel kaca biasa.
 */
@Composable
fun Modifier.playerGlass(
    shape: Shape,
    strength: Float = 1f,
): Modifier {
    val dark = LocalGlassMode.current == GlassMode.DARK
    val backdrop = LocalPlayerBackdrop.current
    if (backdrop == null || Build.VERSION.SDK_INT < 33 || LocalGlassQuality.current == GlassQuality.LOW) {
        return this.glassPanel(shape, strength = strength)
    }
    val fill = if (dark) Color.White.copy(alpha = 0.09f * strength) else Color.White.copy(alpha = 0.52f * strength)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.35f)
            blur(16.dp.toPx())
            lens(refractionHeight = 14.dp.toPx(), refractionAmount = 28.dp.toPx(), chromaticAberration = true)
        },
        highlight = { Highlight.Default.copy(alpha = 1f) },
        shadow = { Shadow(radius = 14.dp, alpha = if (dark) 0.45f else 0.18f) },
        innerShadow = { InnerShadow(radius = 6.dp, alpha = if (dark) 0.5f else 0.35f) },
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

internal fun glassFullWidth(): Modifier = Modifier.fillMaxWidth().height(GlassPlayButtonSize + 16.dp)
