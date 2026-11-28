package com.spectra.app.ui.lockscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** Menyalin gambar pilihan pengguna ke penyimpanan privat aplikasi agar tetap ada setelah restart. */
object LockScreenWallpaperStore {
    private const val FILE_NAME = "lockscreen_wallpaper.jpg"
    private const val MAX_EDGE = 2048

    fun import(context: Context, uri: Uri): String? =
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val bitmap =
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return null
            val file = File(context.filesDir, FILE_NAME)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
}
