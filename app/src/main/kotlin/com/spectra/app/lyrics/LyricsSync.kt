package com.spectra.app.lyrics

/**
 * Sinkronisasi lirik per kata untuk Dynamic Island dan lockscreen.
 * Kata baru dianggap "muncul" saat posisi lagu mencapai waktu mulai kata tersebut;
 * saat jeda instrumental (tanpa lirik) frame ditandai [Frame.instrumental].
 */
object LyricsSync {
    private const val GAP_MS = 1500L
    private const val TAIL_MS = 400L

    const val INSTRUMENTAL_TEXT = "..."

    data class Frame(
        /** Indeks baris aktif; -1 bila lagu belum mencapai baris pertama. */
        val index: Int,
        val instrumental: Boolean,
        /** Jumlah kata yang sudah dinyanyikan pada baris aktif ([Int.MAX_VALUE] bila baris tanpa waktu kata). */
        val sungWords: Int,
    )

    /** Hanya baris utama; vokal latar dibuang agar satu baris tampil satu kali. */
    fun mainLines(lines: List<LyricsEntry>): List<LyricsEntry> =
        lines.filter { !it.isBackground && it.text.isNotBlank() }.ifEmpty { lines }.sortedBy { it.time }

    private fun lineEndMs(lines: List<LyricsEntry>, index: Int): Long {
        val line = lines[index]
        val next = lines.getOrNull(index + 1)
        val words = line.words
        if (!words.isNullOrEmpty()) return (words.last().endTime * 1000).toLong()
        val estimate = line.time + maxOf(2500L, line.text.length * 90L)
        return if (next != null) minOf(next.time, estimate) else estimate
    }

    fun frame(lines: List<LyricsEntry>, positionMs: Long): Frame {
        if (lines.isEmpty()) return Frame(-1, false, 0)
        var index = -1
        for (i in lines.indices) {
            if (lines[i].time <= positionMs) index = i else break
        }
        if (index < 0) {
            return Frame(-1, lines.first().time - positionMs > GAP_MS, 0)
        }
        val next = lines.getOrNull(index + 1)
        val end = lineEndMs(lines, index)
        if (positionMs > end + TAIL_MS && (next == null || next.time - positionMs > GAP_MS)) {
            return Frame(index, true, 0)
        }
        val words = lines[index].words
        val sung =
            if (words.isNullOrEmpty()) Int.MAX_VALUE else words.count { it.startTime * 1000 <= positionMs }
        return Frame(index, false, sung)
    }

    /** Teks baris yang sudah dinyanyikan sampai kata ke-[sung]. */
    fun revealedText(line: LyricsEntry, sung: Int): String {
        val words = line.words
        if (words.isNullOrEmpty() || sung >= words.size) return line.text
        return buildString {
            words.take(sung).forEach { append(it.text); if (it.hasTrailingSpace) append(' ') }
        }.trim()
    }

    /** Teks satu baris untuk Dynamic Island: kata muncul bertahap, "..." saat instrumental. */
    fun islandText(lines: List<LyricsEntry>, positionMs: Long): String? {
        if (lines.isEmpty()) return null
        val f = frame(lines, positionMs)
        if (f.instrumental) return INSTRUMENTAL_TEXT
        if (f.index < 0) return null
        val revealed = revealedText(lines[f.index], f.sungWords)
        if (revealed.isNotBlank()) return revealed
        // Kata pertama baris baru belum tiba: tahan baris sebelumnya agar teks tidak berkedip hilang.
        return lines.getOrNull(f.index - 1)?.text?.takeIf { it.isNotBlank() }
    }
}
