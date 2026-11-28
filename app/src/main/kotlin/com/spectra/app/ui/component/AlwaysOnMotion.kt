package com.spectra.app.ui.component

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer

/**
 * Compose membaca "Skala durasi animator" dari opsi developer dan membuat semua animasi lompat langsung ke akhir
 * saat nilainya 0x. Recomposer jendela dibuat dengan skala tetap 1x, jadi animasi pendek tetap terlihat.
 * Animasi berat (parallax sensor, latar cair berkelanjutan) tetap mati di 0x, lihat reduceMotionEnabled.
 */
private object FixedMotionScale : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

@OptIn(InternalComposeUiApi::class)
fun installAlwaysOnMotion() {
    WindowRecomposerPolicy.setFactory(
        WindowRecomposerFactory { view -> view.createLifecycleAwareWindowRecomposer(FixedMotionScale) },
    )
}
