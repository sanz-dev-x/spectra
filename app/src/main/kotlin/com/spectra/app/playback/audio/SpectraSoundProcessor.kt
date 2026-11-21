package com.spectra.app.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Satu jalur DSP untuk balance, instrument focus, soundstage, dan adaptive limiter.
 * Hanya aktif untuk PCM 16-bit stereo; format lain dilewatkan tanpa diproses.
 */
@UnstableApi
class SpectraSoundProcessor : BaseAudioProcessor() {
    private var sr = 48000
    private var bandCoefs = FloatArray(0)

    // low-pass satu kutub untuk mid (M) dan side (S)
    private var mLow = 0f
    private var mVocLo = 0f
    private var mVocHi = 0f
    private var sVocLo = 0f
    private var sVocHi = 0f
    private var sWide = 0f
    private var crossLpL = 0f
    private var crossLpR = 0f

    private var envFast = 0f
    private var envSlow = 0f

    private var curBalL = 1f
    private var curBalR = 1f

    // delay line stage (early reflections) dan crossfeed
    private var dlL = FloatArray(0)
    private var dlR = FloatArray(0)
    private var dlPos = 0
    private var tap1 = 0
    private var tap2 = 0
    private var crossDelay = 0

    // limiter
    private var limDelayL = FloatArray(0)
    private var limDelayR = FloatArray(0)
    private var limPos = 0
    private var lookahead = 0
    private var limGain = 1f
    private var attackCoef = 0f
    private var relFast = 0f
    private var relSlow = 0f
    private var reductionHold = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sr = inputAudioFormat.sampleRate
        allocate()
        return inputAudioFormat
    }

    private fun lp(freq: Float) = 1f - exp(-2.0 * Math.PI * freq / sr).toFloat()

    private fun allocate() {
        bandCoefs = floatArrayOf(lp(140f), lp(250f), lp(3800f), lp(300f), lp(700f))
        val dlSize = (sr * 0.04f).toInt().coerceAtLeast(64)
        dlL = FloatArray(dlSize)
        dlR = FloatArray(dlSize)
        tap1 = (sr * 0.011f).toInt()
        tap2 = (sr * 0.019f).toInt()
        crossDelay = (sr * 0.0003f).toInt().coerceAtLeast(1)
        lookahead = (sr * 0.001f).toInt().coerceAtLeast(8)
        limDelayL = FloatArray(lookahead)
        limDelayR = FloatArray(lookahead)
        attackCoef = exp(-1.0 / (0.0004 * sr)).toFloat()
        relFast = exp(-1.0 / (0.05 * sr)).toFloat()
        relSlow = exp(-1.0 / (0.35 * sr)).toFloat()
        resetState()
    }

    private fun resetState() {
        mLow = 0f; mVocLo = 0f; mVocHi = 0f; sVocLo = 0f; sVocHi = 0f; sWide = 0f
        crossLpL = 0f; crossLpR = 0f
        envFast = 0f; envSlow = 0f
        dlL.fill(0f); dlR.fill(0f); dlPos = 0
        limDelayL.fill(0f); limDelayR.fill(0f); limPos = 0
        limGain = 1f; reductionHold = 0f
        val b = SpectraSound.params.balance
        curBalL = if (b > 0f) 1f - b else 1f
        curBalR = if (b < 0f) 1f + b else 1f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / 4
        if (frames == 0) return
        val out = replaceOutputBuffer(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        val p = SpectraSound.params
        if (p.neutral && limGain >= 0.9999f) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val inp = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        process(p, frames, { inp.short / 32768f }, { v -> out.putShort(toShort(v)) })
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onQueueEndOfStream() {
        if (lookahead == 0) return
        val p = SpectraSound.params
        if (p.neutral || !p.limiterEnabled) return
        val out = replaceOutputBuffer(lookahead * 4).order(ByteOrder.LITTLE_ENDIAN)
        process(p, lookahead, { 0f }, { v -> out.putShort(toShort(v)) })
        out.flip()
    }

    override fun onFlush() {
        if (lookahead > 0) resetState()
    }

    override fun onReset() {
        bandCoefs = FloatArray(0)
        dlL = FloatArray(0); dlR = FloatArray(0)
        limDelayL = FloatArray(0); limDelayR = FloatArray(0)
        lookahead = 0
    }

    private fun toShort(v: Float): Short = if (v.isNaN()) 0 else (v * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()

    private inline fun process(
        p: SpectraSoundParams,
        frames: Int,
        readSample: () -> Float,
        write: (Float) -> Unit,
    ) {
        val aLow = bandCoefs[0]
        val aVocLo = bandCoefs[1]
        val aVocHi = bandCoefs[2]
        val aWide = bandCoefs[3]
        val aCross = bandCoefs[4]

        // gain focus (dB -> linear), amount -1..1
        val fa = if (p.focus == InstrumentFocus.OFF) 0f else p.focusAmount.coerceIn(-1f, 1f)
        val dbPos = if (fa >= 0f) fa else 0f
        val dbNeg = if (fa < 0f) fa else 0f
        val vocalGain = 10f.pow((dbPos * 5f + dbNeg * 9f) / 20f)
        val bassGain = 10f.pow((dbPos * 5f + dbNeg * 9f) / 20f)
        val sideVocalGain = if (p.focus == InstrumentFocus.VOCAL && fa > 0f) 10f.pow(-fa * 2.5f / 20f) else 1f
        val drums = p.focus == InstrumentFocus.DRUMS && fa != 0f
        val bass = p.focus == InstrumentFocus.BASS && fa != 0f
        val vocal = p.focus == InstrumentFocus.VOCAL && fa != 0f

        // soundstage
        var width = 1f
        var wideHf = 0f
        var presence = 0f
        var reflect = 0f
        var crossfeed = 0f
        when (p.soundstage) {
            SoundstageProfile.OFF -> Unit
            SoundstageProfile.INTIMATE -> { width = 0.78f; presence = 0.19f }
            SoundstageProfile.WIDE -> { wideHf = 0.4f }
            SoundstageProfile.STAGE -> { width = 1.1f; reflect = 0.2f }
            SoundstageProfile.IN_EAR -> { crossfeed = 0.28f; width = 0.95f }
        }

        val b = p.balance.coerceIn(-1f, 1f)
        val tgtL = if (b > 0f) 1f - b else 1f
        val tgtR = if (b < 0f) 1f + b else 1f
        val stepL = (tgtL - curBalL) / frames
        val stepR = (tgtR - curBalR) / frames

        val thr = 0.891f // -1 dBFS
        val dlSize = dlL.size

        repeat(frames) {
            val inL = readSample()
            val inR = readSample()
            var m = (inL + inR) * 0.5f
            var s = (inL - inR) * 0.5f

            // ---- Instrument Focus
            mLow += aLow * (m - mLow)
            mVocLo += aVocLo * (m - mVocLo)
            mVocHi += aVocHi * (m - mVocHi)
            val vocBandM = mVocHi - mVocLo
            sVocLo += aVocLo * (s - sVocLo)
            sVocHi += aVocHi * (s - sVocHi)
            val vocBandS = sVocHi - sVocLo

            var transient = 1f
            if (vocal) {
                m += (vocalGain - 1f) * vocBandM
                s += (sideVocalGain - 1f) * vocBandS
            } else if (bass) {
                m += (bassGain - 1f) * mLow
            } else if (drums) {
                val x = max(abs(inL), abs(inR))
                envFast += (x - envFast) * (if (x > envFast) 0.2f else 0.01f)
                envSlow += (x - envSlow) * (if (x > envSlow) 0.012f else 0.004f)
                val r = ((envFast - envSlow) / (envSlow + 0.002f)).coerceIn(-1f, 1f)
                transient = (1f + fa * 0.6f * r).coerceIn(0.5f, 1.8f)
            }

            // ---- Soundstage
            if (presence != 0f) m += presence * vocBandM
            if (wideHf != 0f) {
                sWide += aWide * (s - sWide)
                s += wideHf * (s - sWide)
            }
            s *= width

            var l = (m + s) * transient
            var r = (m - s) * transient

            if (reflect != 0f) {
                val i1 = (dlPos - tap1 + dlSize) % dlSize
                val i2 = (dlPos - tap2 + dlSize) % dlSize
                dlL[dlPos] = l
                dlR[dlPos] = r
                l += reflect * (dlR[i1] * 0.7f + dlL[i2] * 0.5f)
                r += reflect * (dlL[i1] * 0.7f + dlR[i2] * 0.5f)
                dlPos = (dlPos + 1) % dlSize
            } else if (crossfeed != 0f) {
                dlL[dlPos] = l
                dlR[dlPos] = r
                val idx = (dlPos - crossDelay + dlSize) % dlSize
                crossLpL += aCross * (dlL[idx] - crossLpL)
                crossLpR += aCross * (dlR[idx] - crossLpR)
                val nl = l * (1f - crossfeed * 0.5f) + crossLpR * crossfeed
                val nr = r * (1f - crossfeed * 0.5f) + crossLpL * crossfeed
                l = nl
                r = nr
                dlPos = (dlPos + 1) % dlSize
            }

            // ---- Balance (diratakan per buffer agar tidak berderak)
            curBalL += stepL
            curBalR += stepR
            l *= curBalL
            r *= curBalR

            // ---- Adaptive Limiter dengan lookahead 1 ms
            if (p.limiterEnabled) {
                val peak = max(abs(l), abs(r))
                val need = if (peak > thr) thr / peak else 1f
                if (need < limGain) {
                    limGain = need + (limGain - need) * attackCoef
                    reductionHold = min(1f, reductionHold + 0.002f)
                } else {
                    val rel = relFast + (relSlow - relFast) * reductionHold
                    limGain = 1f + (limGain - 1f) * rel
                    reductionHold *= 0.9999f
                }
                val dl = limDelayL[limPos]
                val dr = limDelayR[limPos]
                limDelayL[limPos] = l
                limDelayR[limPos] = r
                limPos = (limPos + 1) % lookahead
                val g = if (need < limGain) need else limGain
                write((dl * g).coerceIn(-0.9995f, 0.9995f))
                write((dr * g).coerceIn(-0.9995f, 0.9995f))
            } else {
                limGain = 1f
                write(l.coerceIn(-1f, 1f))
                write(r.coerceIn(-1f, 1f))
            }
        }
        curBalL = tgtL
        curBalR = tgtR
    }
}
