package com.spectra.app.ui.menu

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.app.LocalDatabase
import com.spectra.app.LocalNavController
import com.spectra.app.LocalPlayerConnection
import com.spectra.app.LocalSyncUtils
import com.spectra.app.R
import com.spectra.app.db.entities.Song
import com.spectra.app.extensions.toMediaItem
import com.spectra.app.ui.component.ContinueCard

private class ContextAction(
    val label: String,
    val icon: Int,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

@Composable
fun SongContextMenu(
    song: Song,
    onDismiss: () -> Unit,
    onMore: () -> Unit,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val syncUtils = LocalSyncUtils.current
    val context = LocalContext.current
    val live by database.song(song.id).collectAsStateWithLifecycle(initialValue = song)
    val current = live ?: song
    val liked = current.song.liked
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "contextAppear",
    )

    AddToPlaylistDialog(
        isVisible = showPlaylistDialog,
        onGetSong = { listOf(song.id) },
        onGetSongIds = { listOf(song.id) },
        onDismiss = {
            showPlaylistDialog = false
            onDismiss()
        },
    )

    val actions =
        buildList {
            add(
                ContextAction(stringResource(R.string.play_next), R.drawable.playlist_play) {
                    playerConnection?.playNext(current.toMediaItem())
                    onDismiss()
                },
            )
            add(
                ContextAction(stringResource(R.string.add_to_queue), R.drawable.queue_music) {
                    playerConnection?.addToQueue(current.toMediaItem())
                    onDismiss()
                },
            )
            add(
                ContextAction(stringResource(R.string.add_to_playlist), R.drawable.playlist_add) {
                    showPlaylistDialog = true
                },
            )
            add(
                ContextAction(
                    label = stringResource(R.string.action_like),
                    icon = if (liked) R.drawable.ios_heart_filled else R.drawable.ios_heart_outlined,
                    tint = if (liked) MaterialTheme.colorScheme.error else null,
                ) {
                    val updated = current.song.toggleLike()
                    database.query { update(updated) }
                    syncUtils.likeSong(updated)
                },
            )
            add(
                ContextAction(stringResource(R.string.share), R.drawable.share) {
                    val intent =
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                        }
                    context.startActivity(Intent.createChooser(intent, null))
                    onDismiss()
                },
            )
            current.orderedArtists.firstOrNull()?.let { artist ->
                add(
                    ContextAction(stringResource(R.string.view_artist), R.drawable.artist) {
                        navController.navigate("artist/${artist.id}")
                        onDismiss()
                    },
                )
            }
            add(
                ContextAction(stringResource(R.string.misc) + "...", R.drawable.more_horiz) {
                    onMore()
                },
            )
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f * appear.coerceIn(0f, 1f)))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 28.dp)
                        .graphicsLayer {
                            val s = 0.86f + 0.14f * appear
                            scaleX = s
                            scaleY = s
                            alpha = appear.coerceIn(0f, 1f)
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                ) {
                    actions.forEachIndexed { index, action ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = action.onClick)
                                    .heightIn(min = 54.dp)
                                    .padding(horizontal = 18.dp),
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                painter = painterResource(action.icon),
                                contentDescription = null,
                                tint = action.tint ?: MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                ContinueCard(
                    title = song.title,
                    subtitle = current.orderedArtists.joinToString { it.name },
                    thumbnailUrl = song.thumbnailUrl,
                    isPlaying = false,
                    isActive = false,
                    onClick = onDismiss,
                    onLongClick = {},
                    showPlay = false,
                    modifier = Modifier.width(184.dp).height(228.dp),
                )
            }
        }
    }
}
