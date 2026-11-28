/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.CompositionLocalProvider
import com.spectra.app.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.spectra.app.LocalNavController
import com.spectra.app.R
import com.spectra.app.constants.AlbumViewTypeKey
import com.spectra.app.constants.ChipSortTypeKey
import com.spectra.app.constants.LibraryFilter
import com.spectra.app.constants.LibraryViewType
import com.spectra.app.constants.PlaylistViewTypeKey
import com.spectra.app.ui.component.ChipsRow
import com.spectra.app.utils.rememberEnumPreference

@Composable
fun LibraryScreen() {
    val navController = LocalNavController.current
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    var libraryViewType by rememberEnumPreference(com.spectra.app.constants.LibraryMixViewTypeKey, LibraryViewType.GRID)
    var playlistViewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)

    val filterContent = @Composable { }
    val insets = LocalPlayerAwareWindowInsets.current
    val topPadding = insets.asPaddingValues().calculateTopPadding()

    Column(modifier = Modifier.fillMaxSize()) {
    LibraryHeader(current = filterType, onSelect = { filterType = it }, topPadding = topPadding)
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      CompositionLocalProvider(
          LocalPlayerAwareWindowInsets provides insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
          com.spectra.app.ui.component.LocalGridCornerRadius provides androidx.compose.ui.unit.Dp(16f),
          com.spectra.app.ui.component.LocalListCorner provides androidx.compose.ui.unit.Dp(14f),
      ) {
        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = libraryViewType,
                onViewTypeChange = { libraryViewType = it },
                onOpenSongs = { filterType = LibraryFilter.SONGS },
            )
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = playlistViewType,
                onViewTypeChange = { playlistViewType = it },
            )
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.PODCASTS -> LibraryPodcastsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
        }
      }
    }
    }
}
