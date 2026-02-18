package edu.pucmm.tesis_calibracion_camera.sensors

// Representa un punto de alineación objetivo con valores de pitch, roll y yaw
data class AlignmentPoint(
    val pitch: Float,
    val roll: Float,
    val yaw: Float,
    val tolerancePitch: Float = 10f,  // ±10 grados
    val toleranceRoll: Float = 5f,     // ±2 grados
    val toleranceYaw: Float = 20f      // ±20 grados
) {
    // Verifica si los ángulos actuales están dentro de las tolerancias específicas de este punto
    // Nota: Yaw no se verifica porque no afecta la calidad de la captura de imagen
    fun isMatching(pitchAngle: Float, rollAngle: Float, yawAngle: Float): Boolean {
        return Math.abs(pitchAngle - pitch) <= tolerancePitch &&
               Math.abs(rollAngle - roll) <= toleranceRoll
    }
}

// Puntos de alineación predefinidos para calibración
object AlignmentPoints {
    val points = listOf(
        AlignmentPoint(pitch = -10.3f, roll = 0.1f, yaw = 140.1f),
        AlignmentPoint(pitch = 8.8f, roll = 0.5f, yaw = -118.8f),
        AlignmentPoint(pitch = -1f, roll = 9.6f, yaw = 119.3f),
        AlignmentPoint(pitch = -0.8f, roll = -9.7f, yaw = 173.3f)
    )
}
