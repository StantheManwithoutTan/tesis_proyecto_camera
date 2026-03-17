package edu.pucmm.tesis_calibracion_camera.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedPhoto(
    val uri: Uri,
    val localPath: String?
)

class CameraCapture(private val context: Context) {
    
    companion object {
        private const val APP_FOLDER_NAME = "TesisCalbracionCamera"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
    
    fun takePicture(
        controller: LifecycleCameraController,
        onSuccess: (CapturedPhoto) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())
            val (outputFileOptions, fallbackFile) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + File.separator + APP_FOLDER_NAME
                    )
                }
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    collection,
                    contentValues
                ).build() to null
            } else {
                val outputDirectory = getLegacyOutputDirectory()
                if (!outputDirectory.exists()) {
                    outputDirectory.mkdirs()
                }
                val photoFile = File(outputDirectory, "$timestamp.jpg")
                ImageCapture.OutputFileOptions.Builder(photoFile).build() to photoFile
            }

            controller.takePicture(
                outputFileOptions,
                { command -> command.run() },
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri
                        val finalUri = when {
                            savedUri != null -> savedUri
                            fallbackFile != null -> Uri.fromFile(fallbackFile)
                            else -> null
                        }
                        if (finalUri == null) {
                            onError("No se pudo obtener la ubicación de la imagen capturada")
                            return
                        }

                        onSuccess(
                            CapturedPhoto(
                                uri = finalUri,
                                localPath = fallbackFile?.absolutePath
                            )
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        onError("Error al capturar: ${exception.message}")
                    }
                }
            )
        } catch (e: Exception) {
            onError("Error: ${e.message}")
        }
    }

    private fun getLegacyOutputDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            APP_FOLDER_NAME
        )
    }
}
