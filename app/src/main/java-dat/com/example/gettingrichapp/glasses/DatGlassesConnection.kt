package com.example.gettingrichapp.glasses

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DatGlassesConnection(private val context: Context) : GlassesConnection {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var deviceObserverJob: Job? = null
    private var metadataObserverJob: Job? = null

    private var permissionLauncher: ActivityResultLauncher<Permission>? = null
    private var pendingPermissionResult: CompletableDeferred<Boolean>? = null

    /**
     * Must be called from Activity.onCreate() before the activity is STARTED,
     * to register the ActivityResultContract for DAT camera permission.
     */
    fun registerPermissionLauncher(activity: ComponentActivity) {
        permissionLauncher = activity.registerForActivityResult(
            Wearables.RequestPermissionContract()
        ) { result ->
            val granted = result.getOrNull() is PermissionStatus.Granted
            Log.d(TAG, "Camera permission result: granted=$granted")
            pendingPermissionResult?.complete(granted)
            pendingPermissionResult = null
        }
    }

    override suspend fun initialize() {
        try {
            Wearables.initialize(context)
        } catch (e: Exception) {
            Log.e(TAG, "SDK init failed", e)
            _connectionState.value = ConnectionState.Error("SDK init failed: ${e.message}")
            return
        }

        // Observe registration state
        scope.launch {
            Wearables.registrationState.collect { state ->
                Log.d(TAG, "Registration state: $state")
                when (state) {
                    is RegistrationState.Registered -> observeDevices()
                    is RegistrationState.Registering -> {
                        _connectionState.value = ConnectionState.Searching
                    }
                    is RegistrationState.Unregistering -> {
                        _connectionState.value = ConnectionState.Searching
                    }
                    is RegistrationState.Available -> {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    is RegistrationState.Unavailable -> {
                        _connectionState.value = ConnectionState.Error(
                            state.error?.toString() ?: "Registration unavailable"
                        )
                    }
                }
            }
        }
    }

    private fun observeDevices() {
        // Cancel any existing device observer
        deviceObserverJob?.cancel()
        metadataObserverJob?.cancel()

        val selector = AutoDeviceSelector()

        // Also directly observe the devices set for debugging
        scope.launch {
            Wearables.devices.collect { deviceSet ->
                Log.d(TAG, "Devices set updated: size=${deviceSet.size}, ids=${deviceSet.map { it.identifier }}")
            }
        }

        deviceObserverJob = scope.launch {
            selector.activeDevice(Wearables.devices).collect { deviceId ->
                Log.d(TAG, "Active device selected: $deviceId")
                observeDeviceMetadata(deviceId)
            }
        }
    }

    private fun observeDeviceMetadata(deviceId: DeviceIdentifier?) {
        if (deviceId == null) {
            Log.d(TAG, "Active device is null, setting Searching")
            _connectionState.value = ConnectionState.Searching
            return
        }

        // Cancel any previous metadata observer to avoid stale collections
        metadataObserverJob?.cancel()

        val metadataFlow = Wearables.devicesMetadata[deviceId]
        if (metadataFlow == null) {
            Log.d(TAG, "No metadata flow for device $deviceId, setting Searching")
            _connectionState.value = ConnectionState.Searching
            return
        }

        metadataObserverJob = scope.launch {
            metadataFlow.collect { metadata ->
                Log.d(TAG, "Device metadata: name=${metadata.name}, available=${metadata.available}, type=${metadata.deviceType}")
                _connectionState.value = if (metadata.available) {
                    ConnectionState.Connected
                } else {
                    ConnectionState.Searching
                }
            }
        }
    }

    override fun startRegistration(activity: Activity) {
        _connectionState.value = ConnectionState.Searching
        try {
            Wearables.startRegistration(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            _connectionState.value = ConnectionState.Error("Registration failed: ${e.message}")
        }
    }

    override fun startUnregistration(activity: Activity) {
        try {
            Wearables.startUnregistration(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Unregistration failed", e)
            _connectionState.value = ConnectionState.Error("Unregistration failed: ${e.message}")
        }
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun hasCameraPermission(): Boolean {
        return try {
            val result = Wearables.checkPermissionStatus(Permission.CAMERA)
            result.getOrNull() is PermissionStatus.Granted
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun requestCameraPermission(activity: Activity): Boolean {
        // Already granted?
        if (hasCameraPermission()) return true

        val launcher = permissionLauncher
        if (launcher == null) {
            Log.e(TAG, "Permission launcher not registered — call registerPermissionLauncher() from Activity.onCreate()")
            return false
        }

        val deferred = CompletableDeferred<Boolean>()
        pendingPermissionResult = deferred
        launcher.launch(Permission.CAMERA)
        return deferred.await()
    }

    override fun release() {
        deviceObserverJob?.cancel()
        metadataObserverJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val TAG = "DatGlassesConnection"
    }
}
