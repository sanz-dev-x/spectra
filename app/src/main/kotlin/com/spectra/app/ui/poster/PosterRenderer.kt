/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.poster

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class CardGeom(
    val matrix: Matrix,
    val cw: Float,
    val ch: Float,
)

/**
 * Menggambar poster dari [PosterDesign] ke Canvas mana pun, dalam satuan desain (lebar 1080).
 * Preview dan ekspor memakai kode, koordinat, dan urutan lapisan yang sama; ekspor hanya menskalakan canvas.
 * Satu instance per konsumen karena Paint, Camera, dan cache dipakai ulang.
 *
 * Urutan lapisan: foto, latar blur, cahaya, kartu belakang, subjek (mask), kartu depan, efek, vignette,
 * grain, watermark. Tanpa mask subjek semua kartu digambar sesuai urutan daftar.
 */
class PosterRenderer {
    companion object {
        private const val CARD_AR = 1.5f
        private const val CAMERA_Z = -8f

        // Kawanan longgar: satu pusat, burung menyebar mengikuti garis terbang miring; ukuran bervariasi = kedalaman.
        // [x, y, skala, kemiringan, pose sayap (0..2), arah (-1/1)]
        private val BIRDS: List<FloatArray> =
            Random(11).let { r ->
                val cx = 0.25f + r.nextFloat() * 0.5f
                val cy = 0.1f + r.nextFloat() * 0.2f
                List(12) { i ->
                    val depth = r.nextFloat()
                    val spread = 0.1f + depth * 0.28f
                    floatArrayOf(
                        (cx + (r.nextFloat() - 0.5f) * 2f * spread * 1.6f).coerceIn(0f, 1f),
                        (cy + (r.nextFloat() - 0.4f) * spread * 1.8f + i * 0.012f).coerceIn(0f, 1f),
                        0.28f + depth * depth * 1.0f,
                        r.nextFloat() * 24f - 16f,
                        (r.nextInt(3)).toFloat(),
                        if (r.nextBoolean()) 1f else -1f,
                    )
                }.sortedBy { it[2] }
            }

    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val camera = Camera()
    private val cameraM = Matrix()
    private val photoM = Matrix()
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val dstIn = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    private val guardRect = RectF()
    private val guardM = Matrix()
    private val sans = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val sansBold = Typeface.create("sans-serif", Typeface.BOLD)
    private val heart = buildHeart()
    private val birdPaths = listOf(buildBird(0.85f), buildBird(0.1f), buildBird(-0.75f))
    private var grain: Bitmap? = null
    private val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private val perspective get() = modern

    fun draw(
        canvas: Canvas,
        scene: PosterScene,
        d: PosterDesign,
    ) {
        val w = d.ratio.w.toFloat()
        val h = d.ratio.h.toFloat()
        canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        val accent = accentOf(scene)
        val photo = scene.photo
        val cutout = photo?.cutout
        val filter = photoFilter(d.adjust)

        drawBackground(canvas, scene, d, accent, filter)
        if (photo != null && cutout != null && d.photo.backgroundBlur > 0f) {
            drawPhotoLayer(canvas, photo.blurred, photo.bitmap, (d.photo.backgroundBlur * 255).toInt(), d, filter)
        }
        drawLight(canvas, d)
        if (!d.effects.birdsFront) drawBirds(canvas, d)

        val starsOnCard = !d.effects.starsFront
        if (photo != null && cutout != null) {
            d.cards.forEachIndexed { i, c ->
                if (c.behind) {
                    drawCard(canvas, scene, d, i, accent)
                    if (starsOnCard) drawCardStar(canvas, d, i)
                }
            }
            drawSubjectShadow(canvas, photo, cutout, d)
            drawPhotoLayer(canvas, cutout, photo.bitmap, 255, d, filter)
            d.cards.forEachIndexed { i, c ->
                if (!c.behind) {
                    drawCard(canvas, scene, d, i, accent)
                    if (starsOnCard) drawCardStar(canvas, d, i)
                }
            }
        } else {
            d.cards.indices.forEach {
                drawCard(canvas, scene, d, it, accent)
                if (starsOnCard) drawCardStar(canvas, d, it)
            }
        }
        if (d.effects.birdsFront) drawBirds(canvas, d)
        if (d.effects.starsFront) drawStars(canvas, d)
        drawVignette(canvas, d)
        drawGrain(canvas, d)
        drawWatermark(canvas, scene, d)
        canvas.restore()
    }

    // ---------- geometry (dipakai draw, hit-test, dan overlay seleksi) ----------

    fun geom(
        d: PosterDesign,
        card: CardState,
    ): CardGeom {
        val cw = d.ratio.w * card.widthFrac
        val ch = cw * CARD_AR
        val m = Matrix()
        camera.save()
        camera.setLocation(0f, 0f, CAMERA_Z)
        if (perspective) {
            camera.rotateY(card.tiltY)
            camera.rotateX(card.tiltX)
        }
        camera.getMatrix(cameraM)
        camera.restore()
        m.set(cameraM)
        m.preTranslate(-cw / 2f, -ch / 2f)
        m.postRotate(card.rotation)
        m.postTranslate(card.cx * d.ratio.w, card.cy * d.ratio.h)
        return CardGeom(m, cw, ch)
    }


    /** Vektor (px desain) bidang belakang kartu terhadap bidang depan; nol bila kartu datar. */
    private fun edgeOffset(
        card: CardState,
        depth: Float,
    ): FloatArray {
        if (!perspective || (card.tiltX == 0f && card.tiltY == 0f)) return floatArrayOf(0f, 0f)
        val pts = Array(2) { floatArrayOf(0f, 0f) }
        for (k in 0..1) {
            camera.save()
            camera.setLocation(0f, 0f, CAMERA_Z)
            if (k == 1) camera.translate(0f, 0f, depth / 72f)
            camera.rotateY(card.tiltY)
            camera.rotateX(card.tiltX)
            camera.getMatrix(cameraM)
            camera.restore()
            cameraM.mapPoints(pts[k])
        }
        val v = floatArrayOf(pts[1][0] - pts[0][0], pts[1][1] - pts[0][1])
        Matrix().apply { setRotate(card.rotation) }.mapVectors(v)
        return v
    }

    /** Indeks kartu paling atas di titik ([x], [y]) dalam koordinat desain, atau -1. */
    fun hitTest(
        d: PosterDesign,
        x: Float,
        y: Float,
    ): Int {
        for (i in d.cards.indices.reversed()) {
            val g = geom(d, d.cards[i])
            val inv = Matrix()
            if (!g.matrix.invert(inv)) continue
            val p = floatArrayOf(x, y)
            inv.mapPoints(p)
            if (p[0] in 0f..g.cw && p[1] in 0f..g.ch) return i
        }
        return -1
    }

    /** Garis seleksi untuk preview saja. Tidak pernah dipanggil saat ekspor. */
    fun drawSelection(
        canvas: Canvas,
        d: PosterDesign,
        index: Int,
        unit: Float,
        color: Int,
    ) {
        val card = d.cards.getOrNull(index) ?: return
        val g = geom(d, card)
        val r = g.cw * card.radius
        val p = Path().apply { addRoundRect(RectF(0f, 0f, g.cw, g.ch), r, r, Path.Direction.CW) }
        p.transform(g.matrix)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * unit
        paint.color = color
        canvas.drawPath(p, paint)
        paint.reset()
    }

    // ---------- foto ----------

    private var accentSource: Bitmap? = null
    private var accentValue = Color.rgb(120, 140, 170)

    private fun accentOf(scene: PosterScene): Int {
        val bmp = scene.covers.firstOrNull { it != null } ?: return Color.rgb(120, 140, 170)
        if (bmp === accentSource) return accentValue
        val px = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
        val hsv = FloatArray(3)
        Color.colorToHSV(px, hsv)
        hsv[1] = hsv[1].coerceIn(0.25f, 0.8f)
        hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
        accentSource = bmp
        accentValue = Color.HSVToColor(hsv)
        return accentValue
    }

    /** Satu filter untuk latar, blur, dan potongan subjek supaya warnanya selalu sama. */
    private fun photoFilter(a: PhotoAdjust): ColorMatrixColorFilter? {
        if (a.brightness == 0f && a.contrast == 0f && a.saturation == 0f && a.exposure == 0f &&
            a.warmth == 0f && a.tint == 0f && a.fade == 0f
        ) {
            return null
        }
        val m = ColorMatrix()
        val ev = 2f.pow(a.exposure * 1.5f)
        m.postConcat(ColorMatrix(floatArrayOf(ev, 0f, 0f, 0f, 0f, 0f, ev, 0f, 0f, 0f, 0f, 0f, ev, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        val c = 1f + a.contrast
        val t = 128f * (1f - c) + a.brightness * 90f
        m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        m.postConcat(ColorMatrix().apply { setSaturation((1f + a.saturation).coerceAtLeast(0f)) })
        val w = a.warmth * 28f
        val tg = -a.tint * 24f
        val tr = a.tint * 12f
        m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, w + tr, 0f, 1f, 0f, 0f, tg, 0f, 0f, 1f, 0f, -w + tr, 0f, 0f, 0f, 1f, 0f)))
        if (a.fade != 0f) {
            val k = 1f - 0.3f * a.fade
            val o = 255f * 0.18f * a.fade
            m.postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, o, 0f, k, 0f, 0f, o, 0f, 0f, k, 0f, o, 0f, 0f, 0f, 1f, 0f)))
        }
        return ColorMatrixColorFilter(m)
    }

    private fun drawBackground(
        c: Canvas,
        scene: PosterScene,
        d: PosterDesign,
        accent: Int,
        filter: ColorMatrixColorFilter?,
    ) {
        val w = d.ratio.w.toFloat()
        val h = d.ratio.h.toFloat()
        val photo = scene.photo
        if (photo == null) {
            paint.reset()
            paint.isAntiAlias = true
            paint.shader =
                LinearGradient(
                    0f, 0f, w * 0.6f, h,
                    intArrayOf(shade(accent, 0.55f), shade(accent, 0.22f), Color.rgb(8, 10, 16)),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            c.drawRect(0f, 0f, w, h, paint)
            paint.reset()
            return
        }
        c.drawColor(Color.BLACK)
        drawPhotoLayer(c, photo.bitmap, photo.bitmap, 255, d, filter)
    }

    private fun fillPhotoMatrix(
        m: Matrix,
        bmp: Bitmap,
        reference: Bitmap,
        d: PosterDesign,
    ) {
        val p = d.photo
        val base = max(d.ratio.w / reference.width.toFloat(), d.ratio.h / reference.height.toFloat())
        val s = base * p.scale
        m.reset()
        m.postScale(reference.width / bmp.width.toFloat(), reference.height / bmp.height.toFloat())
        m.postTranslate(-reference.width / 2f, -reference.height / 2f)
        m.postScale(if (p.flipH) -s else s, if (p.flipV) -s else s)
        m.postRotate(p.rotation)
        m.postTranslate(d.ratio.w / 2f + p.offsetX, d.ratio.h / 2f + p.offsetY)
    }

    /** Bagian kartu (0..1) yang tertutup potongan subjek, memakai transformasi foto dan kartu yang sama dengan render. */
    fun subjectOverlap(
        d: PosterDesign,
        photo: PosterPhoto,
        card: CardState,
    ): Float {
        val cut = photo.cutout ?: return 0f
        val m = Matrix()
        fillPhotoMatrix(m, cut, photo.bitmap, d)
        val inv = Matrix()
        if (!m.invert(inv)) return 0f
        val g = geom(d, card)
        var hit = 0
        val pt = FloatArray(2)
        for (iy in 0 until 10) {
            for (ix in 0 until 7) {
                pt[0] = g.cw * (ix + 0.5f) / 7f
                pt[1] = g.ch * (iy + 0.5f) / 10f
                g.matrix.mapPoints(pt)
                inv.mapPoints(pt)
                val x = pt[0].toInt()
                val y = pt[1].toInt()
                if (pt[0] >= 0f && pt[1] >= 0f && x < cut.width && y < cut.height && (cut.getPixel(x, y) ushr 24) > 128) hit++
            }
        }
        return hit / 70f
    }

    /**
     * Menggambar ulang siluet orang hanya pada zona kepala (rambut, topi, aksesori) di atas kartu depan.
     * Hanya zona yang dibatasi masker pelindung yang ikut, jadi badan tetap bisa tertutup kartu.
     */
    private fun drawHeadGuard(
        c: Canvas,
        photo: PosterPhoto,
        cutout: Bitmap,
        guard: Bitmap,
        d: PosterDesign,
        filter: ColorMatrixColorFilter?,
    ) {
        fillPhotoMatrix(photoM, cutout, photo.bitmap, d)
        guardRect.set(0f, 0f, cutout.width.toFloat(), cutout.height.toFloat())
        photoM.mapRect(guardRect)
        guardRect.intersect(0f, 0f, d.ratio.w.toFloat(), d.ratio.h.toFloat())
        if (guardRect.isEmpty) return
        val save = c.saveLayer(guardRect, null)
        drawPhotoLayer(c, cutout, photo.bitmap, 255, d, filter)
        // Masker kecil diskalakan ke ukuran cutout lalu dipetakan sama seperti foto.
        fillPhotoMatrix(photoM, cutout, photo.bitmap, d)
        guardM.set(photoM)
        guardM.preScale(cutout.width / guard.width.toFloat(), cutout.height / guard.height.toFloat())
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.xfermode = dstIn
        c.drawBitmap(guard, guardM, paint)
        paint.reset()
        c.restoreToCount(save)
    }


    // ---------- bayangan subjek di atas kartu belakang ----------

    private var shadowSrc: Bitmap? = null
    private var shadowBmp: Bitmap? = null

    private fun subjectShadow(cutout: Bitmap): Bitmap {
        val cached = shadowBmp
        if (cached != null && shadowSrc === cutout) return cached
        val w = (cutout.width / 10).coerceAtLeast(8)
        val h = (cutout.height / 10).coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(cutout, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        var a = FloatArray(w * h) { (px[it] ushr 24) / 255f }
        var b = FloatArray(w * h)
        val r = 4
        repeat(2) {
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (k in -r..r) sum += a[y * w + (x + k).coerceIn(0, w - 1)]
                b[y * w + x] = sum / (2 * r + 1)
            }
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (k in -r..r) sum += b[(y + k).coerceIn(0, h - 1) * w + x]
                a[y * w + x] = sum / (2 * r + 1)
            }
        }
        val out = IntArray(w * h) { ((a[it].coerceIn(0f, 1f) * 255f + 0.5f).toInt()) shl 24 }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        shadowSrc = cutout
        shadowBmp = bmp
        return bmp
    }

    /** Bayangan lembut subjek jatuh di permukaan kartu yang ada di belakangnya; hanya di dalam bentuk kartu. */
    private fun drawSubjectShadow(
        c: Canvas,
        photo: PosterPhoto,
        cutout: Bitmap,
        d: PosterDesign,
    ) {
        if (d.cards.none { it.behind }) return
        val shadow = subjectShadow(cutout)
        d.cards.forEach { card ->
            if (!card.behind) return@forEach
            val g = geom(d, card)
            val r = g.cw * card.radius
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, g.cw, g.ch), r, r, Path.Direction.CW) }
            clip.transform(g.matrix)
            c.save()
            c.clipPath(clip)
            c.translate(d.ratio.w * 0.012f, d.ratio.w * 0.022f)
            fillPhotoMatrix(photoM, shadow, photo.bitmap, d)
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.alpha = (255 * 0.62f * card.surface.coerceAtLeast(0.5f)).toInt()
            c.drawBitmap(shadow, photoM, paint)
            paint.reset()
            c.restore()
        }
    }

    /** [bmp] boleh versi kecil; matriks diskalakan agar selalu sejajar dengan foto asli. */
    private fun drawPhotoLayer(
        c: Canvas,
        bmp: Bitmap,
        reference: Bitmap,
        alpha: Int,
        d: PosterDesign,
        filter: ColorMatrixColorFilter?,
    ) {
        fillPhotoMatrix(photoM, bmp, reference, d)
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.alpha = alpha
        paint.colorFilter = filter
        c.drawBitmap(bmp, photoM, paint)
        paint.reset()
    }

    // ---------- cahaya ----------

    private fun drawLight(
        c: Canvas,
        d: PosterDesign,
    ) {
        val l = d.light
        if (l.intensity <= 0f) return
        val w = d.ratio.w.toFloat()
        val h = d.ratio.h.toFloat()
        paint.reset()
        paint.isAntiAlias = true
        paint.xfermode = screen
        paint.shader =
            RadialGradient(
                l.x * w, l.y * h, (w * l.size).coerceAtLeast(40f),
                intArrayOf(withAlpha(l.color, 0.62f * l.intensity), withAlpha(l.color, 0.16f * l.intensity), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        c.drawRect(0f, 0f, w, h, paint)
        paint.reset()
    }

    private fun drawVignette(
        c: Canvas,
        d: PosterDesign,
    ) {
        val v = d.adjust.vignette
        if (v <= 0f) return
        val w = d.ratio.w.toFloat()
        val h = d.ratio.h.toFloat()
        paint.reset()
        paint.isAntiAlias = true
        paint.shader =
            RadialGradient(
                w / 2f, h * 0.46f, h * 0.68f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(Color.BLACK, 0.8f * v)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        c.drawRect(0f, 0f, w, h, paint)
        paint.reset()
    }

    private fun drawGrain(
        c: Canvas,
        d: PosterDesign,
    ) {
        val g = d.adjust.grain
        if (g <= 0f) return
        val tile =
            grain ?: Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { b ->
                val r = Random(7)
                val px = IntArray(256 * 256) { val v = r.nextInt(256); Color.argb(255, v, v, v) }
                b.setPixels(px, 0, 256, 0, 0, 256, 256)
                grain = b
            }
        paint.reset()
        paint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.alpha = (g * 70f).toInt()
        c.drawRect(0f, 0f, d.ratio.w.toFloat(), d.ratio.h.toFloat(), paint)
        paint.reset()
    }

    // ---------- burung dan kilau ----------

    /**
     * Siluet burung terbang dilihat dari depan-bawah. [pose] 1 = sayap terangkat, 0 = meluncur, -1 = sayap turun.
     * Sayap dua ruas (lengan lalu tangan) dengan ujung menyapu dan bulu primer bergerigi, badan ramping, ekor bercabang.
     */
    private fun buildBird(pose: Float): Path =
        Path().apply {
            val wristY = -0.02f - 0.075f * (pose + 1f)
            val tipY = 0.20f - 0.27f * (pose + 1f)
            val tipX = 0.5f - 0.03f * pose.coerceAtLeast(0f)
            fillType = Path.FillType.WINDING
            for (s in floatArrayOf(-1f, 1f)) {
                moveTo(0f, -0.035f)
                quadTo(s * 0.10f, -0.075f + wristY * 0.2f, s * 0.24f, wristY)
                quadTo(s * 0.38f, wristY + (tipY - wristY) * 0.25f - 0.02f, s * tipX, tipY)
                // tepi belakang bergerigi: bulu primer
                lineTo(s * (tipX - 0.045f), tipY + 0.028f)
                lineTo(s * (tipX - 0.07f), tipY + 0.012f)
                lineTo(s * (tipX - 0.10f), tipY + 0.045f)
                lineTo(s * (tipX - 0.13f), tipY + 0.03f)
                lineTo(s * (tipX - 0.17f), tipY + 0.062f + (wristY - tipY) * 0.1f)
                quadTo(s * 0.22f, wristY + 0.085f, s * 0.12f, 0.045f)
                quadTo(s * 0.05f, 0.06f, 0f, 0.055f)
                close()
            }
            // badan + kepala kecil
            addOval(-0.032f, -0.06f, 0.032f, 0.14f, Path.Direction.CW)
            addCircle(0f, -0.085f, 0.026f, Path.Direction.CW)
            // ekor bercabang
            moveTo(-0.022f, 0.11f)
            lineTo(-0.05f, 0.235f)
            lineTo(0f, 0.19f)
            lineTo(0.05f, 0.235f)
            lineTo(0.022f, 0.11f)
            close()
        }

    private fun drawBirds(
        c: Canvas,
        d: PosterDesign,
    ) {
        val e = d.effects
        val n = (e.birds * BIRDS.size).toInt().coerceIn(0, BIRDS.size)
        if (n == 0 || e.birdOpacity <= 0f) return
        val w = d.ratio.w.toFloat()
        val h = d.ratio.h.toFloat()
        val unit = w * 0.1f * (0.6f + 0.9f * e.birdSize)
        // yang terbesar (terdekat) digambar paling akhir; BIRDS sudah terurut menurut skala
        val shown = BIRDS.take(n)
        for (b in shown) {
            val near = ((b[2] - 0.28f) / 1.0f).coerceIn(0f, 1f)
            // jauh: lebih pudar dan sedikit kabur seperti kabut atmosfer; dekat: gelap dan tajam
            val alpha = e.birdOpacity * (0.45f + 0.5f * near)
            val tone = (46 - 32 * near).toInt()
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.argb((255 * alpha).toInt().coerceIn(0, 255), tone, tone + 6, tone + 14)
            if (modern && near < 0.45f) paint.maskFilter = BlurMaskFilter(unit * b[2] * 0.035f + 0.6f, BlurMaskFilter.Blur.NORMAL)
            val size = unit * b[2]
            c.save()
            c.translate(w * (0.06f + b[0] * 0.88f), h * (0.04f + b[1] * 0.5f))
            c.rotate(b[3])
            c.scale(size * b[5], size * (0.82f + 0.18f * near))
            c.drawPath(birdPaths[b[4].toInt() % 3], paint)
            c.restore()
        }
        paint.reset()
    }

    /** Kilau hanya di sudut kartu; tidak ada bintang lepas di poster. */
    private fun drawStars(
        c: Canvas,
        d: PosterDesign,
    ) {
        d.cards.indices.forEach { drawCardStar(c, d, it) }
    }

    private fun drawCardStar(
        c: Canvas,
        d: PosterDesign,
        index: Int,
    ) {
        val e = d.effects
        if (e.stars <= 0f || e.starOpacity <= 0f) return
        val card = d.cards[index]
        val unit = d.ratio.w * (0.014f + 0.03f * e.starSize)
        val g = geom(d, card)
        val corner = floatArrayOf(g.cw * 0.02f, g.ch * 0.02f)
        g.matrix.mapPoints(corner)
        sparkle(c, corner[0], corner[1], unit * (0.8f + 0.5f * e.stars), e.starOpacity * e.stars, 0.55f + (index % 3) * 0.2f)
    }

    private fun sparkle(
        c: Canvas,
        x: Float,
        y: Float,
        r: Float,
        strength: Float,
        streak: Float,
    ) {
        c.save()
        c.translate(x, y)
        paint.reset()
        paint.isAntiAlias = true
        paint.xfermode = screen
        paint.shader =
            RadialGradient(
                0f, 0f, r * 1.7f,
                intArrayOf(Color.argb((190 * strength).toInt().coerceIn(0, 255), 255, 255, 255), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
        c.drawCircle(0f, 0f, r * 1.7f, paint)
        paint.shader =
            LinearGradient(
                -r * 2.4f, 0f, r * 2.4f, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb((170 * strength * streak).toInt().coerceIn(0, 255), 255, 255, 255), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
        c.drawRect(-r * 2.4f, -r * 0.022f, r * 2.4f, r * 0.022f, paint)
        paint.shader = null
        paint.xfermode = null
        paint.color = Color.WHITE
        paint.alpha = (255 * strength.coerceIn(0.4f, 1f)).toInt()
        val k = r * 0.075f
        path.reset()
        path.moveTo(0f, -r)
        path.quadTo(k, -k, r, 0f)
        path.quadTo(k, k, 0f, r)
        path.quadTo(-k, k, -r, 0f)
        path.quadTo(-k, -k, 0f, -r)
        path.close()
        c.drawPath(path, paint)
        c.restore()
        paint.reset()
    }

    // ---------- kartu ----------

    private fun drawCard(
        c: Canvas,
        scene: PosterScene,
        d: PosterDesign,
        index: Int,
        accent: Int,
    ) {
        val card = d.cards[index]
        val track = scene.tracks.firstOrNull { it.id == card.trackId } ?: return
        val cover = scene.covers.getOrNull(scene.tracks.indexOf(track)) ?: scene.fallbackCover
        val g = geom(d, card)
        val cw = g.cw
        val ch = g.ch
        val radius = cw * card.radius
        val rect = RectF(0f, 0f, cw, ch)
        val devicePath = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        devicePath.transform(g.matrix)
        val shadowOk = modern

        val s = d.shadow
        if (shadowOk && s.opacity > 0f) {
            val rad = Math.toRadians(s.angle.toDouble())
            val dist = s.distance * cw
            c.save()
            c.clipOutPath(devicePath)
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.BLACK
            paint.setShadowLayer(
                (s.blur * cw).coerceAtLeast(1f),
                (cos(rad) * dist).toFloat(),
                (sin(rad) * dist).toFloat(),
                withAlpha(s.color, s.opacity),
            )
            c.drawPath(devicePath, paint)
            c.restore()
        }
        if (shadowOk && card.glow > 0f) {
            c.save()
            c.clipOutPath(devicePath)
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.BLACK
            paint.setShadowLayer(cw * (0.05f + 0.1f * card.glow), 0f, 0f, withAlpha(d.light.color, 0.85f * card.glow))
            c.drawPath(devicePath, paint)
            c.restore()
        }

        if (shadowOk) {
            val off = edgeOffset(card, cw * 0.06f)
            if (hypot(off[0], off[1]) > 0.6f) {
                val wall = Path(devicePath)
                val step = Path()
                for (k in 1..3) {
                    step.set(devicePath)
                    step.offset(off[0] * k / 3f, off[1] * k / 3f)
                    wall.op(step, Path.Op.UNION)
                }
                c.save()
                c.clipOutPath(devicePath)
                paint.reset()
                paint.isAntiAlias = true
                paint.color = Color.argb(((if (card.style == CardStyle.CLEAR || card.style == CardStyle.LIQUID) 100 else 165) * card.surface.coerceAtLeast(0.4f)).toInt(), 196, 201, 211)
                c.drawPath(wall, paint)
                c.restore()
            }
        }

        // permukaan: tergantung gaya kartu
        c.save()
        c.clipPath(devicePath)
        val photo = scene.photo
        when {
            card.style == CardStyle.CLEAR -> Unit
            photo == null -> {
                paint.reset()
                paint.color = withAlpha(shade(accent, 0.35f), card.surface)
                c.drawPaint(paint)
            }
            card.style == CardStyle.LIQUID -> drawLiquid(c, photo, d, card, g)
            else -> drawPhotoLayer(c, photo.blurred, photo.bitmap, (card.surface * 255).toInt(), d, photoFilter(d.adjust))
        }
        c.restore()

        c.save()
        c.concat(g.matrix)
        drawCardBody(c, track, cover, d, card, cw, ch, radius, rect)
        c.restore()
        paint.reset()
    }

    /** Kaca cair: foto tajam diperbesar sedikit (lensa), tepi dibiaskan lebih kuat, ditutup highlight di drawCardBody. */
    private fun drawLiquid(
        c: Canvas,
        photo: PosterPhoto,
        d: PosterDesign,
        card: CardState,
        g: CardGeom,
    ) {
        val center = floatArrayOf(g.cw / 2f, g.ch / 2f)
        g.matrix.mapPoints(center)
        val filter = photoFilter(d.adjust)
        val a = (card.surface * 255).toInt()
        c.save()
        c.scale(1.3f, 1.3f, center[0], center[1])
        drawPhotoLayer(c, photo.bitmap, photo.bitmap, a, d, filter)
        c.restore()

        val inset = g.cw * 0.05f
        val inner = Path().apply {
            addRoundRect(
                RectF(inset, inset, g.cw - inset, g.ch - inset),
                (g.cw * card.radius - inset).coerceAtLeast(1f),
                (g.cw * card.radius - inset).coerceAtLeast(1f),
                Path.Direction.CW,
            )
        }
        inner.transform(g.matrix)
        c.save()
        c.clipPath(inner)
        c.scale(1.07f, 1.07f, center[0], center[1])
        drawPhotoLayer(c, photo.blurred, photo.bitmap, (a * 0.5f).toInt(), d, filter)
        drawPhotoLayer(c, photo.bitmap, photo.bitmap, (a * 0.5f).toInt(), d, filter)
        c.restore()

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = g.cw * 0.01f
        paint.color = Color.argb((70 * card.surface).toInt(), 255, 255, 255)
        c.drawPath(inner, paint)
        paint.reset()
    }

    private fun drawCardBody(
        c: Canvas,
        track: PosterTrack,
        cover: Bitmap?,
        d: PosterDesign,
        card: CardState,
        cw: Float,
        ch: Float,
        radius: Float,
        rect: RectF,
    ) {
        val a = card.surface
        val style = card.style
        val tint =
            when (style) {
                CardStyle.FROSTED -> intArrayOf(118, 120, 126, 120)
                CardStyle.DARK -> intArrayOf(18, 20, 26, 140)
                CardStyle.MILK -> intArrayOf(240, 243, 248, 120)
                CardStyle.MILK_GRAY -> intArrayOf(208, 210, 214, 178)
                CardStyle.MILK_PEARL -> intArrayOf(226, 224, 229, 165)
                CardStyle.MILK_MIST -> intArrayOf(214, 221, 228, 155)
                CardStyle.MILK_SATIN -> intArrayOf(199, 201, 207, 205)
                CardStyle.MILK_STONE -> intArrayOf(192, 188, 182, 185)
                CardStyle.MILK_LATTE -> intArrayOf(210, 199, 188, 175)
                CardStyle.MILK_SMOKE -> intArrayOf(166, 168, 175, 192)
                CardStyle.MILK_GRAPHITE -> intArrayOf(112, 114, 121, 200)
                CardStyle.LIQUID -> intArrayOf(255, 255, 255, 22)
                CardStyle.CLEAR -> intArrayOf(255, 255, 255, 8)
            }
        val sheen =
            when (style) {
                CardStyle.LIQUID -> 1.8f
                CardStyle.CLEAR -> 0.6f
                CardStyle.MILK_SATIN -> 1.5f
                CardStyle.MILK_PEARL -> 1.25f
                else -> 1f
            }
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.argb((tint[3] * a).toInt(), tint[0], tint[1], tint[2])
        c.drawRoundRect(rect, radius, radius, paint)
        paint.shader =
            LinearGradient(
                0f, 0f, cw, ch,
                intArrayOf(
                    Color.argb((70 * a * sheen).toInt().coerceAtMost(255), 255, 255, 255),
                    Color.argb((14 * a * sheen).toInt().coerceAtMost(255), 214, 220, 230),
                    Color.argb((44 * a * sheen).toInt().coerceAtMost(255), 255, 255, 255),
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        c.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        if (style == CardStyle.LIQUID) {
            c.save()
            path.reset()
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            c.clipPath(path)
            paint.shader =
                RadialGradient(cw * 0.12f, ch * 0.07f, cw * 0.65f, Color.argb((120 * a).toInt(), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(rect, paint)
            paint.shader =
                RadialGradient(cw * 0.92f, ch * 0.96f, cw * 0.55f, Color.argb((55 * a).toInt(), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(rect, paint)
            paint.shader = null
            c.restore()
        }

        // tepi: highlight menghadap sumber cahaya
        if (card.edge > 0f) {
            val dx = d.light.x * d.ratio.w - card.cx * d.ratio.w
            val dy = d.light.y * d.ratio.h - card.cy * d.ratio.h
            val ang = atan2(dy, dx) - card.rotation * PI.toFloat() / 180f
            val half = hypot(cw, ch) / 2f
            val ux = cos(ang)
            val uy = sin(ang)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cw * 0.016f
            paint.shader =
                LinearGradient(
                    cw / 2f + ux * half, ch / 2f + uy * half, cw / 2f - ux * half, ch / 2f - uy * half,
                    intArrayOf(Color.argb((235 * card.edge).toInt(), 255, 255, 255), Color.argb((26 * card.edge).toInt(), 255, 255, 255), Color.argb((120 * card.edge).toInt(), 255, 255, 255)),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            c.drawRoundRect(rect, radius, radius, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        }
        if (card.borderWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cw * card.borderWidth
            paint.color = card.borderColor
            val inset = paint.strokeWidth / 2f
            c.drawRoundRect(RectF(inset, inset, cw - inset, ch - inset), radius - inset, radius - inset, paint)
            paint.style = Paint.Style.FILL
        }

        val pad = cw * 0.075f
        val artSize = cw - pad * 2
        val art = RectF(pad, pad, pad + artSize, pad + artSize)
        val artR = cw * 0.05f
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.BLACK
        paint.setShadowLayer(cw * 0.05f, 0f, cw * 0.02f, Color.argb(90, 0, 0, 0))
        // shadow layer pada canvas hardware pra-P hanya aman untuk teks; lewati di sana
        if (modern) c.drawRoundRect(art, artR, artR, paint)
        paint.clearShadowLayer()
        c.save()
        path.reset()
        path.addRoundRect(art, artR, artR, Path.Direction.CW)
        c.clipPath(path)
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val src = Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
            paint.reset()
            paint.isFilterBitmap = true
            c.drawBitmap(cover, src, art, paint)
        } else {
            paint.reset()
            paint.color = Color.argb(60, 255, 255, 255)
            c.drawRect(art, paint)
        }
        c.restore()

        // metadata asli, hanya dipotong secara visual dengan elipsis
        text.reset()
        text.isAntiAlias = true
        text.setShadowLayer(cw * 0.012f, 0f, cw * 0.004f, Color.argb(if (card.style == CardStyle.CLEAR || card.style == CardStyle.LIQUID) 175 else 120, 0, 0, 0))
        text.typeface = sansBold
        text.textSize = cw * 0.074f
        text.color = Color.WHITE
        val maxText = cw - pad * 2 - if (card.showPlayer) cw * 0.12f else 0f
        c.drawText(TextUtils.ellipsize(track.title, text, maxText, TextUtils.TruncateAt.END).toString(), pad, cw * 1.085f, text)
        text.typeface = sans
        text.textSize = cw * 0.052f
        text.color = Color.argb(185, 255, 255, 255)
        c.drawText(TextUtils.ellipsize(track.artist, text, maxText, TextUtils.TruncateAt.END).toString(), pad, cw * 1.15f, text)

        if (!card.showPlayer) return

        // grafis pemutar; murni dekorasi, tanpa posisi atau durasi pemutaran
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(220, 255, 255, 255)
        c.save()
        c.translate(cw - pad - cw * 0.045f, cw * 1.065f)
        c.scale(cw * 0.05f, cw * 0.05f)
        paint.strokeWidth = 0.22f
        c.drawPath(heart, paint)
        c.restore()

        val ty = cw * 1.215f
        paint.reset()
        paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = cw * 0.011f
        paint.color = Color.argb(95, 255, 255, 255)
        c.drawLine(pad, ty, cw - pad, ty, paint)

        val cy = cw * 1.31f
        val u = cw * 0.052f
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        c.drawRoundRect(cw / 2f - u * 0.55f, cy - u * 0.8f, cw / 2f - u * 0.15f, cy + u * 0.8f, u * 0.1f, u * 0.1f, paint)
        c.drawRoundRect(cw / 2f + u * 0.15f, cy - u * 0.8f, cw / 2f + u * 0.55f, cy + u * 0.8f, u * 0.1f, u * 0.1f, paint)
        drawSkip(c, cw / 2f - cw * 0.2f, cy, u, false)
        drawSkip(c, cw / 2f + cw * 0.2f, cy, u, true)

        val vy = cw * 1.415f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = cw * 0.009f
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(80, 255, 255, 255)
        c.drawLine(pad + cw * 0.08f, vy, cw - pad - cw * 0.08f, vy, paint)
        paint.color = Color.argb(200, 255, 255, 255)
        c.drawLine(pad + cw * 0.08f, vy, pad + cw * 0.08f + (cw - pad * 2 - cw * 0.16f) * 0.62f, vy, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(200, 255, 255, 255)
        drawSpeaker(c, pad + cw * 0.025f, vy, cw * 0.05f, false)
        drawSpeaker(c, cw - pad - cw * 0.035f, vy, cw * 0.05f, true)

        // waktu dari durasi lagu yang sebenarnya; posisi mengikuti bar dekoratif (62%), bukan posisi putar
        if (track.durationSec > 0) {
            val elapsed = (track.durationSec * 0.62f).toInt()
            text.reset()
            text.isAntiAlias = true
            text.typeface = sans
            text.textSize = cw * 0.04f
            text.color = Color.argb(150, 255, 255, 255)
            text.setShadowLayer(cw * 0.01f, 0f, cw * 0.003f, Color.argb(110, 0, 0, 0))
            val right = "-" + clock(track.durationSec - elapsed)
            c.drawText(clock(elapsed), pad, cw * 1.272f, text)
            c.drawText(right, cw - pad - text.measureText(right), cw * 1.272f, text)
        }
        paint.reset()
    }

    private fun clock(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    private fun drawSpeaker(
        c: Canvas,
        x: Float,
        y: Float,
        u: Float,
        waves: Boolean,
    ) {
        path.reset()
        path.moveTo(x - u * 0.5f, y - u * 0.25f)
        path.lineTo(x - u * 0.2f, y - u * 0.25f)
        path.lineTo(x + u * 0.2f, y - u * 0.55f)
        path.lineTo(x + u * 0.2f, y + u * 0.55f)
        path.lineTo(x - u * 0.2f, y + u * 0.25f)
        path.lineTo(x - u * 0.5f, y + u * 0.25f)
        path.close()
        c.drawPath(path, paint)
        if (!waves) return
        val style = paint.style
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.1f
        paint.strokeCap = Paint.Cap.ROUND
        c.drawArc(RectF(x - u * 0.15f, y - u * 0.38f, x + u * 0.55f, y + u * 0.38f), -55f, 110f, false, paint)
        c.drawArc(RectF(x - u * 0.35f, y - u * 0.62f, x + u * 0.85f, y + u * 0.62f), -55f, 110f, false, paint)
        paint.style = style
    }

    private fun drawSkip(
        c: Canvas,
        cx: Float,
        cy: Float,
        u: Float,
        forward: Boolean,
    ) {
        val dir = if (forward) 1f else -1f
        path.reset()
        for (k in 0..1) {
            val x0 = cx - dir * u * 0.9f + dir * k * u * 0.95f
            path.moveTo(x0, cy - u * 0.75f)
            path.lineTo(x0 + dir * u * 0.95f, cy)
            path.lineTo(x0, cy + u * 0.75f)
            path.close()
        }
        c.drawPath(path, paint)
    }

    private fun buildHeart(): Path =
        Path().apply {
            moveTo(0f, 0.9f)
            cubicTo(-2.2f, -0.4f, -1.2f, -2.0f, 0f, -0.9f)
            cubicTo(1.2f, -2.0f, 2.2f, -0.4f, 0f, 0.9f)
            close()
        }

    // ---------- watermark ----------

    private fun drawWatermark(
        c: Canvas,
        scene: PosterScene,
        d: PosterDesign,
    ) {
        val logo = scene.logo ?: return
        val wm = d.watermark
        val w = d.ratio.w.toFloat()
        val h = d.ratio.h.toFloat()
        val lw = w * wm.size
        val lh = lw * logo.height / logo.width.toFloat()
        val tint = if (wm.darkLogo) Color.BLACK else Color.WHITE

        text.reset()
        text.isAntiAlias = true
        text.typeface = sansBold
        text.textSize = lh * 0.62f
        text.letterSpacing = 0.06f
        val name = "Spectra"
        val nameW = if (wm.showName) text.measureText(name) else 0f
        val gap = if (wm.showName) lh * 0.3f else 0f
        val contentW = lw + gap + nameW
        val pad = if (wm.mode == WatermarkMode.SOLID) lh * 0.45f else 0f
        val boxW = contentW + pad * 2
        val boxH = lh + pad * 2
        val margin = w * wm.margin
        val x = margin + wm.col.coerceIn(0, 2) * (w - margin * 2 - boxW) / 2f
        val y = margin + wm.row.coerceIn(0, 2) * (h - margin * 2 - boxH) / 2f

        if (wm.mode == WatermarkMode.SOLID) {
            // badge selalu opak; opacity hanya berlaku pada logo
            paint.reset()
            paint.isAntiAlias = true
            paint.color = wm.badgeColor or 0xFF000000.toInt()
            val r = boxH / 2f * wm.badgeRadius
            val ok = modern
            if (wm.shadow && ok) paint.setShadowLayer(boxH * 0.18f, 0f, boxH * 0.06f, Color.argb(110, 0, 0, 0))
            c.drawRoundRect(x, y, x + boxW, y + boxH, r, r, paint)
            paint.clearShadowLayer()
        }
        val lx = x + pad
        val ly = y + pad
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        paint.alpha = (wm.opacity * 255).toInt()
        if (wm.mode == WatermarkMode.TRANSPARENT && wm.shadow && modern) {
            paint.setShadowLayer(lh * 0.12f, 0f, lh * 0.04f, Color.argb(120, 0, 0, 0))
        }
        c.drawBitmap(logo, null, RectF(lx, ly, lx + lw, ly + lh), paint)
        if (wm.showName) {
            text.color = tint
            text.alpha = (wm.opacity * 255).toInt()
            c.drawText(name, lx + lw + gap, ly + lh / 2f + text.textSize * 0.35f, text)
        }
        paint.reset()
    }

    // ---------- warna ----------

    private fun shade(color: Int, f: Float) = Color.rgb((Color.red(color) * f).toInt(), (Color.green(color) * f).toInt(), (Color.blue(color) * f).toInt())

    private fun withAlpha(color: Int, a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
