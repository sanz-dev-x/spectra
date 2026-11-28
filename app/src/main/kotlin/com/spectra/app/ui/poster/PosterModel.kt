/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.spectra.app.db.entities.Song
import com.spectra.app.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

const val POSTER_MIN_TRACKS = 1
const val POSTER_MAX_TRACKS = 5

data class PosterTrack(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSec: Int = -1,
)

fun Song.toPosterTrack() =
    PosterTrack(
        id = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        thumbnailUrl = thumbnailUrl,
        durationSec = song.duration,
    )

/** Status deteksi orang pada foto. Dipakai UI untuk menjelaskan apa yang sebenarnya terjadi. */
enum class PersonStatus { NO_PHOTO, DETECTING, FOUND, NOT_FOUND, UNAVAILABLE }

/** Semua bitmap milik satu foto. [cutout] null bila orang tidak terdeteksi. */
class PosterPhoto(
    val bitmap: Bitmap,
    val blurred: Bitmap,
    val cutout: Bitmap?,
    /** Masker pelindung kepala (resolusi kecil); null bila tidak tersedia. */
    val headGuard: Bitmap? = null,
)

class PosterScene(
    val tracks: List<PosterTrack>,
    val covers: List<Bitmap?>,
    val photo: PosterPhoto?,
    val logo: Bitmap?,
    val fallbackCover: Bitmap?,
)

/** Logo Spectra asli dari resource, dipotong ke batas piksel tidak transparan. Null bila aset tidak ada. */
fun loadSpectraLogo(context: Context): Bitmap? =
    runCatching {
        val src =
            BitmapFactory.decodeResource(
                context.resources,
                com.spectra.app.R.drawable.spectra_launcher_fg,
            ) ?: return@runCatching null
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var l = w
        var t = h
        var r = -1
        var btm = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 8) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > btm) btm = y
            }
        }
        if (r < l || btm < t) null else Bitmap.createBitmap(src, l, t, r - l + 1, btm - t + 1)
    }.getOrNull()

/** Artwork cadangan Spectra (ikon antrean yang dipakai daftar lagu) bila lagu tidak punya cover. */
fun loadFallbackCover(context: Context): Bitmap? =
    runCatching {
        val d = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, com.spectra.app.R.drawable.queue_music)
            ?: return@runCatching null
        val size = 600
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(32, 38, 48))
        val icon = (size * 0.5f).toInt()
        val off = (size - icon) / 2
        d.setBounds(off, off, off + icon, off + icon)
        d.setTint(android.graphics.Color.argb(200, 255, 255, 255))
        d.draw(c)
        bmp
    }.getOrNull()

suspend fun loadPosterCover(
    context: Context,
    url: String?,
): Bitmap? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url.resize(600, 600))
                    .allowHardware(false)
                    .size(600, 600)
                    .build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        }.getOrNull()
    }
}

/** Decode foto pilihan user dengan batas sisi terpanjang, mengikuti orientasi EXIF. */
suspend fun decodePosterPhoto(
    context: Context,
    uri: Uri,
    maxSide: Int = 2048,
): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val decoded =
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                } ?: return@runCatching null
            val orientation =
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            }
            var out = decoded
            if (!m.isIdentity) out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            val longest = max(out.width, out.height)
            if (longest > maxSide) {
                val r = maxSide / longest.toFloat()
                out = Bitmap.createScaledBitmap(out, (out.width * r).toInt().coerceAtLeast(1), (out.height * r).toInt().coerceAtLeast(1), true)
            }
            if (out.config == Bitmap.Config.ARGB_8888) out else out.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
    }

/** Blur kotak 3 lintasan pada versi kecil foto. Murah dan cukup halus untuk kaca & latar depth. */
fun blurForPoster(
    src: Bitmap,
    downscale: Int = 8,
    radius: Int = 5,
): Bitmap {
    val w = (src.width / downscale).coerceAtLeast(8)
    val h = (src.height / downscale).coerceAtLeast(8)
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    repeat(3) {
        boxPass(px, tmp, w, h, radius, true)
        boxPass(tmp, px, w, h, radius, false)
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

private fun boxPass(
    src: IntArray,
    dst: IntArray,
    w: Int,
    h: Int,
    r: Int,
    horizontal: Boolean,
) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val div = r * 2 + 1
    for (line in 0 until lines) {
        var sr = 0
        var sg = 0
        var sb = 0
        fun idx(i: Int) = if (horizontal) line * w + i.coerceIn(0, len - 1) else i.coerceIn(0, len - 1) * w + line
        for (i in -r..r) {
            val c = src[idx(i)]
            sr += (c shr 16) and 0xFF
            sg += (c shr 8) and 0xFF
            sb += c and 0xFF
        }
        for (i in 0 until len) {
            dst[if (horizontal) line * w + i else i * w + line] =
                (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val add = src[idx(i + r + 1)]
            val sub = src[idx(i - r)]
            sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            sb += (add and 0xFF) - (sub and 0xFF)
        }
    }
}
