import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Claves de servicios externos: viven en local.properties (fuera de git) o en variables de entorno
// (PIXA_API_PIXABAY, PIXA_API_KLIPY, PIXA_API_GOOGLEFONTS). Sin clave la app compila igual y esa
// función queda desactivada. Aunque se pongan, acaban empotradas en el APK en tiempo de compilación.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun apiKey(prop: String, env: String): String = (localProperties.getProperty(prop) ?: System.getenv(env) ?: "").trim()

// Versión: version.properties (versionName=1.0.0). Se puede forzar con -PversionName=1.0.1 (la Action de GitHub lo hace).
// El versionCode sale del número (1.2.8 → 10208), así que crece siempre que crece la versión.
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appVersionName: String = ((findProperty("versionName") as String?) ?: versionProps.getProperty("versionName") ?: "1.0.0").trim()
val appVersionCode: Int = appVersionName.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 10000 + it.getOrElse(1) { 0 } * 100 + it.getOrElse(2) { 0 }).coerceAtLeast(1) }

// Firma de release: con KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS y KEY_PASSWORD (las pone la Action) se firma con
// tu keystore; sin ellas (compilación local) se usa la firma de depuración.
val releaseKeystore: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.dskmusic.pixa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dskmusic.pixa"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        resourceConfigurations += listOf("en", "es")
        vectorDrawables { useSupportLibrary = true }
        // MediaPipe y ONNX Runtime traen librerias nativas para las 4 arquitecturas; de
        // uso personal en moviles reales (no emulador), solo hace falta arm64-v8a.
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField(
            "String",
            "PIXABAY_API_KEY",
            "\"${apiKey("pixabay.apiKey", "PIXA_API_PIXABAY")}\"",
        )
        buildConfigField(
            "String",
            "KLIPY_API_KEY",
            "\"${apiKey("klipy.apiKey", "PIXA_API_KLIPY")}\"",
        )
        buildConfigField(
            "String",
            "GOOGLE_FONTS_API_KEY",
            "\"${apiKey("googleFonts.apiKey", "PIXA_API_GOOGLEFONTS")}\"",
        )
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // La variante debug (Run ▶) lleva otro nombre de paquete y se instala a la vez que la release.
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // ncnn (modulo ml) y otras bibliotecas traen la misma libc++_shared.
        jniLibs.pickFirsts += "**/libc++_shared.so"
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*"
        )
    }
    androidResources {
        generateLocaleConfig = false
    }
}

dependencies {
    implementation(project(":ml"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Widget de pantalla de inicio (requisito 2): Glance es la via oficial Compose para App Widgets.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Downloadable Fonts de Google (requisito 10): aporta el proveedor y el array de
    // certificados oficial, por lo que no se empaqueta ningun .ttf en la app.
    implementation(libs.androidx.compose.ui.text.google.fonts)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Coil: carga/cacheo asincrono de miniaturas de proyectos y fotos. Evita escribir
    // un gestor de cache de bitmaps propio para las rejillas de la pantalla de inicio.
    implementation(libs.coil.compose)

    // Censurar caras (requisito 14). Modelo empaquetado, sin red.
    implementation(libs.mlkit.face.detection)
    // OCR y escaner de documentos: los modelos los gestiona Google Play Services.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.document.scanner)

    // Generador de QR (requisito 12). Solo 'core': no incluye escaner ni camara.
    implementation(libs.zxing.core)
}
