package edu.pucmm.tesis_calibracion_camera

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import edu.pucmm.tesis_calibracion_camera.utils.CameraCapture
import edu.pucmm.tesis_calibracion_camera.viewmodel.CameraSensorViewModel

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

@Composable
fun CameraPreviewWithGuide(
    controller: LifecycleCameraController,
    viewModel: CameraSensorViewModel,
    cameraCapture: CameraCapture,
    onCaptureTrigger: ((callback: () -> Unit) -> Unit),
    modifier: Modifier = Modifier
) {
    var lastCaptureTime by remember { mutableStateOf(0L) }
    val minCaptureCooldown = 1000L // 1 segundo entre capturas
    val context = LocalContext.current
    
    val captureCallback = remember {
        {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCaptureTime >= minCaptureCooldown) {
                if (viewModel.sensorReadings.value.isAligned) {
                    cameraCapture.takePicture(
                        controller = controller,
                        onSuccess = { message ->
                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onError = { error ->
                            Toast.makeText(
                                context,
                                error,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    lastCaptureTime = currentTime
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        onCaptureTrigger(captureCallback)
    }
    
    Box(modifier = modifier) {
        // Cámara de fondo
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Guía de alineación en overlay
        AlignmentGuide(
            sensorReadings = viewModel.sensorReadings.value,
            modifier = Modifier.fillMaxSize()
        )
        
        // Botón de captura en la parte inferior
        if (viewModel.sensorReadings.value.isAligned) {
            Button(
                onClick = captureCallback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                enabled = viewModel.sensorReadings.value.isAligned
            ) {
                Text("Capturar Foto", fontSize = 16.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(Color.Red.copy(alpha = 0.7f))
                    .padding(12.dp)
            ) {
                Text(
                    "Alinea la cámara para capturar",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}
