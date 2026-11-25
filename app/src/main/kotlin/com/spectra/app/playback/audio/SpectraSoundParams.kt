package com.spectra.app.playback.audio

enum class SoundstageProfile { OFF, INTIMATE, WIDE, STAGE, IN_EAR }

enum class InstrumentFocus { OFF, VOCAL, DRUMS, BASS }

/**
 * Snapshot parameter untuk [SpectraSoundProcessor]. Urutan proses tetap dan deterministik:
 * Instrument Focus -> Soundstage -> Balance -> Adaptive Limiter (selalu terakhir).
 */
data class SpectraSoundParams(
    val balance: Float = 0f,
    val limiterEnabled: Boolean = true,
    val soundstage: SoundstageProfile = SoundstageProfile.OFF,
    val focus: InstrumentFocus = InstrumentFocus.OFF,
    val focusAmount: Float = 0f,
) {
    val neutral: Boolean
        get() = balance == 0f && !limiterEnabled && soundstage == SoundstageProfile.OFF &&
            (focus == InstrumentFocus.OFF || focusAmount == 0f)
}

object SpectraSound {
    @Volatile
    var params = SpectraSoundParams()
}
