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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class CameraCalibrationInput(
    val distanceCameraToPlaneMm: Double,
    val focalLengthMm: Double,
    val sensorWidthMm: Double
)

data class DetectChannelResult(
    val channel: String,
    val detected: Boolean,
    val pixelCount: Int,
    val markX: Int?,
    val markY: Int?,
    val score: Double?,
    val scale: Double?
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
    private val apiResponseImageMascarasBytes = mutableStateOf<ByteArray?>(null)
    private val apiResponseImageResultadoBytes = mutableStateOf<ByteArray?>(null)
    private val apiResponseImageCalculosMmBytes = mutableStateOf<ByteArray?>(null)
    private val detectChannelResults = mutableStateOf<List<DetectChannelResult>>(emptyList())
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
                        mascarasImageBytes = apiResponseImageMascarasBytes.value,
                        resultadoImageBytes = apiResponseImageResultadoBytes.value,
                        calculosMmImageBytes = apiResponseImageCalculosMmBytes.value,
                        channelResults = detectChannelResults.value,
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
                            isCameraSectionOpen.value = true
                            if (!hasCameraPermission.value) {
                                requestPermissionsIfNeeded()
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

    private fun buildAnalyzeData(responseJson: String): AnalyzeResponseData? {
        return try {
            val root = JSONObject(responseJson)
            val outputFiles = root.optJSONObject("output_files") ?: return null

            val mascaras = outputFiles.optString(OUTPUT_FILE_KEY_MASCARAS).takeIf { it.isNotBlank() }
            val resultado = outputFiles.optString(OUTPUT_FILE_KEY_RESULTADO).takeIf { it.isNotBlank() }
            val calculosMm = outputFiles.optString(OUTPUT_FILE_KEY_CALCULOS_MM).takeIf { it.isNotBlank() }

            if (mascaras == null || resultado == null || calculosMm == null) {
                return null
            }

            val channelResults = CHANNEL_KEYS.mapNotNull { key ->
                val channelObject = root.optJSONObject(key) ?: return@mapNotNull null
                val markObject = channelObject.optJSONObject("mark")
                DetectChannelResult(
                    channel = key,
                    detected = channelObject.optBoolean("detected", false),
                    pixelCount = channelObject.optInt("pixel_count", 0),
                    markX = markObject?.optInt("x"),
                    markY = markObject?.optInt("y"),
                    score = if (markObject != null && !markObject.isNull("score")) {
                        markObject.optDouble("score")
                    } else {
                        null
                    },
                    scale = if (markObject != null && !markObject.isNull("scale")) {
                        markObject.optDouble("scale")
                    } else {
                        null
                    }
                )
            }

            AnalyzeResponseData(
                outputFiles = mapOf(
                    OUTPUT_FILE_KEY_MASCARAS to extractOutputFileName(mascaras),
                    OUTPUT_FILE_KEY_RESULTADO to extractOutputFileName(resultado),
                    OUTPUT_FILE_KEY_CALCULOS_MM to extractOutputFileName(calculosMm)
                ),
                channelResults = channelResults
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchOutputImages(
        mascarasFileName: String?,
        resultadoFileName: String?,
        calculosMmFileName: String?
    ) {
        if (mascarasFileName == null || resultadoFileName == null || calculosMmFileName == null) {
            runOnUiThread {
                isUploading.value = false
                apiErrorMessage.value = "No se pudieron resolver los nombres de output_files"
            }
            return
        }

        val pending = AtomicInteger(3)
        val failed = AtomicBoolean(false)

        var mascarasBytes: ByteArray? = null
        var resultadoBytes: ByteArray? = null
        var calculosMmBytes: ByteArray? = null

        fun onImageLoaded() {
            if (pending.decrementAndGet() == 0 && !failed.get()) {
                runOnUiThread {
                    apiResponseImageMascarasBytes.value = mascarasBytes
                    apiResponseImageResultadoBytes.value = resultadoBytes
                    apiResponseImageCalculosMmBytes.value = calculosMmBytes
                    isUploading.value = false
                    isDetectResultsSectionOpen.value = true
                }
            }
        }

        fun onImageError(message: String) {
            if (failed.compareAndSet(false, true)) {
                runOnUiThread {
                    isUploading.value = false
                    apiErrorMessage.value = message
                }
            }
        }

        requestOutputImage(mascarasFileName, OUTPUT_FILE_KEY_MASCARAS, onError = ::onImageError) {
            mascarasBytes = it
            onImageLoaded()
        }
        requestOutputImage(resultadoFileName, OUTPUT_FILE_KEY_RESULTADO, onError = ::onImageError) {
            resultadoBytes = it
            onImageLoaded()
        }
        requestOutputImage(calculosMmFileName, OUTPUT_FILE_KEY_CALCULOS_MM, onError = ::onImageError) {
            calculosMmBytes = it
            onImageLoaded()
        }
    }

    private fun requestOutputImage(
        fileName: String,
        imageLabel: String,
        onError: (String) -> Unit,
        onSuccess: (ByteArray) -> Unit
    ) {
        val request = Request.Builder()
            .url(API_OUTPUT_BASE_URL + Uri.encode(fileName))
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("No se pudo descargar '$imageLabel': ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyBytes = response.body?.bytes()
                if (!response.isSuccessful || bodyBytes == null || bodyBytes.isEmpty()) {
                    val errorBody = bodyBytes?.toString(Charsets.UTF_8).orEmpty()
                    onError("Error HTTP ${response.code} descargando '$imageLabel': $errorBody")
                    return
                }

                onSuccess(bodyBytes)
            }
        })
    }

    private fun extractOutputFileName(rawPath: String): String {
        return rawPath
            .replace("\\", "/")
            .substringAfterLast("/")
    }

    private data class AnalyzeResponseData(
        val outputFiles: Map<String, String>,
        val channelResults: List<DetectChannelResult>
    )

    private fun clearPreviousResults() {
        apiResponseImageMascarasBytes.value = null
        apiResponseImageResultadoBytes.value = null
        apiResponseImageCalculosMmBytes.value = null
        detectChannelResults.value = emptyList()
    }

    private fun prepareForAnalyzeRequest() {
        clearPreviousResults()
        apiErrorMessage.value = null
        isUploading.value = true
    }

    private fun resetAnalyzeStateOnError(message: String) {
        isUploading.value = false
        apiErrorMessage.value = message
    }

    private fun sendCalibrationPhotoToApi(photoUri: Uri) {
        val imageBytes = contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
        if (imageBytes == null) {
            apiErrorMessage.value = "No se pudo leer la imagen capturada"
            return
        }

        prepareForAnalyzeRequest()

        val mimeType = contentResolver.getType(photoUri) ?: "image/jpeg"
        val fileName = resolveFileName(contentResolver, photoUri)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(API_ANALYZE_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    resetAnalyzeStateOnError("No se pudo conectar con la API local: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    runOnUiThread {
                        resetAnalyzeStateOnError("Error HTTP ${response.code} en /analyze: $responseText")
                    }
                    return
                }

                val analyzeData = buildAnalyzeData(responseText)
                if (analyzeData == null) {
                    runOnUiThread {
                        resetAnalyzeStateOnError("La respuesta de /analyze no tiene el formato esperado")
                    }
                    return
                }

                runOnUiThread {
                    detectChannelResults.value = analyzeData.channelResults
                }

                fetchOutputImages(
                    mascarasFileName = analyzeData.outputFiles[OUTPUT_FILE_KEY_MASCARAS],
                    resultadoFileName = analyzeData.outputFiles[OUTPUT_FILE_KEY_RESULTADO],
                    calculosMmFileName = analyzeData.outputFiles[OUTPUT_FILE_KEY_CALCULOS_MM]
                )
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
        private const val API_ANALYZE_URL = "https://fastapi-dxui.onrender.com/api/v1/detection/analyze"
        private const val API_OUTPUT_BASE_URL = "https://fastapi-dxui.onrender.com/api/v1/detection/output/"
        private const val OUTPUT_FILE_KEY_MASCARAS = "mascaras"
        private const val OUTPUT_FILE_KEY_RESULTADO = "resultado"
        private const val OUTPUT_FILE_KEY_CALCULOS_MM = "calculos_mm"
        private val CHANNEL_KEYS = listOf("C", "M", "Y", "K")
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
    mascarasImageBytes: ByteArray?,
    resultadoImageBytes: ByteArray?,
    calculosMmImageBytes: ByteArray?,
    channelResults: List<DetectChannelResult>,
    onBack: () -> Unit,
    onGoToCamera: () -> Unit
) {
    val mascarasBitmap = remember(mascarasImageBytes) {
        mascarasImageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val resultadoBitmap = remember(resultadoImageBytes) {
        resultadoImageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val calculosMmBitmap = remember(calculosMmImageBytes) {
        calculosMmImageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Resultados de analisis")
        Spacer(modifier = Modifier.height(12.dp))

        if (mascarasBitmap != null) {
            Text("mascaras")
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = mascarasBitmap.asImageBitmap(),
                contentDescription = "Imagen mascaras",
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxHeight = 320.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (resultadoBitmap != null) {
            Text("resultado")
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = resultadoBitmap.asImageBitmap(),
                contentDescription = "Imagen resultado",
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxHeight = 320.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (calculosMmBitmap != null) {
            Text("calculos_mm")
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = calculosMmBitmap.asImageBitmap(),
                contentDescription = "Imagen calculos_mm",
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxHeight = 320.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (channelResults.isNotEmpty()) {
            Text("Canales detectados:")
            Spacer(modifier = Modifier.height(8.dp))

            channelResults.sortedBy { it.channel }.forEach { result ->
                val markText = if (result.markX != null && result.markY != null) {
                    "x=${result.markX}, y=${result.markY}, score=${result.score?.let { "%.4f".format(it) } ?: "n/a"}, scale=${result.scale?.let { "%.2f".format(it) } ?: "n/a"}"
                } else {
                    "sin marca"
                }
                Text("${result.channel}: detectado=${if (result.detected) "si" else "no"}, pixel_count=${result.pixelCount}, $markText")
                Spacer(modifier = Modifier.height(8.dp))
            }
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
