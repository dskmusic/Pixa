plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Modulo aislado para ML Kit Subject Segmentation (requisito 8).
// Vive aparte para que el resto de la app no dependa de una API en beta y para
// que la falta de soporte en el dispositivo se resuelva aqui, sin propagar errores.
android {
    namespace = "com.dskmusic.pixa.ml"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared" } }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.subject.segmentation)
    // ModuleInstall: comprueba y descarga el modelo opcional con progreso real.
    implementation(libs.play.services.base)
    // Requisito del usuario: dos metodos mas de recorte de fondo, los dos con el modelo
    // empaquetado en el APK (sin descarga aparte, funcionan sin red desde el primer uso).
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.onnxruntime.android)
}
