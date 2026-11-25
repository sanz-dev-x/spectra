package com.spectra.app.playback.audio

/** Satu cakupan aktif pada satu waktu agar tidak ada aturan yang saling menimpa. */
enum class VolumeMemoryScope { OFF, SONG, ALBUM, PLAYLIST, DEVICE }

/** Penyimpanan volume per kunci, diserialisasi sebagai baris "kunci=nilai" dan dibatasi ukurannya. */
object VolumeMemory {
    private const val MAX_ENTRIES = 300

    fun parse(raw: String?): LinkedHashMap<String, Float> {
        val map = LinkedHashMap<String, Float>()
        raw?.lineSequence()?.forEach { line ->
            val i = line.lastIndexOf('=')
            if (i <= 0) return@forEach
            val v = line.substring(i + 1).toFloatOrNull() ?: return@forEach
            map[line.substring(0, i)] = v.coerceIn(0f, 1f)
        }
        return map
    }

    fun put(raw: String?, key: String, volume: Float): String {
        val map = parse(raw)
        map.remove(key)
        map[key] = volume.coerceIn(0f, 1f)
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
        return map.entries.joinToString("\n") { "${it.key.replace('\n', ' ')}=${it.value}" }
    }

    fun get(raw: String?, key: String): Float? = parse(raw)[key]
}
