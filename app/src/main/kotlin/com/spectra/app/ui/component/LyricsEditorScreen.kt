/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.RectangleShape
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.spectra.app.R
import com.spectra.app.lyrics.LyricsEntry
import com.spectra.app.ui.theme.AppFont
import com.spectra.app.ui.theme.appFontFamily
import com.spectra.app.ui.theme.isFontImported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs
import android.graphics.Bitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.sin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt

internal enum class LyricsEditorMode { IMAGE, VIDEO }

internal enum class EditorStyle(@StringRes val label: Int) {
    FOCUS(R.string.editor_style_focus),
    LYRICS(R.string.editor_style_lyrics),
    CARD(R.string.editor_style_card),
}

internal enum class EditorRatio(val label: String, val w: Float, val h: Float) {
    R1_1("1:1", 1f, 1f),
    R4_5("4:5", 4f, 5f),
    R9_16("9:16", 9f, 16f),
    R16_9("16:9", 16f, 9f),
    ;

    val value get() = w / h
}

internal enum class EditorBg(@StringRes val label: Int) {
    GLASS(R.string.editor_bg_glass),
    SOLID(R.string.editor_bg_solid),
    BLUR(R.string.editor_bg_blur),
    GRADIENT(R.string.editor_bg_gradient),
}

internal enum class EditorWeight(@StringRes val label: Int, val w: Int) {
    REGULAR(R.string.editor_weight_regular, 400),
    MEDIUM(R.string.editor_weight_medium, 500),
    BOLD(R.string.editor_weight_bold, 700),
    BLACK(R.string.editor_weight_black, 800),
}

internal enum class EditorAlign(@StringRes val label: Int) {
    LEFT(R.string.editor_align_left),
    CENTER(R.string.editor_align_center),
}

private class EditorFont(val id: String, val label: String, val family: (Int) -> FontFamily)

private fun variableFont(resId: Int, weight: Int) = FontFamily(
    Font(resId, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))),
)

private val Ink = Color.White
private const val CLIP_STEP_MS = 500L

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * Editor bagikan lirik sebagai gambar/video. Tahap 2: editor + preview live (belum render file).
 * [lines]/[selected] dari lirik tersinkron; bila [synced] false, lirik tampil statis tanpa timestamp.
 */
@Composable
internal fun LyricsEditorScreen(
    mode: LyricsEditorMode,
    lyricsText: String,
    title: String,
    artists: String,
    thumbnailUrl: String?,
    onClose: () -> Unit,
    lines: List<LyricsEntry> = emptyList(),
    selected: List<Int> = emptyList(),
    synced: Boolean = false,
    songDurationMs: Long = 0L,
    audioUri: Uri? = null,
    audioLoading: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewWidthDp by remember { mutableStateOf(300f) }
    var renderJob by remember { mutableStateOf<Job?>(null) }
    var renderLabel by remember { mutableStateOf<String?>(null) }
    var renderProgress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<RenderResult?>(null) }
    val latestAudio by rememberUpdatedState(audioUri)
    val latestAudioLoading by rememberUpdatedState(audioLoading)
    val sel = remember(selected) { selected.sorted() }
    val canSync = synced && sel.isNotEmpty() && sel.all { lines.getOrNull(it) != null }
    val display = remember(lines, canSync, lyricsText) {
        if (canSync) lines.filter { it.text.isNotBlank() && !it.isBackground }
        else lyricsText.lines().filter { it.isNotBlank() }.map { LyricsEntry(0L, it) }
    }

    val defaultStart = if (canSync) lines[sel.first()].time else 0L
    val defaultDur = if (canSync) {
        val last = lines[sel.last()]
        val upcoming = lines.drop(sel.last() + 1).filter { it.time > last.time && it.text.isNotBlank() }
        // Lanjut ke dua lirik berikutnya (minimal 15 dtk) supaya lagu tidak berhenti di satu baris.
        val endMs = maxOf(upcoming.getOrNull(1)?.time ?: (last.time + 8000L), defaultStart + 15_000L)
        val capMs = if (songDurationMs > defaultStart) songDurationMs else endMs
        ((minOf(endMs, capMs) - defaultStart) / 1000.0).roundToInt().coerceIn(1, 60)
    } else 18

    var style by remember { mutableStateOf(EditorStyle.FOCUS) }
    var ratio by remember { mutableStateOf(EditorRatio.R9_16) }
    var bg by remember { mutableStateOf(EditorBg.GLASS) }
    var weight by remember { mutableStateOf(EditorWeight.BOLD) }
    var align by remember { mutableStateOf(EditorAlign.LEFT) }
    var startMs by remember { mutableLongStateOf(defaultStart) }
    var durSec by remember { mutableStateOf(defaultDur) }
    var playing by remember { mutableStateOf(true) }

    val sysLabel = stringResource(R.string.spectra_font_system)
    val fonts = remember(context, sysLabel) {
        fun sys(name: String, style: Int = android.graphics.Typeface.NORMAL) = FontFamily(android.graphics.Typeface.create(name, style))
        listOf(
            EditorFont("jakarta", "Plus Jakarta Sans") { variableFont(R.font.plus_jakarta_sans, it) },
            EditorFont(AppFont.SF_DISPLAY.id, "iOS") { appFontFamily(context, AppFont.SF_DISPLAY, it) },
            EditorFont(AppFont.SF_ROUNDED.id, "iOS Rounded") { appFontFamily(context, AppFont.SF_ROUNDED, it) },
            EditorFont("system", sysLabel) { FontFamily.SansSerif },
            EditorFont("playfair", "Playfair") { variableFont(R.font.playfair_roman, it) },
            EditorFont("editorial", "Editorial") { variableFont(R.font.playfair_italic, it) },
            EditorFont("grotesk", "Grotesk") { variableFont(R.font.roboto_condensed, it) },
            EditorFont("rubik", "Rubik") { variableFont(R.font.rubik, it) },
            EditorFont("dmsans", "DM Sans") { variableFont(R.font.dm_sans, it) },
            EditorFont("handwriting", "Tulisan tangan") { variableFont(R.font.dancing_script, it) },
            EditorFont("bbh", "BBH Bartle") { FontFamily(Font(R.font.bbh_bartle_regular)) },
        )
    }
    var fontId by remember { mutableStateOf("jakarta") }
    val font = fonts.firstOrNull { it.id == fontId } ?: fonts.first()
    val family = remember(font, weight) { font.family(weight.w) }

    // Artwork + warna adaptif (satu hue, tanpa pelangi).
    var art by remember { mutableStateOf<ImageBitmap?>(null) }
    var base by remember { mutableStateOf(Color(0xFF2A2A2E)) }
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val res = SingletonImageLoader.get(context)
                    .execute(ImageRequest.Builder(context).data(thumbnailUrl).allowHardware(false).build())
                val bmp = res.image?.toBitmap() ?: return@withContext
                val p = Palette.from(bmp).generate()
                val c = p.getDarkMutedColor(p.getDominantColor(0xFF2A2A2E.toInt()))
                base = Color(c)
                art = bmp.asImageBitmap()
            } catch (e: Exception) {
                Timber.e(e, "Editor: gagal memuat artwork")
            }
        }
    }

    // Jam preview: satu state posisi, dibaca hanya di fase draw/derived agar tidak recompose per frame.
    val posMs = remember { mutableLongStateOf(defaultStart) }
    val playingState = rememberUpdatedState(playing)
    LaunchedEffect(startMs, durSec) {
        var elapsed = 0L
        var last = 0L
        posMs.longValue = startMs
        val span = durSec * 1000L
        while (true) {
            if (!playingState.value) {
                snapshotFlow { playingState.value }.first { it }
                last = 0L
            }
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) elapsed += (now - last) / 1_000_000L
                last = now
                posMs.longValue = startMs + (elapsed % span)
            }
        }
    }
    val activeIdx = remember(display, canSync) {
        derivedStateOf {
            if (!canSync) -1
            else display.indexOfLast { it.time <= posMs.longValue }.coerceAtLeast(0)
        }
    }

    fun startRender(video: Boolean) {
        if (renderJob?.isActive == true) return
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        if (activity == null) {
            Toast.makeText(context, context.getString(R.string.editor_render_no_activity), Toast.LENGTH_LONG).show()
            return
        }
        val spec = LyricsRenderSpec(
            style = style, ratio = ratio, bg = bg, family = family, weight = weight, align = align,
            display = display, canSync = canSync, title = title, artists = artists, art = art, base = base,
            startMs = startMs, durMs = durSec * 1000L, songDurationMs = songDurationMs, previewWidthDp = previewWidthDp,
        )
        val framePos = posMs.longValue
        val wasPlaying = playing
        playing = false
        renderProgress = 0f
        renderLabel = context.getString(if (video) R.string.editor_rendering_video else R.string.editor_rendering_image)
        renderJob = scope.launch {
            try {
                if (video && latestAudioLoading) snapshotFlow { latestAudioLoading }.first { !it }
                result = if (video) renderLyricsVideo(activity, spec, latestAudio) { renderProgress = it }
                else renderLyricsImage(activity, spec, framePos, wasPlaying)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.e(e, "Render gagal")
                com.spectra.app.utils.SpectraLog.e("Render", "render gagal", e)
                Toast.makeText(context, context.getString(R.string.editor_render_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
            } finally {
                renderLabel = null
                renderJob = null
                playing = true
            }
        }
    }

    result?.let { r -> LyricsResultScreen(r, onEdit = { result = null }, onDone = { result = null; onClose() }) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF050506))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                LyricsPreview(
                    style = style, ratio = ratio, bg = bg, family = family, weight = weight, align = align,
                    display = display, activeIdx = activeIdx, pos = posMs, canSync = canSync,
                    title = title, artists = artists, art = art, base = base, playing = playing,
                    startMs = startMs, durMs = durSec * 1000L, songDurationMs = songDurationMs,
                    onLayout = { previewWidthDp = it },
                )
            }

            if (renderLabel == null) Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EditorRow(stringResource(R.string.editor_label_style)) {
                    Segmented(EditorStyle.entries, style, { stringResource(it.label) }) { style = it }
                }
                EditorRow(stringResource(R.string.editor_label_ratio)) {
                    Segmented(EditorRatio.entries, ratio, { it.label }) { ratio = it }
                }
                if (canSync) {
                    EditorRow(stringResource(R.string.editor_label_start)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.editor_start_fmt, fmtTime(startMs), durSec),
                                color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            GlassPill(stringResource(R.string.editor_minus_step)) {
                                startMs = (startMs - CLIP_STEP_MS).coerceAtLeast(0L)
                            }
                            Spacer(Modifier.width(6.dp))
                            GlassPill(stringResource(R.string.editor_plus_step)) {
                                val max = if (songDurationMs > 0) songDurationMs else Long.MAX_VALUE
                                startMs = (startMs + CLIP_STEP_MS).coerceAtMost(max)
                            }
                        }
                    }
                }
                EditorRow(stringResource(R.string.editor_label_duration)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = durSec.toFloat(),
                            onValueChange = { durSec = it.roundToInt().coerceIn(1, 60) },
                            valueRange = 1f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = Ink, activeTrackColor = Ink,
                                inactiveTrackColor = Ink.copy(alpha = 0.18f),
                            ),
                            modifier = Modifier.weight(1f).height(28.dp),
                        )
                        if (!canSync) {
                            Text(
                                stringResource(R.string.editor_duration_fmt, durSec),
                                color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
                EditorRow(stringResource(R.string.editor_label_bg)) {
                    Segmented(EditorBg.entries, bg, { stringResource(it.label) }) { bg = it }
                }
                EditorRow(stringResource(R.string.editor_label_font)) {
                    FontDropdown(fonts, font, weight) { fontId = it.id }
                }
                EditorRow(stringResource(R.string.editor_label_weight)) {
                    Segmented(EditorWeight.entries, weight, { stringResource(it.label) }) { weight = it }
                }
                EditorRow(stringResource(R.string.editor_label_align)) {
                    Segmented(EditorAlign.entries, align, { stringResource(it.label) }) { align = it }
                }
            }

            if (renderLabel != null) {
                RenderProgressPanel(renderLabel!!, renderProgress) { renderJob?.cancel() }
            } else {
            // Bar bawah: simpan gambar, play/pause, buat video.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.cancel),
                    color = Ink.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 8.dp, vertical = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.insert_photo), null,
                    tint = Ink, modifier = Modifier.size(24.dp).clip(CircleShape).clickable {
                        startRender(false)
                    },
                )
                Spacer(Modifier.width(18.dp))
                Icon(
                    painterResource(if (playing) R.drawable.pause else R.drawable.play), null,
                    tint = Ink,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { playing = !playing },
                )
                Spacer(Modifier.width(18.dp))
                Box(
                    Modifier.clip(CircleShape).background(Color.White).clickable { startRender(true) }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                ) {
                    Text(stringResource(R.string.editor_make_video), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            }
        }
    }
}

@Composable
private fun EditorRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Ink.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun <T> Segmented(items: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(2.dp),
    ) {
        items.forEach { item ->
            val on = item == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(item), fontSize = 12.sp, maxLines = 1,
                    color = if (on) Ink else Ink.copy(alpha = 0.7f),
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun GlassPill(text: String, content: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.5.dp, Color.White.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        content?.invoke()
    }
}

@Composable
private fun FontDropdown(fonts: List<EditorFont>, current: EditorFont, weight: EditorWeight, onPick: (EditorFont) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        GlassPill(current.label, content = {
            Icon(painterResource(R.drawable.expand_more), null, tint = Ink.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }) { open = true }
        if (open) FontPickerSheet(fonts, current, weight, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

/** Pilihan huruf: layar di belakang diburamkan + digelapkan (blur jendela di Android 12+), panel kaca cair di atasnya. */
@Composable
private fun FontPickerSheet(fonts: List<EditorFont>, current: EditorFont, weight: EditorWeight, onDismiss: () -> Unit, onPick: (EditorFont) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.apply {
                setDimAmount(0.55f)
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setWindowAnimations(-1)
                if (Build.VERSION.SDK_INT >= 31) {
                    addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes = attributes.apply { blurBehindRadius = 45 }
                }
            }
        }
        val enter = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, androidx.compose.animation.core.spring(0.78f, 380f)) }
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null, onClick = onDismiss,
            ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = enter.value.coerceIn(0f, 1f)
                        val k = 0.92f + 0.08f * enter.value
                        scaleX = k; scaleY = k
                        translationY = (1f - enter.value) * 40.dp.toPx()
                    }
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.5f), shape)
                    .glassPanel(shape, strength = 1f)
                    .border(0.6.dp, Color.White.copy(alpha = 0.18f), shape)
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .clickable(enabled = false) {},
            ) {
                fonts.forEachIndexed { i, f ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.1f)))
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).clickable { onPick(f) }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(26.dp)) {
                            if (f.id == current.id) Icon(painterResource(R.drawable.check), null, tint = Ink, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            f.label, color = Ink, fontSize = 17.sp,
                            fontFamily = f.family(weight.w), fontWeight = FontWeight(weight.w),
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Preview ─────────────────────────────

@Composable
internal fun LyricsPreview(
    style: EditorStyle, ratio: EditorRatio, bg: EditorBg, family: FontFamily, weight: EditorWeight,
    align: EditorAlign, display: List<LyricsEntry>, activeIdx: State<Int>, pos: State<Long>, canSync: Boolean,
    title: String, artists: String, art: ImageBitmap?, base: Color, playing: Boolean,
    startMs: Long, durMs: Long, songDurationMs: Long,
    fullBleed: Boolean = false,
    onLayout: (Float) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val r = ratio.value
        val w = minOf(maxWidth, maxHeight * r)
        val h = w / r
        val u = minOf(w, h)
        val shape = if (fullBleed) RectangleShape else RoundedCornerShape(u * 0.06f)
        SideEffect { onLayout(w.value) }
        fun makeCtx(cu: Dp, cw: Dp, ch: Dp, glass: Boolean = false) = PreviewCtx(
            u = cu, h = ch, w = cw, family = family, weight = FontWeight(weight.w),
            center = align == EditorAlign.CENTER, display = display, activeIdx = activeIdx, pos = pos,
            canSync = canSync, title = title, artists = artists, art = art, playing = playing,
            startMs = startMs, durMs = durMs, songDurationMs = songDurationMs, glass = glass,
        )
        val ctx = makeCtx(u, w, h)
        Box(
            Modifier
                .size(w, h)
                .clip(shape)
                .then(if (fullBleed) Modifier else Modifier.border(0.8.dp, Color.White.copy(alpha = if (bg == EditorBg.GLASS) 0.28f else 0.1f), shape)),
        ) {
            if (bg == EditorBg.GLASS) {
                // Bingkai gelap + kartu kaca transparan di dalamnya (seperti kartu pemutar iOS).
                val softOuter = remember(art) { art?.let { softBlur(it, 48, 4) } }
                val softInner = remember(art) { art?.let { softBlur(it, 64, 3) } }
                if (softOuter != null) {
                    Image(softOuter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
                } else Box(Modifier.fillMaxSize().background(lerp(base, Color.Black, 0.5f)))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.22f), 0.5f to Color.Black.copy(alpha = 0.5f), 1f to Color.Black.copy(alpha = 0.92f)),
                    ),
                )
                val mx = u * 0.1f
                val my = u * 0.12f
                val iw = w - mx * 2
                val ih = h - my * 2
                val iu = minOf(iw, ih)
                val ishape = RoundedCornerShape(iu * 0.09f)
                val ictx = makeCtx(iu, iw, ih, glass = true)
                Box(
                    Modifier.padding(horizontal = mx, vertical = my).fillMaxSize().clip(ishape)
                        .border(0.8.dp, Color.White.copy(alpha = 0.22f), ishape),
                ) {
                    if (softInner != null) Image(softInner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.6f, filterQuality = FilterQuality.High)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.26f), 0.55f to Color.White.copy(alpha = 0.12f), 1f to Color.Black.copy(alpha = 0.3f)),
                        ),
                    )
                    when (style) {
                        EditorStyle.FOCUS -> FocusContent(ictx)
                        EditorStyle.LYRICS -> LyricsOnlyContent(ictx)
                        EditorStyle.CARD -> CardContent(ictx)
                    }
                    if (style == EditorStyle.LYRICS) {
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .padding(start = iu * 0.056f, end = iu * 0.056f, bottom = iu * 0.1f)
                                .height(0.8.dp).background(Ink.copy(alpha = 0.18f)),
                        )
                    }
                    if (style == EditorStyle.CARD) {
                        Watermark(ictx, Modifier.align(Alignment.BottomCenter).padding(bottom = iu * 0.045f), pill = true)
                    } else {
                        Watermark(ictx, Modifier.align(Alignment.BottomStart).padding(start = iu * 0.056f, bottom = iu * 0.04f), pill = false)
                    }
                }
                if (style == EditorStyle.CARD && w <= h) {
                    // Ikon samping berada di margin bingkai, di luar kartu kaca.
                    Column(
                        Modifier.align(Alignment.CenterEnd).offset(y = -(h * 0.03f)).padding(end = u * 0.031f),
                        verticalArrangement = Arrangement.spacedBy(u * 0.096f),
                    ) {
                        Icon(painterResource(R.drawable.favorite), null, tint = Ink.copy(alpha = 0.65f), modifier = Modifier.size(u * 0.038f))
                        Icon(painterResource(R.drawable.music_note), null, tint = Ink, modifier = Modifier.size(u * 0.038f))
                        Icon(painterResource(R.drawable.router_antenna), null, tint = Ink.copy(alpha = 0.45f), modifier = Modifier.size(u * 0.038f))
                    }
                }
            } else {
                PreviewBackground(bg, art, base)
                when (style) {
                    EditorStyle.FOCUS -> FocusContent(ctx)
                    EditorStyle.LYRICS -> LyricsOnlyContent(ctx)
                    EditorStyle.CARD -> CardContent(ctx)
                }
                Watermark(ctx, Modifier.align(Alignment.BottomCenter).padding(bottom = u * 0.035f))
            }
        }
    }
}

private class PreviewCtx(
    val u: Dp, val h: Dp, val w: Dp, val family: FontFamily, val weight: FontWeight, val center: Boolean,
    val display: List<LyricsEntry>, val activeIdx: State<Int>, val pos: State<Long>, val canSync: Boolean,
    val title: String, val artists: String, val art: ImageBitmap?, val playing: Boolean,
    val startMs: Long, val durMs: Long, val songDurationMs: Long,
    val glass: Boolean = false,
)


/** Blur murah: perkecil gambar lalu box-blur di CPU sekali. Tanpa RenderEffect, aman untuk render video. */
private fun softBlur(src: ImageBitmap, targetW: Int, radius: Int): ImageBitmap {
    val b = src.asAndroidBitmap()
    val th = (targetW * b.height / b.width.toFloat()).roundToInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(b, targetW, th, true).copy(Bitmap.Config.ARGB_8888, true)
    val w = small.width
    val h = small.height
    var px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
    fun pass(horizontal: Boolean) {
        val out = IntArray(px.size)
        val n = if (horizontal) w else h
        val lines = if (horizontal) h else w
        for (l in 0 until lines) for (i in 0 until n) {
            var r = 0; var g = 0; var bl = 0; var cnt = 0
            for (k in -radius..radius) {
                val j = (i + k).coerceIn(0, n - 1)
                val c = if (horizontal) px[l * w + j] else px[j * w + l]
                r += (c shr 16) and 255; g += (c shr 8) and 255; bl += c and 255; cnt++
            }
            val v = (0xFF shl 24) or ((r / cnt) shl 16) or ((g / cnt) shl 8) or (bl / cnt)
            if (horizontal) out[l * w + i] = v else out[i * w + l] = v
        }
        px = out
    }
    repeat(2) { pass(true); pass(false) }
    small.setPixels(px, 0, w, 0, 0, w, h)
    return small.asImageBitmap()
}

@Composable
private fun PreviewBackground(bg: EditorBg, art: ImageBitmap?, base: Color) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && art != null
    val grad = Brush.verticalGradient(listOf(lerp(base, Color.Black, 0.2f), lerp(base, Color.Black, 0.8f)))
    Box(Modifier.fillMaxSize()) {
        when (bg) {
            EditorBg.SOLID -> Box(Modifier.fillMaxSize().background(lerp(base, Color.Black, 0.45f)))
            EditorBg.GRADIENT -> Box(Modifier.fillMaxSize().background(grad))
            EditorBg.GLASS -> {
                if (canBlur) {
                    Image(art!!, null, Modifier.fillMaxSize().blur(8.dp), contentScale = ContentScale.Crop)
                } else Box(Modifier.fillMaxSize().background(grad))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.18f))),
                    ),
                )
            }
            EditorBg.BLUR -> {
                if (canBlur) {
                    Image(art!!, null, Modifier.fillMaxSize().blur(56.dp), contentScale = ContentScale.Crop)
                } else Box(Modifier.fillMaxSize().background(grad))
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
            }
        }
    }
}

@Composable
private fun PreviewCtx.fs(k: Float): TextUnit = with(LocalDensity.current) { (u * k).toSp() }

@Composable
private fun Watermark(c: PreviewCtx, modifier: Modifier, pill: Boolean = true) {
    val base = if (pill) modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
        .padding(horizontal = c.u * 0.05f, vertical = c.u * 0.024f) else modifier
    Row(base, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.spectra_logo_white), null,
            Modifier.width(c.u * (if (pill) 0.062f else 0.054f)),
            contentScale = ContentScale.FillWidth,
        )
        Spacer(Modifier.width(c.u * (if (pill) 0.016f else 0.012f)))
        Text(
            stringResource(R.string.app_name), color = Ink.copy(alpha = 0.85f),
            fontSize = c.fs(if (pill) 0.036f else 0.03f), fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

@Composable
private fun Art(c: PreviewCtx, modifier: Modifier, radius: Dp) {
    val shape = RoundedCornerShape(radius)
    val bmp = c.art
    if (bmp != null) {
        Image(bmp, null, modifier.clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(shape).background(Color.White.copy(alpha = 0.12f)))
    }
}

/** Baris lirik. Efek karaoke per kata hanya bila baris punya timestamp kata asli. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KaraokeText(
    entry: LyricsEntry,
    pos: State<Long>,
    textStyle: TextStyle,
    center: Boolean,
    karaoke: Boolean,
    modifier: Modifier = Modifier,
) {
    val words = entry.words
    val align = if (center) TextAlign.Center else TextAlign.Start
    if (!karaoke || words.isNullOrEmpty()) {
        Text(entry.text, style = textStyle.copy(textAlign = align), color = Ink, modifier = modifier.fillMaxWidth())
        return
    }
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
    ) {
        words.forEach { w ->
            val t = w.text + if (w.hasTrailingSpace) " " else ""
            Box {
                Text(t, style = textStyle, color = Ink.copy(alpha = 0.4f), maxLines = 1, softWrap = false)
                Text(
                    t, style = textStyle, color = Ink, maxLines = 1, softWrap = false,
                    modifier = Modifier.drawWithContent {
                        val span = (w.endTime - w.startTime).coerceAtLeast(0.001)
                        val p = ((pos.value / 1000.0 - w.startTime) / span).toFloat().coerceIn(0f, 1f)
                        clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewCtx.lineStyle(k: Float) = TextStyle(
    fontFamily = family, fontWeight = weight, fontSize = fs(k), lineHeight = fs(k * 1.22f),
)

@Composable
private fun FocusContent(c: PreviewCtx) {
    val density = LocalDensity.current
    Column(
        Modifier.fillMaxSize().padding(start = c.u * 0.07f, end = c.u * 0.07f, top = c.u * 0.07f, bottom = c.u * 0.1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(c, Modifier.size(c.u * 0.12f), c.u * 0.02f)
            Spacer(Modifier.width(c.u * 0.03f))
            Column {
                Text(c.title, color = Ink, fontSize = c.fs(0.042f), fontFamily = c.family, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.artists, color = Ink.copy(alpha = 0.6f), fontSize = c.fs(0.03f), fontFamily = c.family, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(c.u * 0.03f))
        val listState = rememberLazyListState()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewportPx = with(density) { maxHeight.toPx() }
            val active = c.activeIdx.value
            LaunchedEffect(active, viewportPx) {
                if (active >= 0) listState.animateScrollToItem(active, -(viewportPx * 0.1f).toInt())
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = false,
                contentPadding = PaddingValues(bottom = maxHeight),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 0.06f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                itemsIndexed(c.display) { i, entry ->
                    val d = if (c.canSync) abs(i - active) else 0
                    val alpha = animateFloatAsState(
                        if (d == 0) 1f else (0.5f - 0.1f * (d - 1)).coerceAtLeast(0.16f), tween(320), label = "a",
                    )
                    val blurDp = animateFloatAsState(if (d == 0) 0f else minOf(1.5f * d, 5f), tween(320), label = "b")
                    Box(
                        Modifier
                            .padding(vertical = c.u * 0.016f)
                            .graphicsLayer {
                                this.alpha = alpha.value
                                val px = blurDp.value * density.density
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && px > 0.5f) {
                                    renderEffect = BlurEffect(px, px, TileMode.Decal)
                                }
                            },
                    ) {
                        KaraokeText(entry, c.pos, c.lineStyle(if (c.glass) 0.085f else 0.07f), c.center, karaoke = c.canSync && d == 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsOnlyContent(c: PreviewCtx) {
    if (c.glass && c.canSync) {
        Column(Modifier.fillMaxSize().padding(start = c.u * 0.056f, end = c.u * 0.056f, top = c.u * 0.07f, bottom = c.u * 0.13f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(c, Modifier.size(c.u * 0.12f), c.u * 0.02f)
                Spacer(Modifier.width(c.u * 0.03f))
                Column {
                    Text(c.title, color = Ink, fontSize = c.fs(0.042f), fontFamily = c.family, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.artists, color = Ink.copy(alpha = 0.6f), fontSize = c.fs(0.03f), fontFamily = c.family, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("\u201C", color = Ink.copy(alpha = 0.3f), fontSize = c.fs(0.2f), fontFamily = c.family, fontWeight = FontWeight.Bold)
            val a = c.activeIdx.value
            Column(verticalArrangement = Arrangement.spacedBy(c.u * 0.02f)) {
                ((a - 2).coerceAtLeast(0)..a).forEach { i ->
                    val entry = c.display.getOrNull(i) ?: return@forEach
                    Box(Modifier.graphicsLayer { alpha = if (i == a) 1f else 0.4f }) {
                        KaraokeText(entry, c.pos, c.lineStyle(0.085f).copy(lineHeight = c.fs(0.12f)), c.center, karaoke = i == a)
                    }
                }
            }
        }
        return
    }
    Box(
        Modifier.fillMaxSize().padding(start = c.u * 0.08f, end = c.u * 0.08f, top = c.u * 0.08f, bottom = c.u * 0.12f),
        contentAlignment = if (c.center) Alignment.Center else Alignment.CenterStart,
    ) {
        if (c.canSync) {
            Crossfade(c.activeIdx.value, animationSpec = tween(260), label = "line") { i ->
                c.display.getOrNull(i)?.let { KaraokeText(it, c.pos, c.lineStyle(0.1f), c.center, karaoke = true) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(c.u * 0.02f)) {
                c.display.forEach { KaraokeText(it, c.pos, c.lineStyle(0.07f), c.center, karaoke = false) }
            }
        }
    }
}

@Composable
private fun CardContent(c: PreviewCtx) {
    val wide = c.w > c.h
    val pad = c.u * 0.06f
    if (wide) {
        Row(Modifier.fillMaxSize().padding(start = pad, end = pad, top = pad, bottom = c.u * 0.12f), horizontalArrangement = Arrangement.spacedBy(pad)) {
            Art(c, Modifier.fillMaxHeight().aspectRatio1(), c.u * 0.04f)
            Box(Modifier.weight(1f).fillMaxHeight()) { CardInfo(c, Modifier.align(Alignment.Center)) }
        }
    } else if (c.glass) {
        Column(Modifier.fillMaxSize().padding(start = c.u * 0.074f, end = c.u * 0.074f, top = c.u * 0.09f, bottom = c.u * 0.14f)) {
            Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Art(c, Modifier.fillMaxWidth().aspectRatio1(), c.u * 0.034f)
            }
            Spacer(Modifier.height(c.u * 0.06f))
            CardInfo(c, Modifier)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(start = pad, end = pad, top = pad, bottom = c.u * 0.11f), verticalArrangement = Arrangement.spacedBy(c.u * 0.03f)) {
            Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(c.u * 0.02f)) {
                Art(c, Modifier.weight(1f).aspectRatio1(), c.u * 0.04f)
                Column(Modifier.align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(c.u * 0.035f)) {
                    listOf(R.drawable.favorite_border, R.drawable.music_note, R.drawable.router_antenna).forEach {
                        Icon(painterResource(it), null, tint = Ink.copy(alpha = 0.8f), modifier = Modifier.size(c.u * 0.04f))
                    }
                }
            }
            CardInfo(c, Modifier)
        }
    }
}

private fun Modifier.aspectRatio1() = this.aspectRatio(1f)

@Composable
private fun LiveBars(c: PreviewCtx, modifier: Modifier) {
    Canvas(modifier.size(c.u * 0.07f, c.u * 0.04f)) {
        val t = c.pos.value / 160f
        val n = 9
        val bw = size.width / (n * 2 - 1)
        repeat(n) { i ->
            val f = if (c.playing) 0.25f + 0.75f * abs(sin(t + i * 0.9f)) else 0.3f
            val bh = size.height * f
            drawRoundRect(Ink.copy(alpha = 0.85f), Offset(i * bw * 2, (size.height - bh) / 2), Size(bw, bh), CornerRadius(bw / 2))
        }
    }
}

@Composable
private fun FastIcon(size: Dp, color: Color, forward: Boolean, dim: Boolean = false) {
    Canvas(Modifier.size(size).then(if (dim) Modifier.blur(size * 0.045f, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier)) {
        val w = this.size.width
        val h = this.size.height
        val st = Stroke(width = w * 0.12f, join = StrokeJoin.Round)
        fun x(v: Float) = if (forward) v * w else w - v * w
        listOf(0.1f to 0.5f, 0.48f to 0.88f).forEach { (a, b) ->
            val path = Path().apply {
                moveTo(x(a), h * 0.14f); lineTo(x(b), h * 0.5f); lineTo(x(a), h * 0.86f); close()
            }
            if (dim) {
                // Abu-abu cair: isi gradasi kaca + blur bawaan Compose (Modifier.blur).
                drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = 0.78f), color.copy(alpha = 0.42f))), style = Fill)
                drawPath(path, color.copy(alpha = 0.5f), style = st)
            } else {
                drawPath(path, color, style = Fill)
                drawPath(path, color, style = st)
            }
        }
    }
}

@Composable
private fun CardInfo(c: PreviewCtx, modifier: Modifier) {
    val total = if (c.songDurationMs > 0) c.songDurationMs else c.startMs + c.durMs
    val secs = remember(c.pos) { derivedStateOf { c.pos.value / 1000 } }
    // iOS 26: elemen utama (judul, lirik, kontrol) besar, elemen sekunder (waktu, volume, ikon) kecil.
    val k = if (c.glass && c.w <= c.h) 1.3f else 1f
    val bar = c.u * 0.019f
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(c.title, color = Ink, fontSize = c.fs(0.048f * k), fontFamily = c.family, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.artists, color = Ink.copy(alpha = 0.55f), fontSize = c.fs(0.044f * k), fontFamily = c.family, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LiveBars(c, Modifier.padding(top = c.u * 0.012f))
        }
        Spacer(Modifier.height(c.u * 0.04f * k))
        val line = c.display.getOrNull(if (c.canSync) c.activeIdx.value else 0)
        if (line != null) {
            if (c.canSync) {
                Crossfade(line, animationSpec = tween(220), label = "cardLine") {
                    KaraokeText(it, c.pos, c.lineStyle(0.04f * k), c.center, karaoke = true)
                }
            } else {
                KaraokeText(line, c.pos, c.lineStyle(0.04f * k), c.center, karaoke = false)
            }
        }
        Spacer(Modifier.height(c.u * 0.045f * k))
        Box(
            Modifier.fillMaxWidth().height(bar).clip(CircleShape).background(Ink.copy(alpha = 0.3f))
                .drawBehind {
                    val f = (c.pos.value.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    drawRect(Ink, size = Size(size.width * f, size.height))
                },
        )
        Spacer(Modifier.height(c.u * 0.014f))
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(secs.value * 1000), color = Ink.copy(alpha = 0.55f), fontSize = c.fs(0.026f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("-" + fmtTime(total - secs.value * 1000), color = Ink.copy(alpha = 0.55f), fontSize = c.fs(0.026f), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(c.u * 0.03f * k))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FastIcon(c.u * 0.11f * k, Color(0xFFC4C4CA), forward = false, dim = true)
            Spacer(Modifier.width(c.u * 0.09f * k))
            Icon(painterResource(if (c.playing) R.drawable.ios_pause else R.drawable.ios_play), null, tint = Ink, modifier = Modifier.size(c.u * 0.15f * k))
            Spacer(Modifier.width(c.u * 0.09f * k))
            FastIcon(c.u * 0.11f * k, Ink, forward = true)
        }
        Spacer(Modifier.height(c.u * 0.04f * k))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.volume_down), null, tint = Ink.copy(alpha = 0.6f), modifier = Modifier.size(c.u * 0.042f))
            Box(
                Modifier.weight(1f).padding(horizontal = c.u * 0.03f).height(bar).clip(CircleShape)
                    .background(Ink.copy(alpha = 0.3f))
                    .drawBehind { drawRect(Ink, size = Size(size.width * 0.62f, size.height)) },
            )
            Icon(painterResource(R.drawable.volume_up), null, tint = Ink.copy(alpha = 0.6f), modifier = Modifier.size(c.u * 0.042f))
        }
    }
}
