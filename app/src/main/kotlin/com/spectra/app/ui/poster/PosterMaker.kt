/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.poster

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spectra.app.R
import com.spectra.app.ui.component.glassChip
import com.spectra.app.ui.component.glassPanel
import com.spectra.app.ui.component.glassPopIn
import com.spectra.app.ui.component.screenBackground
import com.spectra.app.utils.ComposeToImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PosterTab(
    @StringRes val label: Int,
) {
    SONGS(R.string.poster_tab_songs),
    PHOTO(R.string.poster_tab_photo),
    CARD(R.string.poster_tab_card),
    LIGHT(R.string.poster_tab_light),
    EFFECTS(R.string.poster_tab_effects),
    WATERMARK(R.string.poster_tab_watermark),
    EXPORT(R.string.poster_tab_export),
}

private val PALETTE =
    listOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFD6A0.toInt(), 0xFFFF8A65.toInt(),
        0xFFE91E63.toInt(), 0xFF7E57C2.toInt(), 0xFF42A5F5.toInt(), 0xFF26A69A.toInt(),
        0xFF11161C.toInt(), 0xFF1E2A44.toInt(),
    )

private val WM_ARROWS = listOf("↖", "↑", "↗", "←", "•", "→", "↙", "↓", "↘")

/**
 * Editor poster layar penuh. Dibuka dari menu titik tiga Playlist, Artist dan Liked.
 * Preview dan ekspor menggambar dari [PosterDesign] yang sama lewat [PosterRenderer].
 */
@Composable
fun PosterMakerDialog(
    tracks: List<PosterTrack>,
    onDismiss: () -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val tracks = remember(tracks) { tracks.distinctBy { it.id } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val trackById = remember(tracks) { tracks.associateBy { it.id } }

    val state =
        remember(tracks) {
            PosterEditorState(
                PosterDesign(cards = layoutCards(tracks.take(POSTER_MAX_TRACKS).map { it.id }, PosterRatio.R9_16)),
            )
        }
    val covers = remember { mutableStateMapOf<String, Bitmap?>() }
    var fallback by remember { mutableStateOf<Bitmap?>(null) }
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    var logoChecked by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<PosterPhoto?>(null) }
    var status by remember { mutableStateOf(PersonStatus.NO_PHOTO) }
    var selected by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(PosterTab.SONGS) }
    var busy by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var segJob by remember { mutableStateOf<Job?>(null) }
    val renderer = remember { PosterRenderer() }
    val selColor = MaterialTheme.colorScheme.primary.toArgb()

    val exportScales =
        remember {
            val mem = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass ?: 128
            if (mem >= 256) listOf(1f, 1.5f, 2f) else listOf(1f, 1.5f)
        }
    // Foto sumber didecode cukup besar agar ekspor terbesar tetap 1:1 (tanpa upscale).
    val photoMaxSide = remember { if (exportScales.size > 2) 3840 else 2048 }
    var exportScaleIdx by remember { mutableStateOf(exportScales.lastIndex) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            fallback = loadFallbackCover(context)
            logo = loadSpectraLogo(context)
        }
        logoChecked = true
    }
    LaunchedEffect(state.design.cards.map { it.trackId }) {
        state.design.cards.forEach { c ->
            if (!covers.containsKey(c.trackId)) covers[c.trackId] = loadPosterCover(context, trackById[c.trackId]?.thumbnailUrl)
        }
    }
    DisposableEffect(Unit) { onDispose { segJob?.cancel() } }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            segJob?.cancel()
            segJob =
                scope.launch {
                    status = PersonStatus.DETECTING
                    val bmp = decodePosterPhoto(context, uri, photoMaxSide)
                    if (bmp == null) {
                        status = if (photo == null) PersonStatus.NO_PHOTO else PersonStatus.NOT_FOUND
                        Toast.makeText(context, R.string.poster_failed, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val blurred = withContext(Dispatchers.Default) { blurForPoster(bmp) }
                    photo = PosterPhoto(bmp, blurred, null)
                    state.update { it.copy(photo = PhotoState()) }
                    when (val r = PosterSegmenter.segment(bmp)) {
                        is SegmentationResult.Person -> {
                            val withCutout = PosterPhoto(bmp, blurred, r.cutout, r.headGuard)
                            photo = withCutout
                            status = PersonStatus.FOUND
                            state.update { cur ->
                                cur.copy(cards = autoArrangeCards(cur) { dsg, card -> renderer.subjectOverlap(dsg, withCutout, card) })
                            }
                        }
                        SegmentationResult.NoPerson -> status = PersonStatus.NOT_FOUND
                        SegmentationResult.Unavailable -> status = PersonStatus.UNAVAILABLE
                    }
                }
        }

    fun currentScene(): PosterScene {
        val picked = state.design.cards.mapNotNull { trackById[it.trackId] }
        return PosterScene(picked, picked.map { covers[it.id] }, photo, logo, fallback)
    }

    fun export(share: Boolean) {
        if (busy || state.design.cards.isEmpty()) return
        busy = true
        val scene = currentScene()
        val design = state.design
        val scale = exportScales.getOrElse(exportScaleIdx) { 1f }
        scope.launch {
            runCatching {
                val uri =
                    withContext(Dispatchers.Default) {
                        val bmp =
                            Bitmap.createBitmap(
                                (design.ratio.w * scale).toInt(),
                                (design.ratio.h * scale).toInt(),
                                Bitmap.Config.ARGB_8888,
                            )
                        try {
                            val cv = AndroidCanvas(bmp)
                            cv.scale(scale, scale)
                            PosterRenderer().draw(cv, scene, design)
                            withContext(Dispatchers.IO) {
                                ComposeToImage.saveBitmapAsFile(context, bmp, "spectra_poster_${System.currentTimeMillis()}")
                            }
                        } finally {
                            bmp.recycle()
                        }
                    }
                uri
            }.onSuccess { uri ->
                if (share) {
                    try {
                        shareImage(context, uri)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.poster_share_failed, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, R.string.poster_saved, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, if (share) R.string.poster_share_failed else R.string.poster_save_failed, Toast.LENGTH_SHORT).show()
            }
            busy = false
        }
    }

    fun requestClose() {
        if (state.dirty) confirmClose = true else onDismiss()
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(screenBackground())
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::requestClose) {
                    Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.close))
                }
                Text(
                    stringResource(R.string.poster_make),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                PChip(stringResource(R.string.poster_undo), false, state.canUndo) { state.undo() }
                Spacer(Modifier.width(6.dp))
                PChip(stringResource(R.string.poster_redo), false, state.canRedo) { state.redo() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    IconButton(onClick = { export(false) }, enabled = !busy) {
                        Icon(painterResource(R.drawable.download), contentDescription = stringResource(R.string.save))
                    }
                }
                IconButton(onClick = { export(true) }, enabled = !busy) {
                    Icon(painterResource(R.drawable.share), contentDescription = stringResource(R.string.share))
                }
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ratio = state.design.ratio
                val aspect = ratio.w / ratio.h.toFloat()
                val w = minOf(maxWidth, maxHeight * aspect)
                val pxScale = with(density) { w.toPx() } / ratio.w
                Canvas(
                    Modifier
                        .width(w)
                        .height(w / aspect)
                        .clip(RoundedCornerShape(22.dp))
                        .pointerInput(ratio, pxScale) {
                            detectTapGestures { pos ->
                                val d = state.design
                                val i = renderer.hitTest(d, pos.x / pxScale, pos.y / pxScale)
                                selected = d.cards.getOrNull(i)?.trackId
                            }
                        }.pointerInput(selected, photo != null, pxScale) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                state.beginChange()
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.positionChanged() }) {
                                        applyGesture(state, selected, photo != null, event.calculatePan(), event.calculateZoom(), event.calculateRotation(), pxScale)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                state.endChange()
                            }
                        },
                ) {
                    val scene = currentScene()
                    val d = state.design
                    drawIntoCanvas { c ->
                        val n = c.nativeCanvas
                        n.save()
                        n.scale(pxScale, pxScale)
                        renderer.draw(n, scene, d)
                        val idx = d.cards.indexOfFirst { it.trackId == selected }
                        if (idx >= 0) renderer.drawSelection(n, d, idx, 1f / pxScale, selColor)
                        n.restore()
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .glassPopIn()
                    .glassPanel(RoundedCornerShape(28.dp))
                    .padding(vertical = 12.dp),
            ) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PosterTab.entries.forEach { t -> PChip(stringResource(t.label), tab == t) { tab = t } }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.height(216.dp)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "posterTab",
                    ) { t ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                        ) {
                            when (t) {
                                PosterTab.SONGS -> SongsPanel(tracks, state, covers) { id -> if (selected == id) selected = null }
                                PosterTab.PHOTO ->
                                    PhotoPanel(
                                        state, status, photo != null, photo?.cutout != null,
                                        onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    )
                                PosterTab.CARD -> CardPanel(state, trackById, selected, photo?.cutout != null) { selected = it }
                                PosterTab.LIGHT -> LightPanel(state)
                                PosterTab.EFFECTS -> EffectsPanel(state)
                                PosterTab.WATERMARK -> WatermarkPanel(state, logoMissing = logoChecked && logo == null)
                                PosterTab.EXPORT ->
                                    ExportPanel(state, exportScales, exportScaleIdx, busy, { exportScaleIdx = it }, { export(it) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.poster_discard_title)) },
            text = { Text(stringResource(R.string.poster_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    onDismiss()
                }) { Text(stringResource(R.string.poster_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.poster_keep_editing)) } },
        )
    }
}

private fun applyGesture(
    state: PosterEditorState,
    selected: String?,
    hasPhoto: Boolean,
    pan: Offset,
    zoom: Float,
    rotation: Float,
    pxScale: Float,
) {
    state.update { d ->
        if (selected != null) {
            d.copy(
                cards =
                    d.cards.map { c ->
                        if (c.trackId != selected || c.locked) {
                            c
                        } else {
                            c.copy(
                                cx = (c.cx + pan.x / pxScale / d.ratio.w).coerceIn(-0.1f, 1.1f),
                                cy = (c.cy + pan.y / pxScale / d.ratio.h).coerceIn(-0.1f, 1.1f),
                                widthFrac = (c.widthFrac * zoom).coerceIn(0.15f, 1.2f),
                                rotation = (c.rotation + rotation).coerceIn(-180f, 180f),
                            )
                        }
                    },
            )
        } else if (hasPhoto) {
            d.copy(
                photo =
                    d.photo.copy(
                        offsetX = d.photo.offsetX + pan.x / pxScale,
                        offsetY = d.photo.offsetY + pan.y / pxScale,
                        scale = (d.photo.scale * zoom).coerceIn(0.5f, 6f),
                    ),
            )
        } else {
            d
        }
    }
}

// ---------- panel ----------

@Composable
private fun SongsPanel(
    tracks: List<PosterTrack>,
    state: PosterEditorState,
    covers: Map<String, Bitmap?>,
    onDeselect: (String) -> Unit,
) {
    val context = LocalContext.current
    val ids = state.design.cards.map { it.trackId }
    if (tracks.isEmpty()) {
        Text(stringResource(R.string.poster_no_songs), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.poster_songs_count, ids.size, POSTER_MAX_TRACKS),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        PChip(stringResource(R.string.poster_reset_composition), false) {
            state.update { it.copy(cards = layoutCards(ids, it.ratio)) }
        }
    }
    tracks.forEach { t ->
        val on = t.id in ids
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    when {
                        on && ids.size > POSTER_MIN_TRACKS -> {
                            onDeselect(t.id)
                            state.update { it.copy(cards = layoutCards(ids - t.id, it.ratio, it.cards)) }
                        }
                        !on && ids.size < POSTER_MAX_TRACKS ->
                            state.update { it.copy(cards = layoutCards(ids + t.id, it.ratio, it.cards)) }
                        !on -> Toast.makeText(context, R.string.poster_max_songs, Toast.LENGTH_SHORT).show()
                    }
                }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cover = covers[t.id]
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .glassChip(false, RoundedCornerShape(8.dp)),
            ) {
                if (cover != null) {
                    Image(cover.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    t.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (on) {
                Text("${ids.indexOf(t.id) + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PhotoPanel(
    state: PosterEditorState,
    status: PersonStatus,
    hasPhoto: Boolean,
    hasCutout: Boolean,
    onPick: () -> Unit,
) {
    val d = state.design
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PChip(stringResource(if (hasPhoto) R.string.poster_change_photo else R.string.poster_pick_photo), true, onClick = onPick)
        if (hasPhoto) PChip(stringResource(R.string.poster_reset_photo), false) { state.update { it.copy(photo = PhotoState()) } }
    }
    Text(
        stringResource(
            when (status) {
                PersonStatus.NO_PHOTO -> R.string.poster_no_photo_hint
                PersonStatus.DETECTING -> R.string.poster_detecting
                PersonStatus.FOUND -> R.string.poster_person_found
                PersonStatus.NOT_FOUND -> R.string.poster_person_none
                PersonStatus.UNAVAILABLE -> R.string.poster_person_unavailable
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    if (!hasPhoto) return
    PToggle(R.string.poster_flip_h, d.photo.flipH) { v -> state.update { it.copy(photo = it.photo.copy(flipH = v)) } }
    PToggle(R.string.poster_flip_v, d.photo.flipV) { v -> state.update { it.copy(photo = it.photo.copy(flipV = v)) } }
    PSlider(R.string.poster_zoom, d.photo.scale, 0.5f..6f, state) { v -> state.update { it.copy(photo = it.photo.copy(scale = v)) } }
    PSlider(R.string.poster_rotation, d.photo.rotation, -180f..180f, state) { v -> state.update { it.copy(photo = it.photo.copy(rotation = v)) } }
    PSlider(R.string.poster_bg_blur, d.photo.backgroundBlur, 0f..1f, state, enabled = hasCutout) { v ->
        state.update { it.copy(photo = it.photo.copy(backgroundBlur = v)) }
    }
    if (!hasCutout) Hint(R.string.poster_needs_person)

    GroupHeader(R.string.poster_group_adjust) { state.update { it.copy(adjust = PhotoAdjust()) } }
    val a = d.adjust
    PSlider(R.string.poster_adj_brightness, a.brightness, -1f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(brightness = v)) } }
    PSlider(R.string.poster_adj_contrast, a.contrast, -1f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(contrast = v)) } }
    PSlider(R.string.poster_adj_saturation, a.saturation, -1f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(saturation = v)) } }
    PSlider(R.string.poster_adj_exposure, a.exposure, -1f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(exposure = v)) } }
    PSlider(R.string.poster_adj_warmth, a.warmth, -1f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(warmth = v)) } }
    PSlider(R.string.poster_adj_tint, a.tint, -1f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(tint = v)) } }
    PSlider(R.string.poster_adj_fade, a.fade, 0f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(fade = v)) } }
    PSlider(R.string.poster_adj_vignette, a.vignette, 0f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(vignette = v)) } }
    PSlider(R.string.poster_adj_grain, a.grain, 0f..1f, state) { v -> state.update { it.copy(adjust = it.adjust.copy(grain = v)) } }
}

@Composable
private fun CardPanel(
    state: PosterEditorState,
    trackById: Map<String, PosterTrack>,
    selected: String?,
    hasCutout: Boolean,
    onSelect: (String?) -> Unit,
) {
    val d = state.design
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        d.cards.forEach { c ->
            val title = trackById[c.trackId]?.title.orEmpty()
            PChip(if (title.length > 16) title.take(15) + "…" else title, selected == c.trackId) { onSelect(c.trackId) }
        }
    }
    val card = d.cards.firstOrNull { it.trackId == selected }
    if (card == null) {
        Hint(R.string.poster_card_select_hint)
        return
    }
    fun edit(block: (CardState) -> CardState) = state.update { dd -> dd.copy(cards = dd.cards.map { if (it.trackId == card.trackId) block(it) else it }) }
    Spacer(Modifier.height(6.dp))
    PToggle(R.string.poster_card_lock, card.locked) { v -> edit { it.copy(locked = v) } }
    PSlider(R.string.poster_pos_x, card.cx, 0f..1f, state, enabled = !card.locked) { v -> edit { it.copy(cx = v) } }
    PSlider(R.string.poster_pos_y, card.cy, 0f..1f, state, enabled = !card.locked) { v -> edit { it.copy(cy = v) } }
    PSlider(R.string.poster_card_size, card.widthFrac, 0.15f..1.2f, state, enabled = !card.locked) { v -> edit { it.copy(widthFrac = v) } }
    PSlider(R.string.poster_rotation, card.rotation, -180f..180f, state, enabled = !card.locked) { v -> edit { it.copy(rotation = v) } }
    PSlider(R.string.poster_card_tilt_h, card.tiltY, -45f..45f, state) { v -> edit { it.copy(tiltY = v) } }
    PSlider(R.string.poster_card_tilt_v, card.tiltX, -45f..45f, state) { v -> edit { it.copy(tiltX = v) } }
    PSlider(R.string.poster_card_radius, card.radius, 0f..0.25f, state) { v -> edit { it.copy(radius = v) } }
    PSlider(R.string.poster_card_surface, card.surface, 0f..1f, state) { v -> edit { it.copy(surface = v) } }
    PSlider(R.string.poster_card_border, card.borderWidth, 0f..0.04f, state) { v -> edit { it.copy(borderWidth = v) } }
    Swatches(R.string.poster_card_border_color, card.borderColor) { c -> edit { it.copy(borderColor = c) } }
    PSlider(R.string.poster_card_glow, card.glow, 0f..1f, state) { v -> edit { it.copy(glow = v) } }
    PSlider(R.string.poster_card_edge, card.edge, 0f..1f, state) { v -> edit { it.copy(edge = v) } }
    Text(stringResource(R.string.poster_card_style), style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardStyle.entries.forEach { st -> PChip(stringResource(st.label), card.style == st) { edit { it.copy(style = st) } } }
    }
    PToggle(R.string.poster_card_player, card.showPlayer) { v -> edit { it.copy(showPlayer = v) } }
    PToggle(R.string.poster_card_behind, card.behind && hasCutout, enabled = hasCutout) { v -> edit { it.copy(behind = v) } }
    if (!hasCutout) Hint(R.string.poster_needs_person)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        val idx = d.cards.indexOfFirst { it.trackId == card.trackId }
        PChip(stringResource(R.string.poster_card_forward), false, idx < d.cards.lastIndex) { state.update { it.copy(cards = it.cards.swap(idx, idx + 1)) } }
        PChip(stringResource(R.string.poster_card_backward), false, idx > 0) { state.update { it.copy(cards = it.cards.swap(idx, idx - 1)) } }
        PChip(stringResource(R.string.poster_card_reset), false) {
            val fresh = defaultCard(d.cards.map { it.trackId }, card.trackId, d.ratio)
            if (fresh != null) edit { fresh.copy(trackId = card.trackId) }
        }
    }
}

private fun <T> List<T>.swap(
    a: Int,
    b: Int,
): List<T> = toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

@Composable
private fun LightPanel(state: PosterEditorState) {
    val l = state.design.light
    val s = state.design.shadow
    GroupHeader(R.string.poster_group_light) { state.update { it.copy(light = LightState()) } }
    PSlider(R.string.poster_light_x, l.x, 0f..1f, state) { v -> state.update { it.copy(light = it.light.copy(x = v)) } }
    PSlider(R.string.poster_light_y, l.y, 0f..1f, state) { v -> state.update { it.copy(light = it.light.copy(y = v)) } }
    PSlider(R.string.poster_light_intensity, l.intensity, 0f..1f, state) { v -> state.update { it.copy(light = it.light.copy(intensity = v)) } }
    PSlider(R.string.poster_light_size, l.size, 0.2f..1.6f, state) { v -> state.update { it.copy(light = it.light.copy(size = v)) } }
    Swatches(R.string.poster_light_color, l.color) { c -> state.update { it.copy(light = it.light.copy(color = c)) } }
    GroupHeader(R.string.poster_group_shadow) { state.update { it.copy(shadow = ShadowState()) } }
    PSlider(R.string.poster_shadow_angle, s.angle, 0f..360f, state) { v -> state.update { it.copy(shadow = it.shadow.copy(angle = v)) } }
    PSlider(R.string.poster_shadow_distance, s.distance, 0f..0.2f, state) { v -> state.update { it.copy(shadow = it.shadow.copy(distance = v)) } }
    PSlider(R.string.poster_shadow_opacity, s.opacity, 0f..1f, state) { v -> state.update { it.copy(shadow = it.shadow.copy(opacity = v)) } }
    PSlider(R.string.poster_shadow_blur, s.blur, 0.01f..0.3f, state) { v -> state.update { it.copy(shadow = it.shadow.copy(blur = v)) } }
    Swatches(R.string.poster_shadow_color, s.color) { c -> state.update { it.copy(shadow = it.shadow.copy(color = c)) } }
}

@Composable
private fun EffectsPanel(state: PosterEditorState) {
    val e = state.design.effects
    GroupHeader(R.string.poster_effect_stars) { state.update { it.copy(effects = it.effects.copy(stars = 0.8f, starSize = 0.5f, starOpacity = 0.9f, starsFront = false)) } }
    PSlider(R.string.poster_fx_amount, e.stars, 0f..1f, state) { v -> state.update { it.copy(effects = it.effects.copy(stars = v)) } }
    PSlider(R.string.poster_fx_size, e.starSize, 0f..1f, state) { v -> state.update { it.copy(effects = it.effects.copy(starSize = v)) } }
    PSlider(R.string.poster_fx_opacity, e.starOpacity, 0f..1f, state) { v -> state.update { it.copy(effects = it.effects.copy(starOpacity = v)) } }
    PToggle(R.string.poster_fx_front, e.starsFront) { v -> state.update { it.copy(effects = it.effects.copy(starsFront = v)) } }
    GroupHeader(R.string.poster_effect_birds) { state.update { it.copy(effects = it.effects.copy(birds = 0.5f, birdSize = 0.5f, birdOpacity = 0.9f, birdsFront = false)) } }
    PSlider(R.string.poster_fx_amount, e.birds, 0f..1f, state) { v -> state.update { it.copy(effects = it.effects.copy(birds = v)) } }
    PSlider(R.string.poster_fx_size, e.birdSize, 0f..1f, state) { v -> state.update { it.copy(effects = it.effects.copy(birdSize = v)) } }
    PSlider(R.string.poster_fx_opacity, e.birdOpacity, 0f..1f, state) { v -> state.update { it.copy(effects = it.effects.copy(birdOpacity = v)) } }
    PToggle(R.string.poster_fx_front, e.birdsFront) { v -> state.update { it.copy(effects = it.effects.copy(birdsFront = v)) } }
}

@Composable
private fun WatermarkPanel(
    state: PosterEditorState,
    logoMissing: Boolean,
) {
    val w = state.design.watermark
    if (logoMissing) Hint(R.string.poster_wm_missing)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PChip(stringResource(R.string.poster_wm_transparent), w.mode == WatermarkMode.TRANSPARENT) { state.update { it.copy(watermark = it.watermark.copy(mode = WatermarkMode.TRANSPARENT)) } }
        PChip(stringResource(R.string.poster_wm_solid), w.mode == WatermarkMode.SOLID) { state.update { it.copy(watermark = it.watermark.copy(mode = WatermarkMode.SOLID)) } }
    }
    Text(stringResource(R.string.poster_wm_position), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0..2) {
                    val on = w.row == r && w.col == c
                    Text(
                        WM_ARROWS[r * 3 + c],
                        style = MaterialTheme.typography.labelLarge,
                        modifier =
                            Modifier
                                .size(40.dp)
                                .glassChip(on, RoundedCornerShape(10.dp))
                                .clickable { state.update { it.copy(watermark = it.watermark.copy(row = r, col = c)) } }
                                .padding(top = 9.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
    PSlider(R.string.poster_wm_size, w.size, 0.08f..0.5f, state) { v -> state.update { it.copy(watermark = it.watermark.copy(size = v)) } }
    PSlider(R.string.poster_wm_margin, w.margin, 0f..0.15f, state) { v -> state.update { it.copy(watermark = it.watermark.copy(margin = v)) } }
    PSlider(R.string.poster_wm_opacity, w.opacity, 0.1f..1f, state) { v -> state.update { it.copy(watermark = it.watermark.copy(opacity = v)) } }
    PToggle(R.string.poster_wm_dark, w.darkLogo) { v -> state.update { it.copy(watermark = it.watermark.copy(darkLogo = v)) } }
    PToggle(R.string.poster_wm_name, w.showName) { v -> state.update { it.copy(watermark = it.watermark.copy(showName = v)) } }
    PToggle(R.string.poster_wm_shadow, w.shadow) { v -> state.update { it.copy(watermark = it.watermark.copy(shadow = v)) } }
    if (w.mode == WatermarkMode.SOLID) {
        Swatches(R.string.poster_wm_badge_color, w.badgeColor) { c -> state.update { it.copy(watermark = it.watermark.copy(badgeColor = c)) } }
        PSlider(R.string.poster_wm_badge_radius, w.badgeRadius, 0f..1f, state) { v -> state.update { it.copy(watermark = it.watermark.copy(badgeRadius = v)) } }
    }
}

@Composable
private fun ExportPanel(
    state: PosterEditorState,
    scales: List<Float>,
    scaleIdx: Int,
    busy: Boolean,
    onScale: (Int) -> Unit,
    onExport: (share: Boolean) -> Unit,
) {
    val d = state.design
    Text(stringResource(R.string.poster_export_ratio), style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PosterRatio.R9_16 to "9:16", PosterRatio.R4_5 to "4:5", PosterRatio.R1_1 to "1:1").forEach { (r, label) ->
            PChip(label, d.ratio == r) {
                state.update { dd ->
                    val f = r.cardFactor / dd.ratio.cardFactor
                    dd.copy(ratio = r, cards = dd.cards.map { it.copy(widthFrac = it.widthFrac * f) })
                }
            }
        }
    }
    Text(stringResource(R.string.poster_export_size), style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        scales.forEachIndexed { i, s -> PChip("${(d.ratio.w * s).toInt()}×${(d.ratio.h * s).toInt()}", i == scaleIdx) { onScale(i) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) PChip(stringResource(R.string.poster_export_save), true, !busy) { onExport(false) }
        PChip(stringResource(R.string.poster_export_share), true, !busy) { onExport(true) }
    }
}

// ---------- komponen kecil ----------

@Composable
private fun PChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier =
            Modifier
                .glassChip(selected, CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun PSlider(
    @StringRes label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    state: PosterEditorState,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(124.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                state.beginChange()
                onChange(it)
            },
            onValueChangeFinished = { state.endChange() },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PToggle(
    @StringRes label: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Swatches(
    @StringRes label: Int,
    current: Int,
    onPick: (Int) -> Unit,
) {
    val desc = stringResource(R.string.poster_color_option)
    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PALETTE.forEach { c ->
            val on = c == current
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .semantics { contentDescription = desc }
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun GroupHeader(
    @StringRes title: Int,
    onReset: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        PChip(stringResource(R.string.poster_reset_group), false, onClick = onReset)
    }
}

@Composable
private fun Hint(
    @StringRes res: Int,
) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

private fun shareImage(
    context: Context,
    uri: Uri,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
