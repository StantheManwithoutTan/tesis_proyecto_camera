package edu.pucmm.tesis_calibracion_camera

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onEnterCamera: () -> Unit
) {
    val breathingAnimation = rememberInfiniteTransition(label = "breathing-animation")
    val glowAlpha by breathingAnimation.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow-alpha"
    )

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0A1F2E),
            Color(0xFF123E57),
            Color(0xFF1E6B73)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Círculo decorativo superior derecho
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 42.dp, y = (-28).dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = glowAlpha))
        )

        // Círculo decorativo inferior izquierdo
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-38).dp, y = 26.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(Color(0xFF8CE7E0).copy(alpha = glowAlpha + 0.05f))
        )

        // Título y subtítulo en la parte superior
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 72.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Calibracion de Camara",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Prepara el dispositivo para iniciar el proceso de captura.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }

        // Botón centrado en pantalla
        Button(
            onClick = onEnterCamera,
            modifier = Modifier
                .align(Alignment.Center)
                .width(220.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF4B942),
                contentColor = Color(0xFF1E2B32)
            )
        ) {
            Text(
                text = "Entrar a camara",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Nota de ayuda en la parte inferior
        Text(
            text = "Asegurate de tener buena iluminacion y estabilidad.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 32.dp, end = 32.dp)
        )
    }
}
