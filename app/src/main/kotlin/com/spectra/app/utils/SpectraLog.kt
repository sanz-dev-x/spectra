package com.spectra.app.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log error ke /sdcard/Spectra. Android 11+ hanya mengizinkan folder itu bila akses semua file
 * diberikan; jika tidak, log jatuh ke /sdcard/Android/data/<paket>/files/Spectra. Tidak pernah melempar exception.
 */
object SpectraLog {
    private const val DIR = "Spectra"
    private const val MAX_BYTES = 512 * 1024L

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun candidates(): List<File> =
        listOfNotNull(
            File(Environment.getExternalStorageDirectory(), DIR),
            appContext?.getExternalFilesDir(null)?.let { File(it, DIR) },
        )

    @Synchronized
    private fun append(
        name: String,
        text: String,
    ) {
        for (dir in candidates()) {
            try {
                dir.mkdirs()
                val file = File(dir, name)
                if (file.exists() && file.length() > MAX_BYTES) file.renameTo(File(dir, "$name.old"))
                FileOutputStream(file, true).use { it.write(text.toByteArray()) }
                return
            } catch (_: Throwable) {
            }
        }
    }

    fun i(
        tag: String,
        message: String,
    ) = append("log.txt", "${now()} I/$tag: $message\n")

    fun e(
        tag: String,
        message: String,
        t: Throwable? = null,
    ) {
        val trace = t?.let { StringWriter().also { w -> it.printStackTrace(PrintWriter(w)) }.toString() }.orEmpty()
        append("log.txt", "${now()} E/$tag: $message\n$trace\n")
    }

    /** Dipanggil CrashHandler sebelum CrashActivity dibuka, supaya log tetap ada walau layar crash gagal tampil. */
    fun crash(report: String) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        append("crash-$stamp.txt", report)
        append("log.txt", "${now()} FATAL: crash disimpan ke crash-$stamp.txt\n")
    }

    /** Mencatat sebab proses sebelumnya mati (termasuk crash native dan dibunuh sistem) yang tidak lewat CrashHandler. */
    fun logPreviousExits(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val prefs = context.getSharedPreferences("spectra_log", Context.MODE_PRIVATE)
            val last = prefs.getLong("lastExitTs", 0L)
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            var newest = last
            for (info in exits.sortedBy { it.timestamp }) {
                if (info.timestamp <= last) continue
                newest = maxOf(newest, info.timestamp)
                val reason = exitReasonName(info.reason)
                append(
                    "log.txt",
                    "${now()} EXIT: proses ${info.processName} mati $reason (status=${info.status}, " +
                        "importance=${info.importance}, desc=${info.description}, at=${Date(info.timestamp)})\n",
                )
                if (info.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE) dumpNative(info)
            }
            prefs.edit().putLong("lastExitTs", newest).apply()
        } catch (t: Throwable) {
            e("SpectraLog", "gagal membaca exit info", t)
        }
    }

    /** Tombstone crash native berbentuk protobuf; teks yang terbaca (nama library, fungsi, pesan abort) diambil supaya sumber SIGSEGV bisa dilacak. */
    private fun dumpNative(info: android.app.ApplicationExitInfo) {
        try {
            val bytes = info.traceInputStream?.use { it.readBytes() }
            if (bytes == null) {
                append("log.txt", "${now()} NATIVE: tidak ada tombstone yang tersedia\n")
                return
            }
            val out = StringBuilder()
            val cur = StringBuilder()
            for (b in bytes.take(400_000)) {
                val c = b.toInt() and 0xFF
                if (c in 32..126) {
                    cur.append(c.toChar())
                } else {
                    if (cur.length >= 5) out.append(cur).append('\n')
                    cur.setLength(0)
                }
            }
            if (cur.length >= 5) out.append(cur)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(info.timestamp))
            append("native-$stamp.txt", out.toString().take(200_000))
            append("log.txt", "${now()} NATIVE: teks tombstone disimpan ke native-$stamp.txt (${bytes.size} byte)\n")
        } catch (t: Throwable) {
            e("SpectraLog", "gagal membaca tombstone", t)
        }
    }

    private fun exitReasonName(r: Int): String =
        when (r) {
            android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
            android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
            android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            else -> "REASON_$r"
        }

    /** Sekali saja: membuka layar izin "akses semua file" supaya log bisa masuk ke /sdcard/Spectra (Android 11+). */
    fun requestStorageAccessOnce(context: Context) {
        try {
            if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) return
            val prefs = context.getSharedPreferences("spectra_log", Context.MODE_PRIVATE)
            if (prefs.getBoolean("askedAllFiles", false)) return
            prefs.edit().putBoolean("askedAllFiles", true).apply()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Throwable) {
        }
    }
}
