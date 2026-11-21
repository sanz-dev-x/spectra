/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.ui.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first

private const val SubStep = 1f / 240f
private const val MaxSubSteps = 16
private const val MaxFrame = 0.05f
private const val SpeedRef = 5f
private const val LeanRate = 14f

private const val LeadK = 1100f
private const val LeadZ = 1f
private const val TrailK = 170f
private const val TrailZ = 1f

private const val MinWidth = 0.85f
private const val MaxWidth = 1.9f
private const val PopX = 0.155f
private const val MoveStretch = 0.3f
private const val StretchSpeed = 0.12f

private const val DriveK = 260f
private const val DriveZ = 1f
private const val PressDrive = 0.8f
private const val LiftGain = 0.2258f
private const val FastK = 200f
private const val FastZ = 1f

private const val HeightK = 260f
private const val HeightZ = 1f

private const val SquashK = 220f
private const val SquashZ = 1f

private const val ShiftK = 160f
private const val ShiftZ = 1f

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Satu sistem pegas untuk lensa dan navbar. Tepi depan dan tepi belakang lensa punya pegas sendiri
 * dengan kekakuan berbeda menurut arah gerak, sehingga regangan, penyusutan, dan trailing
 * muncul dari fisika, bukan dari nilai statis. Tinggi lensa, penyusutan navbar, dan
 * dorongan navbar membaca hasil yang sama, jadi waktunya selalu konsisten. Integrasi memakai delta
 * waktu dengan sub-langkah tetap, sehingga hasilnya sama di 60, 90, dan 120 Hz.
 */
@Stable
internal class LiquidLensPhysics {
    var ready by mutableStateOf(false)
        private set
    var leftPx by mutableFloatStateOf(0f)
        private set
    var rightPx by mutableFloatStateOf(0f)
        private set
    var heightScale by mutableFloatStateOf(1f)
        private set
    var drive by mutableFloatStateOf(0f)
        private set
    var navSquash by mutableFloatStateOf(0f)
        private set
    var navShift by mutableFloatStateOf(0f)
        private set
    var fastness by mutableFloatStateOf(0f)
        private set
    var leanDir by mutableFloatStateOf(0f)
        private set
    var settled by mutableStateOf(true)
        private set

    val centerX: Float get() = (leftPx + rightPx) / 2f

    private var left = 0f
    private var right = 0f
    private var leftVel = 0f
    private var rightVel = 0f
    private var lean = 0f
    private var driveValue = 0f
    private var driveVel = 0f
    private var height = 1f
    private var heightVel = 0f
    private var squash = 0f
    private var squashVel = 0f
    private var shift = 0f
    private var shiftVel = 0f
    private var fast = 0f
    private var fastVel = 0f

    fun leftOr(fallback: Float): Float = if (ready) leftPx else fallback

    fun rightOr(fallback: Float): Float = if (ready) rightPx else fallback

    fun reset(x: Float, itemWidth: Float) {
        left = x
        right = x + itemWidth
        leftVel = 0f
        rightVel = 0f
        lean = 0f
        driveValue = 0f
        driveVel = 0f
        height = 1f
        heightVel = 0f
        squash = 0f
        squashVel = 0f
        shift = 0f
        shiftVel = 0f
        fast = 0f
        fastVel = 0f
        publish()
        settled = true
        ready = true
    }

    private fun isOff(x: Float, itemWidth: Float): Boolean =
        ready && itemWidth > 0f && (abs(left - x) > 0.5f || abs(right - (x + itemWidth)) > 0.5f)

    suspend fun simulate(
        driven: () -> Boolean,
        position: () -> Float,
        velocity: () -> Float,
        itemWidth: () -> Float,
        pressed: () -> Boolean,
    ) {
        while (true) {
            snapshotFlow { driven() || !settled || isOff(position(), itemWidth()) }.first { it }
            var last = withFrameNanos { it }
            while (driven() || !settled || isOff(position(), itemWidth())) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, MaxFrame)
                last = now
                step(dt, position(), velocity(), itemWidth(), pressed())
            }
        }
    }

    private fun step(dt: Float, x: Float, v: Float, itemWidth: Float, pressed: Boolean) {
        if (!ready || itemWidth <= 0f) return
        var remaining = dt
        var guard = 0
        while (remaining > 1e-6f && guard < MaxSubSteps) {
            val h = minOf(remaining, SubStep)
            integrate(h, x, v, itemWidth, pressed)
            remaining -= h
            guard++
        }
        val rest = isResting(x, itemWidth, pressed)
        if (rest) {
            left = x
            right = x + itemWidth
            leftVel = 0f
            rightVel = 0f
            driveValue = 0f
            driveVel = 0f
            height = 1f
            heightVel = 0f
            squash = 0f
            squashVel = 0f
            shift = 0f
            shiftVel = 0f
            fast = 0f
            fastVel = 0f
        }
        publish()
        settled = rest
    }

    private fun integrate(h: Float, x: Float, v: Float, itemWidth: Float, pressed: Boolean) {
        val vn = (v / (itemWidth * SpeedRef)).coerceIn(-1.5f, 1.5f)
        val speed = abs(vn).coerceAtMost(1f)
        lean += (vn.coerceIn(-1f, 1f) - lean) * (1f - exp(-h * LeanRate))
        val lead = lean * 0.5f + 0.5f

        val kRight = mix(TrailK, LeadK, lead)
        val kLeft = mix(LeadK, TrailK, lead)
        val zRight = mix(TrailZ, LeadZ, lead)
        val zLeft = mix(LeadZ, TrailZ, lead)

        val act = (driveValue * 2f).coerceIn(0f, 1f)
        val rs = (lean / StretchSpeed).coerceIn(0f, 1f)
        val ls = (-lean / StretchSpeed).coerceIn(0f, 1f)
        val rightTarget = x + itemWidth * (1f + PopX * act + MoveStretch * rs)
        val leftTarget = x - itemWidth * (PopX * act + MoveStretch * ls)

        rightVel += (kRight * (rightTarget - right) - 2f * zRight * sqrt(kRight) * rightVel) * h
        right += rightVel * h
        leftVel += (kLeft * (leftTarget - left) - 2f * zLeft * sqrt(kLeft) * leftVel) * h
        left += leftVel * h

        val width = right - left
        val minW = itemWidth * MinWidth
        val maxW = itemWidth * MaxWidth
        if (width < minW || width > maxW) {
            val target = width.coerceIn(minW, maxW)
            val mid = (left + right) / 2f
            val mv = (leftVel + rightVel) / 2f
            left = mid - target / 2f
            right = mid + target / 2f
            leftVel = mv
            rightVel = mv
        }

        val driveTarget = maxOf(speed, if (pressed) PressDrive else 0f)
        driveVel += (DriveK * (driveTarget - driveValue) - 2f * DriveZ * sqrt(DriveK) * driveVel) * h
        driveValue += driveVel * h

        val wf = ((right - left) / itemWidth).coerceIn(0.5f, 2f)
        val extra = (wf - (1f + PopX * act)).coerceAtLeast(0f)
        val heightTarget = (1f + LiftGain * act - 0.15f * extra).coerceAtLeast(1f + 0.7f * LiftGain * act)
        heightVel += (HeightK * (heightTarget - height) - 2f * HeightZ * sqrt(HeightK) * heightVel) * h
        height += heightVel * h

        val squashTarget = ((wf - 1f) * 2.3f).coerceIn(-0.6f, 1.2f)
        squashVel += (SquashK * (squashTarget - squash) - 2f * SquashZ * sqrt(SquashK) * squashVel) * h
        squash += squashVel * h

        val fastTarget = ((speed - 0.4f) / 0.4f).coerceIn(0f, 1f)
        fastVel += (FastK * (fastTarget - fast) - 2f * FastZ * sqrt(FastK) * fastVel) * h
        fast += fastVel * h

        val shiftTarget = vn.coerceIn(-1f, 1f)
        shiftVel += (ShiftK * (shiftTarget - shift) - 2f * ShiftZ * sqrt(ShiftK) * shiftVel) * h
        shift += shiftVel * h
    }

    private fun isResting(x: Float, itemWidth: Float, pressed: Boolean): Boolean =
        !pressed &&
            abs(left - x) < 0.1f &&
            abs(right - (x + itemWidth)) < 0.1f &&
            abs(leftVel) < 2f &&
            abs(rightVel) < 2f &&
            abs(driveValue) < 0.003f &&
            abs(driveVel) < 0.02f &&
            abs(height - 1f) < 0.003f &&
            abs(heightVel) < 0.02f &&
            abs(squash) < 0.003f &&
            abs(squashVel) < 0.02f &&
            abs(shift) < 0.003f &&
            abs(shiftVel) < 0.02f &&
            abs(fast) < 0.003f &&
            abs(fastVel) < 0.02f

    private fun publish() {
        leftPx = left
        rightPx = right
        heightScale = maxOf(height, 1f + LiftGain * (driveValue * 3f).coerceIn(0f, 1f))
        drive = driveValue.coerceIn(0f, 1.2f)
        navSquash = squash
        navShift = shift
        fastness = fast.coerceIn(0f, 1f)
        leanDir = lean.coerceIn(-1f, 1f)
    }
}
