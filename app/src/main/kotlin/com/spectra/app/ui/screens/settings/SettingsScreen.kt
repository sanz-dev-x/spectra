/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.screens.settings

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.spectra.app.constants.AppLanguageKey
import com.spectra.app.constants.SYSTEM_DEFAULT
import com.spectra.app.ui.component.EnumDialog
import com.spectra.app.utils.applyAppLanguage
import com.spectra.app.utils.rememberPreference
import java.util.Locale
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.spectra.app.BuildConfig
import com.spectra.app.LocalPlayerAwareWindowInsets
import com.spectra.app.R
import com.spectra.app.ui.component.Material3SettingsGroup
import com.spectra.app.ui.component.Material3SettingsItem
import com.spectra.app.utils.Updater
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.spectra.innertube.utils.parseCookieString
import com.spectra.app.constants.InnerTubeCookieKey
import com.spectra.app.constants.ListenTogetherInTopBarKey
import com.spectra.app.ui.component.rememberGlassControlState
import com.spectra.app.ui.component.settingsGlassCard
import com.spectra.app.ui.component.settingsGlassControl
import com.spectra.app.viewmodels.HomeViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scope = rememberCoroutineScope()
    val (storedLanguage) = rememberPreference(AppLanguageKey, defaultValue = SYSTEM_DEFAULT)
    val currentLanguage =
        if (storedLanguage == SYSTEM_DEFAULT) {
            if (Locale.getDefault().language == "id") "id" else "en"
        } else {
            storedLanguage.substringBefore('-')
        }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    if (showLanguageDialog) {
        EnumDialog(
            onDismiss = { showLanguageDialog = false },
            onSelect = { tag ->
                showLanguageDialog = false
                if (tag != currentLanguage) {
                    (context as? Activity)?.let { activity -> scope.launch { activity.applyAppLanguage(tag) } }
                }
            },
            title = stringResource(R.string.language),
            current = currentLanguage,
            values = listOf("id", "en"),
            valueText = {
                stringResource(if (it == "id") R.string.language_indonesian else R.string.language_english)
            },
        )
    }

    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo(
                "com.google.android.projection.gearhead", 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        SettingsProfileCard(navController = navController)

        Spacer(modifier = Modifier.height(16.dp))

        SettingsActivityGroup(navController = navController)

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.appearance)) },
                    onClick = { navController.navigate("settings/appearance") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.speed),
                    title = { Text(stringResource(R.string.performance)) },
                    onClick = { navController.navigate("settings/performance") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.spectra_dynamic_island)) },
                    onClick = { navController.navigate("settings/dynamic_island") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.language)) },
                    description = {
                        Text(
                            stringResource(
                                if (currentLanguage == "id") R.string.language_indonesian else R.string.language_english,
                            ),
                        )
                    },
                    onClick = { showLanguageDialog = true },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.ios_play),
                        title = { Text(stringResource(R.string.player_and_audio)) },
                        onClick = { navController.navigate("settings/player") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.content)) },
                        onClick = { navController.navigate("settings/content") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.translate),
                        title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                        onClick = { navController.navigate("settings/ai") }
                    )
                )
                if (hasAndroidAuto) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.ic_android_auto),
                            title = { Text(stringResource(R.string.android_auto)) },
                            onClick = { navController.navigate("settings/android_auto") }
                        )
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.notification),
                    title = { Text(stringResource(R.string.spectra_notifications)) },
                    onClick = { navController.navigate("settings/notifications") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.security),
                    title = { Text(stringResource(R.string.privacy)) },
                    onClick = { navController.navigate("settings/privacy") }
                ),
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        StreakSettingsGroup()

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text(stringResource(R.string.storage)) },
                    onClick = { navController.navigate("settings/storage") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.restore),
                    title = { Text(stringResource(R.string.backup_restore)) },
                    onClick = { navController.navigate("settings/backup_restore") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            items = buildList {
                if (isAndroid12OrLater) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.link),
                            title = { Text(stringResource(R.string.default_links)) },
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        R.string.open_app_settings_error,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    )
                }
                if (BuildConfig.UPDATER_AVAILABLE) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.update),
                            title = { Text(stringResource(R.string.updater)) },
                            onClick = { navController.navigate("settings/updater") }
                        )
                    )
                }
                if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
                    val releaseInfo = Updater.getCachedLatestRelease()
                    val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }

                    if (downloadUrl != null) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.update),
                                title = { Text(text = stringResource(R.string.new_version_available)) },
                                description = {
                                    Text(
                                        text = latestVersionName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                showBadge = true,
                                onClick = { uriHandler.openUri(downloadUrl) }
                            )
                        )
                    }
                }
                val showChangelog = com.spectra.app.LocalChangelogState.current
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.newspaper),
                        title = { Text(stringResource(R.string.changelog)) },
                        onClick = { showChangelog.value = true }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text(stringResource(R.string.about)) },
                        onClick = { navController.navigate("settings/about") }
                    )
                )
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsProfileCard(navController: NavController) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, defaultValue = "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    val shape = RoundedCornerShape(26.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val accountInteraction = remember { MutableInteractionSource() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .settingsGlassCard(shape)
                .background(if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { navController.navigate(if (isLoggedIn) "account" else "login") },
                ).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoggedIn && accountImageUrl != null) {
                AsyncImage(
                    model = accountImageUrl,
                    contentDescription = stringResource(R.string.account),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.account),
                    contentDescription = stringResource(R.string.account),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isLoggedIn) accountName else stringResource(R.string.login),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .height(36.dp)
                    .settingsGlassControl(CircleShape, rememberGlassControlState(accountInteraction))
                    .clickable(
                        interactionSource = accountInteraction,
                        indication = null,
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = { navController.navigate("settings/account") },
                    )
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.account),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsActivityGroup(navController: NavController) {
    val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)

    Material3SettingsGroup(
        items =
            buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.history),
                        title = { Text(stringResource(R.string.history)) },
                        onClick = { navController.navigate("history") },
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.stats),
                        title = { Text(stringResource(R.string.stats)) },
                        onClick = { navController.navigate("stats") },
                    ),
                )
                if (listenTogetherInTopBar) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.group_outlined),
                            title = { Text(stringResource(R.string.together)) },
                            onClick = { navController.navigate("listen_together_from_topbar") },
                        ),
                    )
                }
            },
    )
}
