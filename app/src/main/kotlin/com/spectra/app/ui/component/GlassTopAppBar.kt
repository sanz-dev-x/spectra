package com.spectra.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spectra.app.ui.theme.GlassMode

/**
 * Warna top bar. Pada Liquid Glass container dan scrolled container sama (transparan),
 * sehingga bar tidak berganti warna saat daftar di-scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopAppBarColors(): TopAppBarColors {
    val glass = rememberGlassMode() != GlassMode.OFF
    return if (glass) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }
}

/**
 * Pengganti material3.TopAppBar untuk seluruh layar. Di mode Liquid Glass bar memakai scrim gradien
 * yang tetap (tidak bergantung posisi scroll) supaya konten yang lewat di bawahnya tidak menabrak ikon/judul.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = glassTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val glass = rememberGlassMode() != GlassMode.OFF
    val scrim = MaterialTheme.colorScheme.surface
    androidx.compose.material3.TopAppBar(
        title = title,
        modifier =
            if (glass) {
                modifier.background(Brush.verticalGradient(listOf(scrim.copy(alpha = 0.78f), scrim.copy(alpha = 0f))))
            } else {
                modifier
            },
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = expandedHeight,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

