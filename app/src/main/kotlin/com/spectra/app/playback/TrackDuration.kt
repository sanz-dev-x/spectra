package com.spectra.app.playback

import androidx.media3.common.C

/** Durasi aktual dari player; null bila belum diketahui. */
fun validPlayerDurationMs(playerDurationMs: Long): Long? = playerDurationMs.takeIf { it != C.TIME_UNSET && it > 0L }

/** Durasi player adalah acuan; metadata hanya pengganti sementara sebelum player siap. */
fun resolveDurationMs(playerDurationMs: Long, metadataSeconds: Int?): Long =
    validPlayerDurationMs(playerDurationMs)
        ?: metadataSeconds?.takeIf { it > 0 }?.times(1000L)
        ?: 0L
