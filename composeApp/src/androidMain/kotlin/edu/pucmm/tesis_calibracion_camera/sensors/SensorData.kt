package edu.pucmm.tesis_calibracion_camera.sensors

data class SensorReadings(
    val pitchAngle: Float = 0f,      // Ángulo vertical (inclinación arriba/abajo)
    val rollAngle: Float = 0f,       // Ángulo horizontal (inclinación izquierda/derecha)
    val yawAngle: Float = 0f,        // Rotación (brújula)
    val isAligned: Boolean = false,  // true cuando está alineada (ángulos cercanos a 0)
    val distanceInCm: Int = -1       // Distancia en cm (-1 si no disponible)
)

enum class AlignmentStatus {
    MISALIGNED,    // Desalineada
    PARTIALLY_ALIGNED,  // Parcialmente alineada
    PERFECTLY_ALIGNED   // Perfectamente alineada
}
