package edu.pucmm.tesis_calibracion_camera

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform