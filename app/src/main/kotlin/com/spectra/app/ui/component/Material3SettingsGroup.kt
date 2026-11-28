/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spectra.app.R
import com.spectra.app.ui.glass.GlassState
import com.spectra.app.ui.glass.glassRegular
import com.spectra.app.ui.theme.GlassMode

/**
 * Grup Pengaturan memakai permukaan, tinggi baris, ukuran ikon, dan tipografi yang sama dengan menu Kamu.
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>,
    useLowContrast: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 22.dp, end = 16.dp, bottom = 8.dp, top = 14.dp),
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuGroupSurface()
                    .padding(horizontal = MenuGroupPadding),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                Material3SettingsItemRow(item = item)
            }
        }
    }
}

/** State kontrol glass dari interaksi: Disabled > Pressed > Selected > Default. */
@Composable
internal fun rememberGlassControlState(
    interaction: InteractionSource,
    selected: Boolean = false,
    enabled: Boolean = true,
): GlassState {
    val pressed by interaction.collectIsPressedAsState()
    return when {
        !enabled -> GlassState.Disabled
        pressed -> GlassState.Pressed
        selected -> GlassState.Selected
        else -> GlassState.Default
    }
}

/** Kontrol penting di Pengaturan: glassRegular (Terang/Gelap), permukaan solid + overlay state di Kertas/OFF. */
@Composable
internal fun Modifier.settingsGlassControl(
    shape: androidx.compose.ui.graphics.Shape,
    state: GlassState = GlassState.Default,
    hd: Boolean = false,
): Modifier {
    if (rememberGlassMode() == GlassMode.BRIGHT) return this.glassRegular(shape, state, hd = hd).clip(shape)
    val ink = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val overlay =
        when (state) {
            GlassState.Pressed -> ink.copy(alpha = 0.08f)
            GlassState.Selected -> primary.copy(alpha = 0.16f)
            else -> Color.Transparent
        }
    return this
        .then(if (state == GlassState.Disabled) Modifier.graphicsLayer { alpha = 0.38f } else Modifier)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .background(overlay)
        .then(if (state == GlassState.Selected) Modifier.border(1.dp, primary.copy(alpha = 0.5f), shape) else Modifier)
}

@Composable
private fun Material3SettingsItemRow(item: Material3SettingsItem) {
    val contentAlpha = if (item.enabled) 1f else 0.38f
    val touch = remember { MutableInteractionSource() }
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .songTouchAnimation(touch)
                .clickable(
                    interactionSource = touch,
                    indication = null,
                    enabled = item.enabled && item.onClick != null,
                    onClick = { item.onClick?.invoke() },
                ).heightIn(min = MenuRowMinHeight)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(modifier = Modifier.width(MenuIconGap))
        } else if (item.icon != null) {
            if (item.showBadge) {
                BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.error) }) {
                    Icon(painter = item.icon, contentDescription = null, tint = ink, modifier = Modifier.size(MenuIconSize))
                }
            } else {
                Icon(painter = item.icon, contentDescription = null, tint = ink, modifier = Modifier.size(MenuIconSize))
            }
            Spacer(modifier = Modifier.width(MenuIconGap))
        }

        Column(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = ink),
            ) { item.title() }
            item.description?.let { desc ->
                ProvideTextStyle(
                    MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)),
                ) { desc() }
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
        if (item.trailingContent == null && item.onClick != null) {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
    }
}

/**
 * Data class for Material 3 settings item
 */
data class Material3SettingsItem(
    val icon: Painter? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null
)
