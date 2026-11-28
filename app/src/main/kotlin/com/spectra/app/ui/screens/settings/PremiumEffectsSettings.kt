package com.spectra.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import com.spectra.app.R
import com.spectra.app.constants.FxEdgeGlowKey
import com.spectra.app.constants.FxGrainKey
import com.spectra.app.constants.FxSpotlightKey
import com.spectra.app.constants.FxVignetteKey
import com.spectra.app.utils.rememberPreference

@Composable
fun PremiumEffectsSettings(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.fx_premium_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        FxSlider(R.string.fx_grain, FxGrainKey)
        FxSlider(R.string.fx_vignette, FxVignetteKey)
        FxSlider(R.string.fx_edge_glow, FxEdgeGlowKey)
        FxSlider(R.string.fx_spotlight, FxSpotlightKey)
    }
}

@Composable
private fun FxSlider(
    label: Int,
    key: Preferences.Key<Float>,
) {
    val pref = rememberPreference(key, defaultValue = 0f)
    var local by remember { mutableFloatStateOf(pref.value) }
    var dragging by remember { mutableStateOf(false) }
    val shown = if (dragging) local else pref.value
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = if (shown <= 0f) stringResource(R.string.fx_off) else "${(shown * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(
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
