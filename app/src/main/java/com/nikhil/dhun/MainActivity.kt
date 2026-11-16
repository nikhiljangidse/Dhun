package com.nikhil.dhun

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.nikhil.dhun.data.HistoryStore
import com.nikhil.dhun.data.MusicLibrary
import com.nikhil.dhun.data.PlaybackPrefs
import com.nikhil.dhun.data.PlaylistStore
import com.nikhil.dhun.playback.PlayerConnection
import com.nikhil.dhun.ui.DhunApp
import com.nikhil.dhun.ui.PermissionScreen
import com.nikhil.dhun.ui.theme.DhunTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
            if (granted) refreshLibrary()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.nikhil.dhun.data.CrashLog.install(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        HistoryStore.init(this)
        PlaylistStore.init(this)
        PlaybackPrefs.init(this)
        PlayerConnection.connect(this)

        hasPermission = isGranted()
        if (!hasPermission) permissionLauncher.launch(audioPermission)

        setContent {
            DhunTheme {
                if (hasPermission) {
                    DhunApp()
                } else {
                    PermissionScreen(
                        onAllow = { permissionLauncher.launch(audioPermission) },
                        onOpenSettings = {
                            try {
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                                )
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.connect(this)
    }

    override fun onResume() {
        super.onResume()
        hasPermission = isGranted()
        if (hasPermission) refreshLibrary()
    }

    override fun onDestroy() {
        if (isFinishing) PlayerConnection.release()
        super.onDestroy()
    }

    private fun isGranted(): Boolean =
        checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED

    private fun refreshLibrary() {
        lifecycleScope.launch { MusicLibrary.refresh(this@MainActivity) }
    }
}
