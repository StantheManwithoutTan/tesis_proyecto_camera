package edu.pucmm.tesis_calibracion_camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: LifecycleCameraController

    // A state to hold the permission granting status
    private val hasCameraPermission = mutableStateOf(false)

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check if all required permissions were granted
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                hasCameraPermission.value = true // Update state to trigger recomposition
            } else {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the controller here
        cameraController = LifecycleCameraController(baseContext).apply {
            bindToLifecycle(this@MainActivity)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }

        // Check for permissions
        requestPermissions()

        setContent {
            // Only show the camera preview if permissions are granted
            if (hasCameraPermission.value) {
                CameraPreview(
                    controller = cameraController,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // You could add an else block here to show a message
            // to the user if permissions are not granted.
        }
    }

    private fun requestPermissions() {
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
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
