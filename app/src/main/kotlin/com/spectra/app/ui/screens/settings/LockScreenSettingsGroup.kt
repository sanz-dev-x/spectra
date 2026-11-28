package com.spectra.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spectra.app.R
import com.spectra.app.constants.LockScreenArtistWallpaperKey
import com.spectra.app.constants.LockScreenCameraPositionKey
import com.spectra.app.constants.LockScreenCardKey
import com.spectra.app.constants.LockScreenClockKey
import com.spectra.app.constants.LockScreenLyricsKey
import com.spectra.app.constants.LockScreenNotificationsKey
import com.spectra.app.constants.LockScreenTorchPositionKey
import com.spectra.app.constants.LockScreenWallpaperPathKey
import com.spectra.app.constants.LockScreenTextAlignKey
import com.spectra.app.constants.LockShortcutPosition
import com.spectra.app.constants.LockTextAlign
import com.spectra.app.ui.component.DefaultDialog
import com.spectra.app.ui.component.Material3SettingsGroup
import com.spectra.app.ui.component.Material3SettingsItem
import com.spectra.app.ui.lockscreen.LockScreenAccessibilityService
import com.spectra.app.ui.lockscreen.LockScreenWallpaperStore
import com.spectra.app.utils.rememberEnumPreference
import com.spectra.app.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LockScreenSettingsGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (card, setCard) = rememberPreference(LockScreenCardKey, defaultValue = true)
    val (lyrics, setLyrics) = rememberPreference(LockScreenLyricsKey, defaultValue = true)
    val (clock, setClock) = rememberPreference(LockScreenClockKey, defaultValue = true)
    val (notif, setNotif) = rememberPreference(LockScreenNotificationsKey, defaultValue = true)
    val (artistWallpaper, setArtistWallpaper) = rememberPreference(LockScreenArtistWallpaperKey, defaultValue = true)
    val (wallpaperPath, setWallpaperPath) = rememberPreference(LockScreenWallpaperPathKey, defaultValue = "")
    val (torchPos, setTorchPos) = rememberEnumPreference(LockScreenTorchPositionKey, LockShortcutPosition.LEFT)
    val (cameraPos, setCameraPos) = rememberEnumPreference(LockScreenCameraPositionKey, LockShortcutPosition.RIGHT)
    val (textAlign, setTextAlign) = rememberEnumPreference(LockScreenTextAlignKey, LockTextAlign.LEFT)
    var pickPosition by remember { mutableStateOf<Int?>(null) }
    var showTextAlign by remember { mutableStateOf(false) }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val path = withContext(Dispatchers.IO) { LockScreenWallpaperStore.import(context, uri) }
                if (path != null) {
                    setWallpaperPath(path)
                } else {
                    android.widget.Toast.makeText(context, R.string.lockscreen_custom_wallpaper_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

    pickPosition?.let { which ->
        val current = if (which == 0) torchPos else cameraPos
        val set: (LockShortcutPosition) -> Unit = if (which == 0) setTorchPos else setCameraPos
        DefaultDialog(
            onDismiss = { pickPosition = null },
            title = { Text(stringResource(if (which == 0) R.string.lockscreen_flashlight_position else R.string.lockscreen_camera_position)) },
            buttons = { TextButton(onClick = { pickPosition = null }) { Text(stringResource(android.R.string.ok)) } },
        ) {
            LockShortcutPosition.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { set(option) },
                ) {
                    RadioButton(selected = current == option, onClick = { set(option) })
                    Text(stringResource(option.label()), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showTextAlign) {
        DefaultDialog(
            onDismiss = { showTextAlign = false },
            title = { Text(stringResource(R.string.lockscreen_text_position)) },
            buttons = { TextButton(onClick = { showTextAlign = false }) { Text(stringResource(android.R.string.ok)) } },
        ) {
            LockTextAlign.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { setTextAlign(option) },
                ) {
                    RadioButton(selected = textAlign == option, onClick = { setTextAlign(option) })
                    Text(stringResource(option.label()), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    Material3SettingsGroup(
        title = stringResource(R.string.lockscreen_group),
        items =
            buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.lock),
                    title = { Text(stringResource(R.string.lockscreen_a11y_allow)) },
                    description = {
                        Text(stringResource(if (LockScreenAccessibilityService.isEnabled) R.string.lockscreen_a11y_on else R.string.lockscreen_a11y_off))
                    },
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lockscreen_text_position)) },
                    description = { Text(stringResource(textAlign.label())) },
                    onClick = { showTextAlign = true },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.lockscreen_card)) },
                    trailingContent = { Switch(checked = card, onCheckedChange = setCard, thumbContent = { SwitchIcon(card) }) },
                    onClick = { setCard(!card) },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lockscreen_lyrics)) },
                    trailingContent = { Switch(checked = lyrics, onCheckedChange = setLyrics, thumbContent = { SwitchIcon(lyrics) }) },
                    onClick = { setLyrics(!lyrics) },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.timer),
                    title = { Text(stringResource(R.string.lockscreen_clock)) },
                    trailingContent = { Switch(checked = clock, onCheckedChange = setClock, thumbContent = { SwitchIcon(clock) }) },
                    onClick = { setClock(!clock) },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.notification),
                    title = { Text(stringResource(R.string.lockscreen_notifications_shortcut)) },
                    description = { Text(stringResource(R.string.lockscreen_notifications_shortcut_desc)) },
                    trailingContent = { Switch(checked = notif, onCheckedChange = setNotif, thumbContent = { SwitchIcon(notif) }) },
                    onClick = { setNotif(!notif) },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.lockscreen_artist_wallpaper)) },
                    description = { Text(stringResource(R.string.lockscreen_artist_wallpaper_desc)) },
                    trailingContent = { Switch(checked = artistWallpaper, onCheckedChange = setArtistWallpaper, thumbContent = { SwitchIcon(artistWallpaper) }) },
                    onClick = { setArtistWallpaper(!artistWallpaper) },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.insert_photo),
                    title = { Text(stringResource(R.string.lockscreen_custom_wallpaper)) },
                    description = {
                        Text(
                            stringResource(
                                if (wallpaperPath.isNotEmpty()) R.string.lockscreen_custom_wallpaper_set else R.string.lockscreen_custom_wallpaper_desc,
                            ),
                        )
                    },
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.flashlight),
                    title = { Text(stringResource(R.string.lockscreen_flashlight_position)) },
                    description = { Text(stringResource(torchPos.label())) },
                    onClick = { pickPosition = 0 },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.camera),
                    title = { Text(stringResource(R.string.lockscreen_camera_position)) },
                    description = { Text(stringResource(cameraPos.label())) },
                    onClick = { pickPosition = 1 },
                ))
            },
    )
    Spacer(Modifier.height(24.dp))
}

private fun LockShortcutPosition.label(): Int =
    when (this) {
        LockShortcutPosition.LEFT -> R.string.lockscreen_position_left
        LockShortcutPosition.RIGHT -> R.string.lockscreen_position_right
        LockShortcutPosition.OFF -> R.string.lockscreen_position_off
    }

private fun LockTextAlign.label(): Int =
    when (this) {
        LockTextAlign.LEFT -> R.string.lockscreen_text_left
        LockTextAlign.CENTER -> R.string.lockscreen_text_center
    }
