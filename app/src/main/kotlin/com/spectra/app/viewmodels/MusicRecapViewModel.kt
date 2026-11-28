package com.spectra.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectra.app.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

enum class RecapPeriod { YEAR, DAYS30, ALL }

data class RecapSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val minutes: Int,
    val plays: Int,
)

data class RecapArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val minutes: Int,
)

data class RecapSummary(
    val year: Int,
    val totalMinutes: Int,
    val songCount: Int,
    val artistCount: Int,
    val songs: List<RecapSong>,
    val artists: List<RecapArtist>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicRecapViewModel
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) : ViewModel() {
        val period = MutableStateFlow(RecapPeriod.YEAR)
        val summary = MutableStateFlow<RecapSummary?>(null)

        init {
            viewModelScope.launch {
                period.collectLatest { selected ->
                    summary.value = null
                    summary.value = withContext(Dispatchers.IO) { load(selected) }
                }
            }
        }

        fun select(value: RecapPeriod) {
            period.value = value
        }

        private suspend fun load(selected: RecapPeriod): RecapSummary {
            val now = LocalDateTime.now()
            val from =
                when (selected) {
                    RecapPeriod.YEAR -> LocalDateTime.of(now.year, 1, 1, 0, 0).minusNanos(1)
                    RecapPeriod.DAYS30 -> now.minusDays(30)
                    RecapPeriod.ALL -> LocalDateTime.of(2000, 1, 1, 0, 0)
                }
            val minutes = ((database.getTotalPlayTimeInRange(from, now).first() ?: 0L) / 60_000L).toInt()
            val songCount = database.getUniqueSongCountInRange(from, now).first()
            val songs =
                database
                    .mostPlayedSongsStats(from, 10, 0, now)
                    .first()
                    .map { s ->
                        RecapSong(
                            id = s.id,
                            title = s.title,
                            artist = s.artists.joinToString { it.name }.ifBlank { s.artistName.orEmpty() },
                            thumbnailUrl = s.thumbnailUrl,
                            minutes = ((s.timeListened ?: 0L) / 60_000L).toInt(),
                            plays = s.songCountListened,
                        )
                    }
            val allArtists = database.mostPlayedArtists(from, 1000, 0, now).first()
            val artists =
                allArtists
                    .sortedByDescending { it.timeListened ?: 0 }
                    .take(5)
                    .map { a ->
                        RecapArtist(
                            id = a.artist.id,
                            name = a.artist.name,
                            thumbnailUrl = a.artist.thumbnailUrl,
                            minutes = ((a.timeListened ?: 0) / 60_000L).toInt(),
                        )
                    }
            return RecapSummary(now.year, minutes, songCount, allArtists.size, songs, artists)
        }
    }
