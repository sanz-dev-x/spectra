package com.spectra.app.playback.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import timber.log.Timber

/** Preset reverb bawaan Android ([PresetReverb]). */
enum class ReverbPreset(val value: Short) {
    NONE(PresetReverb.PRESET_NONE),
    SMALL_ROOM(PresetReverb.PRESET_SMALLROOM),
    MEDIUM_ROOM(PresetReverb.PRESET_MEDIUMROOM),
    LARGE_ROOM(PresetReverb.PRESET_LARGEROOM),
    MEDIUM_HALL(PresetReverb.PRESET_MEDIUMHALL),
    LARGE_HALL(PresetReverb.PRESET_LARGEHALL),
    PLATE(PresetReverb.PRESET_PLATE),
}

/** Preset pengubah suara: kombinasi kecepatan, pitch (rasio), bass, reverb, dan virtualizer. */
enum class VoicePreset(
    val speed: Float,
    val pitch: Float,
    val bass: Int,
    val reverb: ReverbPreset,
    val virtualizer: Int,
) {
    NORMAL(1f, 1f, 0, ReverbPreset.NONE, 0),
    CONCERT(1f, 1f, 300, ReverbPreset.MEDIUM_HALL, 700),
    ECHO(1f, 1f, 0, ReverbPreset.LARGE_HALL, 0),
    CHIPMUNK(1f, 1.6f, 0, ReverbPreset.NONE, 0),
    ROBOT(1f, 0.85f, 0, ReverbPreset.SMALL_ROOM, 400),
    DEEP(1f, 0.75f, 700, ReverbPreset.NONE, 0),
    RADIO(1f, 1f, 0, ReverbPreset.SMALL_ROOM, 0),
    NIGHT(0.9f, 1f, 300, ReverbPreset.MEDIUM_ROOM, 200),
    VOCAL(1f, 1f, 0, ReverbPreset.NONE, 300),
    CINEMATIC(1f, 1f, 600, ReverbPreset.LARGE_HALL, 900),
}

data class SoundEffectsState(
    val bass: Int = 0,
    val reverb: ReverbPreset = ReverbPreset.NONE,
    val virtualizer: Int = 0,
)

/**
 * Mengikat BassBoost, PresetReverb, dan Virtualizer ke satu audio session.
 * Semua pemanggilan dibungkus runCatching karena tidak semua perangkat mendukung efek tersebut.
 */
class SoundEffectsController {
    private var sessionId = 0
    private var bassBoost: BassBoost? = null
    private var reverb: PresetReverb? = null
    private var virtualizer: Virtualizer? = null
    private var state = SoundEffectsState()

    /** Menyambungkan reverb sebagai aux effect ke player; effectId < 0 berarti lepas. Dipanggil dari service. */
    @Volatile var auxSink: ((effectId: Int, sendLevel: Float) -> Unit)? = null

    @Synchronized
    fun update(newState: SoundEffectsState) {
        state = newState
        apply()
    }

    @Synchronized
    fun bind(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId != sessionId) {
            releaseEffects()
            sessionId = audioSessionId
        }
        apply()
    }

    @Synchronized
    fun release() {
        releaseEffects()
        auxSink?.invoke(-1, 0f)
        runCatching { reverb?.release() }
        reverb = null
        sessionId = 0
    }

    private fun apply() {
        applyReverb()
        if (sessionId <= 0) return
        runCatching {
            if (state.bass > 0) {
                val effect = bassBoost ?: BassBoost(0, sessionId).also { bassBoost = it }
                if (effect.strengthSupported) effect.setStrength(state.bass.coerceIn(0, 1000).toShort())
                effect.enabled = true
            } else {
                bassBoost?.enabled = false
            }
        }.onFailure { Timber.tag(TAG).w(it, "BassBoost gagal") }

        runCatching {
            if (state.virtualizer > 0) {
                val effect = virtualizer ?: Virtualizer(0, sessionId).also { virtualizer = it }
                if (effect.strengthSupported) effect.setStrength(state.virtualizer.coerceIn(0, 1000).toShort())
                effect.enabled = true
            } else {
                virtualizer?.enabled = false
            }
        }.onFailure { Timber.tag(TAG).w(it, "Virtualizer gagal") }
    }

    /** PresetReverb adalah aux effect: dipasang di output mix lalu dikirim lewat player, bukan sebagai insert efek sesi. */
    private fun applyReverb() {
        runCatching {
            if (state.reverb != ReverbPreset.NONE) {
                val effect = reverb ?: PresetReverb(0, 0).also { reverb = it }
                effect.preset = state.reverb.value
                effect.enabled = true
                auxSink?.invoke(effect.id, 1f)
            } else {
                auxSink?.invoke(-1, 0f)
                runCatching { reverb?.release() }
                reverb = null
            }
        }.onFailure {
            Timber.tag(TAG).w(it, "PresetReverb gagal")
            auxSink?.invoke(-1, 0f)
            runCatching { reverb?.release() }
            reverb = null
        }
    }

    private fun releaseEffects() {
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        bassBoost = null
        virtualizer = null
    }

    private companion object {
        const val TAG = "SoundEffects"
    }
}
