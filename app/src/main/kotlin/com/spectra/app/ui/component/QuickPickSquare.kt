package com.spectra.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spectra.app.constants.QuickPickSize
import com.spectra.app.ui.utils.resize

/** Jumlah kolom yang muat penuh di lebar layar: 6x6 = 2 kotak besar, 4x4 = 3 kotak (rasio 6:4 = 3:2). */
fun QuickPickSize.columns(): Int =
    when (this) {
        QuickPickSize.SIZE_6X6 -> 2
        QuickPickSize.SIZE_4X4 -> 3
    }

/** Sisi kotak agar kolom pas memenuhi [availableWidth] dengan jarak [HomeGridGap]. */
fun QuickPickSize.side(availableWidth: Dp): Dp {
    val cols = columns()
    return (availableWidth - HomeGridGap * (cols - 1)) / cols
}

/**
 * Kartu lagu persegi lengkung (bukan lingkaran) dengan lengkung sudut sama seperti kartu history.
 * Sampul memenuhi kotak, judul di bagian bawah di atas gradasi gelap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPickSquare(
    title: String,
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    size: QuickPickSize,
    side: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    forceCrop: Boolean = false,
) {
    val shape = RoundedCornerShape(HomeCardRadius)
    val touchSource = remember { MutableInteractionSource() }
    val big = size == QuickPickSize.SIZE_6X6
    Box(
        modifier =
            modifier
                .songTouchAnimation(touchSource)
                .size(side)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
                .combinedClickable(interactionSource = touchSource, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        ItemThumbnail(
            thumbnailUrl = thumbnailUrl?.resize(if (big) 512 else 360, if (big) 512 else 360),
            isActive = isActive,
            isPlaying = isPlaying,
            shape = androidx.compose.ui.graphics.RectangleShape,
            forceCrop = forceCrop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .aspectRatio(if (big) 3.2f else 2.4f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )
        Text(
            text = title,
            style = if (big) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = if (big) 12.dp else 8.dp, vertical = if (big) 10.dp else 7.dp),
        )
    }
}
