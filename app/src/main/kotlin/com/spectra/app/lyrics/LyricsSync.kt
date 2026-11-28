package com.spectra.app.lyrics

object LyricsSync {
    const val INSTRUMENTAL_TEXT = "..."

    fun mainLines(lines: List<LyricsEntry>): List<LyricsEntry> =
        lines.filter { !it.isBackground && it.text.isNotBlank() }.ifEmpty { lines }

    fun index(
        lines: List<LyricsEntry>,
        positionMs: Long,
    ): Int = if (lines.isEmpty()) -1 else LyricsUtils.findCurrentLineIndex(lines, positionMs)

    fun islandText(
        lines: List<LyricsEntry>,
        positionMs: Long,
    ): String? {
        if (lines.isEmpty()) return null
        val i = index(lines, positionMs)
        if (i < 0) return INSTRUMENTAL_TEXT
        return lines[i].text.takeIf { it.isNotBlank() }
    }
}
