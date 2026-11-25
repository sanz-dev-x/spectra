package com.spectra.app.ui.screens.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.spectra.app.R
import com.spectra.app.constants.LibraryFilter
import com.spectra.app.constants.ShowCachedPlaylistKey
import com.spectra.app.constants.ShowDownloadedPlaylistKey
import com.spectra.app.constants.ShowLikedPlaylistKey
import com.spectra.app.constants.ShowTopPlaylistKey
import com.spectra.app.constants.ShowUploadedPlaylistKey
import com.spectra.app.utils.rememberPreference

/**
 * Jelly saat disentuh: menyempit di lebar, memanjang di tinggi, lalu memantul balik dengan spring.
 * Nilai animasi hanya dibaca di blok graphicsLayer (fase draw), jadi tidak memicu recomposition dan
 * tidak mengganggu klik maupun scroll.
 */
@Composable
private fun Modifier.libraryJelly(interactionSource: MutableInteractionSource, strength: Float = 1f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val amount by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.34f, stiffness = Spring.StiffnessMediumLow + 120f),
        label = "libraryJelly",
    )
    return graphicsLayer {
        scaleX = 1f - 0.06f * strength * amount
        scaleY = 1f + 0.04f * strength * amount
    }
}

/** Judul besar "Pustaka" dan tab teks bergaris bawah: Semua, Playlist, Lagu, Album, Artis, Podcast. */
@Composable
fun LibraryHeader(current: LibraryFilter, onSelect: (LibraryFilter) -> Unit, topPadding: androidx.compose.ui.unit.Dp) {
    val tabs =
        listOf(
            LibraryFilter.LIBRARY to R.string.lib_all,
            LibraryFilter.PLAYLISTS to R.string.filter_playlists,
            LibraryFilter.SONGS to R.string.filter_songs,
            LibraryFilter.ALBUMS to R.string.filter_albums,
            LibraryFilter.ARTISTS to R.string.filter_artists,
            LibraryFilter.PODCASTS to R.string.filter_podcasts,
        )
    Column(Modifier.fillMaxWidth().padding(top = topPadding)) {
        Text(
            stringResource(R.string.filter_library),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 38.sp),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 10.dp),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp)) {
            tabs.forEach { (filter, label) ->
                val selected = filter == current
                val touch = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .libraryJelly(touch)
                        .clickable(interactionSource = touch, indication = null) { onSelect(filter) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(5.dp))
                    // Garis bawah selebar teks tab, tebal 2dp seperti referensi.
                    Box(
                        Modifier
                            .height(2.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Bagian "Koleksi" di bawah tab Semua: pintasan ke playlist otomatis, tiap baris berbentuk kapsul lembut. */
@Composable
fun LibraryCollections(navController: NavController, topSize: Any, onOpenSongs: () -> Unit) {
    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, true)

    val rows =
        buildList {
            if (showLiked) add(Triple(R.drawable.favorite, stringResource(R.string.liked)) { navController.navigate("auto_playlist/liked") })
            if (showDownloaded) add(Triple(R.drawable.offline, stringResource(R.string.offline)) { navController.navigate("auto_playlist/downloaded") })
            if (showCached) add(Triple(R.drawable.cached, stringResource(R.string.cached_playlist)) { navController.navigate("cache_playlist/cached") })
            add(Triple(R.drawable.storage, stringResource(R.string.on_device), onOpenSongs))
            if (showTop) add(Triple(R.drawable.trending_up, stringResource(R.string.my_top) + " $topSize") { navController.navigate("top_playlist/$topSize") })
            if (showUploaded) add(Triple(R.drawable.upload, stringResource(R.string.uploaded_playlist)) { navController.navigate("auto_playlist/uploaded") })
        }

    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            stringResource(R.string.lib_collections),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
        )
        rows.forEach { (icon, title, onClick) ->
            val touch = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .libraryJelly(touch, strength = 0.7f)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable(interactionSource = touch, indication = null, onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(icon), null, modifier = Modifier.size(34.dp))
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 16.dp))
                Icon(painterResource(R.drawable.navigate_next), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
        }
    }
}
