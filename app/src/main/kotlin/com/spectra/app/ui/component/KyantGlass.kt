package com.spectra.app.ui.component

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode
import kotlin.math.max

/** Backdrop Kyant (Liquid Glass). Null di bawah Android 13, jadi pemanggil jatuh ke kaca lama. */
val LocalKyantBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Dasar yang digambar di belakang konten layar supaya kaca punya sesuatu untuk dibiaskan.
 * Selain gradien dasar, ada tiga cahaya radial berwarna (glow) yang warnanya beranimasi halus
 * saat tema berubah. Tanpa latar berwarna, lensa hanya membiaskan warna polos dan kaca terlihat mati.
 */
@Composable
fun rememberKyantBackdrop(
    mode: GlassMode,
    screenColor: Color,
    baseTop: Color = Color.Unspecified,
    baseMid: Color = Color.Unspecified,
    baseBottom: Color = Color.Unspecified,
): LayerBackdrop {
    val currentMode by rememberUpdatedState(mode)
    val screen by rememberUpdatedState(screenColor)
    val dark = mode == GlassMode.DARK
    val tweenSpec = tween<Color>(durationMillis = 900)
    val top by animateColorAsState(if (baseTop.isSpecified) baseTop else if (dark) Color(0xFF2B1F19) else Color(0xFFE9D6D3), tweenSpec, label = "baseTop")
    val mid by animateColorAsState(if (baseMid.isSpecified) baseMid else if (dark) Color(0xFF17110E) else Color(0xFFEFE5E3), tweenSpec, label = "baseMid")
    val bottom by animateColorAsState(if (baseBottom.isSpecified) baseBottom else if (dark) Color(0xFF0D0A09) else Color(0xFFF7F3F1), tweenSpec, label = "baseBottom")
    return rememberLayerBackdrop {
        when (currentMode) {
            // Diagonal: putih transparan di kanan atas, warna artwork pudar di tengah, putih lagi di kiri bawah.
            GlassMode.DARK, GlassMode.BRIGHT ->
                drawRect(
                    Brush.linearGradient(
                        colorStops =
                            arrayOf(
                                0f to top,
                                0.22f to top,
                                0.38f to lerp(top, mid, 0.6f),
                                0.5f to mid,
                                0.62f to lerp(bottom, mid, 0.6f),
                                0.78f to bottom,
                                1f to bottom,
                            ),
                        start = Offset(size.width, 0f),
                        end = Offset(0f, size.height),
                    ),
                )
            GlassMode.OFF -> drawRect(screen)
        }
        drawContent()
    }
}

/**
 * Kemiringan HP (-1..1) dari akselerometer untuk menggeser kilau spekular kaca. Sensor hanya aktif
 * selama aplikasi terlihat, dan dimatikan di HP RAM kecil atau saat animasi sistem dimatikan.
 */
internal object GlassTilt {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set
    private var baseX = 0f
    private var baseY = 0f
    private var sx = 0f
    private var sy = 0f
    private var primed = false
    private var listener: SensorEventListener? = null

    fun start(context: Context) {
        if (listener != null) return
        if (Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        primed = false
        val l =
            object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val ax = e.values[0] / SensorManager.GRAVITY_EARTH
                    val ay = e.values[1] / SensorManager.GRAVITY_EARTH
                    if (!primed) {
                        baseX = ax
                        baseY = ay
                        primed = true
                    }
                    baseX += (ax - baseX) * 0.02f
                    baseY += (ay - baseY) * 0.02f
                    val tx = ((ax - baseX) * 3.2f).coerceIn(-1f, 1f)
                    val ty = ((ay - baseY) * 3.2f).coerceIn(-1f, 1f)
                    sx += (tx - sx) * 0.18f
                    sy += (ty - sy) * 0.18f
                    if (kotlin.math.abs(sx - x) > 0.004f) x = sx
                    if (kotlin.math.abs(sy - y) > 0.004f) y = sy
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
        listener = l
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop(context: Context) {
        val l = listener ?: return
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(l)
        listener = null
        x = 0f
        y = 0f
    }
}

/** Panggil sekali di tema: menyalakan sensor saat aktivitas STARTED dan mematikannya saat STOPPED. */
@Composable
internal fun GlassTiltEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val owner: LifecycleOwner? =
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<LifecycleOwner>()
                .firstOrNull()
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> GlassTilt.start(context)
                    Lifecycle.Event.ON_STOP -> GlassTilt.stop(context)
                    else -> Unit
                }
            }
        if (owner != null) {
            owner.lifecycle.addObserver(observer)
        } else {
            GlassTilt.start(context)
        }
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            GlassTilt.stop(context)
        }
    }
}

/** Tiga warna glow latar (tint dinamis tema/lagu), dipakai bersama oleh backdrop kaca dan latar layar yang terlihat. */
class AmbientGlowColors(val a: Color, val b: Color, val c: Color)

val LocalAmbientGlow = androidx.compose.runtime.compositionLocalOf<AmbientGlowColors?> { null }

class AmbientGlowState(
    val a: androidx.compose.runtime.State<Color>,
    val b: androidx.compose.runtime.State<Color>,
    val c: androidx.compose.runtime.State<Color>,
)

/** Warna glow beranimasi halus (tween 900ms seperti Auralys). Dibaca di fase gambar, tanpa rekomposisi. */
@Composable
fun rememberAmbientGlow(mode: GlassMode): AmbientGlowState {
    val glow = LocalAmbientGlow.current
    val dark = mode == GlassMode.DARK
    val spec = tween<Color>(durationMillis = 900)
    val a = animateColorAsState((glow?.a ?: ChromeOrange).copy(alpha = if (dark) 0.40f else 0.38f), spec, label = "bgGlowA")
    val b = animateColorAsState((glow?.b ?: ChromeAmber).copy(alpha = if (dark) 0.24f else 0.22f), spec, label = "bgGlowB")
    val c = animateColorAsState((glow?.c ?: ChromePurple).copy(alpha = if (dark) 0.28f else 0.26f), spec, label = "bgGlowC")
    return AmbientGlowState(a, b, c)
}

/** Tiga radial gradient Auralys (radius 1.15 / 0.95 / 1.1) di latar layar yang terlihat. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAmbientGlow(g: AmbientGlowState) {
    val r = size.maxDimension
    drawRect(Brush.radialGradient(listOf(g.a.value, Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.18f), radius = r * 1.15f))
    drawRect(Brush.radialGradient(listOf(g.b.value, Color.Transparent), center = Offset(size.width * 0.88f, size.height * 0.52f), radius = r * 0.95f))
    drawRect(Brush.radialGradient(listOf(g.c.value, Color.Transparent), center = Offset(size.width * 0.35f, size.height * 0.97f), radius = r * 1.1f))
}

/** Menandai konten yang direkam sebagai backdrop untuk kaca navbar dan mini player. */
@Composable
fun Modifier.kyantBackdropSource(): Modifier {
    val backdrop = LocalKyantBackdrop.current ?: return this
    return layerBackdrop(backdrop)
}

/** Ungu halus untuk pantulan krom di tepi bawah. */
internal val ChromePurple = Color(0xFF8E6BBF)

private class MeltDrip(val at: Float, val width: Float, val length: Float)

/** Posisi, lebar (dp), dan panjang relatif tetesan. Sengaja tidak simetris supaya terlihat organik. */
private val MeltDrips =
    listOf(
        MeltDrip(0.10f, 9f, 0.55f),
        MeltDrip(0.29f, 14f, 1.00f),
        MeltDrip(0.47f, 7f, 0.38f),
        MeltDrip(0.63f, 11f, 0.78f),
        MeltDrip(0.81f, 15f, 0.92f),
        MeltDrip(0.94f, 7f, 0.42f),
    )

/**
 * Formasi kaca cair yang meleleh ke bawah dari tepi bawah. Digambar SEBELUM permukaan kaca Kyant
 * (di belakangnya), jadi pangkalnya menyatu dengan badan kaca. Ini geometri nyata (path leher + bulir)
 * dengan material krom, bukan refraksi: refraksi latar hanya terjadi di badan utama lewat lens Kyant,
 * karena lens hanya jalan pada CornerBasedShape di dalam batas layout. Tidak mengubah ukuran layout.
 */
internal fun Modifier.liquidMelt(dark: Boolean, intense: Boolean, alpha: () -> Float): Modifier =
    drawWithCache {
        val w = size.width
        val h = size.height
        val maxLen = (if (intense) 12f else 8f).dp.toPx()
        val overlap = 4.dp.toPx()
        val margin = h * 0.5f
        val span = max(w - margin * 2f, 1f)
        val top = h - overlap
        val bottom = h + maxLen
        val body =
            Brush.verticalGradient(
                0f to ChromeChampagne.copy(alpha = 0f),
                0.22f to ChromeChampagne.copy(alpha = if (dark) 0.50f else 0.62f),
                0.52f to ChromeAmber.copy(alpha = 0.62f),
                0.78f to ChromeCopper.copy(alpha = 0.66f),
                1f to ChromePurple.copy(alpha = 0.50f),
                startY = top,
                endY = bottom,
            )
        val rim =
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.35f to ChromeWarmWhite.copy(alpha = 0.75f),
                1f to ChromeBrown.copy(alpha = if (dark) 0.55f else 0.40f),
                startY = top,
                endY = bottom,
            )
        val stroke = Stroke(width = 0.8.dp.toPx())
        class Built(val path: Path, val cx: Float, val bcY: Float, val r: Float)
        val built =
            MeltDrips.map { d ->
                val cx = margin + d.at * span
                val hw = d.width.dp.toPx() / 2f
                val len = maxLen * d.length
                val r = hw
                val neckY = h + len * 0.45f
                val bcY = max(neckY, h + len - r)
                val baseL = cx - hw * 2.2f
                val baseR = cx + hw * 2.2f
                val path =
                    Path().apply {
                        moveTo(baseL, top)
                        cubicTo(baseL + hw * 1.3f, h + len * 0.05f, cx - hw * 0.55f, h + len * 0.15f, cx - hw * 0.55f, neckY)
                        cubicTo(cx - hw * 0.55f, neckY + len * 0.1f, cx - r, bcY - r * 0.3f, cx - r, bcY)
                        cubicTo(cx - r, bcY + r * 0.55f, cx - r * 0.55f, bcY + r, cx, bcY + r)
                        cubicTo(cx + r * 0.55f, bcY + r, cx + r, bcY + r * 0.55f, cx + r, bcY)
                        cubicTo(cx + r, bcY - r * 0.3f, cx + hw * 0.55f, neckY + len * 0.1f, cx + hw * 0.55f, neckY)
                        cubicTo(cx + hw * 0.55f, h + len * 0.15f, baseR - hw * 1.3f, h + len * 0.05f, baseR, top)
                        close()
                    }
                Built(path, cx, bcY, r)
            }
        onDrawWithContent {
            val a = alpha().coerceIn(0f, 1f)
            if (a > 0.01f) {
                built.forEach { b ->
                    drawPath(b.path, body, alpha = a)
                    clipPath(b.path) {
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                                    center = Offset(b.cx - b.r * 0.35f, b.bcY - b.r * 0.4f),
                                    radius = b.r * 0.75f,
                                ),
                            radius = b.r * 0.75f,
                            center = Offset(b.cx - b.r * 0.35f, b.bcY - b.r * 0.4f),
                            alpha = a,
                        )
                    }
                    drawPath(b.path, rim, alpha = a, style = stroke)
                }
            }
            drawContent()
        }
    }

/**
 * Liquid chrome glass memakai library Kyant: vibrancy, blur, dan lens (refraksi nyata, Android 13+).
 * Permukaan: rona oranye (Hue blend) lalu gradien chrome (pantulan champagne, horizon coklat, bounce amber)
 * dan sapuan spekular. Navbar halus; mini player ([intense]) lebih dalam dengan depthEffect dan glow amber.
 * Mengembalikan null bila backdrop belum tersedia, supaya pemanggil memakai fallback.
 */
@Composable
internal fun Modifier.kyantChrome(
    shape: Shape,
    light: Boolean,
    intense: Boolean,
    meltAlpha: (() -> Float)? = null,
    auralys: Boolean = true,
): Modifier? {
    val backdrop = LocalKyantBackdrop.current
    val mode = LocalGlassMode.current
    val lowQuality = LocalGlassQuality.current == GlassQuality.LOW
    if (backdrop == null || Build.VERSION.SDK_INT < 33 || lowQuality) return null
    val dark =
        when (mode) {
            GlassMode.DARK -> true
            GlassMode.BRIGHT -> false
            GlassMode.OFF -> !light
        }
    val k = if (intense) 1f else 0.6f
    val navBlur = LocalGlassNavBlur.current.coerceIn(0.1f, 2f)
    val refractable = shape is CornerBasedShape
    val melted = if (meltAlpha != null && !auralys) liquidMelt(dark, intense, meltAlpha) else this
    return melted.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (auralys) {
                // Resep kaca Auralys: saturasi 1,7 lalu blur lalu lensa 16dp / 22dp tanpa dispersi.
                colorControls(saturation = 1.7f)
                blur((6f * navBlur).dp.toPx())
                if (refractable) {
                    lens(refractionHeight = 16f.dp.toPx(), refractionAmount = 22f.dp.toPx())
                }
            } else {
                vibrancy()
                blur((if (intense) 5f else 3f).dp.toPx())
                if (refractable) {
                    lens(
                        refractionHeight = (if (intense) 18f else 10f).dp.toPx(),
                        refractionAmount = (if (intense) 36f else 20f).dp.toPx(),
                        depthEffect = intense,
                    )
                }
            }
        },
        highlight =
            if (auralys) {
                {
                    // Highlight tepi asli Kyant; sudut cahaya digeser oleh kemiringan HP.
                    Highlight(
                        width = 1.dp,
                        alpha = if (dark) 0.9f else 0.7f,
                        style = HighlightStyle.Default(angle = 45f + 40f * GlassTilt.x - 25f * GlassTilt.y),
                    )
                }
            } else {
                { Highlight.Default }
            },
        shadow = if (auralys) ({ Shadow(radius = 18.dp, alpha = if (dark) 0.5f else 0.28f) }) else ({ Shadow.Default }),
        innerShadow =
            if (auralys) {
                { InnerShadow(radius = (if (intense) 10f else 8f).dp, alpha = if (dark) 0.55f else 0.35f) }
            } else {
                null
            },
        onDrawSurface = {
            if (auralys) {
                drawRect(if (false) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f))
                if (intense) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = if (false) 0.16f else 0.30f), Color.Transparent),
                            center = Offset(size.width * (0.22f + 0.08f * GlassTilt.x), -size.height * 0.1f),
                            radius = size.width * 0.55f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(ChromePurple.copy(alpha = if (false) 0.22f else 0.12f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 1.15f),
                            radius = size.width * 0.5f,
                        ),
                    )
                }
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (false) 0.14f else 0.30f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (false) 0.10f else 0.04f),
                    ),
                )
            } else {
                drawRect(ChromeOrange, blendMode = BlendMode.Hue)
                drawRect(ChromeOrange.copy(alpha = (if (dark) 0.22f else 0.26f) * k))
                drawRect(
                    Brush.verticalGradient(
                        0f to ChromeChampagne.copy(alpha = (if (dark) 0.30f else 0.44f) * k),
                        0.34f to ChromeAmber.copy(alpha = 0.30f * k),
                        0.50f to ChromeOrange.copy(alpha = 0.14f * k),
                        0.58f to ChromeBrown.copy(alpha = (if (dark) 0.34f else 0.16f) * k),
                        0.80f to ChromeCopper.copy(alpha = 0.26f * k),
                        1f to ChromeAmber.copy(alpha = 0.40f * k),
                    ),
                )
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, ChromeWarmWhite.copy(alpha = 0.20f * k), Color.Transparent),
                        start = Offset(size.width * 0.10f, 0f),
                        end = Offset(size.width * 0.34f, size.height),
                    ),
                )
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, ChromeChampagne.copy(alpha = 0.12f * k), Color.Transparent),
                        start = Offset(size.width * 0.64f, 0f),
                        end = Offset(size.width * 0.78f, size.height),
                    ),
                )
                drawRect(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to ChromePurple.copy(alpha = 0.16f * k),
                    ),
                )
                if (intense) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(ChromeAmber.copy(alpha = 0.34f), Color.Transparent),
                            center = Offset(size.width * 0.18f, size.height * 1.05f),
                            radius = size.height * 2.2f,
                        ),
                    )
                }
            }
        },
    )
}
