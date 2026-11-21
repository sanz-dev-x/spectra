package com.spectra.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spectra.app.ui.utils.resize

val HomeCardRadius = 14.dp
val HomeGridGap = 8.dp
val HomeSideInset = 14.dp
val QuickPickCardHeight = 56.dp
val QuickPickCardWidth = 156.dp

/** Kartu persegi panjang lengkung: sampul menempel penuh di sisi kiri (ikut lengkung sudut), judul di kanan, dengan garis tepi tipis. Dipakai grid 2 kolom di Beranda. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPickCard(
    title: String,
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = QuickPickCardHeight,
    forceCrop: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val touchSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .songTouchAnimation(touchSource)
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
                .combinedClickable(interactionSource = touchSource, indication = null, onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemThumbnail(
            thumbnailUrl = thumbnailUrl?.resize(200, 200),
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RectangleShape,
            forceCrop = forceCrop,
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 9.dp, end = 10.dp),
        )
    }
}
