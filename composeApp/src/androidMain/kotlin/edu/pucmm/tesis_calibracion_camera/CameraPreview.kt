package edu.pucmm.tesis_calibracion_camera

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraApp(cameraController: LifecycleCameraController) {
    MaterialTheme {
        CameraPreview(
            controller = cameraController,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                this.controller = controller
            }
        },
        modifier = modifier
    )
}
