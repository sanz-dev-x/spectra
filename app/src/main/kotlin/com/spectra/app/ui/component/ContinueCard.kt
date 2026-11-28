package com.spectra.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.spectra.app.R
import com.spectra.app.ui.utils.resize

/**
 * Kartu "Lanjutkan" di Beranda: potret, sampul memenuhi kartu (tanpa efek kaca), gradasi gelap di bawah untuk
 * judul dan artis, tombol putar kecil di kanan.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPlay: Boolean = true,
    jelly: JellyScroll? = null,
) {
    val touch = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .jellyCard(touch, jelly)
                .clip(RoundedCornerShape(22.dp))
                .combinedClickable(interactionSource = touch, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = thumbnailUrl?.resize(600, 600),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val scrim =
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                            startY = size.height * 0.52f,
                            endY = size.height,
                        )
                    onDrawBehind { drawRect(scrim) }
                },
        )
        if (showPlay) Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp, top = 40.dp)
                .size(30.dp)
                .clip(CircleShape)
                .drawWithCache { onDrawBehind { drawRect(Color.Black.copy(alpha = 0.38f)) } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (isActive && isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
