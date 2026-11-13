# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.dskmusic.pixa.** {
    *** Companion;
}
-keepclasseswithmembers class com.dskmusic.pixa.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dskmusic.pixa.**$$serializer { *; }

# ZXing (solo core)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Glance / Compose
-keep class androidx.glance.** { *; }

# Bug encontrado (requisito del usuario): crash en release, no en debug, al usar el
# recorte general. ONNX Runtime (y MediaPipe, mismo mecanismo) buscan sus clases Java por
# nombre desde codigo nativo JNI (GetMethodID); si R8 las renombra en un build minificado,
# la busqueda falla con java_class == null y ART aborta el proceso entero sin que ningun
# try/catch de Kotlin lo pueda atrapar. Ninguna de las dos librerias trae estas reglas de
# consumidor en su AAR, hay que ponerlas aqui a mano.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
