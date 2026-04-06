package edu.pucmm.tesis_calibracion_camera

import android.Manifest
import android.content.ContentResolver
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.json.JSONObject
import java.io.IOException

data class CameraCalibrationInput(
    val distanceCameraToPlaneMm: Double,
    val focalLengthMm: Double,
    val sensorWidthMm: Double
)

data class DetectAlignmentResult(
    val pair: String,
    val distanceMm: Double
)

data class DetectPulseEstimate(
    val pair: String,
    val pulses: Int,
    val direction: String
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
    private val shouldOpenCameraAfterSettings = mutableStateOf(false)

    // Estado del formulario (en mm)
    private val distanceMm = mutableStateOf("")
    private val focalLengthMm = mutableStateOf("")
    private val sensorWidthMm = mutableStateOf("")

    // Aquí queda listo el payload para enviarlo luego a tu API
    private val calibrationInput = mutableStateOf<CameraCalibrationInput?>(null)
    private val isUploading = mutableStateOf(false)
    private val apiResponseImageBytes = mutableStateOf<ByteArray?>(null)
    private val detectAlignmentResults = mutableStateOf<List<DetectAlignmentResult>>(emptyList())
    private val isDetectResultsSectionOpen = mutableStateOf(false)
    private val apiErrorMessage = mutableStateOf<String?>(null)
    private val appSettings: SharedPreferences by lazy {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
    }

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
        loadSavedCalibrationSettings()

        setContent {
            when {
                isDetectResultsSectionOpen.value -> {
                    DetectResultsScreen(
                        imageBytes = apiResponseImageBytes.value,
                        results = detectAlignmentResults.value,
                        onBack = {
                            isDetectResultsSectionOpen.value = false
                        },
                        onGoToCamera = {
                            isDetectResultsSectionOpen.value = false
                            isCameraSectionOpen.value = true
                        }
                    )
                }

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
                                    isFormSectionOpen.value = false
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

                        apiErrorMessage.value?.let { errorMessage ->
                            androidx.compose.material3.AlertDialog(
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
                            shouldOpenCameraAfterSettings.value = false
                        },
                        continueButtonText = if (shouldOpenCameraAfterSettings.value) {
                            "Guardar y continuar a cámara"
                        } else {
                            "Guardar ajustes"
                        },
                        onContinue = { distance, focal, sensor ->
                            saveCalibrationSettings(distance, focal, sensor)
                            calibrationInput.value = CameraCalibrationInput(
                                distanceCameraToPlaneMm = distance,
                                focalLengthMm = focal,
                                sensorWidthMm = sensor
                            )

                            val continueToCamera = shouldOpenCameraAfterSettings.value
                            shouldOpenCameraAfterSettings.value = false
                            isFormSectionOpen.value = false
                            isCameraSectionOpen.value = continueToCamera

                            if (continueToCamera && !hasCameraPermission.value) {
                                requestPermissionsIfNeeded()
                            }
                        }
                    )
                }

                else -> {
                    HomeScreen(
                        onEnterCamera = {
                            val savedInput = buildCalibrationInputFromCurrentValues()
                            if (savedInput != null) {
                                calibrationInput.value = savedInput
                                isCameraSectionOpen.value = true
                                if (!hasCameraPermission.value) {
                                    requestPermissionsIfNeeded()
                                }
                            } else {
                                shouldOpenCameraAfterSettings.value = true
                                isFormSectionOpen.value = true
                                Toast.makeText(
                                    baseContext,
                                    "Primero debes completar los ajustes de calibración.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onOpenSettings = {
                            shouldOpenCameraAfterSettings.value = false
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

    private fun buildCalibrationInputFromCurrentValues(): CameraCalibrationInput? {
        val distance = distanceMm.value.toDoubleOrNull()
        val focal = focalLengthMm.value.toDoubleOrNull()
        val sensor = sensorWidthMm.value.toDoubleOrNull()

        if (distance == null || distance <= 0.0) return null
        if (focal == null || focal <= 0.0) return null
        if (sensor == null || sensor <= 0.0) return null

        return CameraCalibrationInput(
            distanceCameraToPlaneMm = distance,
            focalLengthMm = focal,
            sensorWidthMm = sensor
        )
    }

    private fun loadSavedCalibrationSettings() {
        distanceMm.value = appSettings.getString(KEY_DISTANCE_MM, "").orEmpty()
        focalLengthMm.value = appSettings.getString(KEY_FOCAL_LENGTH_MM, "").orEmpty()
        sensorWidthMm.value = appSettings.getString(KEY_SENSOR_WIDTH_MM, "").orEmpty()
        calibrationInput.value = buildCalibrationInputFromCurrentValues()
    }

    private fun saveCalibrationSettings(
        distance: Double,
        focal: Double,
        sensor: Double
    ) {
        distanceMm.value = distance.toString()
        focalLengthMm.value = focal.toString()
        sensorWidthMm.value = sensor.toString()

        appSettings.edit()
            .putString(KEY_DISTANCE_MM, distanceMm.value)
            .putString(KEY_FOCAL_LENGTH_MM, focalLengthMm.value)
            .putString(KEY_SENSOR_WIDTH_MM, sensorWidthMm.value)
            .apply()
    }

    private fun sendCalibrationPhotoToApi(photoUri: Uri) {
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
                val responseBody = response.body
                val responseContentType = responseBody?.contentType()?.toString().orEmpty()
                val responseBodyBytes = responseBody?.bytes()
                runOnUiThread {
                    isUploading.value = false
                    if (!response.isSuccessful) {
                        val errorBody = responseBodyBytes?.toString(Charsets.UTF_8).orEmpty()
                        apiErrorMessage.value = "Error HTTP ${response.code}: $errorBody"
                        return@runOnUiThread
                    }

                    if (responseBodyBytes == null || responseBodyBytes.isEmpty()) {
                        apiErrorMessage.value = "La API no devolvio contenido de imagen"
                        return@runOnUiThread
                    }

                    if (!responseContentType.startsWith("image/")) {
                        val bodyAsText = responseBodyBytes.toString(Charsets.UTF_8)
                        apiErrorMessage.value =
                            "Se esperaba una imagen y la API devolvio '$responseContentType': $bodyAsText"
                        return@runOnUiThread
                    }

                    sendDetectRequest(photoUri)

                    apiResponseImageBytes.value = responseBodyBytes
                }
            }
        })
    }

    private fun sendDetectRequest(photoUri: Uri) {
        val imageBytes = contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
        if (imageBytes == null) {
            apiErrorMessage.value = "No se pudo leer la imagen para el analisis de alignment"
            return
        }

        val mimeType = contentResolver.getType(photoUri) ?: "image/jpeg"
        val fileName = resolveFileName(contentResolver, photoUri)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                fileName,
                imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(API_DETECT_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    apiErrorMessage.value = "No se pudo consultar /detect: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string().orEmpty()
                runOnUiThread {
                    if (!response.isSuccessful) {
                        apiErrorMessage.value = "Error HTTP ${response.code} en /detect: $responseText"
                        return@runOnUiThread
                    }

                    val summary = buildDetectSummary(responseText)
                    if (summary == null) {
                        apiErrorMessage.value = "La respuesta de /detect no tiene el formato esperado"
                        return@runOnUiThread
                    }

                    detectAlignmentResults.value = summary
                    isDetectResultsSectionOpen.value = true
                }
            }
        })
    }

    private fun buildDetectSummary(responseJson: String): List<DetectAlignmentResult>? {
        return try {
            val root = JSONObject(responseJson)
            val alignment = root.optJSONObject("alignment") ?: return null

            val entries = mutableListOf<DetectAlignmentResult>()
            val keys = alignment.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entry = alignment.optJSONObject(key) ?: continue
                if (!entry.has("dist_mm") || entry.isNull("dist_mm")) continue
                entries.add(
                    DetectAlignmentResult(
                        pair = key,
                        distanceMm = entry.getDouble("dist_mm")
                    )
                )
            }

            if (entries.isEmpty()) return null

            entries.sortedBy { it.pair }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatDistance(value: Double): String {
        return "%.4f".format(value)
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
        private const val API_UPLOAD_URL = "http://192.168.1.7:8000/detect/image/"
        private const val API_DETECT_URL = "http://192.168.1.7:8000/detect"
        private const val PREFERENCES_NAME = "camera_calibration_settings"
        private const val KEY_DISTANCE_MM = "distance_mm"
        private const val KEY_FOCAL_LENGTH_MM = "focal_length_mm"
        private const val KEY_SENSOR_WIDTH_MM = "sensor_width_mm"

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
private fun DetectResultsScreen(
    imageBytes: ByteArray?,
    results: List<DetectAlignmentResult>,
    onBack: () -> Unit,
    onGoToCamera: () -> Unit
) {
    val bitmap = remember(imageBytes) {
        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val pulseEstimates = remember(results) {
        estimatePulses(results)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Resultados de /detect/")
        Spacer(modifier = Modifier.height(12.dp))

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Imagen procesada por la API",
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxHeight = 320.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text("Distancia entre colores basada en dist_mm:")
        Spacer(modifier = Modifier.height(12.dp))

        results.forEach { result ->
            Text("${result.pair}: ${"%.4f".format(result.distanceMm)} mm")
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Pulsaciones estimadas para llevar C-K, M-K y Y-K a cero (0.002 mm por pulsación):")
        Spacer(modifier = Modifier.height(12.dp))

        pulseEstimates.forEach { estimate ->
            Text("${estimate.pair}: ${estimate.pulses} pulsaciones (${estimate.direction})")
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onGoToCamera,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver a cámara")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar resultados")
        }
    }
}

private fun estimatePulses(results: List<DetectAlignmentResult>): List<DetectPulseEstimate> {
    val targetPairs = setOf("C-K", "M-K", "Y-K")
    return results
        .filter { it.pair in targetPairs }
        .sortedBy { it.pair }
        .map { result ->
            val pulses = kotlin.math.ceil(kotlin.math.abs(result.distanceMm) / 0.002).toInt()
            val direction = if (result.distanceMm >= 0.0) "reducir" else "aumentar"
            DetectPulseEstimate(
                pair = result.pair,
                pulses = pulses,
                direction = direction
            )
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
    continueButtonText: String,
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
                        distanceValue,
                        focalValue,
                        sensorValue
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(continueButtonText)
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
