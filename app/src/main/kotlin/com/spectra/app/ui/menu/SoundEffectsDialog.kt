package com.spectra.app.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackParameters
import com.spectra.app.LocalListenTogetherManager
import com.spectra.app.LocalPlayerConnection
import com.spectra.app.R
import com.spectra.app.constants.FxBassKey
import com.spectra.app.constants.FxReverbKey
import com.spectra.app.constants.FxVirtualizerKey
import com.spectra.app.constants.FxVoicePresetKey
import com.spectra.app.playback.audio.ReverbPreset
import com.spectra.app.playback.audio.VoicePreset
import com.spectra.app.utils.rememberPreference
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
private fun voiceLabel(preset: VoicePreset): String =
    stringResource(
        when (preset) {
            VoicePreset.NORMAL -> R.string.fx_voice_normal
            VoicePreset.CONCERT -> R.string.fx_voice_concert
            VoicePreset.ECHO -> R.string.fx_voice_echo
            VoicePreset.CHIPMUNK -> R.string.fx_voice_chipmunk
            VoicePreset.ROBOT -> R.string.fx_voice_robot
            VoicePreset.DEEP -> R.string.fx_voice_deep
            VoicePreset.RADIO -> R.string.fx_voice_radio
            VoicePreset.NIGHT -> R.string.fx_voice_night
            VoicePreset.VOCAL -> R.string.fx_voice_vocal
            VoicePreset.CINEMATIC -> R.string.fx_voice_cinematic
        },
    )

@Composable
private fun reverbLabel(preset: ReverbPreset): String =
    stringResource(
        when (preset) {
            ReverbPreset.NONE -> R.string.fx_reverb_none
            ReverbPreset.SMALL_ROOM -> R.string.fx_reverb_small_room
            ReverbPreset.MEDIUM_ROOM -> R.string.fx_reverb_medium_room
            ReverbPreset.LARGE_ROOM -> R.string.fx_reverb_large_room
            ReverbPreset.MEDIUM_HALL -> R.string.fx_reverb_medium_hall
            ReverbPreset.LARGE_HALL -> R.string.fx_reverb_large_hall
            ReverbPreset.PLATE -> R.string.fx_reverb_plate
        },
    )

@Composable
private fun FxSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SoundEffectsDialog(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val inRoom = LocalListenTogetherManager.current?.isInRoom ?: false

    var bass by rememberPreference(FxBassKey, defaultValue = 0)
    var virtualizer by rememberPreference(FxVirtualizerKey, defaultValue = 0)
    var reverbName by rememberPreference(FxReverbKey, defaultValue = ReverbPreset.NONE.name)
    var voiceName by rememberPreference(FxVoicePresetKey, defaultValue = VoicePreset.NORMAL.name)

    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed.coerceIn(0.5f, 2f)) }
    var semitones by remember {
        mutableFloatStateOf((12 * log2(player.playbackParameters.pitch)).roundToInt().toFloat().coerceIn(-12f, 12f))
    }
    var tick by remember { mutableStateOf(0) }

    fun pushParams() {
        player.playbackParameters = PlaybackParameters(speed, 2f.pow(semitones / 12f))
    }

    fun customize() {
        voiceName = ""
    }

    val reverb = runCatching { ReverbPreset.valueOf(reverbName) }.getOrDefault(ReverbPreset.NONE)
    val activeVoice = runCatching { VoicePreset.valueOf(voiceName) }.getOrNull()

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fx_title)) },
        dismissButton = {
            TextButton(
                onClick = {
                    bass = 0
                    virtualizer = 0
                    reverbName = ReverbPreset.NONE.name
                    voiceName = VoicePreset.NORMAL.name
                    speed = 1f
                    semitones = 0f
                    pushParams()
                    tick++
                },
            ) { Text(stringResource(R.string.reset)) }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.fx_voice_changer), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    VoicePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = activeVoice == preset,
                            onClick = {
                                voiceName = preset.name
                                bass = preset.bass
                                virtualizer = preset.virtualizer
                                reverbName = preset.reverb.name
                                if (!inRoom) speed = preset.speed.coerceIn(0.5f, 2f)
                                semitones = (12 * log2(preset.pitch)).roundToInt().toFloat()
                                pushParams()
                            },
                            label = { Text(voiceLabel(preset)) },
                        )
                    }
                }

                FxSlider(
                    label = stringResource(R.string.fx_bass_boost),
                    valueText = "${bass / 10}%",
                    value = bass.toFloat(),
                    range = 0f..1000f,
                ) {
                    bass = it.roundToInt()
                    customize()
                }
                FxSlider(
                    label = stringResource(R.string.fx_spatial),
                    valueText = "${virtualizer / 10}%",
                    value = virtualizer.toFloat(),
                    range = 0f..1000f,
                ) {
                    virtualizer = it.roundToInt()
                    customize()
                }

                Text(stringResource(R.string.fx_reverb), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ReverbPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = reverb == preset,
                            onClick = {
                                reverbName = preset.name
                                customize()
                            },
                            label = { Text(reverbLabel(preset)) },
                        )
                    }
                }

                FxSlider(
                    label = stringResource(R.string.speed),
                    valueText = "x${(speed * 100).roundToInt() / 100f}",
                    value = speed,
                    range = 0.5f..2f,
                    enabled = !inRoom,
                ) {
                    speed = (it * 20).roundToInt() / 20f
                    pushParams()
                    customize()
                }
                FxSlider(
                    label = stringResource(R.string.fx_pitch),
                    valueText = "${if (semitones > 0) "+" else ""}${semitones.roundToInt()}",
                    value = semitones,
                    range = -12f..12f,
                ) {
                    semitones = it.roundToInt().toFloat()
                    pushParams()
                    customize()
                }
                Text(
                    stringResource(R.string.fx_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
}
