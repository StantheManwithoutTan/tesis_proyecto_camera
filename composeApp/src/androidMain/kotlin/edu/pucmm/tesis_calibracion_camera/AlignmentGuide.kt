package edu.pucmm.tesis_calibracion_camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.pucmm.tesis_calibracion_camera.sensors.SensorReadings
import kotlin.math.abs

@Composable
fun AlignmentGuide(
    sensorReadings: SensorReadings,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Contenedor principal con overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicador de alineación superior
            AlignmentStatus(sensorReadings)
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Centro: Nivel visual
            LevelIndicator(
                pitchAngle = sensorReadings.pitchAngle,
                rollAngle = sensorReadings.rollAngle,
                isAligned = sensorReadings.isAligned
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Inferior: Información de distancia
            if (sensorReadings.distanceInCm > 0) {
                DistanceIndicator(sensorReadings.distanceInCm)
            } else {
                Text(
                    text = "Sensor de distancia no disponible",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun AlignmentStatus(
    sensorReadings: SensorReadings,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        sensorReadings.isAligned -> "✓ ALINEADA"
        abs(sensorReadings.pitchAngle) > 10f -> "↕ INCLINACIÓN VERTICAL"
        abs(sensorReadings.rollAngle) > 10f -> "↔ INCLINACIÓN HORIZONTAL"
        else -> "⚠ ALINEANDO..."
    }
    
    val backgroundColor = when {
        sensorReadings.isAligned -> Color.Green.copy(alpha = 0.7f)
        abs(sensorReadings.pitchAngle) < 5f && abs(sensorReadings.rollAngle) < 5f -> Color(0xFFFFA500).copy(alpha = 0.7f)
        else -> Color.Red.copy(alpha = 0.7f)
    }
    
    Box(
        modifier = modifier
            .background(backgroundColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusText,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LevelIndicator(
    pitchAngle: Float,
    rollAngle: Float,
    isAligned: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(200.dp)
            .background(Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(
                        color = if (isAligned) Color.Green.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Mostrar ángulos
            Text(
                text = "Pitch: %.1f°".format(pitchAngle),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = "Roll: %.1f°".format(rollAngle),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun DistanceIndicator(
    distanceInCm: Int,
    modifier: Modifier = Modifier
) {
    val distanceStatus = when {
        distanceInCm < 10 -> "MUY CERCA"
        distanceInCm < 20 -> "CERCA"
        distanceInCm < 50 -> "✓ DISTANCIA IDEAL"
        else -> "MUY LEJOS"
    }
    
    val distanceColor = when {
        distanceInCm < 10 -> Color.Red
        distanceInCm < 20 -> Color(0xFFFFA500)
        distanceInCm < 50 -> Color.Green
        else -> Color(0xFFFFA500)
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .background(distanceColor.copy(alpha = 0.7f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = distanceStatus,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "$distanceInCm cm",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
