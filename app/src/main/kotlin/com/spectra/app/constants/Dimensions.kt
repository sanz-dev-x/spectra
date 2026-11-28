/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.constants

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5

// Pill navbar mengisi penuh tinggi container, jadi container = tinggi MiniPlayer.
val NavigationBarHeight = 88.dp // NavFullHeight (74dp) + jarak bawah navbar 14dp
val NavigationRailWidth = 80.dp
val SlimNavBarHeight = 82.dp
val MiniPlayerHeight = 63.dp // diturunkan dari 68dp
val MiniPlayerCorner = 24.dp // sudut lebih organik ala Liquid Glass; tetap aman untuk tombol 40dp
val MiniPlayerBottomSpacing = 14.dp // Jarak jelas antara MiniPlayer dan NavigationBar
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 64.dp
val SuggestionItemHeight = 56.dp
val ListThumbnailSize = 48.dp
val SmallGridThumbnailHeight = 104.dp
val GridThumbnailHeight = 128.dp

val ThumbnailCornerRadius = 8.dp

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec: FiniteAnimationSpec<Dp>
    get() = tween(300, easing = GlassEasing)

val GlassEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

@Volatile
var performanceModeEnabled = true

@Volatile
var reduceMotionEnabled = false

@Volatile
var iosAnimationEnabled = true

private const val NavEnterMillis = 300
private const val NavLiteFadeMillis = 110

object GlassSprings {
    val press = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)
    val release = spring<Float>(dampingRatio = 0.62f, stiffness = 520f)
    val lens = spring<Float>(dampingRatio = 0.8f, stiffness = 360f)
    val lensDrag = spring<Float>(dampingRatio = 0.86f, stiffness = 520f)
    val settle = spring<Float>(dampingRatio = 0.9f, stiffness = 300f)
    val morph = spring<Float>(dampingRatio = 0.78f, stiffness = 240f)
}

/**
 * Satu bahasa gerak: mulai pada frame pertama (tanpa delayMillis), kurva iOS yang cepat di awal, hanya translasi + fade
 * (dikerjakan GPU lewat graphicsLayer). Mode performa TIDAK mengubah gerak jadi fade kaku; ia hanya mengurangi biaya render.
 */
private val TabEnterForward: EnterTransition =
    fadeIn(tween(170, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(tween(240, easing = GlassEasing)) { it / 14 }
private val TabEnterBackward: EnterTransition =
    fadeIn(tween(170, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(tween(240, easing = GlassEasing)) { -it / 14 }
private val TabExit: ExitTransition = fadeOut(tween(90, easing = FastOutLinearInEasing))

fun navEnter(forward: Boolean, tab: Boolean = false): EnterTransition {
    if (tab) return if (forward) TabEnterForward else TabEnterBackward
    if (reduceMotionEnabled) return fadeIn(tween(NavLiteFadeMillis, easing = LinearOutSlowInEasing))
    val dir = if (forward) 1 else -1
    return if (tab) {
        // Pindah tab lewat navbar: ganti halaman seketika, tanpa jeda transisi.
        EnterTransition.None
    } else if (forward) {
        slideInHorizontally(tween(if (iosAnimationEnabled) 320 else NavEnterMillis, easing = GlassEasing)) { if (iosAnimationEnabled) it else it / 4 } +
            fadeIn(tween(110, easing = LinearOutSlowInEasing))
    } else {
        slideInHorizontally(tween(if (iosAnimationEnabled) 320 else NavEnterMillis, easing = GlassEasing)) { if (iosAnimationEnabled) -it / 3 else -it / 8 } +
            fadeIn(tween(110, easing = LinearOutSlowInEasing))
    }
}

fun navExit(forward: Boolean, tab: Boolean = false): ExitTransition {
    if (tab) return TabExit
    if (reduceMotionEnabled) return fadeOut(tween(NavLiteFadeMillis, easing = FastOutLinearInEasing))
    val dir = if (forward) 1 else -1
    return if (tab) {
        ExitTransition.None
    } else if (forward) {
        slideOutHorizontally(tween(if (iosAnimationEnabled) 320 else NavEnterMillis, easing = GlassEasing)) { if (iosAnimationEnabled) -it / 3 else -it / 8 } +
            fadeOut(tween(180, easing = FastOutLinearInEasing))
    } else {
        slideOutHorizontally(tween(if (iosAnimationEnabled) 320 else NavEnterMillis, easing = GlassEasing)) { if (iosAnimationEnabled) it else it / 4 } +
            fadeOut(tween(180, easing = FastOutLinearInEasing))
    }
}
