/**
 * Spectra Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.spectra.app.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.spectra.app.BuildConfig
import ru.lazard.tamperingprotection.TamperingProtection
import kotlin.system.exitProcess

/**
 * Proteksi anti-modifikasi (AndroidTamperingProtection): nama paket, tanda tangan APK, dan mode debug.
 * Hanya aktif di build non-debug. Tanda tangan yang diizinkan dihitung saat build dari keystore yang
 * dipakai build itu sendiri (BuildConfig.TAMPER_SIGNATURES); bila kosong, cek tanda tangan dilewati.
 */
object AppIntegrityGuard {
    /** @return true bila aman dilanjutkan, false bila terdeteksi dimodifikasi (proses dihentikan). */
    fun check(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        val protection = TamperingProtection(context).apply {
            setAcceptedPackageNames(BuildConfig.APPLICATION_ID)
            val sigs = BuildConfig.TAMPER_SIGNATURES.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (sigs.isNotEmpty()) setAcceptedSignatures(*sigs.toTypedArray())
            setAcceptStartInDebugMode(false)
            setAcceptStartOnEmulator(true)
        }
        return try {
            protection.validateAllOrThrowException()
            true
        } catch (e: TamperingProtection.ValidationException) {
            Log.e("AppIntegrityGuard", "Integritas gagal (kode ${e.errorCode})")
            Toast.makeText(context, "Aplikasi terdeteksi dimodifikasi. Pasang ulang dari sumber resmi.", Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
                exitProcess(1)
            }, 2500)
            false
        }
    }
}
