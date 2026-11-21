/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.spectra.app.constants.DarkModeKey
import com.spectra.app.constants.navEnter
import com.spectra.app.constants.navExit
import com.spectra.app.constants.PureBlackKey
import com.spectra.app.ui.screens.artist.ArtistAlbumsScreen
import com.spectra.app.ui.screens.artist.ArtistItemsScreen
import com.spectra.app.ui.screens.artist.ArtistScreen
import com.spectra.app.ui.screens.artist.ArtistSongsScreen
import com.spectra.app.ui.screens.equalizer.EqScreen
import com.spectra.app.ui.screens.equalizer.wizard.WizardScreen
import com.spectra.app.ui.screens.library.LibraryScreen
import com.spectra.app.ui.screens.playlist.AutoPlaylistScreen
import com.spectra.app.ui.screens.playlist.CachePlaylistScreen
import com.spectra.app.ui.screens.playlist.LocalPlaylistScreen
import com.spectra.app.ui.screens.playlist.OnlinePlaylistScreen
import com.spectra.app.ui.screens.playlist.TopPlaylistScreen
import com.spectra.app.ui.screens.podcast.OnlinePodcastScreen
import com.spectra.app.ui.screens.recognition.RecognitionHistoryScreen
import com.spectra.app.ui.screens.recognition.RecognitionScreen
import com.spectra.app.ui.screens.search.OnlineSearchResult
import com.spectra.app.ui.screens.search.SearchScreen
import com.spectra.app.ui.screens.settings.AboutScreen
import com.spectra.app.ui.screens.settings.AccountSettingsScreen
import com.spectra.app.ui.screens.settings.AiSettings
import com.spectra.app.ui.screens.settings.AndroidAutoSettings
import com.spectra.app.ui.screens.settings.AppearanceSettings
import com.spectra.app.ui.screens.settings.AppIconSettings
import com.spectra.app.ui.screens.settings.BackupAndRestore
import com.spectra.app.ui.screens.settings.SpotifyLibraryScreen
import com.spectra.app.ui.screens.settings.ContentSettings
import com.spectra.app.ui.screens.settings.DarkMode
import com.spectra.app.ui.screens.settings.PlayerSettings
import com.spectra.app.ui.screens.settings.DynamicIslandSettings
import com.spectra.app.ui.screens.settings.NotificationSettings
import com.spectra.app.ui.screens.settings.PrivacySettings
import com.spectra.app.ui.screens.settings.RomanizationSettings
import com.spectra.app.ui.screens.settings.SettingsScreen
import com.spectra.app.ui.screens.settings.StorageSettings
import com.spectra.app.ui.screens.settings.ThemeScreen
import com.spectra.app.ui.screens.settings.UpdaterScreen
import com.spectra.app.ui.screens.settings.integrations.IntegrationScreen
import com.spectra.app.ui.screens.settings.integrations.LastFMSettings
import com.spectra.app.ui.screens.settings.integrations.ListenTogetherSettings

import com.spectra.app.ui.screens.wrapped.WrappedScreen
import com.spectra.app.utils.rememberEnumPreference
import com.spectra.app.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    activity: Activity,
    snackbarHostState: SnackbarHostState,
) {
    composable(Screens.Home.route) {
        HomeScreen(snackbarHostState = snackbarHostState)
    }

    composable(Screens.Search.route) { backStackEntry ->
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.OFF)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val glassLight = com.spectra.app.ui.theme.LocalGlassMode.current != com.spectra.app.ui.theme.GlassMode.OFF
        val useDarkTheme =
            remember(darkTheme, isSystemInDarkTheme, glassLight) {
                !glassLight && (if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON)
            }
        val pureBlack =
            remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            }
        SearchScreen(
            pureBlack = pureBlack,
            savedStateHandle = backStackEntry.savedStateHandle
        )
    }

    composable(Screens.Library.route) {
        LibraryScreen()
    }

    composable(Screens.ListenTogether.route) {
        ListenTogetherScreen(navController, showTopBar = false)
    }

    composable(
        route = "listen_together_from_topbar",
    ) {
        ListenTogetherScreen(navController, showTopBar = true)
    }

    composable("history") {
        HistoryScreen(navController)
    }

    composable("stats") {
        StatsScreen(navController)
    }

    composable("music_recap") {
        MusicRecapScreen(navController)
    }

    composable("mood_and_genres") {
        MoodAndGenresScreen(navController)
    }

    composable("account") {
        AccountScreen(navController)
    }

    composable("new_release") {
        NewReleaseScreen(navController)
    }

    composable("charts_screen") {
        ChartsScreen(navController)
    }

    composable(
        route = "browse/{browseId}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
            ),
    ) {
        BrowseScreen(
            navController,
            it.arguments?.getString("browseId"),
        )
    }

    composable(
        route = "search/{query}",
        arguments =
            listOf(
                navArgument("query") {
                    type = NavType.StringType
                },
            ),
        enterTransition = { navEnter(true) },
        exitTransition = { navExit(targetState.destination.route?.startsWith("search/") != true) },
        popEnterTransition = { navEnter(initialState.destination.route?.startsWith("search/") == true) },
        popExitTransition = { navExit(false) },
    ) { backStackEntry ->
        OnlineSearchResult(
            savedStateHandle = backStackEntry.savedStateHandle
        )

    }

    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController)
    }

    composable(
        route = "artist/{artistId}?isPodcastChannel={isPodcastChannel}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("isPodcastChannel") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) {
        ArtistScreen(navController)
    }

    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController)
    }

    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController)
    }

    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController)
    }

    composable(
        route = "online_podcast/{podcastId}",
        arguments =
            listOf(
                navArgument("podcastId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePodcastScreen(navController, scrollBehavior)
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController)
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        AutoPlaylistScreen(navController)
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController)
    }

    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController)
    }

    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }

    composable("settings") {
        SettingsScreen(navController, latestVersionName)
    }

    composable("settings/account") {
        AccountSettingsScreen(navController, latestVersionName)
    }

    composable("settings/appearance") {
        AppearanceSettings(navController, snackbarHostState)
    }

    composable("settings/appearance/theme") {
        ThemeScreen(navController)
    }

    composable("settings/content") {
        ContentSettings(navController)
    }

    composable("settings/content/romanization") {
        RomanizationSettings(navController)
    }

    composable("settings/ai") {
        AiSettings(navController)
    }

    composable("settings/player") {
        PlayerSettings(navController)
    }

    composable("settings/storage") {
        StorageSettings(navController)
    }

    composable("settings/privacy") {
        PrivacySettings(navController)
    }

    composable("settings/notifications") {
        NotificationSettings(navController)
    }

    composable("settings/dynamic_island") {
        DynamicIslandSettings(navController)
    }

    composable("settings/app_icon") {
        AppIconSettings(navController)
    }
    composable("settings/spotify_library") {
        SpotifyLibraryScreen(navController)
    }
    composable("settings/backup_restore") {
        BackupAndRestore(navController)
    }

    composable("settings/integrations") {
        IntegrationScreen(navController)
    }

    composable("settings/integrations/lastfm") {
        LastFMSettings(navController)
    }

    composable(route = "settings/integrations/listen_together") {
        ListenTogetherSettings(navController)
    }

    composable("settings/updater") {
        UpdaterScreen(navController)
    }

    composable("settings/about") {
        AboutScreen(navController)
    }

    composable("login") {
        LoginScreen(navController)
    }

    composable("switch_channel") {
        LoginScreen(
            navController = navController,
            isSwitchingChannel = true,
        )
    }

    composable("wrapped") {
        WrappedScreen()
    }

    composable("community") {
        CommunityScreen()
    }

    composable("equalizer") {
        EqScreen()
    }

    composable("eq_wizard") {
        WizardScreen(onNavigateBack = {
            navController.popBackStack()
        })
    }

    composable(
        route = "recognition?autoStart={autoStart}",
        arguments =
            listOf(
                navArgument("autoStart") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) {
        RecognitionScreen(navController, it.arguments?.getBoolean("autoStart") ?: false)
    }

    composable("recognition_history") {
        RecognitionHistoryScreen(navController)
    }
    composable("settings/android_auto") {
        AndroidAutoSettings(navController)
    }
}
