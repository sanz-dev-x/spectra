package com.spectra.app.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spectra.app.R
import com.spectra.app.constants.IosGlassIntensityKey
import com.spectra.app.constants.PlayerTheme
import com.spectra.app.constants.PlayerThemeKey
import com.spectra.app.ui.component.glassButton3D
import com.spectra.app.utils.rememberEnumPreference
import com.spectra.app.utils.rememberPreference

const val IOS_GLASS_DEFAULT = 60

fun Modifier.iosGlass(
    shape: Shape,
    intensity: Int,
): Modifier {
    val strength = intensity.coerceIn(0, 100) / 100f
    val fillTop = 0.12f + 0.22f * strength
    val fillBottom = 0.06f + 0.12f * strength
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = fillTop), Color.White.copy(alpha = fillBottom))),
            shape,
        ).border(
            0.75.dp,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.45f + 0.2f * strength), Color.White.copy(alpha = 0.08f)),
            ),
            shape,
        )
}

@Composable
fun Modifier.iosOrGlass3D(
    ios: Boolean,
    intensity: Int,
    shape: Shape,
    tint: Color,
): Modifier = if (ios) iosGlass(CircleShape, intensity) else glassButton3D(shape, tint)

@Composable
fun IosPlayerControls(
    isPlaying: Boolean,
    playIcon: Int?,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = Color.White,
    glass: Boolean = false,
) {
    val playRes = playIcon ?: if (isPlaying) R.drawable.ios_pause else R.drawable.ios_play
    val prevDesc = stringResource(R.string.previous)
    val nextDesc = stringResource(R.string.next)
    val playDesc = stringResource(if (isPlaying) R.string.pause else R.string.play)
    if (glass) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth().playerGlass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IosGlyphButton(R.drawable.skip_previous, 36.dp, canSkipPrevious, onPrevious, ink, Modifier.size(72.dp, 64.dp), prevDesc)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(GlassPlayButtonSize).playerGlass(CircleShape, strength = 1.6f),
            ) {
                IosGlyphButton(playRes, 36.dp, true, onPlayPause, ink, Modifier.size(GlassPlayButtonSize), playDesc)
            }
            IosGlyphButton(R.drawable.ios_forward, 36.dp, canSkipNext, onNext, ink, Modifier.size(72.dp, 64.dp), nextDesc)
        }
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        IosGlyphButton(R.drawable.skip_previous, 40.dp, canSkipPrevious, onPrevious, ink, contentDescription = prevDesc)
        IosGlyphButton(playRes, 56.dp, true, onPlayPause, ink, contentDescription = playDesc)
        IosGlyphButton(R.drawable.ios_forward, 40.dp, canSkipNext, onNext, ink, contentDescription = nextDesc)
    }
}

/** Baris volume di bawah kontrol, sama seperti kartu poster: speaker kecil, garis tipis, speaker besar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosVolumeRow(
    modifier: Modifier = Modifier,
    ink: Color = Color.White,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val max = remember { audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableFloatStateOf(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }
    DisposableEffect(audio) {
        val receiver =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                    level = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                }
            }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val state = remember { SliderState(value = level, trackRange = 0f..max.toFloat()) }
    state.value = level
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val trackHeight by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "iosVolumeTrack")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.volume_down), null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        Slider(
            state = state,
            onValueChange = {
                level = it
                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            interactionSource = interaction,
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = { st ->
                val fraction = (st.value / max).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(trackHeight).clip(CircleShape).background(ink.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(ink.copy(alpha = 0.8f)))
                }
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Icon(painterResource(R.drawable.volume_up), null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        Icon(
            painterResource(R.drawable.bluetooth),
            null,
            tint = ink.copy(alpha = 0.8f),
            modifier =
                Modifier
                    .padding(start = 14.dp)
                    .size(20.dp)
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
        )
    }
}

@Composable
private fun IosGlyphButton(
    icon: Int,
    glyphSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    ink: Color = Color.White,
    boxModifier: Modifier = Modifier.size(84.dp, 72.dp),
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iosGlyphScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            boxModifier
                .scale(scale)
                .alpha(if (enabled) 1f else 0.35f)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = ink,
            modifier = Modifier.size(glyphSize),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosPlayerSlider(
    position: Long,
    duration: Long,
    enabled: Boolean,
    onPreview: (Long) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = Color.White,
    restTrackHeight: Dp = 4.dp,
    dragTrackHeight: Dp = 8.dp,
) {
    val range = 0f..(if (duration <= 0L) 0f else duration.toFloat())
    val state = remember(range) { SliderState(value = position.toFloat(), trackRange = range) }
    state.value = position.toFloat().coerceIn(range)
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val trackHeight by animateDpAsState(if (dragging) dragTrackHeight else restTrackHeight, label = "iosTrackHeight")

    Slider(
        state = state,
        onValueChange = { onPreview(it.toLong()) },
        onValueChangeFinished = onFinished,
        enabled = enabled,
        interactionSource = interaction,
        thumb = { Spacer(Modifier.size(0.dp)) },
        track = { sliderState ->
            val span = sliderState.trackRange.endInclusive - sliderState.trackRange.start
            val fraction = if (span <= 0f) 0f else (sliderState.value - sliderState.trackRange.start) / span
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.22f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(ink.copy(alpha = 0.9f)),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun PlayerThemeSheet(onDismiss: () -> Unit) {
    var theme by rememberEnumPreference(PlayerThemeKey, PlayerTheme.DEFAULT)
    var intensity by rememberPreference(IosGlassIntensityKey, defaultValue = IOS_GLASS_DEFAULT)

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.player_theme),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeCard(
                title = stringResource(R.string.player_theme_default),
                selected = theme == PlayerTheme.DEFAULT,
                ios = false,
                onClick = { theme = PlayerTheme.DEFAULT },
                modifier = Modifier.weight(1f),
            )
            ThemeCard(
                title = stringResource(R.string.player_theme_ios),
                selected = theme == PlayerTheme.IOS,
                ios = true,
                onClick = { theme = PlayerTheme.IOS },
                modifier = Modifier.weight(1f),
            )
        }
        if (theme == PlayerTheme.IOS) {
            Text(
                text = stringResource(R.string.player_theme_ios_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.player_theme_glass_intensity) + " · " + intensity,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Slider(
                value = intensity.toFloat(),
                onValueChange = { intensity = it.toInt() },
                valueRange = 0f..100f,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ThemeCard(
    title: String,
    selected: Boolean,
    ios: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ).clickable(onClick = onClick)
                .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF14110F)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(if (ios) 4.dp else 8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (ios) 0.5f else 0.25f)),
                )
                if (ios) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.skip_previous), null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Icon(painterResource(R.drawable.ios_pause), null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Icon(painterResource(R.drawable.ios_forward), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp, 26.dp).clip(CircleShape).background(Color(0xFF3A3532)))
                        Box(Modifier.size(52.dp, 26.dp).clip(CircleShape).background(Color(0xFFEDEDED)))
                        Box(Modifier.size(22.dp, 26.dp).clip(CircleShape).background(Color(0xFF3A3532)))
                    }
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
