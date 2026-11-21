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
import androidx.compose.runtime.remember
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
import com.spectra.app.constants.GlassToneKey
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.utils.rememberPreference

/** Gaya Tampilan: hanya Kertas (solid, datar) dan Liquid Glass (kaca transparan berlapis). */
@Composable
fun GlassModePicker(modifier: Modifier = Modifier) {
    val (modeId, setModeId) = rememberPreference(GlassModeKey, defaultValue = "dark")
    val liquid = GlassMode.byId(modeId) != GlassMode.OFF
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.spectra_display_style),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StyleCard(R.string.spectra_glassmode_paper, selected = !liquid, glass = false, modifier = Modifier.weight(1f)) {
                setModeId(GlassMode.OFF.id)
            }
            StyleCard(R.string.spectra_glassmode_liquid, selected = liquid, glass = true, modifier = Modifier.weight(1f)) {
                setModeId(GlassMode.DARK.id)
            }
        }
    }
}

/** Nada Liquid Glass; ditampilkan di Pengaturan > Tampilan > Tema. */
@Composable
fun GlassToneSelector(modifier: Modifier = Modifier) {
    val (modeId) = rememberPreference(GlassModeKey, defaultValue = "dark")
    if (GlassMode.byId(modeId) == GlassMode.OFF) return
    val (toneId, setToneId) = rememberPreference(GlassToneKey, defaultValue = "bright")
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.spectra_glass_tone),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToneChip(R.string.spectra_glass_tone_system, toneId == "system", Modifier.weight(1f)) { setToneId("system") }
            ToneChip(R.string.spectra_glass_tone_dark, toneId == "dark", Modifier.weight(1f)) { setToneId("dark") }
            ToneChip(R.string.spectra_glass_tone_bright, toneId == "bright", Modifier.weight(1f)) { setToneId("bright") }
        }
    }
}

@Composable
private fun ToneChip(
    label: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier =
            modifier
                .settingsGlassControl(shape, rememberGlassControlState(interaction, selected))
                .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 12.dp),
    )
}

@Composable
private fun StyleCard(
    label: Int,
    selected: Boolean,
    glass: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .settingsGlassControl(shape, rememberGlassControlState(interaction, selected))
                .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .background(
                        if (glass) {
                            Brush.linearGradient(listOf(Color(0xFFD9E4C8), Color(0xFFE9D9E4), Color(0xFFCFE0EC)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFFF6F3EC), Color(0xFFF6F3EC)))
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val inner = RoundedCornerShape(12.dp)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(34.dp)
                        .clip(inner)
                        .background(if (glass) Color.White.copy(alpha = 0.45f) else Color.White)
                        .border(
                            0.8.dp,
                            if (glass) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.15f))) else Brush.verticalGradient(listOf(Color(0x1F000000), Color(0x1F000000))),
                            inner,
                        ),
            )
        }
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
