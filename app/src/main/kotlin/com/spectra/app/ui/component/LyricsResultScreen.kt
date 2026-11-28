/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.spectra.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

private val Bg = Color(0xFF050506)

/**
 * Layar hasil: preview besar (video bisa diputar), tombol bagikan, Ubah / Selesai.
 * File sementara dihapus saat layar ini keluar dari komposisi (Ubah, Selesai, atau Back).
 */
@Composable
internal fun LyricsResultScreen(result: RenderResult, onEdit: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    DisposableEffect(result.file) { onDispose { runCatching { result.file.delete() } } }

    var saved by remember { mutableStateOf(false) }
    val title = stringResource(if (result.isVideo) R.string.result_video_ready else R.string.result_image_ready)

    fun share(pkg: String?) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", result.file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = if (result.isVideo) "video/mp4" else "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (pkg != null) setPackage(pkg)
            }
            context.startActivity(if (pkg == null) Intent.createChooser(send, title) else send)
        } catch (e: Exception) {
            Timber.e(e, "Bagikan gagal")
            Toast.makeText(context, context.getString(R.string.result_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun save() {
        if (saved) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(context, context.getString(R.string.result_save_needs_q), Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { saveToGallery(context, result) }.exceptionOrNull() }
            if (err == null) {
                saved = true
                Toast.makeText(context, context.getString(R.string.result_saved_toast), Toast.LENGTH_SHORT).show()
            } else {
                Timber.e(err, "Simpan ke galeri gagal")
                Toast.makeText(context, context.getString(R.string.result_save_failed, err.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Hanya aplikasi yang terpasang.
    val targets = remember {
        listOf(
            R.string.result_share_wa to "com.whatsapp",
            R.string.result_share_wa_business to "com.whatsapp.w4b",
            R.string.result_share_instagram to "com.instagram.android",
        ).filter { context.packageManager.getLaunchIntentForPackage(it.second) != null }
    }
    val chips = buildList<Pair<String, () -> Unit>> {
        targets.forEach { (label, pkg) -> add(context.getString(label) to { share(pkg) }) }
        add(context.getString(if (saved) R.string.result_saved else R.string.result_save) to { save() })
        add(context.getString(R.string.result_share_more) to { share(null) })
    }

    Dialog(
        onDismissRequest = onEdit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 24.dp.toPx()
                },
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                val r = if (result.width > 0 && result.height > 0) result.width.toFloat() / result.height else 9f / 16f
                val w = minOf(maxWidth, maxHeight * r)
                val shape = RoundedCornerShape(24.dp)
                Box(
                    Modifier.size(w, w / r).clip(shape).background(Color.White.copy(alpha = 0.05f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f), shape),
                ) {
                    if (result.isVideo) VideoPreview(result.file) else ImagePreview(result.file)
                }
            }

            Text(
                title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            result.note?.let {
                Text(
                    it, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEachIndexed { i, (label, action) -> ShareChip(label, primary = i == 0, onClick = action) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    stringResource(R.string.result_edit),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 12.dp),
                )
                Box(Modifier.weight(1f))
                Text(
                    stringResource(R.string.result_done),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDone).padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ShareChip(label: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chipScale",
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(50.dp)
            .clip(shape)
            .background(if (primary) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.08f))
            .then(if (primary) Modifier else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.14f), shape))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, maxLines = 1, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = if (primary) Color.Black else Color.White,
        )
    }
}

@Composable
private fun ImagePreview(file: File) {
    var bmp by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
    }
    bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
}

/** Pemutar video sederhana: MediaPlayer + TextureView (bisa di-clip membulat). Ketuk untuk putar/jeda. */
@Composable
private fun VideoPreview(file: File) {
    val mp = remember { MediaPlayer() }
    var surface by remember { mutableStateOf<Surface?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(surface) {
        val s = surface ?: return@LaunchedEffect
        try {
            mp.setSurface(s)
            if (!prepared) {
                mp.setDataSource(file.absolutePath)
                mp.isLooping = true
                mp.setOnPreparedListener { it.start(); prepared = true; playing = true }
                mp.prepareAsync()
            }
        } catch (e: Exception) {
            Timber.e(e, "Preview video gagal")
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { mp.release() } } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { surface = Surface(st) }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { mp.setSurface(null) }
                        surface?.release()
                        surface = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
    )
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = source, indication = null) {
            if (!prepared) return@clickable
            runCatching {
                if (mp.isPlaying) { mp.pause(); playing = false } else { mp.start(); playing = true }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val a by animateFloatAsState(if (playing) 0f else 1f, tween(160), label = "playIcon")
        if (a > 0.01f) {
            Box(
                Modifier.size(64.dp).graphicsLayer { alpha = a; scaleX = 0.85f + 0.15f * a; scaleY = 0.85f + 0.15f * a }
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.play), null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

/** Simpan lewat MediaStore (Android 10+), tanpa permission penyimpanan. */
private fun saveToGallery(context: Context, result: RenderResult) {
    val resolver = context.contentResolver
    val video = result.isVideo
    val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, result.file.name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val root = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$root/Spectra")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(collection, values) ?: error("MediaStore menolak membuat berkas")
    try {
        resolver.openOutputStream(uri)?.use { out -> result.file.inputStream().use { it.copyTo(out) } }
            ?: error("Tidak bisa menulis ke galeri")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
}
