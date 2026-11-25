package com.spectra.app.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import com.spectra.app.LocalListenTogetherManager
import com.spectra.app.LocalPlayerConnection
import com.spectra.app.R
import com.spectra.app.constants.FxBassKey
import com.spectra.app.constants.FxReverbKey
import com.spectra.app.constants.FxSoundStyleKey
import com.spectra.app.constants.FxVirtualizerKey
import com.spectra.app.constants.FxVoicePresetKey
import com.spectra.app.playback.audio.ReverbPreset
import com.spectra.app.utils.rememberPreference

/** Gaya suara siap pakai: satu pilihan mengatur kecepatan, nada, bass, spasial, dan gema sekaligus. */
enum class SoundStyle(
    val speed: Float,
    val pitch: Float,
    val bass: Int,
    val virtualizer: Int,
    val reverb: ReverbPreset,
    val title: Int,
    val desc: Int,
) {
    NORMAL(1f, 1f, 0, 0, ReverbPreset.NONE, R.string.sound_style_normal, R.string.sound_style_normal_desc),
    SPED_UP(1.2f, 1.2f, 0, 0, ReverbPreset.NONE, R.string.sound_style_sped_up, R.string.sound_style_sped_up_desc),
    SLOWED_REVERB(0.85f, 0.85f, 150, 300, ReverbPreset.LARGE_HALL, R.string.sound_style_slowed, R.string.sound_style_slowed_desc),
    NIGHTCORE(1.4f, 1.4f, 0, 0, ReverbPreset.NONE, R.string.sound_style_nightcore, R.string.sound_style_nightcore_desc),
    LOFI(0.95f, 0.95f, 250, 0, ReverbPreset.SMALL_ROOM, R.string.sound_style_lofi, R.string.sound_style_lofi_desc),
    EIGHT_D(1f, 1f, 0, 1000, ReverbPreset.MEDIUM_ROOM, R.string.sound_style_8d, R.string.sound_style_8d_desc),
    CONCERT(1f, 1f, 300, 700, ReverbPreset.MEDIUM_HALL, R.string.sound_style_concert, R.string.sound_style_concert_desc),
}

@Composable
fun SoundStyleDialog(onDismiss: () -> Unit) {
    val player = (LocalPlayerConnection.current ?: return).player
    val inRoom = LocalListenTogetherManager.current?.isInRoom ?: false
    var styleName by rememberPreference(FxSoundStyleKey, defaultValue = SoundStyle.NORMAL.name)
    var bass by rememberPreference(FxBassKey, defaultValue = 0)
    var virtualizer by rememberPreference(FxVirtualizerKey, defaultValue = 0)
    var reverbName by rememberPreference(FxReverbKey, defaultValue = ReverbPreset.NONE.name)
    var voiceName by rememberPreference(FxVoicePresetKey, defaultValue = "NORMAL")
    val current = runCatching { SoundStyle.valueOf(styleName) }.getOrDefault(SoundStyle.NORMAL)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sound_style), modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sound_style_ok)) } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SoundStyle.entries.forEach { style ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    styleName = style.name
                                    voiceName = ""
                                    bass = style.bass
                                    virtualizer = style.virtualizer
                                    reverbName = style.reverb.name
                                    if (!inRoom) player.playbackParameters = PlaybackParameters(style.speed, style.pitch)
                                }.padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = current == style, onClick = null, modifier = Modifier.padding(end = 14.dp))
                        Column {
                            Text(stringResource(style.title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(style.desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}
