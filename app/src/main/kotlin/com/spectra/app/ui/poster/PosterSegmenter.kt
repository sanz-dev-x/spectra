/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.poster

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed interface SegmentationResult {
    class Person(val cutout: Bitmap, val headGuard: Bitmap?) : SegmentationResult

    data object NoPerson : SegmentationResult

    data object Unavailable : SegmentationResult
}

/**
 * Pemisahan orang memakai ML Kit Selfie Segmentation (model ter-bundle, berjalan on-device).
 * Hasilnya hanya masker orang/latar. Ini bukan peta kedalaman, jadi poster memakai lapisan 2D
 * (orang di atas atau di bawah album art) dan tidak mengklaim rekonstruksi 3D.
 */
object PosterSegmenter {
    private const val TAG = "PosterSegmenter"
    private const val MIN_PERSON_COVERAGE = 0.02f
    private const val SEED_CONF = 0.55f
    private const val GROW_CONF = 0.2f
    private const val LOW_CONF = 0.08f
    private const val COLOR_MIN_CONF = 0.015f
    private const val COLOR_MAX_STEPS = 9
    private const val COLOR_TOLERANCE = 46
    private const val MAX_HOLE_FRACTION = 0.004f

    suspend fun segment(photo: Bitmap): SegmentationResult {
        val mask =
            runCatching { runModel(photo) }
                .onFailure { Log.w(TAG, "Selfie segmentation failed", it) }
                .getOrNull() ?: return SegmentationResult.Unavailable
        return withContext(Dispatchers.Default) { buildCutout(photo, mask) }
    }

    private class Mask(val width: Int, val height: Int, val confidence: FloatArray)

    private suspend fun runModel(photo: Bitmap): Mask? {
        val options =
            SelfieSegmenterOptions
                .Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        val segmenter = Segmentation.getClient(options)
        return try {
            suspendCancellableCoroutine<Mask?> { cont ->
                segmenter
                    .process(InputImage.fromBitmap(photo, 0))
                    .addOnSuccessListener { result: SegmentationMask ->
                        val buf = result.buffer
                        buf.rewind()
                        val fb = buf.asFloatBuffer()
                        val n = result.width * result.height
                        if (n <= 0 || fb.remaining() < n) {
                            cont.resume(null)
                            return@addOnSuccessListener
                        }
                        val data = FloatArray(n)
                        fb.get(data)
                        cont.resume(Mask(result.width, result.height, data))
                    }.addOnFailureListener {
                        Log.w(TAG, "Selfie segmentation task failed", it)
                        cont.resume(null)
                    }
            }
        } finally {
            segmenter.close()
        }
    }

    private val colorLut: FloatArray =
        FloatArray(766) { d ->
            val t = d / 44f
            kotlin.math.exp(-(t * t).toDouble()).toFloat().coerceAtLeast(0.02f)
        }

    private fun buildCutout(
        photo: Bitmap,
        mask: Mask,
    ): SegmentationResult {
        val w = photo.width
        val h = photo.height
        val small = Bitmap.createScaledBitmap(photo, mask.width, mask.height, true)
        val smallPx = IntArray(mask.width * mask.height)
        small.getPixels(smallPx, 0, mask.width, 0, 0, mask.width, mask.height)
        val refined = refine(mask, smallPx)
        var covered = 0
        for (v in refined) if (v > 0.5f) covered++
        if (covered / refined.size.toFloat() < MIN_PERSON_COVERAGE) return SegmentationResult.NoPerson

        val mw = mask.width
        val mh = mask.height
        // Hanya orang utama (terdekat kamera) yang masuk potongan. Orang di belakangnya tetap menjadi bagian foto latar,
        // sehingga kartu "belakang" otomatis menutupi mereka.
        val main = isolateMainSubject(refined, mw, mh)

        val px = IntArray(w * h)
        photo.getPixels(px, 0, w, 0, 0, w, h)
        val sx = mw / w.toFloat()
        val sy = mh / h.toFloat()
        val x0s = IntArray(w)
        val x1s = IntArray(w)
        val txs = FloatArray(w)
        for (x in 0 until w) {
            val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, mw - 1f)
            x0s[x] = fx.toInt()
            x1s[x] = minOf(x0s[x] + 1, mw - 1)
            txs[x] = fx - x0s[x]
        }
        // Upsampling mask sadar-tepi (joint bilateral): bobot tetangga mask dikalikan kemiripan warna dengan piksel
        // foto beresolusi penuh, sehingga tepi alpha menempel pada tepi asli subjek (rambut, jari, bahu).
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, mh - 1f)
            val y0 = fy.toInt()
            val y1 = minOf(y0 + 1, mh - 1)
            val ty = fy - y0
            val r0 = y0 * mw
            val r1 = y1 * mw
            val row = y * w
            for (x in 0 until w) {
                val tx = txs[x]
                val i00 = r0 + x0s[x]
                val i10 = r0 + x1s[x]
                val i01 = r1 + x0s[x]
                val i11 = r1 + x1s[x]
                val b00 = (1f - tx) * (1f - ty)
                val b10 = tx * (1f - ty)
                val b01 = (1f - tx) * ty
                val b11 = tx * ty
                val p = px[row + x]
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                val w00 = b00 * colorLut[colorDiff(pr, pg, pb, smallPx[i00])]
                val w10 = b10 * colorLut[colorDiff(pr, pg, pb, smallPx[i10])]
                val w01 = b01 * colorLut[colorDiff(pr, pg, pb, smallPx[i01])]
                val w11 = b11 * colorLut[colorDiff(pr, pg, pb, smallPx[i11])]
                val ws = w00 + w10 + w01 + w11
                val c =
                    if (ws > 1e-5f) {
                        (main[i00] * w00 + main[i10] * w10 + main[i01] * w01 + main[i11] * w11) / ws
                    } else {
                        main[i00] * b00 + main[i10] * b10 + main[i01] * b01 + main[i11] * b11
                    }
                val t = ((c - 0.36f) / 0.34f).coerceIn(0f, 1f)
                val a = (t * t * (3f - 2f * t) * 255f + 0.5f).toInt()
                px[row + x] = (a shl 24) or (p and 0x00FFFFFF)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return SegmentationResult.Person(out, null)
    }

    private fun colorDiff(
        r: Int,
        g: Int,
        b: Int,
        c: Int,
    ): Int =
        kotlin.math.abs(r - ((c shr 16) and 0xFF)) + kotlin.math.abs(g - ((c shr 8) and 0xFF)) + kotlin.math.abs(b - (c and 0xFF))

    /**
     * Pisahkan orang utama dari orang di belakangnya. Langkah 1: komponen terhubung; yang terbesar dan terdekat ke bawah
     * foto jadi utama, orang lain yang sama dekat (ukuran sebanding) ikut, sisanya latar. Langkah 2: blob yang menyatu
     * dipisah lewat profil puncak kepala per kolom; kepala yang jauh lebih rendah dan terpisah lembah dianggap orang belakang.
     */
    private fun isolateMainSubject(
        v: FloatArray,
        w: Int,
        h: Int,
    ): FloatArray {
        val n = w * h
        val label = IntArray(n)
        val queue = IntArray(n)
        val areas = ArrayList<Int>()
        val maxYs = ArrayList<Int>()
        val minXs = ArrayList<Int>()
        val maxXs = ArrayList<Int>()
        val minYs = ArrayList<Int>()
        for (s in 0 until n) {
            if (label[s] != 0 || v[s] <= 0.5f) continue
            val id = areas.size + 1
            var qh = 0
            var qt = 0
            queue[qt++] = s
            label[s] = id
            var mnx = w
            var mxx = -1
            var mny = h
            var mxy = -1
            while (qh < qt) {
                val i = queue[qh++]
                val x = i % w
                val y = i / w
                if (x < mnx) mnx = x
                if (x > mxx) mxx = x
                if (y < mny) mny = y
                if (y > mxy) mxy = y
                if (x > 0 && label[i - 1] == 0 && v[i - 1] > 0.5f) { label[i - 1] = id; queue[qt++] = i - 1 }
                if (x < w - 1 && label[i + 1] == 0 && v[i + 1] > 0.5f) { label[i + 1] = id; queue[qt++] = i + 1 }
                if (y > 0 && label[i - w] == 0 && v[i - w] > 0.5f) { label[i - w] = id; queue[qt++] = i - w }
                if (y < h - 1 && label[i + w] == 0 && v[i + w] > 0.5f) { label[i + w] = id; queue[qt++] = i + w }
            }
            areas.add(qt)
            maxYs.add(mxy)
            minXs.add(mnx)
            maxXs.add(mxx)
            minYs.add(mny)
        }
        if (areas.isEmpty()) return v
        var mainId = 0
        var bestScore = -1f
        for (k in areas.indices) {
            val score = areas[k] * (1f + 0.6f * maxYs[k] / h.toFloat())
            if (score > bestScore) {
                bestScore = score
                mainId = k
            }
        }
        val mainArea = areas[mainId]
        val keep = BooleanArray(areas.size)
        for (k in areas.indices) {
            val nearBottom = maxYs[k] >= maxYs[mainId] - h * 0.10f
            val big = areas[k] >= mainArea * 0.55f
            val near =
                areas[k] < n * 0.012f &&
                    (minXs[k] + maxXs[k]) / 2 in (minXs[mainId] - w / 16)..(maxXs[mainId] + w / 16) &&
                    minYs[k] >= minYs[mainId] - h / 16
            keep[k] = k == mainId || (big && nearBottom) || near
        }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val l = label[i]
            if (l != 0 && keep[l - 1]) out[i] = v[i]
        }
        // piksel tepi (0.0..0.5) milik komponen utama ikut dipertahankan agar tepi tetap halus
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (out[i] > 0f || v[i] <= 0f) continue
                var near = false
                for (dy in -2..2) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -2..2) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        val l = label[yy * w + xx]
                        if (l != 0 && keep[l - 1]) near = true
                    }
                }
                if (near) out[i] = v[i]
            }
        }
        splitMergedBackground(out, w, h)
        return out
    }

    private fun splitMergedBackground(
        m: FloatArray,
        w: Int,
        h: Int,
    ) {
        val top = IntArray(w) { -1 }
        var minTop = h
        var maxBottom = -1
        for (x in 0 until w) {
            for (y in 0 until h) {
                if (m[y * w + x] > 0.5f) {
                    top[x] = y
                    if (y < minTop) minTop = y
                    break
                }
            }
            for (y in h - 1 downTo 0) {
                if (m[y * w + x] > 0.5f) {
                    if (y > maxBottom) maxBottom = y
                    break
                }
            }
        }
        if (maxBottom <= minTop) return
        val span = (maxBottom - minTop).toFloat()
        val topS = FloatArray(w) { h.toFloat() }
        for (x in 0 until w) {
            var sum = 0f
            var cnt = 0
            for (dx in -2..2) {
                val t = top.getOrElse(x + dx) { -1 }
                if (t >= 0) {
                    sum += t
                    cnt++
                }
            }
            if (cnt > 0 && top[x] >= 0) topS[x] = sum / cnt
        }
        val r = maxOf(3, (w * 0.05f).toInt())
        val peaks = ArrayList<Int>()
        var x = 0
        while (x < w) {
            if (top[x] < 0) {
                x++
                continue
            }
            var isMin = true
            for (dx in -r..r) {
                val xx = x + dx
                if (xx in 0 until w && topS[xx] < topS[x]) {
                    isMin = false
                    break
                }
            }
            if (isMin) {
                peaks.add(x)
                x += r
            } else {
                x++
            }
        }
        if (peaks.size < 2) return
        // gabungkan puncak bersebelahan yang tidak dipisah lembah cukup dalam
        var i = 0
        while (i < peaks.size - 1) {
            val a = peaks[i]
            val b = peaks[i + 1]
            var valley = 0f
            var vx = a
            for (xx in a..b) {
                if (top[xx] < 0) {
                    valley = h.toFloat()
                    vx = xx
                    break
                }
                if (topS[xx] > valley) {
                    valley = topS[xx]
                    vx = xx
                }
            }
            val lower = maxOf(topS[a], topS[b])
            if (valley - lower < span * 0.04f) {
                if (topS[a] <= topS[b]) peaks.removeAt(i + 1) else peaks.removeAt(i)
            } else {
                i++
            }
        }
        if (peaks.size < 2) return
        val bounds = IntArray(peaks.size + 1)
        bounds[0] = 0
        bounds[peaks.size] = w
        for (k in 0 until peaks.size - 1) {
            var valley = -1f
            var vx = peaks[k]
            for (xx in peaks[k]..peaks[k + 1]) {
                if (topS[xx] > valley) {
                    valley = topS[xx]
                    vx = xx
                }
            }
            bounds[k + 1] = vx
        }
        val mass = FloatArray(peaks.size)
        for (k in peaks.indices) {
            for (xx in bounds[k] until bounds[k + 1]) {
                for (yy in 0 until h) if (m[yy * w + xx] > 0.5f) mass[k] += 1f
            }
        }
        var mainK = 0
        for (k in peaks.indices) if (mass[k] > mass[mainK]) mainK = k
        val mainTop = topS[peaks[mainK]]
        for (k in peaks.indices) {
            if (k == mainK) continue
            if (topS[peaks[k]] >= mainTop + span * 0.12f) {
                for (xx in bounds[k] until bounds[k + 1]) for (yy in 0 until h) m[yy * w + xx] = 0f
            }
        }
        // haluskan potongan pemisah satu lintasan
        val copy = m.copyOf()
        for (yy in 0 until h) {
            for (xx in 0 until w) {
                var sum = 0f
                var cnt = 0
                for (dy in -1..1) {
                    val y2 = yy + dy
                    if (y2 < 0 || y2 >= h) continue
                    for (dx in -1..1) {
                        val x2 = xx + dx
                        if (x2 < 0 || x2 >= w) continue
                        sum += copy[y2 * w + x2]
                        cnt++
                    }
                }
                m[yy * w + xx] = sum / cnt
            }
        }
    }

    /**
     * Penyempurnaan mask pada resolusi mask (murah): bagian dengan kepercayaan rendah yang menempel pada orang
     * (topi, rambut, aksesori) ikut dimasukkan lewat hysteresis; lubang kecil di dalam siluet ditutup;
     * lalu tepi dihaluskan satu lintasan agar tidak bergerigi.
     */
    private fun refine(mask: Mask, colors: IntArray): FloatArray {
        val w = mask.width
        val h = mask.height
        val n = w * h
        val src = mask.confidence
        val v = FloatArray(n)
        val queue = IntArray(n)
        var qh = 0
        var qt = 0
        val seen = BooleanArray(n)
        val steps = ByteArray(n)
        for (i in 0 until n) {
            if (src[i] >= SEED_CONF) {
                seen[i] = true
                queue[qt++] = i
            }
        }
        // Hysteresis + pertumbuhan berbasis warna: topi, kacamata, headphone, syal, dan barang yang dipakai
        // sering berkepercayaan rendah di model selfie. Piksel tetangga yang warnanya dekat dengan bagian
        // orang yang sudah terpilih ikut dimasukkan (maksimal beberapa langkah dari tepi) supaya tidak bocor ke latar.
        while (qh < qt) {
            val i = queue[qh++]
            val colorGrown = steps[i].toInt() > 0
            v[i] = if (colorGrown) 0.85f else if (src[i] >= GROW_CONF) maxOf(src[i], 0.85f) else minOf(1f, src[i] * 4f)
            val x = i % w
            val y = i / w
            for (k in 0 until 4) {
                val j =
                    when (k) {
                        0 -> if (x > 0) i - 1 else -1
                        1 -> if (x < w - 1) i + 1 else -1
                        2 -> if (y > 0) i - w else -1
                        else -> if (y < h - 1) i + w else -1
                    }
                if (j < 0 || seen[j]) continue
                if (src[j] >= LOW_CONF) {
                    seen[j] = true
                    steps[j] = steps[i]
                    queue[qt++] = j
                } else if (src[j] >= COLOR_MIN_CONF && steps[i] < COLOR_MAX_STEPS && colorClose(colors[i], colors[j])) {
                    seen[j] = true
                    steps[j] = (steps[i] + 1).toByte()
                    queue[qt++] = j
                }
            }
        }
        fillSmallHoles(v, w, h)
        // dua lintasan blur kotak 3x3 supaya tepi siluet halus dan tidak terlihat dipotong tangan
        var cur = v
        repeat(2) {
            val out = FloatArray(n)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sum = 0f
                    var cnt = 0
                    for (dy in -1..1) {
                        val yy = y + dy
                        if (yy < 0 || yy >= h) continue
                        for (dx in -1..1) {
                            val xx = x + dx
                            if (xx < 0 || xx >= w) continue
                            sum += cur[yy * w + xx]
                            cnt++
                        }
                    }
                    out[y * w + x] = sum / cnt
                }
            }
            cur = out
        }
        val out = cur
        return out
    }

    /**
     * Masker pelindung kepala (ALPHA_8, resolusi mask): per kolom, dari titik tertinggi siluet turun sekitar 28% tinggi
     * subjek (memudar sampai 38%). Dipakai renderer agar rambut, topi, dan aksesori kepala tetap di depan kartu
     * walaupun kartu itu berada di lapisan depan; badan tetap boleh tertutup kartu.
     */
    private fun buildHeadGuard(
        v: FloatArray,
        w: Int,
        h: Int,
    ): Bitmap? {
        val top = IntArray(w) { -1 }
        var minTop = h
        var maxBottom = -1
        for (x in 0 until w) {
            for (y in 0 until h) {
                if (v[y * w + x] > 0.5f) {
                    top[x] = y
                    if (y < minTop) minTop = y
                    break
                }
            }
            for (y in h - 1 downTo 0) {
                if (v[y * w + x] > 0.5f) {
                    if (y > maxBottom) maxBottom = y
                    break
                }
            }
        }
        if (maxBottom <= minTop) return null
        val span = (maxBottom - minTop).toFloat()
        val solid = span * 0.34f
        val fade = span * 0.12f
        // Naikkan batas atas ±3 kolom agar tepi topi/rambut yang miring tetap tercakup.
        val lifted = IntArray(w) { -1 }
        for (x in 0 until w) {
            var best = -1
            for (dx in -3..3) {
                val t = top.getOrElse(x + dx) { -1 }
                if (t >= 0 && (best < 0 || t < best)) best = t
            }
            lifted[x] = best
        }
        val bytes = ByteArray(w * h)
        for (x in 0 until w) {
            val t = lifted[x]
            if (t < 0) continue
            for (y in 0 until h) {
                val d = y - t
                val a = if (d <= solid) 1f else (1f - (d - solid) / fade).coerceIn(0f, 1f)
                if (a > 0f) bytes[y * w + x] = (a * 255f + 0.5f).toInt().toByte()
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        out.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
        return out
    }

    private fun colorClose(
        a: Int,
        b: Int,
    ): Boolean {
        val dr = kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
        return dr + dg + db < COLOR_TOLERANCE * 3 / 2
    }

    private fun grow(
        j: Int,
        src: FloatArray,
        seen: BooleanArray,
        queue: IntArray,
        qt: Int,
    ): Boolean {
        if (seen[j] || src[j] < LOW_CONF) return false
        seen[j] = true
        queue[qt] = j
        return true
    }

    /** Area latar yang terkurung seluruhnya oleh orang dan kecil (celah antar rambut/aksesori) dianggap bagian orang. */
    private fun fillSmallHoles(
        v: FloatArray,
        w: Int,
        h: Int,
    ) {
        val n = w * h
        val state = ByteArray(n) // 0 belum, 1 latar luar, 2 sudah diperiksa
        val queue = IntArray(n)
        fun flood(
            start: Int,
            mark: Byte,
            collect: Boolean,
        ): Int {
            var qh = 0
            var qt = 0
            queue[qt++] = start
            state[start] = mark
            while (qh < qt) {
                val i = queue[qh++]
                val x = i % w
                val y = i / w
                val nb = intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)
                for (j in nb) {
                    if (j < 0 || state[j].toInt() != 0 || v[j] >= 0.5f) continue
                    state[j] = mark
                    queue[qt++] = j
                }
            }
            if (collect && qt <= n * MAX_HOLE_FRACTION) for (k in 0 until qt) v[queue[k]] = 0.85f
            return qt
        }
        for (x in 0 until w) {
            if (state[x].toInt() == 0 && v[x] < 0.5f) flood(x, 1, false)
            val b = (h - 1) * w + x
            if (state[b].toInt() == 0 && v[b] < 0.5f) flood(b, 1, false)
        }
        for (y in 0 until h) {
            val l = y * w
            val r = l + w - 1
            if (state[l].toInt() == 0 && v[l] < 0.5f) flood(l, 1, false)
            if (state[r].toInt() == 0 && v[r] < 0.5f) flood(r, 1, false)
        }
        for (i in 0 until n) {
            if (state[i].toInt() == 0 && v[i] < 0.5f) flood(i, 2, true)
        }
    }
}
