package com.spectra.app.ui.lockscreen

import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.spectra.app.constants.LockScreenArtistWallpaperKey
import com.spectra.app.constants.LockScreenCameraPositionKey
import com.spectra.app.constants.LockScreenCardKey
import com.spectra.app.constants.LockScreenClockKey
import com.spectra.app.constants.LockScreenLyricsKey
import com.spectra.app.constants.LockScreenNotificationsKey
import com.spectra.app.constants.LockScreenTorchPositionKey
import com.spectra.app.constants.LockScreenWallpaperPathKey
import com.spectra.app.constants.LockShortcutPosition
import com.spectra.app.utils.rememberEnumPreference
import com.spectra.app.utils.rememberPreference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.spectra.app.R
import com.spectra.app.db.entities.LyricsEntity
import com.spectra.app.di.LyricsHelperEntryPoint
import com.spectra.app.lyrics.LyricsEntry
import com.spectra.app.lyrics.LyricsSync
import com.spectra.app.lyrics.LyricsUtils
import com.spectra.app.playback.PlayerConnection
import com.spectra.app.playback.resolveDurationMs
import com.spectra.app.ui.player.IosPlayerControls
import com.spectra.app.ui.player.IosPlayerSlider
import com.spectra.app.ui.player.LocalPlayerBackdrop
import com.spectra.app.ui.player.playerGlass
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode
import com.spectra.app.utils.makeTimeString
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val POSITION_TICK_MS = 250L
private const val LYRIC_TICK_MS = 33L
private const val WORD_FADE_MS = 280f

@Composable
fun LockScreenPlayer(
    pc: PlayerConnection,
    onUnlock: () -> Unit,
    onUnlockToNotifications: () -> Unit,
    onEmpty: () -> Unit,
) {
    val context = LocalContext.current
    val meta by pc.mediaMetadata.collectAsStateWithLifecycle()
    val song by pc.currentSong.collectAsStateWithLifecycle()
    val lyricsEntity by pc.currentLyrics.collectAsStateWithLifecycle()
    val isPlaying by pc.isPlaying.collectAsStateWithLifecycle()
    val canPrev by pc.canSkipPrevious.collectAsStateWithLifecycle()
    val canNext by pc.canSkipNext.collectAsStateWithLifecycle()

    val reduceMotion =
        remember {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    val mode = LocalGlassMode.current
    val scheme = MaterialTheme.colorScheme
    val ink =
        when (mode) {
            GlassMode.DARK -> Color.White
            GlassMode.BRIGHT -> Color(0xFF14141A)
            GlassMode.OFF -> scheme.onSurface
        }

    val current = meta
    LaunchedEffect(current == null) { if (current == null) onEmpty() }
    if (current == null) return

    val artworkUrl = song?.orderedArtists?.firstOrNull()?.thumbnailUrl ?: current.thumbnailUrl
    val songCoverUrl = current.thumbnailUrl ?: artworkUrl
    val backdrop = rememberLayerBackdrop()

    // Posisi dan durasi hanya diperbarui saat layar terlihat dan lagu diputar.
    var position by remember { mutableLongStateOf(pc.player.currentPosition) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(isPlaying, current.id) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                duration = resolveDurationMs(pc.player.duration, current.duration)
                position = pc.player.currentPosition.let { if (duration > 0) it.coerceAtMost(duration) else it }
                if (!isPlaying) break
                delay(POSITION_TICK_MS)
            } while (isActive)
        }
    }

    // Lirik: ambil dari database; bila belum ada, cari sekali per lagu.
    val lyricsForSong = lyricsEntity?.takeIf { it.id == current.id }
    var fetching by remember(current.id) { mutableStateOf(false) }
    LaunchedEffect(current.id, lyricsForSong == null) {
        if (lyricsForSong != null) return@LaunchedEffect
        fetching = true
        try {
            withContext(Dispatchers.IO) {
                val helper =
                    EntryPointAccessors
                        .fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java)
                        .lyricsHelper()
                val result = helper.getLyrics(current)
                pc.database.query { upsert(LyricsEntity(current.id, result.lyrics, result.provider)) }
            }
        } catch (_: Exception) {
        } finally {
            fetching = false
        }
    }
    val rawLyrics = lyricsForSong?.lyrics
    val lines by produceState<List<LyricsEntry>>(emptyList(), rawLyrics) {
        value =
            withContext(Dispatchers.Default) {
                if (rawLyrics.isNullOrBlank() || rawLyrics == LyricsEntity.LYRICS_NOT_FOUND) {
                    emptyList()
                } else {
                    LyricsUtils.parseLyrics(rawLyrics)
                }
            }
    }
    val synced = lines.size > 1 && lines.any { it.time > 0L }
    val syncLines = remember(lines) { LyricsSync.mainLines(lines) }
    // Posisi khusus lirik: diperbarui rapat agar kata muncul tepat waktu tanpa merekomposisi seluruh layar.
    var lyricPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, current.id, synced) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                lyricPosition = pc.player.currentPosition
                if (!isPlaying || !synced) break
                delay(LYRIC_TICK_MS)
            } while (isActive)
        }
    }

    val shape = RoundedCornerShape(32.dp)
    val (showCard) = rememberPreference(LockScreenCardKey, defaultValue = true)
    val (showLyrics) = rememberPreference(LockScreenLyricsKey, defaultValue = true)
    val (showClock) = rememberPreference(LockScreenClockKey, defaultValue = true)
    val (showNotifications) = rememberPreference(LockScreenNotificationsKey, defaultValue = true)
    val (artistWallpaper) = rememberPreference(LockScreenArtistWallpaperKey, defaultValue = true)
    val (customWallpaper) = rememberPreference(LockScreenWallpaperPathKey, defaultValue = "")
    val (torchPos) = rememberEnumPreference(LockScreenTorchPositionKey, LockShortcutPosition.LEFT)
    val (cameraPos) = rememberEnumPreference(LockScreenCameraPositionKey, LockShortcutPosition.RIGHT)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Wallpaper: artwork artis bila aktif dan tersedia; selain itu gambar pilihan pengguna.
    val wallpaperModel: Any? =
        when {
            artistWallpaper && !songCoverUrl.isNullOrBlank() -> songCoverUrl
            customWallpaper.isNotEmpty() -> java.io.File(customWallpaper)
            else -> songCoverUrl
        }
    val songArtUrl = current.thumbnailUrl ?: artworkUrl
    val wallInk = Color.White

    androidx.compose.runtime.CompositionLocalProvider(LocalPlayerBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Latar: gambar yang sama diperbesar lalu di-blur; gambar utuh (tanpa crop) di atasnya.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(wallpaperModel).crossfade(!reduceMotion).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(60.dp),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context).data(wallpaperModel).crossfade(!reduceMotion).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Scrim netral untuk keterbacaan jam, lirik, dan kontrol di atas wallpaper terang.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.4f to Color.Black.copy(alpha = 0.12f),
                        0.65f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Black.copy(alpha = 0.6f),
                    ),
                ),
            )
            Icon(
                painter = painterResource(R.drawable.small_icon),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding()
                        .padding(top = 0.dp, end = 36.dp)
                        .size(16.dp),
            )
            var swipe by remember { mutableStateOf(0f) }
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { swipe = 0f },
                                onDragEnd = { if (swipe < -120f) onUnlock() },
                                onVerticalDrag = { _, dy -> swipe += dy },
                            )
                        }.padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    if (showClock && !landscape) {
                        Clock3D(ink = wallInk)
                    }
                    if (showNotifications) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(onClickLabel = stringResource(R.string.lockscreen_show_notifications), onClick = onUnlockToNotifications),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.notification),
                                contentDescription = stringResource(R.string.lockscreen_show_notifications),
                                tint = wallInk,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (showLyrics) {
                    LyricsWindow(
                        lines = lines,
                        syncLines = syncLines,
                        positionProvider = { lyricPosition },
                        synced = synced,
                        notFound = !fetching && lyricsForSong != null && lines.isEmpty(),
                        ink = wallInk,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (showCard) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (mode == GlassMode.OFF) {
                                        Modifier.background(scheme.surfaceContainer, shape)
                                    } else {
                                        Modifier.playerGlass(shape, strength = if (mode == GlassMode.BRIGHT) 2f else 1f)
                                    },
                                ).padding(16.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(songArtUrl).build(),
                                contentDescription = stringResource(R.string.lockscreen_artwork_desc),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                Text(
                                    text = current.title,
                                    color = ink,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = current.artists.joinToString { it.name },
                                    color = ink.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Column {
                            IosPlayerSlider(
                                position = dragPosition ?: position,
                                duration = duration,
                                enabled = duration > 0,
                                onPreview = { dragPosition = it },
                                onFinished = {
                                    dragPosition?.let { pc.seekTo(it); position = it }
                                    dragPosition = null
                                },
                                ink = ink,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(makeTimeString(dragPosition ?: position), color = ink.copy(alpha = 0.78f), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    if (duration > 0) "-" + makeTimeString((duration - (dragPosition ?: position)).coerceAtLeast(0L)) else "",
                                    color = ink.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        IosPlayerControls(
                            isPlaying = isPlaying,
                            playIcon = null,
                            canSkipPrevious = canPrev,
                            canSkipNext = canNext,
                            onPrevious = pc::seekToPrevious,
                            onPlayPause = pc::togglePlayPause,
                            onNext = pc::seekToNext,
                            ink = ink,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ShortcutSlot(LockShortcutPosition.LEFT, torchPos, cameraPos, wallInk)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable(onClickLabel = stringResource(R.string.lockscreen_swipe_up), onClick = onUnlock),
                    ) {
                        Text(
                            text = stringResource(R.string.lockscreen_swipe_up),
                            color = wallInk,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(140.dp).height(5.dp).clip(CircleShape).background(wallInk.copy(alpha = 0.85f)))
                    }
                    ShortcutSlot(LockShortcutPosition.RIGHT, torchPos, cameraPos, wallInk)
                }
            }
        }
    }
}

@Composable
private fun ShortcutSlot(side: LockShortcutPosition, torchPos: LockShortcutPosition, cameraPos: LockShortcutPosition, ink: Color) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        when {
            torchPos == side -> TorchButton(ink)
            cameraPos == side -> CameraButton(ink)
        }
    }
}

@Composable
private fun RoundShortcut(icon: Int, description: String, active: Boolean, ink: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.Black.copy(alpha = 0.45f))
                .clickable(onClickLabel = description, onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = if (active) Color.Black else ink)
    }
}

@Composable
private fun TorchButton(ink: Color) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(CameraManager::class.java) }
    val cameraId = remember { runCatching { manager.cameraIdList.firstOrNull { id -> manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull() }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(cameraId) {
        val callback =
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) on = enabled
                }
            }
        if (cameraId != null) manager.registerTorchCallback(callback, null)
        onDispose { if (cameraId != null) manager.unregisterTorchCallback(callback) }
    }
    if (cameraId == null) return
    RoundShortcut(R.drawable.flashlight, stringResource(R.string.lockscreen_flashlight), on, ink) {
        runCatching { manager.setTorchMode(cameraId, !on) }
    }
}

@Composable
private fun CameraButton(ink: Color) {
    val context = LocalContext.current
    RoundShortcut(R.drawable.camera, stringResource(R.string.lockscreen_camera), false, ink) {
        runCatching {
            context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Jam dengan kedalaman lembut: lapisan bayangan statis di belakang dan gradien highlight di depan. Tanpa animasi idle. */
@Composable
private fun Clock3D(ink: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - (value.second * 1000L + value.nano / 1_000_000L))
        }
    }
    val locale = Locale.getDefault()
    val separator = if (locale.language == "in" || locale.language == "id") "." else ":"
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH${separator}mm" else "h${separator}mm", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
    val base = TextStyle(fontSize = 88.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.lock), contentDescription = null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = date, color = ink.copy(alpha = 0.92f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Box {
            for (i in 4 downTo 1) {
                Text(
                    text = time,
                    style = base,
                    color = Color.Black.copy(alpha = 0.16f),
                    modifier = Modifier.offset(x = (i * 0.7f).dp, y = (i * 1.1f).dp).clearAndSetSemantics {},
                )
            }
            Text(
                text = time,
                style = base.copy(brush = Brush.verticalGradient(listOf(ink, ink.copy(alpha = 0.78f)))),
            )
        }
    }
}

@Composable
private fun LyricsWindow(
    lines: List<LyricsEntry>,
    syncLines: List<LyricsEntry>,
    positionProvider: () -> Long,
    synced: Boolean,
    notFound: Boolean,
    ink: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.headlineMedium
    Box(modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.TopStart) {
        if (!synced) {
            val message =
                when {
                    lines.isNotEmpty() -> R.string.lockscreen_lyrics_unsynced
                    notFound -> R.string.lockscreen_lyrics_unavailable
                    else -> R.string.lockscreen_lyrics_searching
                }
            Text(stringResource(message), color = ink.copy(alpha = 0.9f), style = style)
            return@Box
        }
        val frame = LyricsSync.frame(syncLines, positionProvider())
        val stateKey = (frame.index + 1) * 2 + if (frame.instrumental) 1 else 0
        AnimatedContent(
            targetState = stateKey,
            transitionSpec = {
                if (reduceMotion) {
                    ContentTransform(fadeIn(tween(0)), fadeOut(tween(0)))
                } else {
                    (slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(280))) togetherWith
                        (slideOutVertically(tween(280)) { -it / 6 } + fadeOut(tween(200)))
                }
            },
            label = "lockLyrics",
        ) { key ->
            val index = key / 2 - 1
            val instrumental = key % 2 == 1
            val live = LyricsSync.frame(syncLines, positionProvider())
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (index < 0 && instrumental) {
                    Text(
                        text = LyricsSync.INSTRUMENTAL_TEXT,
                        color = ink,
                        style = style,
                        fontWeight = FontWeight.Bold,
                    )
                }
                for (i in (index - 1)..(index + 2)) {
                    val line = syncLines.getOrNull(i) ?: continue
                    val active = i == index
                    if (active && instrumental) {
                        Text(
                            text = LyricsSync.INSTRUMENTAL_TEXT,
                            color = ink,
                            style = style,
                            fontWeight = FontWeight.Bold,
                        )
                        continue
                    }
                    val animatePos = if (active && live.index == index) positionProvider() else null
                    Text(
                        text = wordRevealText(line, animatePos, ink),
                        color = ink,
                        style = style,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(if (active) 1f else if (i < index) 0.4f else if (i == index + 1) 0.6f else 0.3f),
                    )
                }
            }
        }
    }
}

/** Tiap kata memudar masuk (fade) sesaat setelah waktunya tiba; kata yang belum tiba hampir transparan. */
private fun wordRevealText(line: LyricsEntry, positionMs: Long?, ink: Color): AnnotatedString {
    val words = line.words
    if (positionMs == null || words.isNullOrEmpty()) return AnnotatedString(line.text)
    return buildAnnotatedString {
        words.forEachIndexed { i, w ->
            val progress = ((positionMs - w.startTime * 1000.0) / WORD_FADE_MS).toFloat().coerceIn(0f, 1f)
            val eased = progress * progress * (3f - 2f * progress)
            withStyle(SpanStyle(color = ink.copy(alpha = 0.18f + 0.82f * eased))) {
                append(w.text)
                if (w.hasTrailingSpace && i < words.lastIndex) append(" ")
            }
        }
    }
}
