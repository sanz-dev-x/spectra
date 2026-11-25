/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.glass

import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spectra.app.constants.GlassSprings
import com.spectra.app.constants.MiniPlayerCorner
import com.spectra.app.ui.component.SoftGlass
import com.spectra.app.ui.component.SoftGlassHigh

/**
 * Design tokens Liquid Glass. Nilai awal identik dengan literal yang sebelumnya tertanam di kyantChrome.
 *
 * Slot: [light] = Liquid Glass Terang, [dark] = Liquid Glass Gelap.
 */
@Immutable
class GlassValue<T>(
    val light: T,
    val dark: T,
) {
    fun pick(dark: Boolean): T = if (dark) this.dark else light
}

/** Lensa: tinggi dan jumlah refraksi. */
@Immutable
class GlassLens(
    val height: Dp,
    val amount: Dp,
)

/** Permukaan utama (navbar, tab bar, mini player, area teks). */
object GlassRegular {
    /** Isi permukaan kaca Terang/Gelap. Kertas memakai jalur krom (lihat [GlassTint]). */
    val fill = GlassValue(SoftGlass.copy(alpha = 0.62f), Color(0xFF26262B).copy(alpha = 0.50f))

    /** Kilau atas (gradien vertikal dan titik cahaya mini player). */
    val sheen = GlassValue(SoftGlassHigh.copy(alpha = 0.24f), SoftGlassHigh.copy(alpha = 0.14f))

    /** Bayangan tipis di dasar permukaan. */
    val shade = GlassValue(Color.Black.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.04f))
}

/** Lensa dan saturasi (refraksi) untuk navbar dan mini player. */
object GlassClear {
    const val SATURATION = 1.7f
    const val FILL_SCALE = 0.5f
    val lensNav = GlassValue(GlassLens(16.dp, 22.dp), GlassLens(16.dp, 22.dp))
    val lensMini = GlassValue(GlassLens(16.dp, 22.dp), GlassLens(16.dp, 22.dp))
}

/** Scrim hitam di atas permukaan. 0 di navbar dan mini player; konstanta Tahap 2 hanya acuan. */
object GlassScrim {
    val alpha = GlassValue(0f, 0f)
    const val MAX_ALPHA = 0.55f
    const val STEP = 0.05f
    const val MIN_RATIO = 4.5f
}

/**
 * Tint adaptif navbar dan mini player mengikuti artwork/latar. Alpha kecil dan luminans dikunci per mode,
 * jadi artwork terang tidak membuat kaca Gelap terang, dan artwork gelap tidak membuatnya hitam datar.
 * Kertas/OFF tidak memakai. Set [ENABLED] ke false untuk kembali ke fill statis.
 */
object GlassAdapt {
    const val ENABLED = true
    val tintAlpha = GlassValue(0.10f, 0.14f)
    val lumMin = GlassValue(0.55f, 0.08f)
    val lumMax = GlassValue(0.92f, 0.45f)
}

/** Rona ungu halus di tepi bawah mini player. */
object GlassTint {
    val purple = GlassValue(0.12f, 0.12f)
}

/** Highlight tepi. */
object GlassHighlight {
    val edgeWidth = 1.dp
    val edgeAlpha = GlassValue(0.7f, 0.9f)
    const val ANGLE_BASE = 45f
    const val TILT_X = 40f
    const val TILT_Y = 25f
}

/** Bayangan luar dan dalam. */
object GlassShadow {
    val radius = 18.dp
    val alpha = GlassValue(0.28f, 0.5f)
    val innerRadiusNav = 8.dp
    val innerRadiusMini = 10.dp
    val innerAlpha = GlassValue(0.35f, 0.55f)
}

/** Blur dasar, dikalikan LocalGlassNavBlur. */
object GlassBlur {
    val nav = GlassValue(20.dp, 20.dp)
    val mini = GlassValue(26.dp, 26.dp)
}

/** Gerak. Belum dipakai pemanggil; mengacu ke konstanta yang sudah ada. */
object GlassMotion {
    const val COLOR_MS = 900
    val press: SpringSpec<Float> get() = GlassSprings.press
    val release: SpringSpec<Float> get() = GlassSprings.release
    val settle: SpringSpec<Float> get() = GlassSprings.settle
    val morph: SpringSpec<Float> get() = GlassSprings.morph
}

/** Refraksi/specular HD di tepi. Slot: light = Terang, dark = Gelap. Kertas/OFF tidak memakai. */
object GlassHd {
    val bezel = 16.dp
    val bezelButton = 10.dp
    val specK = GlassValue(0.44f, 0.55f)
    val envK = GlassValue(0.45f, 0.36f)
    const val SPEC_EXP = 70f
    const val SPEC_EXP_GAIN = 20f
    const val SPEC_WIDTH_DP = 1.4f
}
