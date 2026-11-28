package com.spectra.app.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.spectra.app.R
import com.spectra.app.ui.poster.loadPosterCover
import com.spectra.app.viewmodels.RecapSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val PosterWidth = 1080
private const val PosterHeight = 1920
private const val Gutter = 80f

fun recapToneColor(bitmap: Bitmap): Int {
    val px = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
    val hsv = FloatArray(3)
    Color.colorToHSV(px, hsv)
    hsv[1] = (hsv[1] * 1.1f).coerceAtMost(0.85f)
    hsv[2] = hsv[2].coerceIn(0.32f, 0.62f)
    return Color.HSVToColor(hsv)
}

private fun blend(
    a: Int,
    b: Int,
    t: Float,
): Int {
    val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
    val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
    val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    return Color.rgb(r, g, bl)
}

private fun textPaint(
    size: Float,
    color: Int,
    bold: Boolean = false,
    serif: Boolean = false,
    spacing: Float = 0f,
): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        letterSpacing = spacing
    }

private fun fit(
    text: String,
    paint: TextPaint,
    maxWidth: Float,
): String = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

private fun Canvas.drawCropped(
    bitmap: Bitmap?,
    dst: RectF,
    path: Path,
    placeholder: Paint,
) {
    save()
    clipPath(path)
    if (bitmap == null) {
        drawRect(dst, placeholder)
    } else {
        val scale = maxOf(dst.width() / bitmap.width, dst.height() / bitmap.height)
        val sw = (dst.width() / scale).toInt().coerceIn(1, bitmap.width)
        val sh = (dst.height() / scale).toInt().coerceIn(1, bitmap.height)
        val sx = (bitmap.width - sw) / 2
        val sy = (bitmap.height - sh) / 2
        drawBitmap(bitmap, Rect(sx, sy, sx + sw, sy + sh), dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
    restore()
}

private fun roundedPath(
    rect: RectF,
    radius: Float,
): Path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }

private fun circlePath(rect: RectF): Path = Path().apply { addCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, Path.Direction.CW) }

suspend fun buildRecapPoster(
    context: Context,
    summary: RecapSummary,
): Bitmap {
    val songThumbs = summary.songs.take(5).map { loadPosterCover(context, it.thumbnailUrl) }
    val artistThumbs = summary.artists.take(5).map { loadPosterCover(context, it.thumbnailUrl) }
    return withContext(Dispatchers.Default) { drawPoster(context, summary, songThumbs, artistThumbs) }
}

private fun drawPoster(
    context: Context,
    summary: RecapSummary,
    songThumbs: List<Bitmap?>,
    artistThumbs: List<Bitmap?>,
): Bitmap {
    val bitmap = Bitmap.createBitmap(PosterWidth, PosterHeight, Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    val white = Color.WHITE
    val base = songThumbs.firstOrNull()?.let { recapToneColor(it) } ?: Color.rgb(96, 34, 34)
    val bg =
        Paint().apply {
            shader =
                LinearGradient(
                    0f,
                    0f,
                    PosterWidth * 0.55f,
                    PosterHeight.toFloat(),
                    intArrayOf(blend(base, white, 0.06f), blend(base, Color.BLACK, 0.45f), blend(base, Color.BLACK, 0.88f)),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
        }
    c.drawRect(0f, 0f, PosterWidth.toFloat(), PosterHeight.toFloat(), bg)

    val appName = context.getString(R.string.app_name)
    c.drawText(context.getString(R.string.recap_poster_kicker, appName.uppercase()), Gutter, 170f, textPaint(34f, Color.argb(190, 255, 255, 255), bold = true, spacing = 0.22f))

    val title = context.getString(R.string.recap_hero_title, summary.year)
    val titlePaint = textPaint(124f, white, bold = true, serif = true)
    while (titlePaint.measureText(title) > PosterWidth - Gutter * 2 && titlePaint.textSize > 60f) titlePaint.textSize -= 4f
    c.drawText(title, Gutter, 310f, titlePaint)

    c.drawText(summary.totalMinutes.toString(), Gutter - 4f, 500f, textPaint(200f, white, bold = true))
    val sub = "${context.getString(R.string.recap_minutes_listened)} · ${context.getString(R.string.recap_songs_count, summary.songCount)}"
    c.drawText(sub, Gutter, 580f, textPaint(44f, Color.argb(200, 255, 255, 255)))

    val card = RectF(64f, 640f, PosterWidth - 64f, 1760f)
    c.drawRoundRect(card, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(30, 255, 255, 255) })
    c.drawRoundRect(card, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(46, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    })

    val placeholder = Paint().apply { color = Color.argb(40, 255, 255, 255) }
    val muted = Color.argb(178, 255, 255, 255)
    val rankX = 110f
    val thumbX = 170f
    val thumbSize = 72f
    val pitch = 88f
    val textX = thumbX + thumbSize + 24f
    val maxText = card.right - 56f - textX

    c.drawText(context.getString(R.string.recap_top_songs), rankX, 722f, textPaint(40f, white, bold = true))
    summary.songs.take(5).forEachIndexed { i, song ->
        val top = 750f + i * pitch
        c.drawText((i + 1).toString(), rankX, top + 46f, textPaint(34f, if (i == 0) Color.rgb(255, 99, 99) else muted, bold = true))
        val rect = RectF(thumbX, top, thumbX + thumbSize, top + thumbSize)
        c.drawCropped(songThumbs.getOrNull(i), rect, roundedPath(rect, 16f), placeholder)
        val tp = textPaint(36f, white, bold = true)
        c.drawText(fit(song.title, tp, maxText), textX, top + 32f, tp)
        val ap = textPaint(29f, muted)
        c.drawText(fit(song.artist, ap, maxText), textX, top + 64f, ap)
    }

    c.drawText(context.getString(R.string.recap_top_artists), rankX, 1262f, textPaint(40f, white, bold = true))
    summary.artists.take(5).forEachIndexed { i, artist ->
        val top = 1290f + i * pitch
        c.drawText((i + 1).toString(), rankX, top + 46f, textPaint(34f, if (i == 0) Color.rgb(255, 99, 99) else muted, bold = true))
        val rect = RectF(thumbX, top, thumbX + thumbSize, top + thumbSize)
        c.drawCropped(artistThumbs.getOrNull(i), rect, circlePath(rect), placeholder)
        val minutesText = context.getString(R.string.recap_minutes_short, artist.minutes)
        val mp = textPaint(30f, muted)
        val minutesWidth = mp.measureText(minutesText)
        c.drawText(minutesText, card.right - 56f - minutesWidth, top + 46f, mp)
        val np = textPaint(38f, white, bold = true)
        c.drawText(fit(artist.name, np, maxText - minutesWidth - 20f), textX, top + 48f, np)
    }

    val mark = BitmapFactory.decodeResource(context.resources, R.drawable.spectra_launcher_mark)
    if (mark != null) {
        val logoPaint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(white, PorterDuff.Mode.SRC_IN)
            }
        c.drawBitmap(mark, Rect(36, 166, 476, 366), RectF(Gutter, 1800f, Gutter + 132f, 1860f), logoPaint)
    }
    c.drawText(appName, Gutter + 160f, 1846f, textPaint(56f, white, bold = true))
    return bitmap
}

suspend fun shareRecap(
    context: Context,
    summary: RecapSummary,
) {
    val bitmap = buildRecapPoster(context, summary)
    val uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "recap").apply { mkdirs() }
            val file = File(dir, "recap_${summary.year}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        }
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${context.getString(R.string.recap_hero_title, summary.year)} · ${context.getString(R.string.app_name)}")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.recap_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
