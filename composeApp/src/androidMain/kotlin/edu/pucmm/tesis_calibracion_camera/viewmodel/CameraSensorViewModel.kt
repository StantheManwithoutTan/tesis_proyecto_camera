package edu.pucmm.tesis_calibracion_camera.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import edu.pucmm.tesis_calibracion_camera.sensors.DeviceSensors
import edu.pucmm.tesis_calibracion_camera.sensors.SensorReadings

class CameraSensorViewModel(context: Context) : ViewModel() {
    
    private val deviceSensors = DeviceSensors(context)
    val sensorReadings = mutableStateOf(SensorReadings())
    
    init {
        deviceSensors.onSensorUpdate = { readings ->
            sensorReadings.value = readings
        }
    }
    
    fun startMonitoring() {
        deviceSensors.startListening()
    }
    
    fun stopMonitoring() {
        deviceSensors.stopListening()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
