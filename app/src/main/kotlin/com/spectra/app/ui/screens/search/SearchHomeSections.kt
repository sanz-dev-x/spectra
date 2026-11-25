/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.spectra.app.LocalDatabase
import com.spectra.app.R
import com.spectra.app.db.entities.Song
import com.spectra.app.ui.utils.resize
import java.time.LocalDateTime

private const val RecentLimit = 10
private const val MostPlayedLimit = 20
private val RecentCardSize = 140.dp
private val SearchHomeCoverShape = RoundedCornerShape(20.dp)

class SearchHomeState(
    val recent: List<Song>,
    val mostPlayed: List<Song>,
)

@Composable
fun rememberSearchHomeState(): SearchHomeState {
    val database = LocalDatabase.current
    val allTime = remember { LocalDateTime.of(1970, 1, 1, 0, 0) }
    val recent by remember { database.recentlyPlayedSongs(RecentLimit) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val mostPlayed by remember { database.mostPlayedSongs(fromTimeStamp = allTime, limit = MostPlayedLimit) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    return SearchHomeState(recent, mostPlayed)
}

private fun Song.artistLine() = orderedArtists.joinToString { it.name }

fun LazyListScope.searchHomeSections(
    state: SearchHomeState,
    onPlay: (Song) -> Unit,
) {
    if (state.recent.isNotEmpty()) {
        item(key = "search_home_recent_title") { SearchHomeTitle(R.string.search_recently_played) }
        item(key = "search_home_recent_row") {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.recent, key = { "search_recent_${it.id}" }) { song ->
                    Column(
                        modifier =
                            Modifier
                                .width(RecentCardSize)
                                .clip(SearchHomeCoverShape)
                                .clickable { onPlay(song) },
                    ) {
                        SearchHomeCover(song.thumbnailUrl, Modifier.size(RecentCardSize))
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = song.artistLine(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (state.mostPlayed.isNotEmpty()) {
        item(key = "search_home_most_title") { SearchHomeTitle(R.string.search_most_played) }
        itemsIndexed(state.mostPlayed, key = { _, song -> "search_most_${song.id}" }) { index, song ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(song) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(40.dp),
                )
                SearchHomeCover(song.thumbnailUrl, Modifier.size(52.dp), RoundedCornerShape(12.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artistLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(key = "search_home_end_spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SearchHomeTitle(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
    )
}

@Composable
private fun SearchHomeCover(
    url: String?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape = SearchHomeCoverShape,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url?.resize(400, 400)).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape),
    )
}
