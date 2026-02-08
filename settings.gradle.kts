rootProject.name = "Tesis_calibracion_camera"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
        maven {
            url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    }
}

include(":composeApp")
