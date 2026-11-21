package com.spectra.app.ui.lockscreen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Layanan opsional agar pemutar lockscreen dapat diluncurkan saat layar terkunci.
 * Tidak membaca layar, tidak meminta event, dan tidak mengambil konten jendela.
 */
class LockScreenAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: LockScreenAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }
}
