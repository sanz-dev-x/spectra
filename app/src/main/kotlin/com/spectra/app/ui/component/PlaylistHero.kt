/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.spectra.app.R

/**
 * Header playlist layar penuh: cover (kolase 2x2 bila lebih dari satu) memenuhi lebar layar sampai ke bawah
 * status bar, memudar ke latar, judul besar di kiri bawah, lalu baris tombol Putar (pil putih), Acak, dan Lainnya.
 */
@Composable
fun PlaylistHero(
    thumbnails: List<String>,
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    coverOverride: String? = null,
    placeholderIcon: Int = R.drawable.queue_music,
    extra: @Composable ColumnScope.() -> Unit = {},
    coverOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val background = MaterialTheme.colorScheme.background
    val covers = remember(thumbnails, coverOverride) { if (coverOverride != null) listOf(coverOverride) else thumbnails.filter { it.isNotBlank() } }
    val scrim = remember(background) {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.38f),
            0.22f to Color.Transparent,
            0.5f to Color.Transparent,
            0.78f to background.copy(alpha = 0.82f),
            1f to background,
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            when {
                covers.isEmpty() ->
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(placeholderIcon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(96.dp),
                        )
                    }

                covers.size == 1 ->
                    AsyncImage(model = covers[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

                else ->
                    Column(Modifier.fillMaxSize()) {
                        for (row in 0..1) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                for (col in 0..1) {
                                    AsyncImage(
                                        model = covers[(row * 2 + col) % covers.size],
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
            }
            Box(Modifier.fillMaxSize().background(scrim))
            coverOverlay()
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                extra()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ios_play), null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            HeroCircleButton(R.drawable.shuffle, stringResource(R.string.shuffle), onShuffle)
            Spacer(Modifier.width(12.dp))
            HeroCircleButton(R.drawable.more_horiz, null, onMore)
        }
    }
}

@Composable
private fun HeroCircleButton(icon: Int, description: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
        modifier = Modifier.size(48.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), description, modifier = Modifier.size(22.dp))
        }
    }
}
