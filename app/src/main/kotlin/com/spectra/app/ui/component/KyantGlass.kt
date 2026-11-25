package com.spectra.app.ui.component

import androidx.compose.ui.unit.dp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.effect
import com.spectra.app.ui.glass.GlassEdgeHd
import com.spectra.app.ui.glass.GlassHd
import com.spectra.app.ui.glass.GlassHdEnabled
import com.spectra.app.ui.glass.GlassTouch
import com.spectra.app.ui.glass.glassTouchLight
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.spectra.app.ui.glass.GlassBlur
import com.spectra.app.ui.glass.GlassMotion
import com.spectra.app.ui.glass.GlassAdapt
import com.spectra.app.ui.glass.GlassTouchLiquid
import com.spectra.app.ui.glass.GlassTouchLiquidEnabled
import com.spectra.app.ui.glass.GlassTouchLiquidSpec
import com.spectra.app.ui.glass.GlassTouchLiquidState
import com.spectra.app.ui.glass.glassTouchLiquidTrack
import com.spectra.app.ui.glass.GlassClear
import com.spectra.app.ui.glass.GlassEnergyEnabled
import com.spectra.app.ui.glass.GlassLensGate
import com.spectra.app.ui.glass.GlassHighlight
import com.spectra.app.ui.glass.GlassRegular
import com.spectra.app.ui.glass.GlassScrim
import com.spectra.app.ui.glass.GlassShadow
import com.spectra.app.ui.glass.GlassTint
import com.spectra.app.ui.theme.GlassMode
import com.spectra.app.ui.theme.LocalGlassMode

/** Backdrop Kyant (Liquid Glass). Null di bawah Android 13, jadi pemanggil jatuh ke kaca lama. */
val LocalKyantBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Putih lembut (bukan putih asli) dan hijau lembut (bukan hijau pekat) untuk latar liquid glass. */
val SoftWhite = Color(0xFFF5F6F1)
val SoftGreen = Color(0xFFF5F6F1)

val DarkBaseTop = Color(0xFF000000)
val DarkBaseMid = Color(0xFF000000)
val DarkBaseBottom = Color(0xFF000000)

/**
 * Latar dua diagonal: kanan atas putih lembut -> tengah hijau lembut -> kiri bawah putih lembut,
 * sedangkan kiri atas hijau lembut -> tengah hijau lembut -> kanan bawah putih lembut.
 * Diagonal pertama digambar penuh, diagonal kedua memudarkan sudut kanan bawah ke warna [bottom].
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftDiagonal(top: Color, mid: Color, bottom: Color) {
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
    drawRect(
        Brush.linearGradient(
            colorStops =
                arrayOf(
                    0f to bottom.copy(alpha = 0f),
                    0.5f to bottom.copy(alpha = 0f),
                    0.82f to bottom.copy(alpha = 0.7f),
                    1f to bottom.copy(alpha = 0.97f),
                ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        ),
    )
}

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
    val top by animateColorAsState(if (baseTop.isSpecified) baseTop else if (dark) DarkBaseTop else SoftWhite, tweenSpec, label = "baseTop")
    val mid by animateColorAsState(if (baseMid.isSpecified) baseMid else if (dark) DarkBaseMid else SoftGreen, tweenSpec, label = "baseMid")
    val bottom by animateColorAsState(if (baseBottom.isSpecified) baseBottom else if (dark) DarkBaseBottom else SoftWhite, tweenSpec, label = "baseBottom")
    return rememberLayerBackdrop {
        when (currentMode) {
            // Diagonal: putih transparan di kanan atas, warna artwork pudar di tengah, putih lagi di kiri bawah.
            GlassMode.DARK, GlassMode.BRIGHT -> drawSoftDiagonal(top, mid, bottom)
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

internal val SoftGlass = Color(0xFFDADCE1)

internal val SoftGlassHigh = Color(0xFFEDEEF2)

/**
 * Liquid Glass memakai library Kyant: saturasi, blur, dan lens (refraksi nyata, Android 13+).
 * Navbar tipis; mini player ([intense]) memakai blur dan lensa mini.
 * Mengembalikan null bila backdrop belum tersedia, supaya pemanggil memakai fallback.
 */
@Composable
internal fun Modifier.kyantChrome(
    shape: Shape,
    light: Boolean,
    intense: Boolean,
    fillScale: Float = 1f,
    blurEnabled: Boolean = true,
    extraScrim: (() -> Float)? = null,
    energy: (() -> Float)? = null,
    hd: Boolean = true,
    @Suppress("UNUSED_PARAMETER") water: Boolean = false, // dipertahankan agar pemanggil tidak berubah; ripple idle diganti distorsi sentuh
    tuning: com.spectra.app.ui.glass.GlassTuning? = null,
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
    val navBlur = LocalGlassNavBlur.current.coerceIn(0.1f, 2f)
    val refractable = shape is CornerBasedShape
    val blurDp = (if (intense) GlassBlur.mini else GlassBlur.nav).pick(dark)
    val lensSpec = (if (intense) GlassClear.lensMini else GlassClear.lensNav).pick(dark)
    val edgeAlpha = GlassHighlight.edgeAlpha.pick(dark)
    val shadowAlpha = GlassShadow.alpha.pick(dark)
    val innerRadius = if (intense) GlassShadow.innerRadiusMini else GlassShadow.innerRadiusNav
    val innerAlpha = GlassShadow.innerAlpha.pick(dark)
    val fill = GlassRegular.fill.pick(dark)
    val sheen = GlassRegular.sheen.pick(dark)
    val shade = GlassRegular.shade.pick(dark)
    val scrimAlpha = GlassScrim.alpha.pick(dark)
    val purpleAlpha = GlassTint.purple.pick(dark)
    val hdEffect = remember(hd) { if (hd && GlassHdEnabled && mode != GlassMode.OFF && Build.VERSION.SDK_INT >= 33) GlassEdgeHd() else null }
    // Navbar (intense = false) tidak pernah ikut warna lagu; hanya mini player yang tidak berubah.
    val followSong = intense
    val envColor = (if (followSong) LocalAmbientGlow.current?.a else null).let { (it ?: ChromeOrange).copy(alpha = 1f) }
    val hdSpecK = GlassHd.specK.pick(dark)
    val hdEnvK = GlassHd.envK.pick(dark)
    // Distorsi cair di titik sentuh menggantikan ripple idle: tidak ada animasi atau frame tambahan saat tidak disentuh.
    val liquidOn = GlassTouchLiquidEnabled && mode != GlassMode.OFF && Build.VERSION.SDK_INT >= 33
    val liquidState = remember(liquidOn) { if (liquidOn) GlassTouchLiquidState() else null }
    val liquidFx = remember(liquidOn) { if (liquidOn && Build.VERSION.SDK_INT >= 33) GlassTouchLiquid() else null }
    val liquidSpecK = GlassTouchLiquidSpec.specK.pick(dark)
    val refraction = LocalGlassRefraction.current.coerceIn(0f, 1.5f)
    val adaptSource =
        if (GlassAdapt.ENABLED && mode != GlassMode.OFF && followSong && com.spectra.app.ui.theme.LocalCoverBackground.current) {
            LocalGlassBackdrop.current?.songColor?.takeIf { it.isSpecified } ?: LocalAmbientGlow.current?.a
        } else {
            null
        }
    val adaptTint =
        animateColorAsState(
            targetValue = adaptSource?.let { clampGlassTint(it, GlassAdapt.lumMin.pick(dark), GlassAdapt.lumMax.pick(dark)) } ?: Color.Transparent,
            animationSpec = tween(durationMillis = GlassMotion.COLOR_MS),
            label = "glassAdaptTint",
        )
    val adaptAlpha = GlassAdapt.tintAlpha.pick(dark)
    val e = { if (GlassEnergyEnabled) (energy?.invoke() ?: 0f).coerceIn(0f, 1f) else 0f }
    return glassTouchLight().then(if (liquidState != null) Modifier.glassTouchLiquidTrack(liquidState) else Modifier).drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = GlassClear.SATURATION + 0.2f * e())
            val userBlur = tuning?.blur ?: 0f
            if (userBlur > 0.001f) {
                blur((userBlur * com.spectra.app.ui.glass.GlassTuningMaxBlurDp).dp.toPx())
            } else if (blurEnabled) {
                blur((blurDp * navBlur).toPx())
            }
            if (liquidFx != null && liquidState != null && GlassLensGate.enabled && refraction > 0f) {
                val minDim = minOf(size.width, size.height)
                liquidFx
                    .effect(
                        w = size.width,
                        h = size.height,
                        touchX = liquidState.x,
                        touchY = liquidState.y,
                        radius = minOf(GlassTouchLiquidSpec.RADIUS_DP.dp.toPx(), minDim * 0.95f),
                        peak = GlassTouchLiquidSpec.PEAK_DP.dp.toPx() * refraction,
                        energy = liquidState.energy.value,
                        lightX = -0.55f + 0.25f * GlassTilt.x + 0.9f * GlassTouch.x,
                        lightY = -0.75f + 0.25f * GlassTilt.y + 0.9f * GlassTouch.y,
                        specK = liquidSpecK,
                    )?.let { effect(it) }
            }
            if (hdEffect != null && refractable && GlassLensGate.enabled) {
                val en = e()
                val half = minOf(size.width, size.height) * 0.5f
                val cr = (shape as CornerBasedShape).topStart.toPx(size, this).coerceIn(0f, half)
                hdEffect
                    .effect(
                        w = size.width,
                        h = size.height,
                        cr = cr,
                        bezel = GlassHd.bezel.toPx().coerceAtMost(half),
                        specW = GlassHd.SPEC_WIDTH_DP.dp.toPx(),
                        lightX = -0.55f + 0.25f * GlassTilt.x + 0.9f * GlassTouch.x * en,
                        lightY = -0.75f + 0.25f * GlassTilt.y + 0.9f * GlassTouch.y * en,
                        env = envColor,
                        envK = hdEnvK * (1f + 0.27f * en),
                        specK = hdSpecK * (1f + 0.64f * en),
                        specExp = GlassHd.SPEC_EXP + GlassHd.SPEC_EXP_GAIN * en,
                    )?.let { effect(it) }
            }
            if (refractable) {
                lens(
                    refractionHeight = (lensSpec.height.toPx() + 4.dp.toPx() * e()) * (if (blurEnabled) 1f else 1.2f),
                    refractionAmount = lensSpec.amount.toPx() * (1f + 0.45f * e()) * (if (blurEnabled) 1f else 1.25f),
                )
            }
        },
        highlight = {
            Highlight(
                width = GlassHighlight.edgeWidth,
                alpha = (edgeAlpha * (1f + 0.35f * e())).coerceAtMost(1f),
                style = HighlightStyle.Default(angle = GlassHighlight.ANGLE_BASE + GlassHighlight.TILT_X * GlassTilt.x - GlassHighlight.TILT_Y * GlassTilt.y),
            )
        },
        shadow = { Shadow(radius = GlassShadow.radius + 8.dp * e(), alpha = (shadowAlpha * (1f + 0.4f * e())).coerceAtMost(1f)) },
        innerShadow = { InnerShadow(radius = innerRadius, alpha = innerAlpha) },
        onDrawSurface = {
            val glassOpacity = 1f - (tuning?.transparency ?: 0f)
            val sheenK = sheen.copy(alpha = sheen.alpha * glassOpacity)
            val shadeK = shade.copy(alpha = shade.alpha * glassOpacity)
            drawRect(fill, alpha = fillScale * glassOpacity)
            val tint = adaptTint.value
            if (tint.alpha > 0f) drawRect(tint.copy(alpha = adaptAlpha * glassOpacity))
            if (scrimAlpha > 0f) drawRect(Color.Black.copy(alpha = scrimAlpha * glassOpacity))
            if (intense) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(sheenK, Color.Transparent),
                        center = Offset(size.width * (0.22f + 0.08f * GlassTilt.x), -size.height * 0.1f),
                        radius = size.width * 0.55f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(ChromePurple.copy(alpha = purpleAlpha * glassOpacity), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 1.15f),
                        radius = size.width * 0.5f,
                    ),
                )
            }
            drawRect(
                Brush.verticalGradient(
                    0f to sheenK,
                    0.45f to Color.Transparent,
                    1f to shadeK,
                ),
            )
            val extra = extraScrim?.invoke() ?: 0f
            if (extra > 0f) drawRect(Color.Black.copy(alpha = extra.coerceAtMost(GlassScrim.MAX_ALPHA)))
        },
    )
}

/** Menjaga luminans tint dalam [lo]..[hi] dengan mencampur ke hitam/putih, supaya mode tetap terbaca. */
internal fun clampGlassTint(c: Color, lo: Float, hi: Float): Color {
    val l = c.copy(alpha = 1f).luminance()
    return when {
        l > hi -> lerp(c.copy(alpha = 1f), Color.Black, ((l - hi) / l).coerceIn(0f, 1f))
        l < lo -> lerp(c.copy(alpha = 1f), Color.White, ((lo - l) / (1f - l)).coerceIn(0f, 1f))
        else -> c.copy(alpha = 1f)
    }
}
