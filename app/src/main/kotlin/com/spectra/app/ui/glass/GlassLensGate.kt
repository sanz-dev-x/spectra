package com.spectra.app.ui.glass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView

private const val JANK_NS = 33_000_000L
private const val JANK_FRAMES = 8
private const val CALM_FRAMES = 600
private const val MAX_TRIPS = 3

/**
 * Saklar shader lensa AGSL. Mati setelah [JANK_FRAMES] frame berturut-turut lebih lambat dari [JANK_NS],
 * hidup lagi setelah [CALM_FRAMES] frame normal, dan permanen mati setelah [MAX_TRIPS] kali dalam satu proses.
 */
object GlassLensGate {
    var enabled by mutableStateOf(true)
        private set
    private var jank = 0
    private var calm = 0
    private var trips = 0

    internal fun onFrame(totalNs: Long) {
        if (totalNs > JANK_NS) {
            calm = 0
            jank++
            if (enabled && jank >= JANK_FRAMES) {
                enabled = false
                trips++
            }
        } else {
            jank = 0
            if (!enabled && trips < MAX_TRIPS && ++calm >= CALM_FRAMES) {
                enabled = true
                calm = 0
            }
        }
    }
}

private fun Context.findWindow(): Window? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c.window
        c = c.baseContext
    }
    return null
}

@Composable
fun GlassLensGateEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findWindow()
        val listener =
            Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                GlassLensGate.onFrame(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
            }
        val handler = Handler(Looper.getMainLooper())
        val attached = runCatching { window?.addOnFrameMetricsAvailableListener(listener, handler) }.isSuccess && window != null
        onDispose {
            if (attached) runCatching { window?.removeOnFrameMetricsAvailableListener(listener) }
        }
    }
}
