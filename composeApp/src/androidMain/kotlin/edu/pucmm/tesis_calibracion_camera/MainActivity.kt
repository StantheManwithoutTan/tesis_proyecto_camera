package edu.pucmm.tesis_calibracion_camera

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import edu.pucmm.tesis_calibracion_camera.utils.CameraCapture
import edu.pucmm.tesis_calibracion_camera.viewmodel.CameraSensorViewModel
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

data class CameraCalibrationInput(
    val distanceCameraToPlaneMm: Double,
    val focalLengthMm: Double,
    val sensorWidthMm: Double
)

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: LifecycleCameraController
    private lateinit var sensorViewModel: CameraSensorViewModel
    private lateinit var cameraCapture: CameraCapture
    private val httpClient = OkHttpClient()

    // Callback para capturar foto desde el botón de volumen
    private var onVolumeDownPressed: (() -> Unit)? = null

    // Estado de permisos y navegación
    private val hasCameraPermission = mutableStateOf(false)
    private val isFormSectionOpen = mutableStateOf(false)
    private val isCameraSectionOpen = mutableStateOf(false)

    // Estado del formulario (en mm)
    private val distanceMm = mutableStateOf("")
    private val focalLengthMm = mutableStateOf("")
    private val sensorWidthMm = mutableStateOf("")

    // Aquí queda listo el payload para enviarlo luego a tu API
    private val calibrationInput = mutableStateOf<CameraCalibrationInput?>(null)
    private val isUploading = mutableStateOf(false)
    private val apiResponseJson = mutableStateOf<String?>(null)
    private val apiErrorMessage = mutableStateOf<String?>(null)

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                hasCameraPermission.value = true
            } else {
                val deniedPermissions = permissions.filter { !it.value }.keys.joinToString(", ")
                Toast.makeText(
                    baseContext,
                    "Permisos denegados: $deniedPermissions. La app necesita estos permisos para funcionar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorViewModel = CameraSensorViewModel(baseContext)

        cameraController = LifecycleCameraController(baseContext).apply {
            bindToLifecycle(this@MainActivity)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }

        cameraCapture = CameraCapture(baseContext)

        setContent {
            when {
                isCameraSectionOpen.value -> {
                    if (hasCameraPermission.value) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CameraPreviewWithGuide(
                                controller = cameraController,
                                viewModel = sensorViewModel,
                                cameraCapture = cameraCapture,
                                onCaptureTrigger = { callback ->
                                    onVolumeDownPressed = callback
                                },
                                onPhotoCaptured = { photoUri ->
                                    sendCalibrationPhotoToApi(photoUri)
                                },
                                onBack = {
                                    isCameraSectionOpen.value = false
                                    isFormSectionOpen.value = true
                                    onVolumeDownPressed = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isUploading.value) {
                                Text(
                                    text = "Enviando captura a la API...",
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 20.dp)
                                )
                            }
                        }

                        apiResponseJson.value?.let { responseJson ->
                            AlertDialog(
                                onDismissRequest = { apiResponseJson.value = null },
                                title = { Text("Respuesta JSON de la API") },
                                text = { Text(responseJson) },
                                confirmButton = {
                                    TextButton(onClick = { apiResponseJson.value = null }) {
                                        Text("Cerrar")
                                    }
                                }
                            )
                        }

                        apiErrorMessage.value?.let { errorMessage ->
                            AlertDialog(
                                onDismissRequest = { apiErrorMessage.value = null },
                                title = { Text("Error al llamar API") },
                                text = { Text(errorMessage) },
                                confirmButton = {
                                    TextButton(onClick = { apiErrorMessage.value = null }) {
                                        Text("Cerrar")
                                    }
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Se requieren permisos para usar la cámara")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { requestPermissionsIfNeeded() }) {
                                    Text("Solicitar Permisos")
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = {
                                    isCameraSectionOpen.value = false
                                    isFormSectionOpen.value = false
                                    onVolumeDownPressed = null
                                }) {
                                    Text("Volver al inicio")
                                }
                            }
                        }
                    }
                }

                isFormSectionOpen.value -> {
                    CameraSetupFormScreen(
                        distanceMm = distanceMm.value,
                        onDistanceChange = { distanceMm.value = it },
                        focalLengthMm = focalLengthMm.value,
                        onFocalLengthChange = { focalLengthMm.value = it },
                        sensorWidthMm = sensorWidthMm.value,
                        onSensorWidthChange = { sensorWidthMm.value = it },
                        onBack = {
                            isFormSectionOpen.value = false
                        },
                        onContinue = { distance, focal, sensor ->
                            calibrationInput.value = CameraCalibrationInput(
                                distanceCameraToPlaneMm = distance,
                                focalLengthMm = focal,
                                sensorWidthMm = sensor
                            )

                            // Temporal: aviso de payload listo para API
                            Toast.makeText(
                                baseContext,
                                "Datos listos para API: d=$distance mm, f=$focal mm, sensor=$sensor mm",
                                Toast.LENGTH_SHORT
                            ).show()

                            isFormSectionOpen.value = false
                            isCameraSectionOpen.value = true

                            if (!hasCameraPermission.value) {
                                requestPermissionsIfNeeded()
                            }
                        }
                    )
                }

                else -> {
                    HomeScreen(
                        onEnterCamera = {
                            isFormSectionOpen.value = true
                        }
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (isCameraSectionOpen.value && hasCameraPermission.value) {
                    onVolumeDownPressed?.invoke()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
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
        val allPermissionsGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allPermissionsGranted) {
            hasCameraPermission.value = true
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun sendCalibrationPhotoToApi(photoUri: Uri) {
        val payload = calibrationInput.value
        if (payload == null) {
            Toast.makeText(this, "Faltan los datos del formulario", Toast.LENGTH_SHORT).show()
            return
        }

        val imageBytes = contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
        if (imageBytes == null) {
            apiErrorMessage.value = "No se pudo leer la imagen capturada"
            return
        }

        isUploading.value = true
        apiErrorMessage.value = null

        val mimeType = contentResolver.getType(photoUri) ?: "image/jpeg"
        val fileName = resolveFileName(contentResolver, photoUri)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("distanceCameraToPlaneMm", payload.distanceCameraToPlaneMm.toString())
            .addFormDataPart("focalLengthMm", payload.focalLengthMm.toString())
            .addFormDataPart("sensorWidthMm", payload.sensorWidthMm.toString())
            .addFormDataPart(
                "image",
                fileName,
                imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(API_UPLOAD_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    isUploading.value = false
                    apiErrorMessage.value = "No se pudo conectar con la API local: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string().orEmpty()
                runOnUiThread {
                    isUploading.value = false
                    if (!response.isSuccessful) {
                        apiErrorMessage.value = "Error HTTP ${response.code}: $responseBody"
                        return@runOnUiThread
                    }
                    apiResponseJson.value = responseBody.ifBlank { "{}" }
                }
            }
        })
    }

    private fun resolveFileName(contentResolver: ContentResolver, uri: Uri): String {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return "captured_image.jpg"
    }

    companion object {
        // En emulador Android, 10.0.2.2 apunta a localhost de tu PC.
        // marca de que punta al api
        private const val API_UPLOAD_URL = "http://10.0.2.2:8000/calibration"

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

@Composable
private fun CameraSetupFormScreen(
    distanceMm: String,
    onDistanceChange: (String) -> Unit,
    focalLengthMm: String,
    onFocalLengthChange: (String) -> Unit,
    sensorWidthMm: String,
    onSensorWidthChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: (distanceMm: Double, focalLengthMm: Double, sensorWidthMm: Double) -> Unit
) {
    var attemptedSubmit by remember { mutableStateOf(false) }

    val distanceValue = distanceMm.toDoubleOrNull()
    val focalValue = focalLengthMm.toDoubleOrNull()
    val sensorValue = sensorWidthMm.toDoubleOrNull()

    val distanceError = attemptedSubmit && (distanceValue == null || distanceValue <= 0.0)
    val focalError = attemptedSubmit && (focalValue == null || focalValue <= 0.0)
    val sensorError = attemptedSubmit && (sensorValue == null || sensorValue <= 0.0)

    val formValid = distanceValue != null && distanceValue > 0.0 &&
            focalValue != null && focalValue > 0.0 &&
            sensorValue != null && sensorValue > 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Configuración para calibración")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Todos los valores deben estar en milímetros (mm).")
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = distanceMm,
            onValueChange = onDistanceChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Distancia cámara-plano (mm)") },
            isError = distanceError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        if (distanceError) {
            Text("Ingresa una distancia válida mayor que 0.")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = focalLengthMm,
            onValueChange = onFocalLengthChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Longitud focal de la cámara (mm)") },
            isError = focalError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        if (focalError) {
            Text("Ingresa una longitud focal válida mayor que 0.")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = sensorWidthMm,
            onValueChange = onSensorWidthChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ancho del sensor (mm)") },
            isError = sensorError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        if (sensorError) {
            Text("Ingresa un ancho de sensor válido mayor que 0.")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                attemptedSubmit = true
                if (formValid) {
                    onContinue(
                        distanceValue!!,
                        focalValue!!,
                        sensorValue!!
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar y continuar a cámara")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }
    }
}
