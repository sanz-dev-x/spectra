package com.spectra.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.spectra.app.LocalPlayerAwareWindowInsets
import com.spectra.app.R
import com.spectra.app.constants.EnableHighRefreshRateKey
import com.spectra.app.constants.OpeningIntroKey
import com.spectra.app.constants.PerformanceModeKey
import com.spectra.app.constants.ScrollEdgeEffectKey
import com.spectra.app.ui.component.IconButton
import com.spectra.app.ui.component.Material3SettingsGroup
import com.spectra.app.ui.component.Material3SettingsItem
import com.spectra.app.ui.component.Switch
import com.spectra.app.ui.component.TopAppBar
import com.spectra.app.ui.utils.backToMain
import com.spectra.app.utils.rememberPreference

@Composable
private fun PerformanceSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            Icon(
                painter = painterResource(if (checked) R.drawable.check else R.drawable.close),
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettings(navController: NavController) {
    val (performanceMode, onPerformanceModeChange) = rememberPreference(PerformanceModeKey, defaultValue = true)
    val (highRefreshRate, onHighRefreshRateChange) = rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
    val (scrollEdgeEffect, onScrollEdgeEffectChange) = rememberPreference(ScrollEdgeEffectKey, defaultValue = true)
    val (openingIntro, onOpeningIntroChange) = rememberPreference(OpeningIntroKey, defaultValue = true)

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        Material3SettingsGroup(
            title = stringResource(R.string.performance_section_navigation),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text(stringResource(R.string.performance_instant_navigation)) },
                        description = { Text(stringResource(R.string.performance_instant_navigation_desc)) },
                        trailingContent = { PerformanceSwitch(performanceMode, onPerformanceModeChange) },
                        onClick = { onPerformanceModeChange(!performanceMode) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.refresh),
                        title = { Text(stringResource(R.string.performance_high_refresh_rate)) },
                        description = { Text(stringResource(R.string.performance_high_refresh_rate_desc)) },
                        trailingContent = { PerformanceSwitch(highRefreshRate, onHighRefreshRateChange) },
                        onClick = { onHighRefreshRateChange(!highRefreshRate) },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.performance_section_effects),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.nav_bar),
                        title = { Text(stringResource(R.string.performance_scroll_edge)) },
                        description = { Text(stringResource(R.string.performance_scroll_edge_desc)) },
                        trailingContent = { PerformanceSwitch(scrollEdgeEffect, onScrollEdgeEffectChange) },
                        onClick = { onScrollEdgeEffectChange(!scrollEdgeEffect) },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.play),
                        title = { Text(stringResource(R.string.performance_opening_intro)) },
                        description = { Text(stringResource(R.string.performance_opening_intro_desc)) },
                        trailingContent = { PerformanceSwitch(openingIntro, onOpeningIntroChange) },
                        onClick = { onOpeningIntroChange(!openingIntro) },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.performance)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
