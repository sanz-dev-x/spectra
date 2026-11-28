/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.screens.wrapped.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.spectra.app.R
import com.spectra.app.db.entities.SongWithStats
import com.spectra.app.ui.component.glassPanel
import com.spectra.app.ui.screens.wrapped.components.WrappedInk
import com.spectra.app.ui.theme.bbh_bartle
import kotlinx.coroutines.delay

@Composable
fun WrappedTop5SongsScreen(topSongs: List<SongWithStats>, isVisible: Boolean) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            visible = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(800, delayMillis = 100)) + slideInVertically(tween(800, delayMillis = 100)),
            ) {
                Text(
                    text = stringResource(id = R.string.wrapped_top_5_songs_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = WrappedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }

            topSongs.forEachIndexed { index, song ->
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(500, delayMillis = 250 + index * 110)) + slideInVertically(tween(500, delayMillis = 250 + index * 110)) { it / 2 },
                ) {
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .padding(vertical = 3.dp)
                                .fillMaxWidth()
                                .glassPanel(shape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontFamily = bbh_bartle,
                            fontSize = if (index < 3) 26.sp else 20.sp,
                            color = WrappedInk.copy(alpha = if (index < 3) 1f else 0.75f),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(44.dp),
                        )
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = WrappedInk,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artists.joinToString(" ${stringResource(R.string.and)} ") { it.name }.ifBlank { song.artistName.orEmpty() },
                                color = WrappedInk.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${song.songCountListened}x",
                            color = WrappedInk.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
