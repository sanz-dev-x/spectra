package com.spectra.app.ui.lockscreen

import android.content.Intent
import android.app.KeyguardManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.spectra.app.constants.LockScreenTextAlignKey
import com.spectra.app.constants.LockTextAlign
import com.spectra.app.ui.poster.loadPosterCover
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
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
private const val LYRIC_TICK_MS = 16L
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
    val lyricsOffset = song?.song?.lyricsOffset ?: 0
    var lyricPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, current.id, synced, lyricsOffset) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                lyricPosition = pc.player.currentPosition + lyricsOffset
                if (!isPlaying || !synced) break
                delay(LYRIC_TICK_MS)
            } while (isActive)
        }
    }

    val shape = RoundedCornerShape(30.dp)
    val (showCard) = rememberPreference(LockScreenCardKey, defaultValue = true)
    val (showLyrics) = rememberPreference(LockScreenLyricsKey, defaultValue = true)
    val (showClock) = rememberPreference(LockScreenClockKey, defaultValue = true)
    val (showNotifications) = rememberPreference(LockScreenNotificationsKey, defaultValue = true)
    val (torchPos) = rememberEnumPreference(LockScreenTorchPositionKey, LockShortcutPosition.LEFT)
    val (cameraPos) = rememberEnumPreference(LockScreenCameraPositionKey, LockShortcutPosition.RIGHT)
    val (textAlignPref) = rememberEnumPreference(LockScreenTextAlignKey, LockTextAlign.LEFT)
    val centered = textAlignPref == LockTextAlign.CENTER
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Gambar kunci hanya tampil bila HP dikunci dengan PIN, pola, atau kata sandi.
    val isSecure = remember { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }

    val songArtUrl = current.thumbnailUrl ?: artworkUrl
    val wallInk = Color.White

    // Mode teks di tengah: dua warna terbanyak dari cover membentuk gradasi latar.
    val coverPalette by produceState<LockPalette?>(null, songCoverUrl) {
        value =
            if (songCoverUrl.isNullOrBlank()) {
                null
            } else {
                loadPosterCover(context, songCoverUrl)?.let { bmp ->
                    withContext(Dispatchers.Default) { bmp.toLockPalette() }
                }
            }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalPlayerBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                LockGradientBackground(coverPalette, Modifier.fillMaxSize())
            }
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
                        }.padding(vertical = 16.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.TopCenter) {
                    if (showClock && !landscape) {
                        Clock3D(ink = wallInk, secure = isSecure)
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
                if (showLyrics) {
                    LyricsWindow(
                        lines = lines,
                        syncLines = syncLines,
                        positionProvider = { lyricPosition },
                        synced = synced,
                        notFound = !fetching && lyricsForSong != null && lines.isEmpty(),
                        ink = wallInk,
                        reduceMotion = reduceMotion,
                        centered = centered,
                        modifier = Modifier.weight(1f).padding(horizontal = LYRICS_SIDE_PADDING),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (showCard) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier =
                            Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth()
                                .playerGlass(shape, strength = if (mode == GlassMode.BRIGHT) 2f else 1f).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(songArtUrl).build(),
                                contentDescription = stringResource(R.string.lockscreen_artwork_desc),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                Text(
                                    text = current.title,
                                    color = ink,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = current.artists.joinToString { it.name },
                                    color = ink.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
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
                            restTrackHeight = 6.dp,
                            dragTrackHeight = 10.dp,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        )
                        // Waktu dirapatkan ke slider (area sentuh slider 48dp sengaja tumpang tindih dengan baris ini).
                        Row(Modifier.fillMaxWidth().offset(y = (-10).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            val timeStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 16.sp)
                            Text(makeTimeString(dragPosition ?: position), color = ink.copy(alpha = 0.72f), style = timeStyle)
                            Text(
                                if (duration > 0) "-" + makeTimeString((duration - (dragPosition ?: position)).coerceAtLeast(0L)) else "",
                                color = ink.copy(alpha = 0.72f),
                                style = timeStyle,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        LockControls(
                            isPlaying = isPlaying,
                            canPrev = canPrev,
                            canNext = canNext,
                            onPrevious = pc::seekToPrevious,
                            onPlayPause = pc::togglePlayPause,
                            onNext = pc::seekToNext,
                            ink = ink,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Box(Modifier.fillMaxWidth().height(SWIPE_BLOCK_HEIGHT).padding(horizontal = 24.dp)) {
                    SwipeHint(
                        ink = wallInk,
                        reduceMotion = reduceMotion,
                        onClick = onUnlock,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Box(Modifier.align(Alignment.CenterStart).requiredSize(56.dp), contentAlignment = Alignment.Center) {
                        ShortcutSlot(LockShortcutPosition.LEFT, torchPos, cameraPos, wallInk)
                    }
                    Box(Modifier.align(Alignment.CenterEnd).requiredSize(56.dp), contentAlignment = Alignment.Center) {
                        ShortcutSlot(LockShortcutPosition.RIGHT, torchPos, cameraPos, wallInk)
                    }
                }
            }
        }
    }
}

/** Tinggi blok "Geser ke atas": teks 20dp + jarak 9dp + garis 5dp. Pintasan senter/kamera melayang di sisinya tanpa mengubah posisi. */
private val SWIPE_BLOCK_HEIGHT = 34.dp
private val LYRICS_SIDE_PADDING = 20.dp

@Composable
private fun SwipeHint(ink: Color, reduceMotion: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hint = stringResource(R.string.lockscreen_swipe_up)
    val sweep =
        if (reduceMotion) {
            null
        } else {
            rememberInfiniteTransition(label = "swipeShimmer").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
                label = "swipeShimmerX",
            )
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = hint, onClick = onClick),
    ) {
        Text(
            text = hint,
            color = ink.copy(alpha = 0.6f),
            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            modifier =
                Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val p = sweep?.value ?: return@drawWithContent
                        val band = size.width * 0.5f
                        val x = -band + (size.width + band) * p
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.5f to ink,
                                    1f to Color.Transparent,
                                    startX = x,
                                    endX = x + band,
                                ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    },
        )
        Spacer(Modifier.height(9.dp))
        Box(Modifier.width(146.dp).height(5.dp).clip(CircleShape).background(ink.copy(alpha = 0.9f)))
    }
}

@Composable
private fun LockControls(
    isPlaying: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    ink: Color,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        LockGlyph(R.drawable.ls_skip_previous, 37.dp, canPrev, onPrevious, ink, stringResource(R.string.previous))
        LockGlyph(
            if (isPlaying) R.drawable.ios_pause else R.drawable.ios_play,
            47.dp,
            true,
            onPlayPause,
            ink,
            stringResource(if (isPlaying) R.string.pause else R.string.play),
        )
        LockGlyph(R.drawable.ls_skip_next, 37.dp, canNext, onNext, ink, stringResource(R.string.next))
    }
}

/** Tombol ikon polos: jarak antar pusat 100dp seperti di video acuan. */
@Composable
private fun LockGlyph(icon: Int, glyph: Dp, enabled: Boolean, onClick: () -> Unit, ink: Color, description: String) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, label = "lockGlyphScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(width = 100.dp, height = 56.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.35f }
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = ink, modifier = Modifier.size(glyph))
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

/**
 * Menempatkan teks sehingga garis dasarnya berjarak [top] dari tepi atas dan [bottom] dari tepi bawah layout.
 * Posisi jadi persis sama di semua font; dipakai agar jarak tanggal, jam, dan lirik mengikuti video acuan.
 */
private fun Modifier.baselineBox(top: Dp, bottom: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val baseline = placeable[FirstBaseline].let { if (it == AlignmentLine.Unspecified) placeable.height else it }
        val topPx = top.roundToPx()
        layout(placeable.width, topPx + bottom.roundToPx()) {
            placeable.placeRelative(0, topPx - baseline)
        }
    }

/** Gambar kunci (hanya bila HP terkunci sandi/pola), tanggal, dan jam besar dengan bayangan lembut. */
@Composable
private fun Clock3D(ink: Color, secure: Boolean, modifier: Modifier = Modifier) {
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
    val clockStyle =
        MaterialTheme.typography.displayLarge.copy(
            fontSize = 108.sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-3).sp,
            brush = Brush.verticalGradient(listOf(ink, androidx.compose.ui.graphics.lerp(ink, Color.Black, 0.14f))),
            shadow = Shadow(color = Color.Black.copy(alpha = 0.3f), offset = Offset(0f, 5f), blurRadius = 7f),
        )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Ruang ikon selalu disisakan agar tanggal dan jam tidak bergeser saat HP tanpa kunci.
        Box(Modifier.size(18.dp)) {
            if (secure) {
                Icon(painterResource(R.drawable.ls_lock), contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = date,
            color = ink.copy(alpha = 0.95f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 24.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.baselineBox(top = 28.dp, bottom = 0.dp),
        )
        Text(
            text = time,
            style = clockStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.baselineBox(top = 106.dp, bottom = 14.dp),
        )
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
    centered: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.headlineMedium.copy(fontSize = 38.sp, lineHeight = 48.sp)
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Box(modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.TopStart) {
        if (!synced) {
            val message =
                when {
                    lines.isNotEmpty() -> R.string.lockscreen_lyrics_unsynced
                    notFound -> R.string.lockscreen_lyrics_unavailable
                    else -> R.string.lockscreen_lyrics_searching
                }
            Text(
                stringResource(message),
                color = ink.copy(alpha = 0.9f),
                style = style,
                fontWeight = FontWeight.Bold,
                textAlign = align,
                modifier = Modifier.fillMaxWidth().padding(top = LYRICS_ACTIVE_TOP),
            )
            return@Box
        }
        val listState = rememberLazyListState()
        val index by remember(syncLines) { derivedStateOf { LyricsSync.index(syncLines, positionProvider()) } }
        LaunchedEffect(index, syncLines) {
            listState.animateScrollToItem(if (index < 0) 0 else index + 1)
        }
        val fadeMillis = if (reduceMotion) 140 else 320
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(top = LYRICS_ACTIVE_TOP, bottom = 320.dp),
            userScrollEnabled = false,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.07f to Color.Black,
                                    0.84f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
        ) {
            item(key = "gap") {
                val dots by animateFloatAsState(if (index < 0) 1f else 0f, tween(fadeMillis), label = "lockGap")
                Text(
                    text = LyricsSync.INSTRUMENTAL_TEXT,
                    color = ink,
                    style = style,
                    fontWeight = FontWeight.Bold,
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = dots },
                )
            }
            itemsIndexed(syncLines, key = { i, l -> "${i}_${l.time}" }) { i, line ->
                val active = i == index
                val target = if (active) 1f else if (i < index) 0.45f else if (i == index + 1) 0.55f else 0.4f
                val level by animateFloatAsState(target, tween(fadeMillis), label = "lockLine")
                val text = if (active) wordRevealText(line, positionProvider(), ink) else AnnotatedString(line.text)
                Text(
                    text = text,
                    color = ink,
                    style = style,
                    fontWeight = FontWeight.Bold,
                    textAlign = align,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = level },
                )
            }
        }
    }
}

/** Jarak dari tepi atas area lirik ke baris aktif (baris sebelumnya tetap terlihat di atasnya). */
private val LYRICS_ACTIVE_TOP = 132.dp

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
