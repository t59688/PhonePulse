package com.aizeek.phonepulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import com.aizeek.phonepulse.update.UpdateRepository
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.aizeek.phonepulse.service.ScreenTrackerService
import com.aizeek.phonepulse.ui.MainScreen
import com.aizeek.phonepulse.ui.theme.BgDark
import com.aizeek.phonepulse.ui.theme.PhonePulseTheme

class MainActivity : ComponentActivity() {
    private var updateOpenRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateOpenRequest = savedInstanceState?.getInt("update_open_request") ?: 0
        if (savedInstanceState == null && intent.action == UpdateRepository.OPEN_UPDATES) updateOpenRequest++
        enableEdgeToEdge()

        // Ensure background service is running
        try {
            ScreenTrackerService.start(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            PhonePulseTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        try {
                            ScreenTrackerService.start(this@MainActivity)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark
                ) {
                    MainScreen(updateOpenRequest = updateOpenRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UpdateRepository.OPEN_UPDATES) updateOpenRequest++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("update_open_request", updateOpenRequest)
        super.onSaveInstanceState(outState)
    }
}
