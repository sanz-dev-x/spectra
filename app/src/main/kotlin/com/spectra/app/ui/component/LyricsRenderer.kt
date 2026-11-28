@file:Suppress("DEPRECATION")

/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spectra.app.R
import com.spectra.app.lyrics.LyricsEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import com.spectra.app.utils.SpectraLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/** Semua yang dibutuhkan untuk menggambar ulang preview editor pada resolusi keluaran. */
internal class LyricsRenderSpec(
    val style: EditorStyle,
    val ratio: EditorRatio,
    val bg: EditorBg,
    val family: FontFamily,
    val weight: EditorWeight,
    val align: EditorAlign,
    val display: List<LyricsEntry>,
    val canSync: Boolean,
    val title: String,
    val artists: String,
    val art: ImageBitmap?,
    val base: Color,
    val startMs: Long,
    val durMs: Long,
    val songDurationMs: Long,
    /** Lebar preview di editor (dp). Dipakai agar skala keluaran sama dengan preview. */
    val previewWidthDp: Float,
) {
    val width: Int get() = when (ratio) { EditorRatio.R16_9 -> 1920; else -> 1080 }
    val height: Int get() = when (ratio) {
        EditorRatio.R1_1 -> 1080
        EditorRatio.R4_5 -> 1350
        EditorRatio.R9_16 -> 1920
        EditorRatio.R16_9 -> 1080
    }
}

internal class RenderResult(
    val file: File,
    val isVideo: Boolean,
    val note: String?,
    val width: Int = 0,
    val height: Int = 0,
)

private const val AVC = MediaFormat.MIMETYPE_VIDEO_AVC

private fun cacheDir(context: Context): File =
    File(context.cacheDir, "lyrics_share").apply { mkdirs() }

private fun clearOld(dir: File) {
    dir.listFiles()?.forEach { runCatching { it.delete() } }
}

// ───────────────────────── Host Compose di luar layar ─────────────────────────

@Composable
private fun RenderHostContent(spec: LyricsRenderSpec, pos: State<Long>, playing: Boolean) {
    val outer = LocalDensity.current
    val pxPerDp = spec.width / spec.previewWidthDp.coerceAtLeast(1f)
    CompositionLocalProvider(LocalDensity provides Density(pxPerDp, outer.fontScale)) {
        val active = remember(spec) {
            derivedStateOf {
                if (!spec.canSync) -1 else spec.display.indexOfLast { it.time <= pos.value }.coerceAtLeast(0)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LyricsPreview(
                style = spec.style, ratio = spec.ratio, bg = spec.bg, family = spec.family, weight = spec.weight,
                align = spec.align, display = spec.display, activeIdx = active, pos = pos, canSync = spec.canSync,
                title = spec.title, artists = spec.artists, art = spec.art, base = spec.base, playing = playing,
                startMs = spec.startMs, durMs = spec.durMs, songDurationMs = spec.songDurationMs,
                fullBleed = true,
            )
        }
    }
}

/** ComposeView berukuran piksel keluaran, dipasang di luar layar; composable-nya sama dengan preview. */
private class RenderHost(val activity: Activity, val spec: LyricsRenderSpec, playing: Boolean) {
    val pos = mutableLongStateOf(spec.startMs)
    val view = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { RenderHostContent(spec, pos, playing) }
    }

    fun attach() {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.addView(view, FrameLayout.LayoutParams(spec.width, spec.height))
        view.translationX = -(spec.width + 200f)
    }

    fun detach() {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    /** Gambar view ke surface (hardware canvas, jadi blur/RenderEffect ikut tergambar). */
    fun drawTo(surface: Surface) {
        val c = surface.lockHardwareCanvas()
        try {
            view.draw(c)
        } finally {
            surface.unlockCanvasAndPost(c)
        }
    }
}

private suspend fun warmUp() {
    delay(700) // beri waktu komposisi + animasi scroll awal selesai
    repeat(3) { withFrameNanos { } }
}

// ───────────────────────────── Gambar ─────────────────────────────

internal suspend fun renderLyricsImage(
    activity: Activity,
    spec: LyricsRenderSpec,
    posMs: Long,
    playing: Boolean,
): RenderResult {
    val dir = cacheDir(activity).also { clearOld(it) }
    val out = File(dir, "spectra_lyrics_${System.currentTimeMillis()}.png")
    val host = RenderHost(activity, spec, playing)
    val reader = if (Build.VERSION.SDK_INT >= 29) {
        ImageReader.newInstance(
            spec.width, spec.height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
    } else ImageReader.newInstance(spec.width, spec.height, PixelFormat.RGBA_8888, 2)
    try {
        host.attach()
        host.pos.longValue = posMs
        Snapshot.sendApplyNotifications()
        warmUp()
        val got = CompletableDeferred<Unit>()
        reader.setOnImageAvailableListener({ got.complete(Unit) }, Handler(Looper.getMainLooper()))
        host.drawTo(reader.surface)
        got.await()
        val bmp = (reader.acquireLatestImage() ?: error("Frame gambar kosong")).use { image ->
            val plane = image.planes[0]
            val rowPx = plane.rowStride / plane.pixelStride
            val full = Bitmap.createBitmap(rowPx, spec.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer)
            if (rowPx == spec.width) full else Bitmap.createBitmap(full, 0, 0, spec.width, spec.height).also { full.recycle() }
        }
        withContext(Dispatchers.IO) {
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bmp.recycle()
        return RenderResult(out, isVideo = false, note = null, width = spec.width, height = spec.height)
    } catch (e: Throwable) {
        out.delete()
        throw e
    } finally {
        host.detach()
        reader.close()
    }
}

// ───────────────────────────── Video ─────────────────────────────

/**
 * Muxer dengan audio yang di-antre: audio di-encode paralel dan disimpan di memori sampai track video
 * terdaftar, jadi video tidak pernah menunggu audio lebih dari sekadar format AAC-nya siap.
 */
private class MuxerHub(path: String, private val expectAudio: Boolean) {
    private class Sample(val data: ByteArray, val pts: Long, val flags: Int)

    private val muxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val lock = Object()
    private val queue = ArrayList<Sample>()
    private var audioFormat: MediaFormat? = null
    private var audioDropped = !expectAudio
    private var videoTrack = -1
    private var audioTrack = -1
    private var started = false
    private var finished = false

    fun setAudioFormat(f: MediaFormat) = synchronized(lock) {
        if (!started && !audioDropped) audioFormat = f
        lock.notifyAll()
    }

    /** Buang audio yang sudah diantre supaya jalur cadangan bisa mulai bersih. false = muxer sudah jalan. */
    fun resetAudio(): Boolean = synchronized(lock) {
        if (started) return false
        audioFormat = null
        queue.clear()
        true
    }

    fun dropAudio() = synchronized(lock) {
        if (!started) { audioDropped = true; audioFormat = null; queue.clear() }
        lock.notifyAll()
    }

    /** Daftarkan track video, tunggu format audio (maks 20 dtk), lalu mulai muxer. false = dibatalkan. */
    fun addVideoTrack(f: MediaFormat, cancelled: () -> Boolean): Boolean = synchronized(lock) {
        videoTrack = muxer.addTrack(f)
        val deadline = System.currentTimeMillis() + 20_000
        while (!audioDropped && audioFormat == null) {
            if (cancelled()) return false
            if (System.currentTimeMillis() > deadline) { audioDropped = true; queue.clear(); break }
            lock.wait(20)
        }
        audioFormat?.let { audioTrack = muxer.addTrack(it) }
        muxer.start()
        started = true
        val info = MediaCodec.BufferInfo()
        for (smp in queue) writeAudioLocked(smp, info)
        queue.clear()
        lock.notifyAll()
        true
    }

    private fun writeAudioLocked(smp: Sample, info: MediaCodec.BufferInfo) {
        if (audioTrack < 0) return
        info.set(0, smp.data.size, smp.pts, smp.flags)
        muxer.writeSampleData(audioTrack, ByteBuffer.wrap(smp.data), info)
    }

    fun queueAudio(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (audioDropped || finished) return
            val data = ByteArray(info.size)
            buf.get(data)
            val smp = Sample(data, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
            if (started) writeAudioLocked(smp, MediaCodec.BufferInfo()) else queue.add(smp)
        }
    }

    fun writeVideo(buf: ByteBuffer, info: MediaCodec.BufferInfo) = synchronized(lock) {
        if (started && !finished) muxer.writeSampleData(videoTrack, buf, info)
    }

    fun finish() = synchronized(lock) {
        if (!finished) {
            finished = true
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }
}

private fun drainVideo(enc: MediaCodec, hub: MuxerHub, cancelled: () -> Boolean) {
    val info = MediaCodec.BufferInfo()
    var ready = false
    var base = -1L
    while (!cancelled()) {
        val i = enc.dequeueOutputBuffer(info, 10_000)
        if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (!hub.addVideoTrack(enc.outputFormat, cancelled)) return
            ready = true
        } else if (i >= 0) {
            val buf = enc.getOutputBuffer(i)
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
            if (info.size > 0 && buf != null && ready) {
                if (base < 0) base = info.presentationTimeUs
                info.presentationTimeUs -= base
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                hub.writeVideo(buf, info)
            }
            enc.releaseOutputBuffer(i, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }
}

private fun firstAudioTrack(ex: MediaExtractor): Int {
    for (i in 0 until ex.trackCount) {
        if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i
    }
    return -1
}

private fun probeAudio(ctx: Context, uri: Uri): Boolean {
    val ex = MediaExtractor()
    return try {
        ex.setDataSource(ctx, uri, null)
        val t = firstAudioTrack(ex)
        if (t < 0) return false
        val f = ex.getTrackFormat(t)
        val ch = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        ch in 1..2
    } catch (e: Exception) {
        false
    } finally {
        ex.release()
    }
}

/** Potong [startUs, startUs+durUs] dari sumber, decode ke PCM 16-bit, encode AAC-LC. true = berhasil penuh. */
/** Fade singkat di awal (15 ms) dan akhir (80 ms) potongan supaya tidak ada bunyi klik/pop. PCM 16-bit little-endian. */
private fun applyEdgeFade(pcm: ByteArray, ch: Int, firstFrame: Long, totalFrames: Long, sr: Int) {
    val inN = sr * 15L / 1000
    val outN = sr * 80L / 1000
    val frames = pcm.size / (2 * ch)
    for (f in 0 until frames) {
        val idx = firstFrame + f
        var g = 1f
        if (idx < inN) g = idx.toFloat() / inN
        val left = totalFrames - idx
        if (left < outN) g = minOf(g, (left.coerceAtLeast(0)).toFloat() / outN)
        if (g >= 1f) continue
        for (c in 0 until ch) {
            val o = (f * ch + c) * 2
            val v = (pcm[o].toInt() and 0xFF) or (pcm[o + 1].toInt() shl 8)
            val r = (v.toShort() * g).toInt()
            pcm[o] = r.toByte()
            pcm[o + 1] = (r shr 8).toByte()
        }
    }
}

private fun passthroughAudio(ctx: Context, uri: Uri, startUs: Long, durUs: Long, hub: MuxerHub, cancelled: () -> Boolean): Boolean {
    val ex = MediaExtractor()
    return try {
        ex.setDataSource(ctx, uri, null)
        val ti = firstAudioTrack(ex)
        if (ti < 0) return false
        val f = ex.getTrackFormat(ti)
        if (f.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) return false
        ex.selectTrack(ti)
        ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val cap = if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024) else 256 * 1024
        val buf = ByteBuffer.allocate(cap)
        val info = MediaCodec.BufferInfo()
        val endUs = startUs + durUs
        hub.setAudioFormat(f)
        var n = 0
        while (!cancelled()) {
            buf.clear()
            val size = ex.readSampleData(buf, 0)
            if (size < 0) break
            val t = ex.sampleTime
            if (t > endUs) break
            if (t >= startUs) {
                info.set(0, size, t - startUs, 0)
                buf.position(0)
                buf.limit(size)
                hub.queueAudio(buf, info)
                n++
            }
            ex.advance()
        }
        n > 0
    } catch (e: Exception) {
        SpectraLog.e("Render", "passthroughAudio gagal", e)
        false
    } finally {
        ex.release()
    }
}

/** Utama: decode + encode ulang (ada fade, trim presisi). Cadangan: salin AAC apa adanya. Gagal keduanya: tanpa audio. */
private fun encodeAudio(ctx: Context, uri: Uri, startUs: Long, durUs: Long, hub: MuxerHub, cancelled: () -> Boolean): Boolean {
    if (transcodeAudio(ctx, uri, startUs, durUs, hub, cancelled)) return true
    if (cancelled()) return false
    if (hub.resetAudio() && passthroughAudio(ctx, uri, startUs, durUs, hub, cancelled)) return true
    if (!cancelled()) hub.dropAudio()
    return false
}

private fun transcodeAudio(ctx: Context, uri: Uri, startUs: Long, durUs: Long, hub: MuxerHub, cancelled: () -> Boolean): Boolean {
    val ex = MediaExtractor()
    var dec: MediaCodec? = null
    var enc: MediaCodec? = null
    var track = -1
    try {
        ex.setDataSource(ctx, uri, null)
        val ti = firstAudioTrack(ex)
        if (ti < 0) { return false }
        val inFmt = ex.getTrackFormat(ti)
        ex.selectTrack(ti)
        ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        dec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
        runCatching { inFmt.setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT) }
        dec.configure(inFmt, null, null, 0)
        dec.start()

        val endUs = startUs + durUs
        val dInfo = MediaCodec.BufferInfo()
        val eInfo = MediaCodec.BufferInfo()
        val pending = ArrayDeque<ByteArray>()
        var headOff = 0
        var sr = 0
        var ch = 0
        var pcmFloat = false
        var inEos = false
        var decEos = false
        var encEosSent = false
        var done = false
        var samplesOut = 0L
        var framesQueued = 0L

        while (!done) {
            if (cancelled()) return false
            if (decEos && enc == null) { SpectraLog.e("Render", "decoder tidak menghasilkan audio", null); return false }
            var busy = false
            if (!inEos) {
                val i = dec.dequeueInputBuffer(0)
                if (i >= 0) {
                    val b = dec.getInputBuffer(i)!!
                    val n = ex.readSampleData(b, 0)
                    val t = ex.sampleTime
                    if (n < 0 || t > endUs) {
                        dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inEos = true
                    } else {
                        dec.queueInputBuffer(i, 0, n, t, 0); ex.advance()
                    }
                    busy = true
                }
            }
            if (!decEos) {
                val o = dec.dequeueOutputBuffer(dInfo, 0)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val pcmEnc = if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    else android.media.AudioFormat.ENCODING_PCM_16BIT
                    pcmFloat = pcmEnc == android.media.AudioFormat.ENCODING_PCM_FLOAT
                    if (ch !in 1..2 || (!pcmFloat && pcmEnc != android.media.AudioFormat.ENCODING_PCM_16BIT)) {
                        SpectraLog.e("Render", "audio format tidak didukung ch=$ch pcm=$pcmEnc", null)
                        return false
                    }
                    val ef = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_BIT_RATE, if (ch == 1) 128_000 else 192_000)
                    }
                    enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    enc.configure(ef, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    enc.start()
                    busy = true
                } else if (o >= 0) {
                    val b = dec.getOutputBuffer(o)
                    if (dInfo.size > 0 && b != null && sr > 0) {
                        val bpf = (if (pcmFloat) 4 else 2) * ch
                        val total = dInfo.size / bpf
                        val pts = dInfo.presentationTimeUs
                        val skip = if (pts < startUs) ((startUs - pts) * sr / 1_000_000L).toInt().coerceAtMost(total) else 0
                        val remainUs = endUs - (pts + skip * 1_000_000L / sr)
                        val take = if (remainUs <= 0) 0 else min(total - skip, (remainUs * sr / 1_000_000L).toInt())
                        if (take > 0) {
                            b.position(dInfo.offset + skip * bpf)
                            if (pcmFloat) {
                                val fb = b.order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
                                val arr = ByteArray(take * ch * 2)
                                val sb = java.nio.ByteBuffer.wrap(arr).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                repeat(take * ch) { sb.put((fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
                                pending.addLast(arr)
                            } else {
                                val arr = ByteArray(take * bpf)
                                b.get(arr)
                                pending.addLast(arr)
                            }
                            applyEdgeFade(pending.last(), ch, framesQueued, durUs * sr / 1_000_000L, sr)
                            framesQueued += take
                        }
                    }
                    decEos = dInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    dec.releaseOutputBuffer(o, false)
                    busy = true
                }
            }
            val e = enc
            if (e != null) {
                if (pending.isNotEmpty() || (decEos && !encEosSent)) {
                    val i = e.dequeueInputBuffer(0)
                    if (i >= 0) {
                        val b = e.getInputBuffer(i)!!
                        b.clear()
                        val limit = b.capacity() - b.capacity() % (2 * ch)
                        var n = 0
                        while (pending.isNotEmpty() && n < limit) {
                            val h = pending.first()
                            val c = min(h.size - headOff, limit - n)
                            b.put(h, headOff, c)
                            n += c; headOff += c
                            if (headOff >= h.size) { pending.removeFirst(); headOff = 0 }
                        }
                        val ptsUs = samplesOut * 1_000_000L / sr
                        if (n > 0) {
                            e.queueInputBuffer(i, 0, n, ptsUs, 0)
                            samplesOut += n / (2 * ch)
                        } else {
                            e.queueInputBuffer(i, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            encEosSent = true
                        }
                        busy = true
                    }
                }
                val o = e.dequeueOutputBuffer(eInfo, 0)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    hub.setAudioFormat(e.outputFormat)
                    track = 0
                    busy = true
                } else if (o >= 0) {
                    val b = e.getOutputBuffer(o)
                    if (eInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) eInfo.size = 0
                    if (eInfo.size > 0 && b != null && track >= 0) {
                        b.position(eInfo.offset)
                        b.limit(eInfo.offset + eInfo.size)
                        hub.queueAudio(b, eInfo)
                    }
                    e.releaseOutputBuffer(o, false)
                    if (eInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                    busy = true
                }
            }
            if (!busy) Thread.sleep(2)
        }
        return true
    } catch (e: Exception) {
        SpectraLog.e("Render", "encodeAudio gagal", e)
        return false
    } finally {
        runCatching { dec?.stop() }; runCatching { dec?.release() }
        runCatching { enc?.stop() }; runCatching { enc?.release() }
        ex.release()
    }
}

/**
 * Jembatan frame -> encoder. hwui gagal membuat EGL surface untuk input surface MediaCodec di sebagian GPU
 * (abort "getFrame() called on a context with no surface"), jadi frame digambar ke ImageReader dulu lalu
 * dikirim ke encoder lewat EGL sendiri dengan config EGL_RECORDABLE_ANDROID. Semua panggilan harus di satu thread.
 */
private class GlEncoderSurface(surface: Surface) {
    private val dpy: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val ctx: EGLContext
    private val surf: EGLSurface
    private val program: Int
    private val tex: Int
    private val quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    init {
        val ver = IntArray(2)
        check(EGL14.eglInitialize(dpy, ver, 0, ver, 1)) { "EGL gagal diinisialisasi" }
        val attrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE,
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        check(EGL14.eglChooseConfig(dpy, attrs, 0, cfgs, 0, 1, n, 0) && n[0] > 0) { "Config EGL untuk encoder tidak ada" }
        ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(ctx != EGL14.EGL_NO_CONTEXT) { "Context EGL gagal dibuat" }
        surf = EGL14.eglCreateWindowSurface(dpy, cfgs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(surf != EGL14.EGL_NO_SURFACE) { "EGL surface encoder gagal dibuat" }
        check(EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) { "eglMakeCurrent gagal" }

        program = link(
            "attribute vec4 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){ gl_Position = aPos; vUv = aUv; }",
            "precision mediump float; varying vec2 vUv; uniform sampler2D uTex; void main(){ gl_FragColor = texture2D(uTex, vUv); }",
        )
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        tex = t[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun shader(type: Int, src: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, src)
        GLES20.glCompileShader(id)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "Shader gagal: " + GLES20.glGetShaderInfoLog(id) }
        return id
    }

    private fun link(vs: String, fs: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Program GL gagal di-link" }
        return p
    }

    /** [uMax] memotong padding baris bila bitmap lebih lebar dari frame. */
    fun draw(bmp: Bitmap, uMax: Float, w: Int, h: Int, ptsNs: Long) {
        GLES20.glViewport(0, 0, w, h)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        quad.clear()
        quad.put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, uMax, 1f, -1f, 1f, 0f, 0f, 1f, 1f, uMax, 0f))
        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val aUv = GLES20.glGetAttribLocation(program, "aUv")
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(2)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGLExt.eglPresentationTimeANDROID(dpy, surf, ptsNs)
        check(EGL14.eglSwapBuffers(dpy, surf)) { "eglSwapBuffers gagal" }
    }

    fun release() {
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(dpy, surf)
        EGL14.eglDestroyContext(dpy, ctx)
        EGL14.eglTerminate(dpy)
    }

    private companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}

/**
 * Render video MP4 (H.264 + AAC bila ada audio). Frame diambil dari composable yang sama dengan
 * preview, digambar ke input surface encoder secara real-time (satu frame per vsync).
 * FPS: 120 hanya bila layar ≥110 Hz dan encoder mendukung; selain itu 60; terakhir 30.
 */
internal suspend fun renderLyricsVideo(
    activity: Activity,
    spec: LyricsRenderSpec,
    audioUri: Uri?,
    onProgress: (Float) -> Unit,
): RenderResult = coroutineScope {
    val job = coroutineContext[Job]!!
    val cancelled = { !job.isActive }
    val dir = cacheDir(activity).also { clearOld(it) }
    val out = File(dir, "spectra_lyrics_${System.currentTimeMillis()}.mp4")

    val audioOk = audioUri != null && withContext(Dispatchers.IO) { probeAudio(activity, audioUri) }
    var note: String? = when {
        audioUri == null -> activity.getString(R.string.editor_note_no_audio_source)
        !audioOk -> activity.getString(R.string.editor_note_audio_unreadable)
        else -> null
    }

    SpectraLog.i("Render", "video mulai ${spec.width}x${spec.height} style=${spec.style} bg=${spec.bg} dur=${spec.durMs}ms audio=${audioUri != null}/$audioOk")
    val enc = MediaCodec.createEncoderByType(AVC)
    val caps = enc.codecInfo.getCapabilitiesForType(AVC).videoCapabilities!!
    val refresh = (if (Build.VERSION.SDK_INT >= 30) activity.display?.refreshRate else null)
        ?: activity.windowManager.defaultDisplay.refreshRate
    fun supports(f: Int) = runCatching { caps.areSizeAndRateSupported(spec.width, spec.height, f.toDouble()) }.getOrDefault(false)
    val fps = when {
        supports(60) -> 60
        else -> 30
    }
    val bitrate = (spec.width.toLong() * spec.height * fps / 10).toInt()
        .coerceIn(caps.bitrateRange.lower, caps.bitrateRange.upper)
    val fmt = MediaFormat.createVideoFormat(AVC, spec.width, spec.height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    SpectraLog.i("Render", "encoder configure fps=$fps bitrate=$bitrate")
    enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val surface = enc.createInputSurface()
    enc.start()
    SpectraLog.i("Render", "encoder start")

    val hub = MuxerHub(out.absolutePath, audioOk)
    val host = RenderHost(activity, spec, playing = true)
    val audioFailed = AtomicBoolean(false)
    val glExec = Executors.newSingleThreadExecutor()
    val glDispatcher = glExec.asCoroutineDispatcher()
    var gl: GlEncoderSurface? = null
    val frameReady = Channel<Unit>(Channel.CONFLATED)
    val reader = if (Build.VERSION.SDK_INT >= 29) {
        ImageReader.newInstance(
            spec.width, spec.height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
    } else ImageReader.newInstance(spec.width, spec.height, PixelFormat.RGBA_8888, 2)
    reader.setOnImageAvailableListener({ frameReady.trySend(Unit) }, Handler(Looper.getMainLooper()))
    var frameBmp: Bitmap? = null
    var videoJob: Job? = null
    var audioJob: Job? = null
    var success = false
    try {
        videoJob = launch(Dispatchers.IO) { drainVideo(enc, hub, cancelled) }
        if (audioOk) {
            audioJob = launch(Dispatchers.IO) {
                val ok = encodeAudio(activity, audioUri!!, spec.startMs * 1000L, spec.durMs * 1000L, hub, cancelled)
                if (!ok && !cancelled()) audioFailed.set(true)
            }
        }
        gl = withContext(glDispatcher) { GlEncoderSurface(surface) }
        SpectraLog.i("Render", "EGL encoder siap")
        host.attach()
        SpectraLog.i("Render", "host attach")
        warmUp()
        SpectraLog.i("Render", "warmUp selesai, mulai frame")
        val aj = audioJob
        if (aj != null) kotlinx.coroutines.withTimeoutOrNull(15_000) { aj.join() }

        val durNs = spec.durMs * 1_000_000L
        var startNs = -1L
        var frames = 0
        var lastEl = 0L
        while (true) {
            ensureActive()
            val now = withFrameNanos { it }
            if (startNs < 0) startNs = now else {
                // frame yang disusun pada vsync sebelumnya: gambar ke ImageReader, lalu kirim ke encoder via EGL
                host.drawTo(reader.surface)
                withTimeout(3000) { frameReady.receive() }
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val plane = image.planes[0]
                        val rowPx = plane.rowStride / plane.pixelStride
                        val bmp = frameBmp?.takeIf { it.width == rowPx }
                            ?: Bitmap.createBitmap(rowPx, spec.height, Bitmap.Config.ARGB_8888).also { frameBmp = it }
                        plane.buffer.rewind()
                        bmp.copyPixelsFromBuffer(plane.buffer)
                        val uMax = spec.width.toFloat() / rowPx
                        val ts = lastEl
                        withContext(glDispatcher) { gl!!.draw(bmp, uMax, spec.width, spec.height, ts) }
                    } finally {
                        image.close()
                    }
                    if (++frames % 60 == 1) SpectraLog.i("Render", "frame $frames")
                }
            }
            val el = now - startNs
            if (el >= durNs) break
            lastEl = el
            host.pos.longValue = spec.startMs + el / 1_000_000L
            Snapshot.sendApplyNotifications()
            onProgress((el.toFloat() / durNs).coerceIn(0f, 1f))
        }
        SpectraLog.i("Render", "frame selesai total=$frames, tutup encoder")
        enc.signalEndOfInputStream()
        videoJob.join()
        audioJob?.join()
        if (audioOk && audioFailed.get()) note = activity.getString(R.string.editor_note_audio_failed)
        onProgress(1f)
        success = true
        SpectraLog.i("Render", "video selesai")
        RenderResult(out, isVideo = true, note = note, width = spec.width, height = spec.height)
    } finally {
        withContext(NonCancellable) {
            host.detach()
            videoJob?.cancelAndJoin()
            audioJob?.cancelAndJoin()
            runCatching { withContext(glDispatcher) { gl?.release() } }
            glExec.shutdown()
            runCatching { reader.close() }
            frameBmp?.recycle()
            runCatching { enc.stop() }
            runCatching { enc.release() }
            runCatching { surface.release() }
            hub.finish()
            if (!success) out.delete()
        }
    }
}

// ───────────────────────────── UI ─────────────────────────────

/** Panel progres di bawah editor: judul + persen, bar, dan tombol Batal. */
@Composable
internal fun RenderProgressPanel(label: String, progress: Float, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            "$label ${(progress * 100).toInt()}%",
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.18f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.cancel),
            color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.End).clip(CircleShape).clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}
