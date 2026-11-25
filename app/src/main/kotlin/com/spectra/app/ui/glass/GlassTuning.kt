/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import com.spectra.app.constants.MiniGlassBlurKey
import com.spectra.app.constants.MiniGlassTransparencyKey
import com.spectra.app.constants.NavGlassBlurKey
import com.spectra.app.constants.NavGlassTransparencyKey
import com.spectra.app.utils.rememberPreference

enum class GlassChrome { NAV, MINI }

/**
 * Pengaturan kaca per komponen (Navbar / Mini Player): blur dan transparansi latar, masing-masing 0..1.
 * Nilai dibaca langsung dari State di fase draw, sehingga slider berubah real-time tanpa recomposition.
 * Default (blur 0, transparency 0) sama persis dengan tampilan sebelumnya.
 */
@Stable
class GlassTuning(
    private val blurState: State<Float>,
    private val transparencyState: State<Float>,
) {
    val blur: Float get() = blurState.value.coerceIn(0f, 1f)
    val transparency: Float get() = transparencyState.value.coerceIn(0f, 1f)
}

const val GlassTuningMaxBlurDp = 32f

@Composable
fun rememberGlassTuning(chrome: GlassChrome): GlassTuning {
    val nav = chrome == GlassChrome.NAV
    val blur = rememberPreference(if (nav) NavGlassBlurKey else MiniGlassBlurKey, 0f)
    val transparency = rememberPreference(if (nav) NavGlassTransparencyKey else MiniGlassTransparencyKey, 0f)
    return androidx.compose.runtime.remember(chrome) { GlassTuning(blur, transparency) }
}
