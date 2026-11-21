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

val NavigationBarHeight = 80.dp
val SlimNavBarHeight = 68.dp
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

val ThumbnailCornerRadius = 3.dp

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec: FiniteAnimationSpec<Dp>
    get() = tween(300, easing = GlassEasing)

val GlassEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

@Volatile
var performanceModeEnabled = true

@Volatile
var reduceMotionEnabled = false

private const val NavEnterMillis = 300
private const val NavExitMillis = 180
private const val NavFadeInMillis = 200
private const val NavFadeOutMillis = 120

object GlassSprings {
    val press = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)
    val release = spring<Float>(dampingRatio = 0.62f, stiffness = 520f)
    val lens = spring<Float>(dampingRatio = 0.8f, stiffness = 360f)
    val lensDrag = spring<Float>(dampingRatio = 0.86f, stiffness = 520f)
    val settle = spring<Float>(dampingRatio = 0.9f, stiffness = 300f)
    val morph = spring<Float>(dampingRatio = 0.78f, stiffness = 240f)
}

/** Satu bahasa gerak: masuk geser pendek + fade, keluar lebih cepat dari masuk; tab hanya fade-through tipis. */
fun navEnter(forward: Boolean, tab: Boolean = false): EnterTransition {
    if (performanceModeEnabled || reduceMotionEnabled) return EnterTransition.None
    val dir = if (forward) 1 else -1
    return if (tab) {
        fadeIn(tween(NavFadeInMillis, delayMillis = 40, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(tween(NavEnterMillis, easing = GlassEasing)) { dir * it / 14 }
    } else if (forward) {
        slideInHorizontally(tween(NavEnterMillis, easing = GlassEasing)) { it / 4 } +
            fadeIn(tween(NavFadeInMillis, easing = LinearOutSlowInEasing))
    } else {
        slideInHorizontally(tween(NavEnterMillis, easing = GlassEasing)) { -it / 8 } +
            fadeIn(tween(NavFadeInMillis, easing = LinearOutSlowInEasing))
    }
}

fun navExit(forward: Boolean, tab: Boolean = false): ExitTransition {
    if (performanceModeEnabled || reduceMotionEnabled) return ExitTransition.None
    val dir = if (forward) 1 else -1
    return if (tab) {
        fadeOut(tween(NavFadeOutMillis, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(tween(NavExitMillis, easing = GlassEasing)) { -dir * it / 18 }
    } else if (forward) {
        slideOutHorizontally(tween(NavEnterMillis, easing = GlassEasing)) { -it / 8 } +
            fadeOut(tween(NavExitMillis, easing = FastOutLinearInEasing))
    } else {
        slideOutHorizontally(tween(NavEnterMillis, easing = GlassEasing)) { it / 4 } +
            fadeOut(tween(NavExitMillis, easing = FastOutLinearInEasing))
    }
}
