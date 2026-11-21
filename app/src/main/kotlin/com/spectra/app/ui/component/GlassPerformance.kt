package com.spectra.app.ui.component

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassQuality { LOW, MEDIUM, HIGH }

val LocalGlassQuality = staticCompositionLocalOf { GlassQuality.MEDIUM }

/** Liquid Glass selalu dipaksa penuh: tidak ada penurunan kualitas karena hemat daya, RAM, atau perangkat. */
@Suppress("UNUSED_PARAMETER")
fun detectGlassQuality(context: Context): GlassQuality =
    if (Build.VERSION.SDK_INT >= 33) GlassQuality.HIGH else GlassQuality.MEDIUM

@Composable
@ReadOnlyComposable
fun glassBlur(requested: Dp): Dp {
    val cap =
        when (LocalGlassQuality.current) {
            GlassQuality.HIGH -> 48.dp
            GlassQuality.MEDIUM -> 32.dp
            GlassQuality.LOW -> 20.dp
        }
    return if (requested > cap) cap else requested
}
