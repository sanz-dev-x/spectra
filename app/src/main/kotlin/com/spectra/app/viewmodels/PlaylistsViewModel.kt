/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.spectra.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectra.app.constants.AddToPlaylistSortDescendingKey
import com.spectra.app.constants.AddToPlaylistSortTypeKey
import com.spectra.app.constants.PlaylistSortType
import com.spectra.app.db.MusicDatabase
import com.spectra.app.extensions.toEnum
import com.spectra.app.utils.SyncUtils
import com.spectra.app.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[AddToPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[AddToPlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Suspend function that waits for sync to complete
    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }
}
