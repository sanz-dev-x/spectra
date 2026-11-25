package com.spectra.app.ui.screens.spotify

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.spectra.app.LocalPlayerAwareWindowInsets
import com.spectra.app.LocalPlayerConnection
import com.spectra.app.R
import com.spectra.app.models.toMediaMetadata
import com.spectra.app.playback.queues.YouTubeQueue
import com.spectra.app.spotify.SpotifyAccount
import com.spectra.app.spotify.SpotifyLibrary
import com.spectra.app.spotify.SpotifyListItem
import com.spectra.app.spotify.SpotifyPlaylist
import com.spectra.app.spotify.SpotifyTrack
import com.spectra.app.ui.component.IconButton
import com.spectra.app.ui.component.TopAppBar
import com.spectra.app.ui.component.songTouchAnimation
import com.spectra.app.ui.utils.backToMain
import com.spectra.innertube.YouTube
import com.spectra.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyInk = Color(0xFF121212)
private val LikedGradient = Brush.linearGradient(listOf(Color(0xFF4A2FE0), Color(0xFFA6B8FF)))

@Composable
fun SpotifyMark(size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size).clip(CircleShape).background(SpotifyGreen)) {
        val s = this.size.width
        val sw = s * 0.075f
        listOf(0.36f to 0.64f, 0.50f to 0.52f, 0.63f to 0.40f).forEach { (yf, wf) ->
            val w = s * wf
            val y = s * yf
            val p = Path().apply {
                moveTo(s / 2 - w / 2, y + s * 0.02f)
                quadraticTo(s / 2, y - s * 0.09f, s / 2 + w / 2, y + s * 0.04f)
            }
            drawPath(p, SpotifyInk, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Cover(url: String?, size: Dp, shape: RoundedCornerShape = RoundedCornerShape(8.dp), liked: Boolean = false) {
    val m = Modifier.size(size).clip(shape)
    when {
        liked ->
            Box(m.background(LikedGradient), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.favorite), null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            }
        url.isNullOrBlank() ->
            Box(m.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.library_music), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.45f))
            }
        else -> AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = m)
    }
}

@Composable
private fun PillButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val touch = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(52.dp)
            .songTouchAnimation(touch)
            .clip(RoundedCornerShape(26.dp))
            .background(
                when {
                    primary -> Color(0xFFF2F3F8)
                    else -> scheme.surfaceContainerHigh.copy(alpha = 0.85f)
                },
            )
            .then(if (!primary && !danger) Modifier.border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(26.dp)) else Modifier)
            .clickable(interactionSource = touch, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                primary -> Color(0xFF0B0C10)
                danger -> Color(0xFFFF7B72)
                else -> scheme.onSurface
            },
        )
    }
}

@Composable
fun SpotifyConnectScreen(navController: NavController) {
    val context = LocalContext.current
    val profile = remember { SpotifyAccount.profile(context) }
    val connected = profile != null && SpotifyAccount.isConnected(context)
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            SpotifyMark(72.dp)
            Spacer(Modifier.height(20.dp))
            if (!connected) {
                Text(stringResource(R.string.spx_connect_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.spx_connect_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerHigh.copy(alpha = 0.7f)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PerkRow(R.drawable.library_music, stringResource(R.string.spx_perk_library))
                    PerkRow(R.drawable.play, stringResource(R.string.spx_perk_play))
                    PerkRow(R.drawable.tune, stringResource(R.string.spx_perk_cover))
                }
                Spacer(Modifier.height(24.dp))
                PillButton(
                    stringResource(R.string.spx_connect_title),
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (SpotifyAccount.isConfigured()) {
                            navController.navigate("spotify/login")
                        } else {
                            Toast.makeText(context, R.string.spx_not_configured, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            } else {
                Text("Spotify", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(28.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.horizontalGradient(listOf(SpotifyGreen.copy(alpha = 0.18f), scheme.surfaceContainerHigh.copy(alpha = 0.7f))))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        if (profile?.imageUrl != null) {
                            AsyncImage(profile.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(profile?.name?.take(1)?.uppercase().orEmpty(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(profile?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.you_connected), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(28.dp).clip(CircleShape).background(SpotifyGreen), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.check), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                PillButton(stringResource(R.string.spx_open_library), primary = false, modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate("spotify/library") })
                Spacer(Modifier.height(12.dp))
                PillButton(
                    stringResource(R.string.spx_disconnect),
                    primary = false,
                    danger = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        SpotifyAccount.disconnect(context)
                        navController.popBackStack()
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.spx_local_note),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
        TopAppBar(
            title = { Text("Spotify") },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun PerkRow(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, modifier = Modifier.size(26.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 18.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val authUrl = remember(attempt) { SpotifyAccount.buildAuthorizeUrl(context) }
    val onRedirect by rememberUpdatedState<(String) -> Unit>({ url ->
        if (!busy) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { SpotifyAccount.completeLogin(context, url) }.isSuccess }
                busy = false
                if (ok) {
                    navController.popBackStack()
                } else {
                    failed = true
                }
            }
        }
    })
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0C10)).windowInsetsPadding(WindowInsets.statusBars).padding(bottom = bottomInset)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
            SpotifyMark(40.dp, Modifier.padding(start = 4.dp))
            Text(stringResource(R.string.spx_login_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (failed) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.spx_login_failed), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    PillButton(stringResource(R.string.spx_retry), primary = true, modifier = Modifier.fillMaxWidth(0.6f), onClick = {
                        failed = false
                        attempt++
                    })
                }
            } else {
                androidx.compose.runtime.key(attempt) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                setBackgroundColor(android.graphics.Color.parseColor("#0B0C10"))
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val url = request.url.toString()
                                        if (url.startsWith(SpotifyAccount.REDIRECT_URI)) {
                                            onRedirect(url)
                                            return true
                                        }
                                        return false
                                    }
                                }
                                loadUrl(authUrl)
                            }
                        },
                        onRelease = {
                            it.stopLoading()
                            it.destroy()
                        },
                    )
                }
                if (busy) {
                    Box(Modifier.fillMaxSize().background(Color(0xCC0B0C10)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.lock), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.spx_login_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

@Composable
fun SpotifyLibraryListScreen(navController: NavController) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Result<Pair<Int, List<SpotifyListItem>>>?>(null) }
    LaunchedEffect(attempt) {
        result = null
        result = withContext(Dispatchers.IO) {
            runCatching { SpotifyAccount.likedCount(context) to SpotifyAccount.playlists(context) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        val r = result
        when {
            r == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            r.isFailure -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.spx_load_failed), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PillButton(stringResource(R.string.spx_retry), primary = true, modifier = Modifier.fillMaxWidth(0.6f), onClick = { attempt++ })
            }
            else -> {
                val (likedCount, lists) = r.getOrThrow()
                LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(), modifier = Modifier.fillMaxSize()) {
                    item(key = "liked") {
                        LibraryRow(null, stringResource(R.string.liked_songs), stringResource(R.string.spx_songs_count, likedCount), liked = true) {
                            navController.navigate("spotify/playlist/${SpotifyAccount.LIKED_ID}/${Uri.encode(context.getString(R.string.liked_songs))}")
                        }
                    }
                    items(lists, key = { it.id }) { pl ->
                        LibraryRow(pl.imageUrl, pl.name, pl.owner.ifBlank { stringResource(R.string.spx_songs_count, pl.trackCount) }) {
                            navController.navigate("spotify/playlist/${Uri.encode(pl.id)}/${Uri.encode(pl.name)}")
                        }
                    }
                }
            }
        }
        TopAppBar(
            title = { Text("Spotify") },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun LibraryRow(imageUrl: String?, title: String, subtitle: String, liked: Boolean = false, onClick: () -> Unit) {
    val touch = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().songTouchAnimation(touch).clickable(interactionSource = touch, indication = null, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(imageUrl, 56.dp, liked = liked)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SpotifyPlaylistScreen(navController: NavController, id: String, name: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Result<List<SpotifyTrack>>?>(null) }
    var saved by remember { mutableStateOf(SpotifyLibrary.load(context).any { it.id == id }) }
    val isLiked = id == SpotifyAccount.LIKED_ID

    LaunchedEffect(attempt) {
        result = null
        result = withContext(Dispatchers.IO) { runCatching { SpotifyAccount.tracks(context, id) } }
    }

    fun play(t: SpotifyTrack) {
        scope.launch {
            val song = withContext(Dispatchers.IO) {
                YouTube.search("${t.title} ${t.artist}", YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
            }
            if (song == null || playerConnection == null) {
                Toast.makeText(context, R.string.spx_no_match, Toast.LENGTH_SHORT).show()
            } else {
                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val r = result
        when {
            r == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            r.isFailure -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.spx_load_failed), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PillButton(stringResource(R.string.spx_retry), primary = true, modifier = Modifier.fillMaxWidth(0.6f), onClick = { attempt++ })
            }
            else -> {
                val tracks = r.getOrThrow()
                LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(), modifier = Modifier.fillMaxSize()) {
                    item(key = "header") {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cover(tracks.firstOrNull()?.artworkUrl, 112.dp, RoundedCornerShape(14.dp), liked = isLiked)
                                Column(Modifier.padding(start = 18.dp)) {
                                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.spx_songs_count, tracks.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                PillButton(stringResource(R.string.spx_play), primary = true, modifier = Modifier.weight(1f), onClick = { tracks.firstOrNull()?.let(::play) })
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)).clickable { tracks.randomOrNull()?.let(::play) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(painterResource(R.drawable.shuffle), null) }
                            }
                            Spacer(Modifier.height(10.dp))
                            PillButton(
                                stringResource(if (saved) R.string.spx_saved else R.string.spx_save_to_library),
                                primary = false,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (!saved) {
                                        SpotifyLibrary.upsert(context, SpotifyPlaylist(id = id, name = name, owner = "Spotify", artworkUrl = tracks.firstOrNull()?.artworkUrl, tracks = tracks))
                                        saved = true
                                    }
                                },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    items(tracks) { t -> TrackRow(t, onClick = { play(t) }, onSearch = { navController.navigate("search/${Uri.encode("${t.title} ${t.artist}")}") }) }
                }
            }
        }
        TopAppBar(
            title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun TrackRow(t: SpotifyTrack, onClick: () -> Unit, onSearch: () -> Unit) {
    val touch = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().songTouchAnimation(touch).clickable(interactionSource = touch, indication = null, onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(t.artworkUrl, 48.dp, RoundedCornerShape(6.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }, onLongClick = {}) { Icon(painterResource(R.drawable.more_vert), null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.search)) }, onClick = {
                    menu = false
                    onSearch()
                })
            }
        }
    }
}
