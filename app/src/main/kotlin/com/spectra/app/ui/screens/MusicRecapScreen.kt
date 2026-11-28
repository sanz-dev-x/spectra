package com.spectra.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.spectra.app.LocalDatabase
import com.spectra.app.LocalPlayerAwareWindowInsets
import com.spectra.app.LocalPlayerConnection
import com.spectra.app.R
import com.spectra.app.extensions.toMediaItem
import com.spectra.app.ui.component.menuGroupSurface
import com.spectra.app.db.MusicDatabase
import com.spectra.app.ui.poster.loadPosterCover
import com.spectra.app.utils.recapToneColor
import com.spectra.app.utils.shareRecap
import com.spectra.app.viewmodels.MusicRecapViewModel
import com.spectra.app.viewmodels.RecapPeriod
import com.spectra.app.viewmodels.RecapSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PageMillis = 2000
private const val StoryPageCount = 6
private const val ProgressPrefs = "music_recap_progress"
private const val ProgressKey = "page"

@Composable
fun MusicRecapScreen(
    navController: NavController,
    viewModel: MusicRecapViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    var storyStart by rememberSaveable { mutableStateOf(-1) }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val shareNow: () -> Unit = {
        summary?.let { current -> scope.launch { runCatching { shareRecap(context, current) } } }
    }

    BackHandler(enabled = storyStart >= 0) { storyStart = -1 }

    val current = summary
    val topSongId = current?.songs?.firstOrNull()?.id
    val playingState = playerConnection?.isPlaying?.collectAsStateWithLifecycle()
    val playing = playingState?.value == true

    // Tombol utama: jeda / lanjut musik recap (lagu teratas diputar ulang selama recap).
    val toggleMusic: () -> Unit = {
        val connection = playerConnection
        if (connection != null) {
            if (connection.isPlaying.value) {
                connection.player.pause()
            } else if (topSongId != null && connection.player.currentMediaItem?.mediaId == topSongId) {
                connection.player.play()
            } else if (topSongId != null) {
                scope.launch { playRecapMusic(database, playerConnection, topSongId) }
            }
        }
    }

    if (storyStart >= 0 && current != null && current.songs.isNotEmpty()) {
        RecapStory(summary = current, startPage = storyStart, onClose = { storyStart = -1 }, onShare = shareNow)
    } else {
        RecapOverview(
            summary = current,
            period = period,
            playing = playing,
            onBack = { navController.navigateUp() },
            onPeriod = viewModel::select,
            onStart = { storyStart = it },
            onToggleMusic = toggleMusic,
            onShare = shareNow,
        )
    }
}

private suspend fun playRecapMusic(
    database: MusicDatabase,
    connection: com.spectra.app.playback.PlayerConnection?,
    songId: String,
) {
    val player = connection?.player ?: return
    val song = database.song(songId).first() ?: return
    connection.playQueue(
        com.spectra.app.playback.queues.ListQueue(title = song.title, items = listOf(song.toMediaItem())),
    )
    delay(600)
    player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
}

@Composable
private fun RecapOverview(
    summary: RecapSummary?,
    period: RecapPeriod,
    playing: Boolean,
    onBack: () -> Unit,
    onPeriod: (RecapPeriod) -> Unit,
    onStart: (Int) -> Unit,
    onToggleMusic: () -> Unit,
    onShare: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
    ) {
        // Header tetap di atas (bukan bagian daftar), jadi teksnya tidak pernah menimpa kartu saat digulir.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack).padding(8.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recap_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = summary != null && summary.songs.isNotEmpty(), onClick = onShare),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.share), contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
        ) {
            if (summary == null) {
                item(key = "recap_loading") {
                    Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                item(key = "recap_summary") {
                    Column(
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .menuGroupSurface()
                                .padding(20.dp),
                    ) {
                        Text(stringResource(R.string.recap_hero_title, summary.year), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ink)
                        Spacer(Modifier.height(12.dp))
                        Text(summary.totalMinutes.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ink, lineHeight = 36.sp)
                        Text(stringResource(R.string.recap_minutes_listened), fontSize = 15.sp, color = muted)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RecapPill(stringResource(R.string.recap_songs_count, summary.songCount), ink)
                            RecapPill(stringResource(R.string.recap_artists_count, summary.artistCount), ink)
                        }
                    }
                }
                item(key = "recap_periods") {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            RecapPeriod.YEAR to R.string.recap_period_year,
                            RecapPeriod.DAYS30 to R.string.recap_period_30,
                            RecapPeriod.ALL to R.string.recap_period_all,
                        ).forEach { (value, label) ->
                            val selected = value == period
                            Text(
                                text = stringResource(label),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = if (selected) MaterialTheme.colorScheme.surface else ink,
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) ink else Color.Transparent)
                                        .border(BorderStroke(1.dp, if (selected) ink else MaterialTheme.colorScheme.outlineVariant), CircleShape)
                                        .clickable { onPeriod(value) }
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
                if (summary.songs.isEmpty()) {
                    item(key = "recap_empty") {
                        Text(stringResource(R.string.recap_empty), color = muted, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
                    }
                } else {
                    item(key = "recap_buttons") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(CircleShape)
                                        .background(ink)
                                        .clickable(onClick = onToggleMusic),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(if (playing) R.drawable.pause else R.drawable.play),
                                    null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(if (playing) R.string.recap_pause else R.string.recap_continue),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.surface,
                                )
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        .clickable { onStart(0) },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.graphic_eq), null, tint = ink, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.recap_watch), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                            }
                        }
                    }
                    item(key = "recap_top_title") {
                        Text(
                            stringResource(R.string.recap_top_songs),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink,
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
                        )
                    }
                    item(key = "recap_top_list") {
                        Column(
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .menuGroupSurface()
                                    .padding(vertical = 6.dp, horizontal = 14.dp),
                        ) {
                            summary.songs.forEachIndexed { index, song ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        (index + 1).toString(),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index == 0) ink else muted,
                                        modifier = Modifier.width(32.dp),
                                    )
                                    AsyncImage(
                                        model = song.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${song.artist} · ${stringResource(R.string.recap_minutes_short, song.minutes)}",
                                            fontSize = 13.sp,
                                            color = muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapPill(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier =
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun rememberCoverTone(
    url: String?,
    fallback: Color,
): Color {
    val context = LocalContext.current
    var tone by remember(url) { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        val bitmap: Bitmap = loadPosterCover(context, url) ?: return@LaunchedEffect
        tone = withContext(Dispatchers.Default) { Color(recapToneColor(bitmap)) }
    }
    return tone
}

// Tinggi area header cerita (bar progres + judul + tombol tutup) di bawah status bar.
// Isi halaman diberi jarak sebesar ini supaya teks tidak pernah menimpa header.
private val StoryHeaderHeight = 76.dp

@Composable
private fun RecapStory(
    summary: RecapSummary,
    startPage: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val topSongId = summary.songs.firstOrNull()?.id

    // Musik recap tetap menyala setelah cerita ditutup; tombol "Jeda Recap" di ringkasan yang mengontrolnya.
    LaunchedEffect(playerConnection, topSongId) {
        val id = topSongId ?: return@LaunchedEffect
        val player = playerConnection?.player ?: return@LaunchedEffect
        if (player.currentMediaItem?.mediaId == id) {
            player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            if (!player.isPlaying) player.play()
        } else {
            playRecapMusic(database, playerConnection, id)
        }
    }

    var page by remember { mutableIntStateOf(startPage.coerceIn(0, StoryPageCount - 1)) }
    var held by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    fun goTo(target: Int) {
        if (target >= StoryPageCount) onClose() else page = target.coerceAtLeast(0)
    }

    LaunchedEffect(page) {
        context.getSharedPreferences(ProgressPrefs, Context.MODE_PRIVATE).edit().putInt(ProgressKey, if (page >= StoryPageCount - 1) 0 else page).apply()
    }

    var settledPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(page, held) {
        if (settledPage != page) {
            progress.snapTo(0f)
            settledPage = page
        }
        if (held) return@LaunchedEffect
        val remaining = ((1f - progress.value) * PageMillis).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        // Tiap halaman tampil 2 detik; halaman terakhir (penutup) berhenti di sana.
        if (page < StoryPageCount - 1) goTo(page + 1)
    }

    val songTone = rememberCoverTone(summary.songs.firstOrNull()?.thumbnailUrl, Color(0xFF6B2A2A))
    val artistTone = rememberCoverTone(summary.artists.firstOrNull()?.thumbnailUrl, Color(0xFF6B6B70))
    val target = if (page == 2 || page == 4) artistTone else songTone
    val tone by animateColorAsState(target, tween(600), label = "recapTone")

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(lerp(tone, Color.White, 0.10f), tone, lerp(tone, Color.Black, 0.78f), lerp(tone, Color.Black, 0.9f)),
                    ),
                ).pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            held = true
                            tryAwaitRelease()
                            held = false
                        },
                        onTap = { offset ->
                            if (offset.x < size.width * 0.34f) goTo(page - 1) else goTo(page + 1)
                        },
                    )
                },
    ) {
        // Isi halaman: dimulai di bawah header dan dipusatkan di sisa layar, jadi tidak menimpa apa pun.
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = StoryHeaderHeight, bottom = 28.dp),
        ) {
            Crossfade(targetState = page, animationSpec = tween(350), label = "recapPage") { index ->
                when (index) {
                    0 -> StoryIntro(summary)
                    1 -> StoryTopSong(summary)
                    2 -> StoryTopArtist(summary)
                    3 -> StoryTopSongs(summary)
                    4 -> StoryTopArtists(summary)
                    else -> StoryClosing(summary, onShare)
                }
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(StoryPageCount) { i ->
                    val fraction = if (i < page) 1f else if (i == page) progress.value else 0f
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.spectra_launcher_mark), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.recap_hero_title, summary.year),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.close), null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private val SoftWhite = Color.White.copy(alpha = 0.78f)

@Composable
private fun StoryIntro(summary: RecapSummary) {
    // Angka menit menghitung naik dengan cepat seperti di video referensi.
    val counter = remember { Animatable(0f) }
    LaunchedEffect(summary.totalMinutes) {
        counter.snapTo(0f)
        counter.animateTo(summary.totalMinutes.toFloat(), tween(700, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.recap_hero_title, summary.year), color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.recap_played_for), color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(counter.value.toInt().toString(), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black, lineHeight = 70.sp)
        Text(stringResource(R.string.recap_minutes_word), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text(
            "${stringResource(R.string.recap_songs_count, summary.songCount)}  ·  ${stringResource(R.string.recap_artists_count, summary.artistCount)}",
            color = SoftWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StoryTopSong(summary: RecapSummary) {
    val song = summary.songs.firstOrNull() ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.recap_top_songs), color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(22.dp))
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(26.dp))
        Text(song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(song.artist, color = SoftWhite, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        StoryPill(stringResource(R.string.recap_played_times, song.plays))
    }
}

@Composable
private fun StoryTopArtist(summary: RecapSummary) {
    val artist = summary.artists.firstOrNull() ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.recap_top_artists), color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(22.dp))
        AsyncImage(
            model = artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(1f).clip(CircleShape).border(3.dp, Color.White.copy(alpha = 0.92f), CircleShape),
        )
        Spacer(Modifier.height(26.dp))
        Text(artist.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        StoryPill(stringResource(R.string.recap_minutes_full, artist.minutes))
    }
}

@Composable
private fun StoryTopSongs(summary: RecapSummary) {
    val songs = summary.songs.take(5)
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.recap_top_n_songs, songs.size), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        songs.forEachIndexed { index, song ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((index + 1).toString(), color = if (index == 0) Color.White else SoftWhite, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(32.dp))
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = SoftWhite, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StoryTopArtists(summary: RecapSummary) {
    val artists = summary.artists.take(5)
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.recap_top_n_artists, artists.size), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        artists.forEachIndexed { index, artist ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((index + 1).toString(), color = if (index == 0) Color.White else SoftWhite, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(32.dp))
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.recap_minutes_short, artist.minutes), color = SoftWhite, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StoryClosing(
    summary: RecapSummary,
    onShare: () -> Unit,
) {
    val appName = stringResource(R.string.app_name)
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(
            painter = painterResource(R.drawable.spectra_launcher_mark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(76.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(appName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(36.dp))
        Text(stringResource(R.string.recap_that_is), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.recap_thanks, appName), color = SoftWhite, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.recap_hero_title, summary.year), color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier.clip(CircleShape).background(Color.White).clickable(onClick = onShare).padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.share), null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.recap_share), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StoryPill(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
