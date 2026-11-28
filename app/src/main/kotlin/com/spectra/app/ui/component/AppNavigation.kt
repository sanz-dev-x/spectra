/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.spectra.app.ui.theme.LocalLargeNav
import com.spectra.app.ui.theme.LocalGlassMode
import com.spectra.app.constants.GlassSprings
import com.spectra.app.ui.theme.GlassMaterial
import com.spectra.app.ui.theme.LocalGlassMaterial
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.spectra.app.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

/** Ikon item navigasi. */
@Composable
private fun NavItemIcon(
    screen: Screens,
    iconRes: Int,
    title: String,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    accountImageUrl: String?,
) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = title,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
    accountImageUrl: String? = null,
) {
    val railLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val containerColor = if (pureBlack) Color.Black else if (railLight) Color(0xFFF2F2F4) else Color(0xFF1C1C1E)
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem || isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem && !isHomeHoldItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // Long presses are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    NavItemIcon(
                        screen = screen,
                        iconRes = iconRes,
                        title = stringResource(screen.titleId),
                        tint = LocalContentColor.current,
                        size = 24.dp,
                        accountImageUrl = accountImageUrl,
                    )
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
    accountImageUrl: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val liquid = remember { LiquidLensPhysics() }
    val shape = remember { RoundedCornerShape(50) }
    // Follow the app theme: Liquid Glass Dark -> dark navbar, Bright -> bright navbar, off -> standard (surface based).
    val navGlassMode = LocalGlassMode.current
    val lightSurface =
        when (navGlassMode) {
            com.spectra.app.ui.theme.GlassMode.DARK -> false
            com.spectra.app.ui.theme.GlassMode.BRIGHT -> true
        }
    val glassBase =
        if (lightSurface) SoftGlass.copy(alpha = 0.78f) else if (navGlassMode == com.spectra.app.ui.theme.GlassMode.DARK) Color.White.copy(alpha = 0.72f) else Color(0xFF2A2A2D).copy(alpha = 0.78f)
    // Warna navbar tetap netral: tidak mengikuti warna lagu maupun warna tema dinamis.
    val navInk = Color(0xFF1C1C1E)
    val idleBase = if (lightSurface) navInk.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.78f)
    val idleColor = idleBase.copy(alpha = if (lightSurface) 0.78f else 0.85f)
    val glassAccent = if (lightSurface) Color(0xFF8A8A8E) else Color(0xFF9A9A9E)
    val activeTint = if (lightSurface) navInk else Color.White
    val large = LocalLargeNav.current
    val collapse = LocalNavCollapse.current
    val collapseFraction = collapse.fraction
    val dockInsetLeftPx = WindowInsets.systemBars.getLeft(density, androidx.compose.ui.platform.LocalLayoutDirection.current)
    val dockShiftPx = with(density) { (MiniPlayerSideMargin - NavDockEdge).toPx() } + dockInsetLeftPx
    val homeIndex = navigationItems.indexOf(Screens.Home)

    val count = navigationItems.size.coerceAtLeast(1)
    val selectedIndex =
        navigationItems.indexOfFirst { isRouteSelected(currentRoute, it.route, navigationItems) }

    var hoverIndex by remember { mutableIntStateOf(-1) }
    var pendingIndex by remember { mutableIntStateOf(-1) }
    var fastTap by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var innerWidthPx by remember { mutableFloatStateOf(0f) }
    val barJelly = remember { Animatable(0f) }
    val itemWidthPx = innerWidthPx / count

    LaunchedEffect(selectedIndex) { pendingIndex = -1 }

    val shownIndex =
        when {
            hoverIndex >= 0 -> hoverIndex
            pendingIndex >= 0 -> pendingIndex
            else -> selectedIndex
        }

    val hlScope = androidx.compose.runtime.rememberCoroutineScope()
    val touchHighlight = rememberGlassTouchHighlight(hlScope, strength = 1.3f)
    val lensInsetPx = with(density) { 2.dp.toPx() }
    val lensX = remember { Animatable(0f) }
    var lensPlaced by remember { mutableStateOf(false) }
    val tapMode: () -> Boolean = { !dragging && (fastTap || hoverIndex >= 0) }
    LaunchedEffect(shownIndex, itemWidthPx) {
        if (shownIndex < 0 || itemWidthPx <= 0f || dragging) return@LaunchedEffect
        val target = shownIndex * itemWidthPx
        if (!lensPlaced) {
            lensX.snapTo(target)
            liquid.reset(target, itemWidthPx)
            lensPlaced = true
        } else {
            lensX.animateTo(target, if (tapMode()) NavTapSettle else NavSettle)
        }
    }
    val dragAmount by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 300f),
        label = "lensDrag",
    )

    val latestItems by rememberUpdatedState(navigationItems)
    val latestSelected by rememberUpdatedState(selectedIndex)
    val latestOnClick by rememberUpdatedState(onItemClick)
    val latestSearchLong by rememberUpdatedState(onSearchLongClick)
    val latestHomeHold by rememberUpdatedState(onHomeLongHold)
    val latestCollapse by rememberUpdatedState(collapseFraction)
    val latestHomeIndex by rememberUpdatedState(homeIndex)

    // Indikator lensa kaca asli (seperti LiquidBottomTabs Kyant): lensa membiaskan baris tab lewat backdrop gabungan.
    val kyantBackdrop = LocalKyantBackdrop.current
    val lensCapable = kyantBackdrop != null && android.os.Build.VERSION.SDK_INT >= 33 && LocalGlassQuality.current != GlassQuality.LOW
    val tabsBackdrop = rememberLayerBackdrop()
    val lensBackdrop = if (kyantBackdrop != null) rememberCombinedBackdrop(kyantBackdrop, tabsBackdrop) else null
    val twoDpPx = with(density) { 2.dp.toPx() }
    val anchorReachPx = with(density) { NavBottomPadding.toPx() }
    val silhouetteTint = Color.Transparent
    val navWiden by animateFloatAsState(
        targetValue = if (dragging && latestCollapse() < 0.5f) 1f else 0f,
        animationSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
        label = "navWiden",
    )

    val lensActive: () -> Float = {
        val t = (liquid.drive * 3f).coerceIn(0f, 1f)
        t * t * (3f - 2f * t) * (1f - latestCollapse().coerceIn(0f, 1f))
    }
    val navShrinkMaxPx = with(density) { NavLensShrink.toPx() }
    val navShrinkPx: () -> Float = { navShrinkMaxPx * lensActive() }
    val iconShrinkScale: () -> Float = { 1f - (NavLensShrink / NavFullHeight) * lensActive() }

    val optics = remember(lensCapable) { if (lensCapable && android.os.Build.VERSION.SDK_INT >= 33) LensOptics() else null }
    LaunchedEffect(liquid) {
        liquid.simulate(
            driven = { dragging || (lensX.isRunning && !tapMode()) },
            position = { lensX.value },
            velocity = { if (tapMode()) 0f else lensX.velocity },
            itemWidth = { innerWidthPx / latestItems.size.coerceAtLeast(1) },
            pressed = { dragging && latestCollapse() < 0.5f },
        )
    }

    val navGlassTuning = com.spectra.app.ui.glass.rememberGlassTuning(com.spectra.app.ui.glass.GlassChrome.NAV)
    CompositionLocalProvider(LocalGlassMaterial provides GlassMaterial.CLEAR) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(bottom = NavBottomPadding)
                    .fillMaxWidth()
                    .navSideMargin(fraction = { 1f }, from = MiniPlayerSideMargin)
                    .graphicsLayer {
                        translationY = -NavCollapsedRaise.toPx() * latestCollapse()
                    },
        ) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset((-dockShiftPx * latestCollapse()).roundToInt(), 0) }
                    .graphicsLayer {
                        val j = barJelly.value * (1f - latestCollapse().coerceIn(0f, 1f))
                        val sc = 1f + 0.05f * j
                        scaleX = sc
                        scaleY = sc
                    }
                    .navHeightLock { navShrinkPx() }
                    .tintedGlass3D(shape, lightSurface, fillScale = 0.35f, blurEnabled = false, tuning = navGlassTuning, energy = { maxOf(navWiden, 0.5f * latestCollapse()) })
                    .graphicsLayer {
                        clip = latestCollapse() > 0.001f
                        this.shape = shape
                    }
                    .then(touchHighlight.observe)
                    .then(touchHighlight.draw)
                    .collapsePill(
                        fraction = collapseFraction,
                        collapsedWidth = NavCollapsedCircleSize,
                        collapsedHeight = NavCollapsedCircleSize,
                        shrinkPx = navShrinkPx,
                    ).padding(5.dp)
                    .onSizeChanged { innerWidthPx = it.width.toFloat() }
                    .pointerInput(count) {
                        coroutineScope {
                            val scope = this
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val w = size.width.toFloat().coerceAtLeast(1f)

                                fun indexAt(x: Float): Int = ((x / w) * count).toInt().coerceIn(0, count - 1)

                                val isCollapsed = latestCollapse() > 0.5f && latestHomeIndex >= 0
                                val downIndex = if (isCollapsed) latestHomeIndex else indexAt(down.position.x)
                                val downItem = latestItems.getOrNull(downIndex)
                                val longAction: (() -> Unit)? =
                                    when {
                                        downItem == Screens.Search -> latestSearchLong
                                        downItem == Screens.Home -> latestHomeHold
                                        else -> null
                                    }
                                val longDelay =
                                    if (downItem == Screens.Home) 15_000L else viewConfiguration.longPressTimeoutMillis

                                var longFired = false
                                var dragged = false
                                var cancelled = false
                                var lastIndex = downIndex
                                var dragTarget = lensX.value
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                hoverIndex = downIndex
                                scope.launch { barJelly.animateTo(1f, tween(70, easing = FastOutSlowInEasing)) }
                                if (!isCollapsed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                val longJob =
                                    longAction?.let { action ->
                                        scope.launch {
                                            delay(longDelay)
                                            longFired = true
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            action()
                                        }
                                    }

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        cancelled = true
                                        break
                                    }
                                    if (change.changedToUpIgnoreConsumed()) {
                                        change.consume()
                                        break
                                    }
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!isCollapsed && !dragged && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                                        dragged = true
                                        dragging = true
                                        longJob?.cancel()
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) { lensX.stop() }
                                        dragTarget = lensX.value
                                    }
                                    if (dragged) {
                                        // Indikator mengikuti jari 1:1 lewat selisih gerak, bukan melompat ke posisi absolut.
                                        val wNow = size.width.toFloat().coerceAtLeast(1f)
                                        val itemW = wNow / count
                                        val maxX = (wNow - itemW).coerceAtLeast(0f)
                                        val dx = change.positionChange().x
                                        dragTarget = (dragTarget + dx).coerceIn(0f, maxX)
                                        val followTo = dragTarget
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            lensX.animateTo(followTo, LensFollow)
                                        }
                                        val idx = (((dragTarget + itemW / 2f) / itemW).toInt()).coerceIn(0, count - 1)
                                        if (idx != lastIndex) {
                                            lastIndex = idx
                                            hoverIndex = idx
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        change.consume()
                                    }
                                }

                                longJob?.cancel()
                                dragging = false
                                scope.launch { barJelly.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 700f)) }
                                // Saat dilepas, tujuan dihitung dari posisi indikator + proyeksi kecepatan 0,18 detik (fling).
                                val finalIndex =
                                    if (dragged) {
                                        val itemW = size.width.toFloat().coerceAtLeast(1f) / count
                                        val vx = tracker.calculateVelocity().x
                                        (((dragTarget / itemW) + vx / itemW * 0.18f).roundToInt()).coerceIn(0, count - 1)
                                    } else {
                                        downIndex
                                    }
                                if (dragged) {
                                    val settleX = finalIndex * (size.width.toFloat().coerceAtLeast(1f) / count)
                                    scope.launch {
                                        lensX.animateTo(
                                            targetValue = settleX,
                                            animationSpec = NavSettle,
                                            initialVelocity = 0f,
                                        )
                                    }
                                }
                                if (!cancelled && !longFired) {
                                    fastTap = !dragged
                                    pendingIndex = finalIndex
                                    latestItems.getOrNull(finalIndex)?.let { screen ->
                                        latestOnClick(screen, finalIndex == latestSelected)
                                    }
                                }
                                hoverIndex = -1
                            }
                        }
                    },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (lensCapable) Modifier.layerBackdrop(tabsBackdrop) else Modifier)
                        .graphicsLayer { alpha = (1f - 2f * collapseFraction()).coerceIn(0f, 1f) }
            ) {
                navigationItems.forEachIndexed { index, screen ->
                    val active = index == shownIndex
                    val iconRes = if (active) screen.iconIdActive else screen.iconIdInactive
                    val fg by animateColorAsState(
                        targetValue = if (active) activeTint else idleColor,
                        label = "glassItemFg",
                    )
                    val title = stringResource(screen.titleId)
                    val itemScale = if (active) 1.08f else 1f
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .graphicsLayer { val sc = itemScale * iconShrinkScale(); scaleX = sc; scaleY = sc; translationY = NavLensIconDrop.toPx() * lensActive() }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    selected = index == selectedIndex
                                    onClick(label = title) {
                                        latestOnClick(screen, index == latestSelected)
                                        true
                                    }
                                }.then(if (slimNav) Modifier else Modifier.height(NavFullHeight - 10.dp))
                                .padding(vertical = if (slimNav) 8.dp else 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (slimNav) Arrangement.Center else Arrangement.SpaceBetween,
                    ) {
                        NavItemIcon(
                            screen = screen,
                            iconRes = iconRes,
                            title = title,
                            tint = fg,
                            size = 24.dp,
                            accountImageUrl = accountImageUrl,
                        )
                        if (!slimNav) {
                            Text(
                                text = title,
                                color = fg,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                }
            }
            if (homeIndex >= 0) {
                val homeActive = selectedIndex == homeIndex
                val homeTint by animateColorAsState(
                    targetValue = if (homeActive) activeTint else idleColor,
                    label = "collapsedHomeTint",
                )
                Box(
                    modifier =
                        Modifier
                            .width(NavCollapsedCircleSize - 12.dp)
                            .fillMaxHeight()
                            .graphicsLayer { alpha = (2f * collapseFraction() - 1f).coerceIn(0f, 1f) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (homeActive) Screens.Home.iconIdActive else Screens.Home.iconIdInactive),
                        contentDescription = stringResource(Screens.Home.titleId),
                        tint = homeTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        if (shownIndex >= 0 && itemWidthPx > 0f) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                        .graphicsLayer { alpha = (1f - 2f * collapseFraction()).coerceIn(0f, 1f) },
            ) {

                if (itemWidthPx > 0f) {
                    Box(
                        modifier =
                            Modifier
                                .width(with(density) { itemWidthPx.toDp() + 6.dp })
                                .fillMaxHeight()
                                .graphicsLayer {
                                    if (lensCapable && lensBackdrop != null) alpha = 1f
                                    translationX = liquid.leftOr(lensX.value) - 3.dp.toPx()
                                    if (liquid.ready) {
                                        val base = itemWidthPx + 6.dp.toPx()
                                        scaleX = (liquid.rightPx - liquid.leftPx + 6.dp.toPx()) / base.coerceAtLeast(1f)
                                        scaleY = liquid.heightScale
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    }
                                },
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .padding(horizontal = 2.dp)
                                    .graphicsLayer { alpha = if (lensCapable && lensBackdrop != null) 1f - (liquid.drive * 3f).coerceIn(0f, 1f) else 1f - dragAmount }
                                    .restPill(glassAccent),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .padding(horizontal = 2.dp)
                                    .graphicsLayer {
                                        alpha = if (lensCapable && lensBackdrop != null) 0f else dragAmount
                                    }.dragLens(lightSurface),
                        )
                    }
                }
            }
        }

        if (shownIndex >= 0 && itemWidthPx > 0f && lensCapable && lensBackdrop != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .offset { IntOffset((-dockShiftPx * latestCollapse()).roundToInt(), 0) }
                        .padding(horizontal = 5.dp)
                        .graphicsLayer { alpha = (1f - 2f * collapseFraction()).coerceIn(0f, 1f) },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (lensCapable && lensBackdrop != null) {
                    Box(
                        modifier =
                            Modifier
                                .layout { measurable, constraints ->
                                    val hMax = constraints.maxHeight
                                    val baseW = itemWidthPx + 2.dp.toPx()
                                    val w = if (liquid.ready) liquid.rightPx - liquid.leftPx + 2.dp.toPx() else baseW
                                    val sy = if (liquid.ready) liquid.heightScale else 1f
                                    val h = ((hMax - 6.dp.toPx()) * sy).roundToInt().coerceAtLeast(1)
                                    val m = 28.dp.roundToPx()
                                    val p = measurable.measure(Constraints.fixed(w.roundToInt().coerceAtLeast(1) + 2 * m, h + 2 * m))
                                    layout(p.width - 2 * m, hMax) { p.place(-m, (hMax - h) / 2 - m) }
                                }
                                .graphicsLayer {
                                    translationX = liquid.leftOr(lensX.value) - 1.dp.toPx()
                                    alpha = (liquid.drive * 3f).coerceIn(0f, 1f)
                                }
                                .padding(28.dp)
                                .drawBackdrop(
                                    backdrop = lensBackdrop,
                                    shape = { NavLensShape },
                                    effects = {
                                        val q = 0.85f + 0.1f * liquid.drive + 0.2f * liquid.fastness
                                        lens(
                                            refractionHeight = 16f.dp.toPx() * q,
                                            refractionAmount = 32f.dp.toPx() * q,
                                            chromaticAberration = true,
                                        )
                                    },
                                    highlight = { Highlight.Default.copy(alpha = 1f) },
                                    shadow = { Shadow(radius = (12f + 8f * liquid.drive).dp, alpha = 0.55f + 0.2f * liquid.drive) },
                                    innerShadow = {
                                        val bend = abs((liquid.rightPx - liquid.leftPx) / itemWidthPx.coerceAtLeast(1f) - 1f).coerceAtMost(0.6f)
                                        InnerShadow(radius = (6f + 5f * bend).dp, alpha = 0.7f)
                                    },
                                    onDrawSurface = {
                                        optics?.draw(
                                            scope = this,
                                            bezel = minOf(size.width, size.height) * 0.46f,
                                            lightX = -0.55f - 0.35f * liquid.leanDir,
                                            lightY = -0.75f,
                                            strength = 0.85f + 0.15f * liquid.drive,
                                        )
                                        drawNavSilhouette(
                                            liquid = liquid,
                                            itemWidthPx = itemWidthPx,
                                            navWidthPx = innerWidthPx,
                                            navHeightPx = (NavFullHeight - 5.dp).toPx(),
                                            lensInsetPx = twoDpPx,
                                            edgePadPx = 5.dp.toPx(),
                                            squeeze = 1f,
                                            tint = silhouetteTint,
                                            scaled = false,
                                        )
                                    },
                                ),
                    )
                }
            }
        }
        }
    }
    }
}

/** Siluet lensa referensi: squircle lebar dengan sudut sekitar 38% tinggi, bukan kapsul atau kotak. */
private val NavLensShape = RoundedCornerShape(50)

private val NavSettle = spring<Float>(dampingRatio = 1f, stiffness = 380f)

/** Tap biasa (tanpa geser / tekan lama): lensa pindah cepat, supaya klik beruntun tidak terasa lambat. */
private val NavTapSettle = spring<Float>(dampingRatio = 1f, stiffness = 3600f)

private val LensFollow = spring<Float>(dampingRatio = 1f, stiffness = 900f)


/** Penanda tab aktif: kapsul ungu translusen dengan tepi tipis. */
private fun Modifier.restPill(accent: Color): Modifier =
    this
        .clip(CircleShape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))))
        .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.10f))), CircleShape)

/** Saat digeser: pil yang sama, sedikit lebih terang. */
@Composable
internal fun Modifier.dragLens(light: Boolean): Modifier =
    this
        .clip(NavLensShape)
        .background(Brush.verticalGradient(if (light) listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.08f)) else listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.14f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.70f), Color.White.copy(alpha = 0.10f))), NavLensShape)
