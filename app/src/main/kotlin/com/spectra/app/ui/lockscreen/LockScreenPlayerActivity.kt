package com.spectra.app.ui.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.spectra.app.db.MusicDatabase
import com.spectra.app.playback.MusicService
import com.spectra.app.playback.MusicService.MusicBinder
import com.spectra.app.playback.PlayerConnection
import com.spectra.app.ui.theme.SpectraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Pemutar di atas lockscreen. Ditutup otomatis saat perangkat dibuka kuncinya. */
@AndroidEntryPoint
class LockScreenPlayerActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    private var connection by mutableStateOf<PlayerConnection?>(null)
    private var bound = false

    private val serviceConnection =
        object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service is MusicBinder) {
                    connection = PlayerConnection(this@LockScreenPlayerActivity, service, database, lifecycleScope)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connection?.dispose()
                connection = null
            }
        }

    private val unlockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bound = bindService(Intent(this, MusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
        setContent {
            SpectraTheme(darkTheme = isSystemInDarkTheme()) {
                connection?.let { LockScreenPlayer(it, onUnlock = ::requestUnlock, onUnlockToNotifications = ::finish, onEmpty = ::finish) }
            }
        }
    }

    private fun requestUnlock() {
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
            },
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        if (bound) runCatching { unbindService(serviceConnection) }
        bound = false
        connection?.dispose()
        connection = null
        super.onDestroy()
    }
}
