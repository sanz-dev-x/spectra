package com.spectra.app.ui.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.spectra.app.LocalDatabase
import com.spectra.app.LocalPlayerConnection
import com.spectra.app.R
import com.spectra.app.db.entities.LyricsEntity
import com.spectra.app.lyrics.LyricsEntry
import com.spectra.app.lyrics.LyricsUtils
import com.spectra.app.models.MediaMetadata
import com.spectra.innertube.YouTube
import com.spectra.innertube.models.SongItem
import com.spectra.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Warna dominan cover yang dilembutkan (bukan warna asli yang tajam). Unspecified selama cover belum selesai dibaca.
 */
@Composable
fun rememberCoverTint(thumbnailUrl: String?): Color {
    val context = LocalContext.current
    var tint by remember { mutableStateOf(Color.Unspecified) }
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl.isNullOrBlank()) {
            tint = Color.Unspecified
            return@LaunchedEffect
        }
        val extracted =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(thumbnailUrl)
                            .size(96, 96)
                            .allowHardware(false)
                            .build()
                    val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@runCatching null
                    val palette = Palette.from(bitmap).maximumColorCount(12).generate()
                    val rgb =
                        palette.getMutedColor(0).takeIf { it != 0 }
                            ?: palette.getDominantColor(0).takeIf { it != 0 }
                            ?: palette.getVibrantColor(0).takeIf { it != 0 }
                            ?: return@runCatching null
                    softenCoverColor(rgb)
                }.getOrNull()
            }
        if (extracted != null) tint = extracted
    }
    return tint
}

private fun softenCoverColor(rgb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    hsl[1] = (hsl[1] * 0.62f).coerceIn(0.08f, 0.55f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.62f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Latar pemutar: cover besar di atas yang makin ke bawah makin gelap dan menyatu dengan warna cover (versi lembut)
 * yang dicampur warna dasar tema (gelap/terang). Di mode video, cover disembunyikan dan latar tinggal warna campuran.
 */
@Composable
fun PlayerCoverBackdrop(
    thumbnailUrl: String?,
    dark: Boolean,
    videoMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = rememberCoverTint(thumbnailUrl)
    val themeTop = if (dark) Color(0xFF16161A) else Color(0xFFF4F4F8)
    val themeBottom = if (dark) Color(0xFF050507) else Color(0xFFE6E6EC)
    val hasTint = tint != Color.Unspecified
    val topTarget = if (hasTint) lerp(themeTop, tint, if (dark) 0.42f else 0.34f) else themeTop
    val bottomTarget = if (hasTint) lerp(themeBottom, tint, if (dark) 0.20f else 0.22f) else themeBottom
    val spec = tween<Color>(durationMillis = 800)
    val top by animateColorAsState(topTarget, spec, label = "coverTop")
    val bottom by animateColorAsState(bottomTarget, spec, label = "coverBottom")
    val imageAlpha by animateFloatAsState(if (videoMode) 0f else 1f, tween(450), label = "coverAlpha")
    val heroFraction = 0.62f
    val seam = lerp(top, bottom, heroFraction)

    Box(modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        if (!thumbnailUrl.isNullOrBlank() && imageAlpha > 0.01f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(heroFraction)
                        .alpha(imageAlpha),
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.42f to Color.Transparent,
                                    0.70f to seam.copy(alpha = 0.62f),
                                    0.90f to seam.copy(alpha = 0.92f),
                                    1f to seam,
                                ),
                            ),
                )
            }
        }
    }
}

/** Toggle Audio / Video gaya pil, seperti desain iOS berukuran ringkas. */
@Composable
fun PlayerAudioVideoToggle(
    videoSelected: Boolean,
    ink: Color,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val selectedBg = ink.copy(alpha = 0.20f)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(ink.copy(alpha = 0.10f), shape),
    ) {
        androidx.compose.foundation.layout.Row(modifier = Modifier.padding(3.dp)) {
            listOf(false to R.string.player_mode_audio, true to R.string.player_mode_video).forEach { (isVideo, label) ->
                val selected = videoSelected == isVideo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .width(84.dp)
                            .height(30.dp)
                            .clip(shape)
                            .background(if (selected) selectedBg else Color.Transparent, shape)
                            .clickable { if (!selected) onSelect(isVideo) },
                ) {
                    Text(
                        text = stringResource(label),
                        color = if (selected) ink else ink.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Mencari ID video resmi (klip musik asli) untuk lagu yang sedang diputar.
 * Jika lagu sudah berupa video, dipakai langsung. Jika tidak ada klip resmi yang cocok, mengembalikan null (tidak ada video buatan).
 */
suspend fun resolveOfficialVideoId(metadata: MediaMetadata): String? {
    if (metadata.isVideoSong) return metadata.id
    val artist = metadata.artists.firstOrNull()?.name.orEmpty()
    val query = "$artist ${metadata.title}".trim()
    if (query.isBlank()) return null
    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull() ?: return null
    val titleKey = normalizeKey(metadata.title)
    val artistKey = normalizeKey(artist)
    return result.items
        .filterIsInstance<SongItem>()
        .firstOrNull { item ->
            val sameType = item.musicVideoType == MUSIC_VIDEO_TYPE_OMV
            val titleOk = normalizeKey(item.title).contains(titleKey) || titleKey.contains(normalizeKey(item.title))
            val artistOk = artistKey.isEmpty() || item.artists.any { normalizeKey(it.name).contains(artistKey) || artistKey.contains(normalizeKey(it.name)) }
            val durationOk = item.duration == null || metadata.duration <= 0 || abs(item.duration!! - metadata.duration) <= 15
            sameType && titleOk && artistOk && durationOk
        }?.id
}

private fun normalizeKey(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

private class VideoBridge(
    private val onReadyCb: () -> Unit,
    private val onErrorCb: () -> Unit,
) {
    @JavascriptInterface
    fun onReady() = onReadyCb()

    @JavascriptInterface
    fun onError() = onErrorCb()
}

/**
 * Video resmi dari YouTube lewat pemutar tersemat resmi. Video dibisukan; suara tetap dari pemutar lagu aplikasi,
 * dan posisi/jeda video disinkronkan ke pemutar lagu.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficialVideoView(
    metadata: MediaMetadata,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    ink: Color = Color.White,
) {
    var videoId by remember(metadata.id) { mutableStateOf<String?>(null) }
    var resolving by remember(metadata.id) { mutableStateOf(true) }
    var failed by remember(metadata.id) { mutableStateOf(false) }
    LaunchedEffect(metadata.id) {
        resolving = true
        failed = false
        videoId = withContext(Dispatchers.IO) { runCatching { resolveOfficialVideoId(metadata) }.getOrNull() }
        resolving = false
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(if (!resolving && (videoId == null || failed) && !metadata.thumbnailUrl.isNullOrBlank()) Color.Transparent else Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val id = videoId
        when {
            resolving -> Text(stringResource(R.string.player_video_loading), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            (id == null || failed) && !metadata.thumbnailUrl.isNullOrBlank() -> AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)),
            )
            id == null || failed -> Text(
                stringResource(R.string.player_video_unavailable),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
            else -> {
                var ready by remember(id) { mutableStateOf(false) }
                val playingState = rememberUpdatedState(isPlaying)
                val positionState = rememberUpdatedState(positionProvider)
                var webView by remember(id) { mutableStateOf<WebView?>(null) }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            addJavascriptInterface(
                                VideoBridge(
                                    onReadyCb = { post { ready = true } },
                                    onErrorCb = { post { failed = true } },
                                ),
                                "SpectraBridge",
                            )
                            val startSec = (positionProvider() / 1000L).coerceAtLeast(0L)
                            loadDataWithBaseURL("https://www.youtube.com", embedHtml(id, startSec), "text/html", "utf-8", null)
                            webView = this
                        }
                    },
                )

                LaunchedEffect(webView, ready) {
                    val view = webView ?: return@LaunchedEffect
                    if (!ready) return@LaunchedEffect
                    while (true) {
                        val posSec = positionState.value() / 1000.0
                        view.evaluateJavascript("syncVideo($posSec,${playingState.value});", null)
                        delay(700)
                    }
                }

                DisposableEffect(id) {
                    onDispose {
                        webView?.let {
                            it.removeJavascriptInterface("SpectraBridge")
                            it.stopLoading()
                            it.loadUrl("about:blank")
                            it.destroy()
                        }
                        webView = null
                    }
                }
            }
        }
    }
}

private fun embedHtml(
    videoId: String,
    startSec: Long,
): String {
    val safeId = videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    return """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%}</style></head>
        <body><div id="p"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
        var player=null,ok=false;
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$safeId',
            playerVars:{controls:0,playsinline:1,rel:0,modestbranding:1,disablekb:1,fs:0,iv_load_policy:3,start:$startSec},
            events:{
              onReady:function(e){e.target.mute();ok=true;SpectraBridge.onReady();},
              onError:function(e){SpectraBridge.onError();}
            }});
        }
        function syncVideo(pos,playing){
          if(!ok||!player||!player.getPlayerState)return;
          try{
            var cur=player.getCurrentTime();
            if(Math.abs(cur-pos)>1.5){player.seekTo(pos,true);}
            var st=player.getPlayerState();
            if(playing&&st!==1&&st!==3){player.playVideo();}
            if(!playing&&st===1){player.pauseVideo();}
            player.mute();
          }catch(err){}
        }
        </script></body></html>
    """.trimIndent()
}

/**
 * Satu baris lirik yang sedang berjalan di bawah nama artis, kecil; bagian yang sudah dinyanyikan
 * berwarna penuh dan sisanya redup, mengikuti urutan lirik.
 */
private const val LYRIC_LEAD_MS = 100L
private const val SYLLABLE_MS = 240L

private fun isVowelChar(c: Char): Boolean = c.lowercaseChar() in "aeiouáàâäãåéèêëíìîïóòôöõúùûüy"

private fun computeSyllableEnds(text: String): IntArray {
    val ends = ArrayList<Int>()
    var i = 0
    val len = text.length
    while (i < len) {
        while (i < len && text[i].isWhitespace()) i++
        if (i >= len) break
        val wordStart = i
        while (i < len && !text[i].isWhitespace()) i++
        val wordEnd = i
        var tail = wordEnd
        while (tail < len && text[tail].isWhitespace()) tail++
        val nuclei = ArrayList<Int>()
        var j = wordStart
        while (j < wordEnd) {
            if (isVowelChar(text[j])) {
                while (j < wordEnd && isVowelChar(text[j])) j++
                nuclei.add(j)
            } else {
                j++
            }
        }
        if (nuclei.isEmpty()) {
            ends.add(tail)
        } else {
            for (idx in 0 until nuclei.size - 1) ends.add(nuclei[idx])
            ends.add(tail)
        }
    }
    if (ends.isNotEmpty()) ends[ends.lastIndex] = len
    return ends.toIntArray()
}

@Composable
fun NowPlayingLyricLine(
    mediaMetadata: MediaMetadata,
    positionProvider: () -> Long,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val database = LocalDatabase.current

    var heldLines by remember(mediaMetadata.id) { mutableStateOf<List<LyricsEntry>>(emptyList()) }
    val rawLyrics = currentLyrics?.takeIf { it.id == mediaMetadata.id }?.lyrics?.trim()
    val parsedLines: List<LyricsEntry>? =
        remember(rawLyrics) {
            when {
                rawLyrics == null -> null
                rawLyrics.isBlank() || rawLyrics == LyricsEntity.LYRICS_NOT_FOUND -> emptyList()
                else -> runCatching { LyricsUtils.parseLyrics(rawLyrics).filter { it.text.isNotBlank() } }.getOrDefault(emptyList())
            }
        }
    if (parsedLines != null && parsedLines !== heldLines) heldLines = parsedLines
    val lines = heldLines

    val lyricsMissing = rawLyrics == null
    LaunchedEffect(mediaMetadata.id, lyricsMissing) {
        if (lyricsMissing && heldLines.isEmpty()) {
            delay(500)
            withContext(Dispatchers.IO) {
                runCatching {
                    val entryPoint =
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            com.spectra.app.di.LyricsHelperEntryPoint::class.java,
                        )
                    val fetched = entryPoint.lyricsHelper().getLyrics(mediaMetadata)
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, fetched.lyrics, fetched.provider))
                    }
                }
            }
        }
    }

    var litChars by remember(mediaMetadata.id) { mutableStateOf(0) }
    var syllableText by remember(mediaMetadata.id) { mutableStateOf("") }
    var syllableEnds by remember(mediaMetadata.id) { mutableStateOf(IntArray(0)) }
    var shown by remember(mediaMetadata.id) { mutableStateOf("") }
    val latestLines by rememberUpdatedState(lines)
    LaunchedEffect(mediaMetadata.id) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val current = latestLines
            if (current.isEmpty()) continue
            val pos = positionProvider() + LYRIC_LEAD_MS
            val i = LyricsUtils.findCurrentLineIndex(current, pos).coerceAtLeast(0)
            val entry = current.getOrNull(i) ?: continue
            val words = entry.words?.takeIf { it.isNotEmpty() }
            val lit: Int
            val display: String
            if (words != null) {
                val sb = StringBuilder()
                var count = 0
                val posSec = pos / 1000.0
                for (w in words) {
                    val len = w.text.length
                    val dur = (w.endTime - w.startTime).coerceAtLeast(0.001)
                    val f = ((posSec - w.startTime) / dur).coerceIn(0.0, 1.0)
                    count += (len * f).toInt().let { if (f > 0.0 && it == 0) 1 else it }
                    sb.append(w.text)
                    if (w.hasTrailingSpace) {
                        sb.append(' ')
                        if (f >= 1.0) count += 1
                    }
                }
                display = sb.toString().trimEnd()
                lit = count.coerceAtMost(display.length)
            } else {
                val start = entry.time
                val next = if (i + 1 < current.size) current[i + 1].time else start + 6000L
                val span = (next - start).coerceAtLeast(500L)
                display = entry.text
                if (display != syllableText) {
                    syllableText = display
                    syllableEnds = computeSyllableEnds(display)
                }
                val ends = syllableEnds
                val n = ends.size.coerceAtLeast(1)
                val dur = minOf((span - 120L).coerceAtLeast(600L), n * SYLLABLE_MS).coerceAtLeast(600L)
                val per = dur.toFloat() / n
                val elapsed = (pos - start).coerceAtLeast(0L).toFloat()
                val k = (elapsed / per).toInt()
                lit =
                    if (ends.isEmpty() || k >= n) {
                        display.length
                    } else {
                        val f = (elapsed - k * per) / per
                        val eased = 0.5f * f + 0.5f * f * f * (3f - 2f * f)
                        val prev = if (k == 0) 0 else ends[k - 1]
                        prev + ((ends[k] - prev) * eased).toInt()
                    }
            }
            if (display.isBlank()) continue
            if (display != shown) shown = display
            if (lit != litChars) litChars = lit
        }
    }

    if (lines.isEmpty() && shown.isEmpty()) return

    val text = shown
    val styled =
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White)) { append(text.take(litChars)) }
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.45f))) { append(text.drop(litChars)) }
        }
    Text(
        text = styled,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().height(42.dp),
    )
}

/** Judul daftar putar bergaya miring tebal besar, ditaruh di atas seperti desain pemutar iOS. */
@Composable
fun PlayerHeaderTitle(
    queueTitle: String?,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.player_now_playing),
            color = ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        if (!queueTitle.isNullOrBlank()) {
            Text(
                text = queueTitle,
                color = ink,
                fontSize = 26.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
