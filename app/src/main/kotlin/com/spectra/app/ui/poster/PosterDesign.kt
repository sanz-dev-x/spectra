/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.poster

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spectra.app.R

enum class PosterRatio(
    val w: Int,
    val h: Int,
    /** Faktor lebar kartu agar komposisi tetap muat saat poster lebih pendek. */
    val cardFactor: Float,
) {
    R9_16(1080, 1920, 1f),
    R4_5(1080, 1350, 0.82f),
    R1_1(1080, 1080, 0.62f),
}

enum class CardStyle(
    @StringRes val label: Int,
) {
    FROSTED(R.string.poster_style_frosted),
    LIQUID(R.string.poster_style_liquid),
    CLEAR(R.string.poster_style_clear),
    DARK(R.string.poster_style_dark),
    MILK(R.string.poster_style_milk),
    MILK_GRAY(R.string.poster_style_milk_gray),
    MILK_PEARL(R.string.poster_style_milk_pearl),
    MILK_MIST(R.string.poster_style_milk_mist),
    MILK_SATIN(R.string.poster_style_milk_satin),
    MILK_STONE(R.string.poster_style_milk_stone),
    MILK_LATTE(R.string.poster_style_milk_latte),
    MILK_SMOKE(R.string.poster_style_milk_smoke),
    MILK_GRAPHITE(R.string.poster_style_milk_graphite),
}

enum class WatermarkMode { TRANSPARENT, SOLID }

data class CardState(
    val trackId: String,
    val cx: Float,
    val cy: Float,
    val widthFrac: Float,
    val rotation: Float,
    val tiltX: Float,
    val tiltY: Float,
    val radius: Float = 0.09f,
    val surface: Float = 1f,
    val borderWidth: Float = 0.005f,
    val borderColor: Int = 0xFFFFFFFF.toInt(),
    val glow: Float = 0f,
    val edge: Float = 0.8f,
    val behind: Boolean = false,
    val locked: Boolean = false,
    val showPlayer: Boolean = true,
    val style: CardStyle = CardStyle.DARK,
)

data class PhotoState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val backgroundBlur: Float = 0f,
)

/** Semua -1..1 kecuali vignette dan grain (0..1). 0 berarti tidak ada efek. */
data class PhotoAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val exposure: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
)

data class LightState(
    val x: Float = 0.22f,
    val y: Float = 0.30f,
    val intensity: Float = 0f,
    val size: Float = 0.9f,
    val color: Int = 0xFFFFD6A0.toInt(),
)

data class ShadowState(
    val angle: Float = 90f,
    val distance: Float = 0.05f,
    val opacity: Float = 0.45f,
    val blur: Float = 0.12f,
    val color: Int = 0xFF000000.toInt(),
)

data class EffectState(
    val stars: Float = 0.8f,
    val starSize: Float = 0.5f,
    val starOpacity: Float = 0.9f,
    val starsFront: Boolean = false,
    val birds: Float = 0.5f,
    val birdSize: Float = 0.5f,
    val birdOpacity: Float = 0.9f,
    val birdsFront: Boolean = false,
)

data class WatermarkState(
    val mode: WatermarkMode = WatermarkMode.TRANSPARENT,
    /** 0..2 kolom, 0..2 baris pada grid sembilan posisi. */
    val col: Int = 1,
    val row: Int = 2,
    val size: Float = 0.18f,
    val margin: Float = 0.05f,
    val opacity: Float = 0.9f,
    val badgeColor: Int = 0xFF11161C.toInt(),
    val badgeRadius: Float = 0.5f,
    val shadow: Boolean = false,
    val darkLogo: Boolean = false,
    val showName: Boolean = true,
)

data class PosterDesign(
    val ratio: PosterRatio = PosterRatio.R9_16,
    val photo: PhotoState = PhotoState(),
    val adjust: PhotoAdjust = PhotoAdjust(),
    val cards: List<CardState> = emptyList(),
    val light: LightState = LightState(),
    val shadow: ShadowState = ShadowState(),
    val effects: EffectState = EffectState(),
    val watermark: WatermarkState = WatermarkState(),
)

private class Slot(
    val fx: Float,
    val fy: Float,
    val w: Float,
    val tiltY: Float,
    val rot: Float,
    val behind: Boolean = false,
)

// Pola mengikuti referensi: kartu kecil tersebar, miring berlawanan kiri/kanan, satu kartu di tepi kanan
// berada di belakang subjek. Posisi sebagai pecahan lebar/tinggi poster 9:16.
private val SLOTS: Array<List<Slot>> =
    arrayOf(
        listOf(Slot(0.50f, 0.38f, 0.52f, 0f, -2f)),
        listOf(Slot(0.30f, 0.26f, 0.38f, 13f, -6f), Slot(0.72f, 0.52f, 0.38f, -13f, 4f)),
        listOf(
            Slot(0.22f, 0.20f, 0.35f, 13f, -6f),
            Slot(0.62f, 0.38f, 0.35f, 0f, 0f),
            Slot(0.34f, 0.68f, 0.35f, 10f, 4f),
        ),
        listOf(
            Slot(0.20f, 0.19f, 0.34f, 13f, -6f),
            Slot(0.82f, 0.15f, 0.32f, -14f, 4f),
            Slot(0.50f, 0.37f, 0.35f, 0f, 0f),
            Slot(0.36f, 0.70f, 0.34f, 10f, 5f),
        ),
        listOf(
            Slot(0.18f, 0.20f, 0.34f, 13f, -6f),
            Slot(0.84f, 0.16f, 0.32f, -14f, 4f),
            Slot(0.52f, 0.37f, 0.35f, 0f, 0f),
            Slot(0.95f, 0.40f, 0.31f, -14f, 2f, behind = true),
            Slot(0.38f, 0.71f, 0.34f, 10f, 5f),
        ),
    )

/** Komposisi awal terkurasi untuk 1..5 kartu. Kartu yang sudah ada dipertahankan bila id-nya sama. */
fun layoutCards(
    ids: List<String>,
    ratio: PosterRatio,
    keep: List<CardState> = emptyList(),
): List<CardState> {
    val n = ids.size.coerceIn(POSTER_MIN_TRACKS, POSTER_MAX_TRACKS)
    val slots = SLOTS[n - 1]
    val kept = keep.associateBy { it.trackId }
    return ids.take(n).mapIndexed { i, id ->
        kept[id] ?: slots[i].let {
            CardState(
                trackId = id,
                cx = it.fx,
                cy = it.fy,
                widthFrac = it.w * ratio.cardFactor,
                rotation = it.rot,
                tiltX = 4f,
                tiltY = it.tiltY,
                behind = it.behind,
            )
        }
    }
}

/** Satu kartu kembali ke posisi awalnya; gaya (radius, border, dst.) ikut default. */
fun defaultCard(
    ids: List<String>,
    id: String,
    ratio: PosterRatio,
): CardState? = layoutCards(ids, ratio).firstOrNull { it.trackId == id }

/**
 * Penyimpan desain dengan undo/redo. Satu gerakan drag atau satu geseran slider menjadi satu langkah:
 * [beginChange] menyimpan snapshot sekali, [endChange] membuangnya bila ternyata tidak ada perubahan.
 */
class PosterEditorState(
    initial: PosterDesign,
) {
    var design by mutableStateOf(initial)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set

    private val undo = ArrayDeque<PosterDesign>()
    private val redo = ArrayDeque<PosterDesign>()
    private var pending: PosterDesign? = null
    private val baseline = initial

    fun beginChange() {
        if (pending == null) pending = design
    }

    fun endChange() {
        val before = pending ?: return
        pending = null
        if (before == design) return
        undo.addLast(before)
        if (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        sync()
    }

    /** Ubah desain. Bila tidak sedang dalam gerakan, langsung menjadi satu langkah undo. */
    fun update(block: (PosterDesign) -> PosterDesign) {
        val standalone = pending == null
        if (standalone) beginChange()
        design = block(design)
        if (standalone) endChange() else dirty = design != baseline
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(design)
        design = prev
        pending = null
        sync()
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(design)
        design = next
        pending = null
        sync()
    }

    private fun sync() {
        canUndo = undo.isNotEmpty()
        canRedo = redo.isNotEmpty()
        dirty = design != baseline
    }

    private companion object {
        const val MAX_HISTORY = 80
    }
}

/**
 * Susun ulang kartu setelah subjek terdeteksi. Tiap kartu dicoba di grid posisi dan dipilih yang paling sedikit
 * menutupi subjek, tidak bertabrakan dengan kartu lain, dan paling dekat dengan pola awal. Bila tidak ada tempat
 * kosong, kartu diperkecil. Kartu yang terkunci tidak disentuh. [overlapOf] = bagian kartu (0..1) yang menutupi subjek.
 */
fun autoArrangeCards(
    design: PosterDesign,
    overlapOf: (PosterDesign, CardState) -> Float,
): List<CardState> {
    val ratio = design.ratio
    val aspect = 1.5f * ratio.w / ratio.h
    val placed = ArrayList<CardState>()
    val base = layoutCards(design.cards.map { it.trackId }, ratio)
    design.cards.forEachIndexed { i, original ->
        if (original.locked) {
            placed += original
            return@forEachIndexed
        }
        val slot = base[i]
        var best: CardState? = null
        var bestCost = Float.MAX_VALUE
        for (scale in floatArrayOf(1f, 0.9f, 0.8f)) {
            val wf = slot.widthFrac * scale
            val hf = wf * aspect
            var cy = 0.12f
            while (cy <= 0.80f) {
                var cx = 0.14f
                while (cx <= 0.90f) {
                    val inside = cx - wf / 2f >= 0.02f && cx + wf / 2f <= 0.98f && cy - hf / 2f >= 0.03f && cy + hf / 2f <= 0.92f
                    if (inside || slot.behind) {
                        val cand = slot.copy(cx = cx, cy = cy, widthFrac = wf)
                        var cost = overlapOf(design, cand) * 5f + (1f - scale) * 2f
                        for (o in placed) {
                            val ow = (minOf(cx + wf / 2f, o.cx + o.widthFrac / 2f) - maxOf(cx - wf / 2f, o.cx - o.widthFrac / 2f)).coerceAtLeast(0f)
                            val oh = (minOf(cy + hf / 2f, o.cy + o.widthFrac * aspect / 2f) - maxOf(cy - hf / 2f, o.cy - o.widthFrac * aspect / 2f)).coerceAtLeast(0f)
                            cost += ow * oh / (wf * hf) * 8f
                        }
                        cost += (kotlin.math.abs(cx - slot.cx) + kotlin.math.abs(cy - slot.cy)) * 0.8f
                        if (cost < bestCost) {
                            bestCost = cost
                            best = cand
                        }
                    }
                    cx += 0.04f
                }
                cy += 0.04f
            }
            if (bestCost < 0.5f) break
        }
        val chosen = best ?: slot
        // kartu yang masih sedikit menyentuh subjek diselipkan di belakangnya agar terasa berlapis
        val o = overlapOf(design, chosen)
        placed += if (o in 0.12f..0.6f) chosen.copy(behind = true) else chosen.copy(behind = slot.behind && o > 0f)
    }
    return placed
}
