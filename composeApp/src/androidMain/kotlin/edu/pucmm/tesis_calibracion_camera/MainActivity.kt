package edu.pucmm.tesis_calibracion_camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import edu.pucmm.tesis_calibracion_camera.utils.CameraCapture
import edu.pucmm.tesis_calibracion_camera.viewmodel.CameraSensorViewModel

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: LifecycleCameraController
    private lateinit var sensorViewModel: CameraSensorViewModel
    private lateinit var cameraCapture: CameraCapture
    
    // Callback para capturar foto desde el botón de volumen
    private var onVolumeDownPressed: (() -> Unit)? = null

    // A state to hold the permission granting status
    private val hasCameraPermission = mutableStateOf(false)

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check if all required permissions were granted
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                hasCameraPermission.value = true // Update state to trigger recomposition
            } else {
                // Show a more helpful message
                val deniedPermissions = permissions.filter { !it.value }.keys.joinToString(", ")
                Toast.makeText(
                    baseContext,
                    "Permisos denegados: $deniedPermissions. La app necesita estos permisos para funcionar.",
                    Toast.LENGTH_LONG
                ).show()
                // Don't set hasCameraPermission to true, keep it false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the sensor view model
        sensorViewModel = CameraSensorViewModel(baseContext)

        // Initialize the controller here
        cameraController = LifecycleCameraController(baseContext).apply {
            bindToLifecycle(this@MainActivity)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Initialize camera capture
        cameraCapture = CameraCapture(baseContext)

        // Check for permissions
        requestPermissionsIfNeeded()

        setContent {
            // Only show the camera preview if permissions are granted
            if (hasCameraPermission.value) {
                CameraPreviewWithGuide(
                    controller = cameraController,
                    viewModel = sensorViewModel,
                    cameraCapture = cameraCapture,
                    onCaptureTrigger = { callback ->
                        onVolumeDownPressed = callback
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Show a message to request permissions
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Se requieren permisos para usar la cámara")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            requestPermissionsIfNeeded()
                        }) {
                            Text("Solicitar Permisos")
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                onVolumeDownPressed?.invoke()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorViewModel.startMonitoring()
    }

    override fun onPause() {
        super.onPause()
        sensorViewModel.stopMonitoring()
    }

    private fun requestPermissionsIfNeeded() {
        // Check if permissions are already granted
        val allPermissionsGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allPermissionsGranted) {
            hasCameraPermission.value = true
        } else {
            // Launch the permission request
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
}
