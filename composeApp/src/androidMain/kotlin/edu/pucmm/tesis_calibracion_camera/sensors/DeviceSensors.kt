package edu.pucmm.tesis_calibracion_camera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt

class DeviceSensors(context: Context) : SensorEventListener {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val distanceSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    
    // Buffers para datos sensoriales
    private var accelerometerData = FloatArray(3)
    private var magnetometerData = FloatArray(3)
    private var distanceValue = -1f
    
    // Matrices de rotación
    private val rotationMatrix = FloatArray(9)
    private val inclination = FloatArray(3)
    
    // Callback
    var onSensorUpdate: ((SensorReadings) -> Unit)? = null
    
    private var isListening = false
    
    fun startListening() {
        if (isListening) return
        
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        distanceSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        isListening = true
    }
    
    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerData = event.values.copyOf()
                updateOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetometerData = event.values.copyOf()
                updateOrientation()
            }
            Sensor.TYPE_PROXIMITY -> {
                distanceValue = event.values[0]
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No necesario para este caso
    }
    
    private fun updateOrientation() {
        if (SensorManager.getRotationMatrix(
            rotationMatrix,
            inclination,
            accelerometerData,
            magnetometerData
        )) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // Convertir de radianes a grados
            val pitchAngle = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val rollAngle = Math.toDegrees(orientation[2].toDouble()).toFloat()
            val yawAngle = Math.toDegrees(orientation[0].toDouble()).toFloat()
            
            // Determinar si está alineada (ángulos cercanos a 0)
            // Tolerancia de ±5 grados
            val isAligned = Math.abs(pitchAngle) < 5f && Math.abs(rollAngle) < 5f
            
            // Calcular distancia en cm
            val distanceInCm = if (distanceValue > 0) distanceValue.toInt() else -1
            
            val readings = SensorReadings(
                pitchAngle = pitchAngle,
                rollAngle = rollAngle,
                yawAngle = yawAngle,
                isAligned = isAligned,
                distanceInCm = distanceInCm
            )
            
            onSensorUpdate?.invoke(readings)
        }
    }
}
