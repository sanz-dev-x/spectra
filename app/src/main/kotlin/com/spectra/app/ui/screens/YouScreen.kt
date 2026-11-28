package com.spectra.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.spectra.app.LocalPlayerAwareWindowInsets
import com.spectra.app.R
import com.spectra.app.ui.component.MenuGroupGap
import com.spectra.app.ui.component.MenuGroupPadding
import com.spectra.app.ui.component.MenuIconGap
import com.spectra.app.ui.component.MenuIconSize
import com.spectra.app.ui.component.MenuRowMinHeight
import com.spectra.app.ui.component.menuGroupSurface
import com.spectra.app.ui.component.songTouchAnimation

private class MenuItem(val icon: Int, val title: String, val trailing: (@Composable () -> Unit)? = null, val onClick: () -> Unit)

@Composable
fun YouScreen(navController: NavController) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.you_tab),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp),
        )

        MenuGroup(
            listOf(
                MenuItem(R.drawable.history, stringResource(R.string.history)) { navController.navigate("history") },
                MenuItem(R.drawable.stats, stringResource(R.string.you_stats_recap)) { navController.navigate("stats") },
                MenuItem(R.drawable.ios_people, stringResource(R.string.listen_together)) { navController.navigate("listen_together_from_topbar") },
                MenuItem(R.drawable.mic, stringResource(R.string.you_recognize)) { navController.navigate("recognition") },
                MenuItem(R.drawable.ios_sliders, stringResource(R.string.equalizer)) { navController.navigate("equalizer") },
            ),
        )

        Spacer(Modifier.height(MenuGroupGap))
        MenuGroup(
            listOf(
                MenuItem(R.drawable.settings, stringResource(R.string.settings)) { navController.navigate("settings") },
                MenuItem(R.drawable.info, stringResource(R.string.about)) { navController.navigate("settings/about") },
            ),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuGroup(items: List<MenuItem>) {
    Column(
        Modifier
            .fillMaxWidth()
            .menuGroupSurface()
            .padding(horizontal = MenuGroupPadding),
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
            MenuRow(item)
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem) {
    val touch = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .songTouchAnimation(touch)
            .clickable(interactionSource = touch, indication = null, onClick = item.onClick)
            .heightIn(min = MenuRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(item.icon), null, modifier = Modifier.size(MenuIconSize))
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = MenuIconGap, vertical = 10.dp),
        )
        if (item.trailing != null) {
            item.trailing.invoke()
        } else {
            Icon(painterResource(R.drawable.navigate_next), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
