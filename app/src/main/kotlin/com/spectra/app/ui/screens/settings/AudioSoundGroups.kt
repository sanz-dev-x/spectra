package com.spectra.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.spectra.app.R
import com.spectra.app.constants.AdaptiveLimiterKey
import com.spectra.app.constants.AudioBalanceKey
import com.spectra.app.constants.CrossfadeGaplessKey
import com.spectra.app.constants.InstrumentFocusAmountKey
import com.spectra.app.constants.InstrumentFocusKey
import com.spectra.app.constants.LockScreenPlayerKey
import com.spectra.app.constants.SoundstageProfileKey
import com.spectra.app.constants.VolumeMemoryScopeKey
import com.spectra.app.playback.audio.InstrumentFocus
import com.spectra.app.playback.audio.SoundstageProfile
import com.spectra.app.playback.audio.VolumeMemoryScope
import com.spectra.app.ui.component.DefaultDialog
import com.spectra.app.ui.component.EnumDialog
import com.spectra.app.ui.component.Material3SettingsGroup
import com.spectra.app.ui.component.Material3SettingsItem
import com.spectra.app.ui.component.Switch
import com.spectra.app.utils.rememberEnumPreference
import com.spectra.app.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SwitchIcon(checked: Boolean) {
    Icon(
        painter = painterResource(if (checked) R.drawable.check else R.drawable.close),
        contentDescription = null,
        modifier = Modifier.size(SwitchDefaults.IconSize),
    )
}

/**
 * Tiga kelompok sesuai tujuan. Satu kontrol per fungsi; kontrol lanjutan hanya muncul saat dibutuhkan.
 * Loudness Match dioper dari pemanggil karena dialognya sudah dimiliki PlayerSettings.
 */
@Composable
fun AudioSoundGroups(
    loudnessMatch: Boolean,
    onLoudnessMatchChange: (Boolean) -> Unit,
    loudnessLabel: String,
    onLoudnessClick: () -> Unit,
) {
    val (gapless, onGaplessChange) = rememberPreference(CrossfadeGaplessKey, defaultValue = true)

    val (lockScreenPlayer, setLockScreenPlayer) = rememberPreference(LockScreenPlayerKey, defaultValue = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    var showOverlayInfo by remember { mutableStateOf(false) }
    // Pemutar penuh di atas lockscreen opsional: tanpa izin overlay, kontrol tetap tampil lewat notifikasi media.
    fun askOverlayIfNeeded() {
        if (!android.provider.Settings.canDrawOverlays(context)) showOverlayInfo = true
    }
    val notificationLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (!granted) {
                android.widget.Toast.makeText(context, R.string.lockscreen_notification_denied, android.widget.Toast.LENGTH_LONG).show()
            }
            askOverlayIfNeeded()
        }
    val onLockScreenPlayerChange: (Boolean) -> Unit = { enabled ->
        setLockScreenPlayer(enabled)
        if (enabled) {
            val needsNotification =
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            if (needsNotification) {
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                askOverlayIfNeeded()
            }
        }
    }
    if (showOverlayInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOverlayInfo = false },
            title = { Text(stringResource(R.string.lockscreen_overlay_title)) },
            text = { Text(stringResource(R.string.lockscreen_overlay_message) + "\n\n" + stringResource(R.string.lockscreen_a11y_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayInfo = false
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                }) { Text(stringResource(R.string.lockscreen_overlay_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayInfo = false
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text(stringResource(R.string.lockscreen_a11y_title)) }
            },
        )
    }

    val (balance, onBalanceChange) = rememberPreference(AudioBalanceKey, defaultValue = 0f)
    val (limiter, onLimiterChange) = rememberPreference(AdaptiveLimiterKey, defaultValue = true)
    val (memoryScope, onMemoryScopeChange) = rememberEnumPreference(VolumeMemoryScopeKey, defaultValue = VolumeMemoryScope.OFF)

    val (soundstage, onSoundstageChange) = rememberEnumPreference(SoundstageProfileKey, defaultValue = SoundstageProfile.OFF)
    val (focus, onFocusChange) = rememberEnumPreference(InstrumentFocusKey, defaultValue = InstrumentFocus.OFF)
    val (focusAmount, onFocusAmountChange) = rememberPreference(InstrumentFocusAmountKey, defaultValue = 0f)

    var showBalance by remember { mutableStateOf(false) }
    var showSoundstage by remember { mutableStateOf(false) }
    var showFocus by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }

    val soundstageLabel: @Composable (SoundstageProfile) -> String = {
        stringResource(
            when (it) {
                SoundstageProfile.OFF -> R.string.soundstage_off
                SoundstageProfile.INTIMATE -> R.string.soundstage_intimate
                SoundstageProfile.WIDE -> R.string.soundstage_wide
                SoundstageProfile.STAGE -> R.string.soundstage_stage
                SoundstageProfile.IN_EAR -> R.string.soundstage_in_ear
            },
        )
    }
    val focusLabel: @Composable (InstrumentFocus) -> String = {
        stringResource(
            when (it) {
                InstrumentFocus.OFF -> R.string.instrument_focus_off
                InstrumentFocus.VOCAL -> R.string.instrument_focus_vocal
                InstrumentFocus.DRUMS -> R.string.instrument_focus_drums
                InstrumentFocus.BASS -> R.string.instrument_focus_bass
            },
        )
    }
    val memoryLabel: @Composable (VolumeMemoryScope) -> String = {
        stringResource(
            when (it) {
                VolumeMemoryScope.OFF -> R.string.volume_memory_off
                VolumeMemoryScope.SONG -> R.string.volume_memory_song
                VolumeMemoryScope.ALBUM -> R.string.volume_memory_album
                VolumeMemoryScope.PLAYLIST -> R.string.volume_memory_playlist
                VolumeMemoryScope.DEVICE -> R.string.volume_memory_device
            },
        )
    }
    val balancePct = (balance * 100).roundToInt()
    val balanceLabel =
        when {
            balancePct == 0 -> stringResource(R.string.balance_center)
            balancePct < 0 -> stringResource(R.string.balance_left, abs(balancePct))
            else -> stringResource(R.string.balance_right, balancePct)
        }

    if (showBalance) {
        DefaultDialog(
            onDismiss = { showBalance = false },
            title = { Text(stringResource(R.string.audio_balance)) },
            buttons = {
                TextButton(onClick = { onBalanceChange(0f) }) { Text(stringResource(R.string.balance_center)) }
                TextButton(onClick = { showBalance = false }) { Text(stringResource(android.R.string.ok)) }
            },
        ) {
            Text(balanceLabel)
            Slider(value = balance, onValueChange = { onBalanceChange((it * 20).roundToInt() / 20f) }, valueRange = -1f..1f)
        }
    }
    if (showFocus) {
        DefaultDialog(
            onDismiss = { showFocus = false },
            title = { Text(stringResource(R.string.instrument_focus)) },
            buttons = { TextButton(onClick = { showFocus = false }) { Text(stringResource(android.R.string.ok)) } },
        ) {
            InstrumentFocus.values().forEach { option ->
                TextButton(onClick = { onFocusChange(option); if (option == InstrumentFocus.OFF) onFocusAmountChange(0f) }) {
                    Text(focusLabel(option) + if (option == focus) "  ✓" else "")
                }
            }
            if (focus != InstrumentFocus.OFF) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.focus_amount))
                Slider(value = focusAmount, onValueChange = { onFocusAmountChange((it * 20).roundToInt() / 20f) }, valueRange = -1f..1f)
            }
        }
    }
    if (showSoundstage) {
        EnumDialog(
            onDismiss = { showSoundstage = false },
            onSelect = { onSoundstageChange(it); showSoundstage = false },
            title = stringResource(R.string.soundstage_profile),
            current = soundstage,
            values = SoundstageProfile.values().toList(),
            valueText = { soundstageLabel(it) },
        )
    }
    if (showMemory) {
        EnumDialog(
            onDismiss = { showMemory = false },
            onSelect = { onMemoryScopeChange(it); showMemory = false },
            title = stringResource(R.string.volume_memory),
            current = memoryScope,
            values = VolumeMemoryScope.values().toList(),
            valueText = { memoryLabel(it) },
        )
    }

    Column {
        Material3SettingsGroup(
            title = stringResource(R.string.audio_group_playback),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.gapless_album)) },
                    trailingContent = { Switch(checked = gapless, onCheckedChange = onGaplessChange, thumbContent = { SwitchIcon(gapless) }) },
                    onClick = { onGaplessChange(!gapless) },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.lock),
                    title = { Text(stringResource(R.string.lockscreen_player)) },
                    description = { Text(stringResource(R.string.lockscreen_player_desc)) },
                    trailingContent = { Switch(checked = lockScreenPlayer, onCheckedChange = onLockScreenPlayerChange, thumbContent = { SwitchIcon(lockScreenPlayer) }) },
                    onClick = { onLockScreenPlayerChange(!lockScreenPlayer) },
                ))
            },
        )
        Spacer(Modifier.height(24.dp))
        LockScreenSettingsGroup()
        
        Material3SettingsGroup(
            title = stringResource(R.string.audio_group_volume),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text(stringResource(R.string.audio_normalization)) },
                    trailingContent = { Switch(checked = loudnessMatch, onCheckedChange = onLoudnessMatchChange, thumbContent = { SwitchIcon(loudnessMatch) }) },
                    onClick = { onLoudnessMatchChange(!loudnessMatch) },
                ))
                if (loudnessMatch) {
                    add(Material3SettingsItem(
                        icon = painterResource(R.drawable.volume_up),
                        title = { Text(stringResource(R.string.loudness_level)) },
                        description = { Text(loudnessLabel) },
                        onClick = onLoudnessClick,
                    ))
                }
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.audio_balance)) },
                    description = { Text(balanceLabel) },
                    onClick = { showBalance = true },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_down),
                    title = { Text(stringResource(R.string.adaptive_limiter)) },
                    trailingContent = { Switch(checked = limiter, onCheckedChange = onLimiterChange, thumbContent = { SwitchIcon(limiter) }) },
                    onClick = { onLimiterChange(!limiter) },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.volume_mute),
                    title = { Text(stringResource(R.string.volume_memory)) },
                    description = { Text(memoryLabel(memoryScope)) },
                    onClick = { showMemory = true },
                ))
            },
        )
        Spacer(Modifier.height(24.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.audio_group_sound),
            items = buildList {
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.discover_tune),
                    title = { Text(stringResource(R.string.soundstage_profile)) },
                    description = { Text(soundstageLabel(soundstage)) },
                    onClick = { showSoundstage = true },
                ))
                add(Material3SettingsItem(
                    icon = painterResource(R.drawable.equalizer),
                    title = { Text(stringResource(R.string.instrument_focus)) },
                    description = { Text(focusLabel(focus)) },
                    onClick = { showFocus = true },
                ))
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}
