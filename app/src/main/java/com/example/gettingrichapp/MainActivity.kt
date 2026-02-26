package com.example.gettingrichapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.gettingrichapp.glasses.DatGlassesConnection
import com.example.gettingrichapp.ui.navigation.AppNavigation
import com.example.gettingrichapp.ui.theme.GettingRichAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            initializeGlasses()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register the DAT camera permission launcher before the activity is STARTED.
        // This must happen before setContent so the contract is registered in time.
        val glasses = ServiceLocator.glassesConnection
        if (glasses is DatGlassesConnection) {
            glasses.registerPermissionLauncher(this)
        }

        if (hasBluetoothPermissions()) {
            initializeGlasses()
        } else {
            requestBluetoothPermissions()
        }

        setContent {
            GettingRichAppTheme {
                AppNavigation()
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }

    private fun initializeGlasses() {
        activityScope.launch {
            ServiceLocator.glassesConnection.initialize()
        }
    }
}
