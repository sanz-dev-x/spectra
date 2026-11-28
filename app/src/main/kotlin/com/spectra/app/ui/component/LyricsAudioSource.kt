/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import java.io.File

/**
 * Sumber audio untuk video lirik. Lagu lokal dipakai langsung; lagu streaming disalin dari cache
 * pemutar/unduhan (kunci cache = mediaId) ke file sementara supaya MediaExtractor bisa membacanya.
 */
@UnstableApi
internal fun resolveLyricsAudio(context: Context, mediaId: String?, direct: Uri?, export: (File) -> Long, vararg caches: Cache): Uri? {
    if (direct != null && (direct.scheme == "content" || direct.scheme == "file")) return direct
    if (mediaId.isNullOrBlank()) return null
    val dir = File(context.cacheDir, "lyrics_audio").apply { mkdirs() }
    dir.listFiles()?.forEach { runCatching { it.delete() } }
    val out = File(dir, "src_${mediaId.hashCode()}.bin")
    var best = runCatching { export(out) }.getOrDefault(0L)
    if (best > 0L) return out.toUri()
    out.delete()
    for (cache in caches) {
        val tmp = File(dir, "tmp_${System.nanoTime()}.bin")
        val n = runCatching { copyContiguous(cache, mediaId, tmp) }.getOrDefault(0L)
        if (n > best) {
            best = n
            out.delete()
            tmp.renameTo(out)
        } else tmp.delete()
    }
    return if (best > 0 && out.exists()) out.toUri() else null
}

@UnstableApi
private fun copyContiguous(cache: Cache, key: String, dst: File): Long {
    var expected = 0L
    dst.outputStream().buffered().use { os ->
        for (span in cache.getCachedSpans(key)) {
            val f = span.file ?: continue
            if (!span.isCached) continue
            if (span.position > expected) break
            val end = span.position + span.length
            if (end <= expected) continue
            f.inputStream().use { ins ->
                ins.skip(expected - span.position)
                ins.copyTo(os)
            }
            expected = end
        }
    }
    return expected
}
