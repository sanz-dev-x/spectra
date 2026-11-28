package com.spectra.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.spectra.app.ui.component.rememberGlassControlState
import com.spectra.app.ui.component.settingsGlassControl
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spectra.app.R
import com.spectra.app.constants.GlassModeKey
import com.spectra.app.constants.MiniGlassBlurKey
import com.spectra.app.constants.MiniGlassTransparencyKey
import com.spectra.app.constants.NavGlassBlurKey
import com.spectra.app.constants.NavGlassTransparencyKey
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.utils.rememberPreference

/** Pengaturan kaca Navbar dan Mini Player; masing-masing punya key preferensi sendiri sehingga tidak saling memengaruhi. */
@Composable
fun GlassTuningSettings(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        GlassTuningCard(
            title = R.string.spectra_glass_navbar,
            blurKey = NavGlassBlurKey,
            transparencyKey = NavGlassTransparencyKey,
        )
        GlassTuningCard(
            title = R.string.spectra_glass_mini_player,
            blurKey = MiniGlassBlurKey,
            transparencyKey = MiniGlassTransparencyKey,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun GlassTuningCard(
    title: Int,
    blurKey: androidx.datastore.preferences.core.Preferences.Key<Float>,
    transparencyKey: androidx.datastore.preferences.core.Preferences.Key<Float>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        GlassSliderRow(R.string.spectra_glass_blur, blurKey, 0f)
        GlassSliderRow(R.string.spectra_glass_transparency, transparencyKey, 0f)
    }
}

@Composable
private fun GlassSliderRow(
    label: Int,
    key: androidx.datastore.preferences.core.Preferences.Key<Float>,
    default: Float,
) {
    val pref = rememberPreference(key, defaultValue = default)
    var local by remember { androidx.compose.runtime.mutableFloatStateOf(pref.value) }
    var dragging by remember { mutableStateOf(false) }
    val shown = if (dragging) local else pref.value
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(shown * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    androidx.compose.material3.Slider(
        value = shown,
        onValueChange = {
            dragging = true
            local = it
            pref.value = it
        },
        onValueChangeFinished = { dragging = false },
        valueRange = 0f..1f,
        modifier = Modifier.fillMaxWidth(),
    )
}
