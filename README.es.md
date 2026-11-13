<div align="center">

# 🎨 Pixa by DSK

### Tu estudio de edición completo, en el bolsillo.

**Capas, selección, texto, dibujo, retoque, IA en tu móvil, cómics, collages, memes y posts de mentira para redes.**
**Sin anuncios. Sin cuentas. Sin rastreadores.**

[🇬🇧 English](README.md) · [🇪🇸 Español](README.es.md)

<br>

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-757575?style=for-the-badge&logo=materialdesign&logoColor=white)
![Android](https://img.shields.io/badge/Android%2010%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![ONNX Runtime](https://img.shields.io/badge/ONNX%20Runtime-005CED?style=for-the-badge&logo=onnx&logoColor=white)
![ML Kit](https://img.shields.io/badge/ML%20Kit-4285F4?style=for-the-badge&logo=google&logoColor=white)
![AGSL](https://img.shields.io/badge/AGSL%20shaders-5586A4?style=for-the-badge&logo=opengl&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)
[![License](https://img.shields.io/badge/License-Apache%202.0-D22128?style=for-the-badge&logo=apache&logoColor=white)](LICENSE)

![Stars](https://img.shields.io/github/stars/dskmusic/Pixa?style=social)
![Latest release](https://img.shields.io/github/v/release/dskmusic/Pixa?label=release&color=brightgreen)
![Last commit](https://img.shields.io/github/last-commit/dskmusic/Pixa)

**[⬇️ Descargar el último APK](https://github.com/dskmusic/Pixa/releases/latest/download/Pixa.apk)** · **[🌐 pixa.dskmusic.com](https://pixa.dskmusic.com)**

</div>

---

> 📸 *Capturas:* próximamente en [`docs/screenshots/`](docs/screenshots).

## ✨ ¿Qué es Pixa?

**Pixa by DSK** es un editor de imágenes nativo para Android para quien quiere **hacer de todo sin salir de una sola app**:
retocar una foto, quitarle el fondo, montar un cómic con bocadillos que señalan a las caras, crear un meme,
fabricar un collage, unir capturas o procesar cincuenta fotos de golpe.

- ⚡ **Rápido y fluido**: los gestos se previsualizan a menor resolución y al soltar vuelve la máxima calidad.
- 🧩 **Todo editable**: texto, formas, QR, degradados y bocadillos son capas que puedes reabrir cuando quieras.
- 🔒 **Privado**: la IA corre en tu dispositivo. Sin anuncios, sin cuentas, sin analíticas.
- 🌍 **En tu idioma**: español e inglés.

## 📊 Pixa en cifras

| 🔤 Estilos de texto | 🖼️ Filtros | ✨ Efectos | 🧱 Diseños de collage | 🤖 Métodos de quitar fondo | 📱 Plataformas sociales |
|:-:|:-:|:-:|:-:|:-:|:-:|
| **100** | **80** | **50** | **120** | **9** | **12** |

Además: 30 estilos de capa, 20 marcos, 20 texturas de texto, 16 modos de fusión, 12 plantillas de meme y 6 tipos de degradado.

## 🧰 Lo más destacado

| | |
|---|---|
| **🗂️ Capas** | Capas de imagen, texto, forma, QR, emoji, degradado, color sólido y bocadillo; 16 modos de fusión, máscaras de recorte, grupos, estilos de capa (sombra, resplandor, contorno) y rasterizar. |
| **🎯 Selección** | Rectángulo, elipse, lazo libre y poligonal, **lazo magnético**, varita mágica y selección por color; añadir / restar / intersecar, calar y duplicar en capa nueva. |
| **🎨 Color y looks** | Curvas, niveles con histograma, HSL por color, 80 filtros, LUTs `.cube` importables, 50 efectos con vista previa viva y cortina antes/después. |
| **🔤 Texto** | Google Fonts y las tuyas, 100 estilos de un toque, 20 texturas generadas, imagen dentro de las letras, texto curvado y sobre trayecto. |
| **🖌️ Dibujo y retoque** | Pincel, lápiz, aerógrafo, simetría, tampón de clonar, pinceles de desenfoque y enfoque, ojos rojos y desenfoque automático de caras para tu privacidad. |
| **🧠 IA en el móvil** | Quitar fondo (9 modelos), seleccionar por toque, eliminar objetos, ampliar ×4, quitar ruido, mejorar caras, colorear, desenfoque de retrato, OCR y escáner de documentos. |
| **🦸 Creadores** | Collage y cómic, memes, posts de mentira (X, Instagram, TikTok, WhatsApp…), unir fotos y editor por lotes en segundo plano. |
| **🛟 Red de seguridad** | Autoguardado y recuperación tras un cierre, papelera de 30 días, versiones guardadas, copias de seguridad e historial completo. |
| **📤 Exportar** | PNG, JPEG, WEBP y PDF, tamaño objetivo, control de EXIF, solo la selección o la capa, impresión directa. |

## 🧠 IA en tu dispositivo

Los modelos se descargan **una sola vez, bajo demanda**, funcionan en local con ONNX Runtime y se pueden borrar para recuperar espacio.
Antes de cada tarea Pixa te dice cuánto va a descargar y cuánto tardará.

| Función | Modelo | Peso |
|---|---|---|
| Ampliar / mejorar detalle | Swin2SR lite | 8 MB |
| Quitar ruido | SCUNet | 77 MB |
| Seleccionar por toque | MobileSAM | 45 MB |
| Desenfoque de retrato / niebla | Depth Anything V2 Small | 27 MB |
| Colorear | DDColor tiny | 135 MB |
| Eliminar objetos | MI-GAN | 28 MB |
| Mejorar caras | GFPGAN 1.4 | 340 MB |
| Quitar fondo | ML Kit, MediaPipe, U2-Net, ISNet, MODNet, RMBG 1.4… | variable |

Las direcciones de descarga están en [`ml/src/main/assets/ai_models.json`](ml/src/main/assets) y pueden apuntar a tu propio espejo.

## 🚀 Compilar y ejecutar

**Requisitos:** un Android Studio reciente, **JDK 17** (sirve el que trae) y el **SDK de Android 35**. Móvil con Android 10 o superior.

```bash
git clone https://github.com/dskmusic/Pixa.git
cd Pixa
```

1. Abre en Android Studio la carpeta **raíz** (la que contiene `settings.gradle.kts`).
2. *(Opcional)* copia [`local.properties.example`](local.properties.example) como `local.properties` y añade tus claves de API (abajo).
3. Pulsa **Run ▶**.

```bash
./gradlew :app:assembleDebug     # APK de depuración (se instala como com.dskmusic.pixa.debug)
./gradlew :app:assembleRelease   # APK de release (R8 + recorte de recursos)
```

### 🔑 Claves de API (opcionales)

Tres funciones online necesitan una clave gratuita. Sin ella la app compila y funciona con normalidad y esa función queda desactivada.
Pon la clave en `local.properties` (nunca se sube) **o** en una variable de entorno:

| Función | `local.properties` | Variable de entorno | Dónde conseguirla |
|---|---|---|---|
| Imágenes de stock | `pixabay.apiKey` | `PIXA_API_PIXABAY` | [pixabay.com/api/docs](https://pixabay.com/api/docs/) |
| Stickers | `klipy.apiKey` | `PIXA_API_KLIPY` | [klipy.com/developers](https://klipy.com/developers) |
| Lista completa de Google Fonts | `googleFonts.apiKey` | `PIXA_API_GOOGLEFONTS` | [Google Cloud Console](https://console.cloud.google.com/apis/credentials) (API de Web Fonts) |

> ⚠️ Las claves acaban dentro del APK compilado. Las releases publicadas aquí se compilan **sin** claves.

### 🤖 Releases con GitHub Actions

*Actions → Build release APK → Run workflow* compila un APK firmado y lo publica como `Pixa.apk`.
La versión sale de [`version.properties`](version.properties) o del formulario. Necesita los secretos del repositorio
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.

## 🏗️ Por dentro

Kotlin · Jetpack Compose (Material 3) · MVVM con corrutinas y `Flow` · DataStore · WorkManager · widget con Glance ·
ONNX Runtime · ML Kit · ZXing core · Coil. **Una sola implementación de dibujo**: la vista previa y la exportación final
llaman al mismo código de `android.graphics`, así que lo que ves es exactamente lo que exportas. Filtros, desenfoques, degradados,
texto curvado, marcos y exportación a PDF están escritos a mano con `android.graphics` y AGSL, sin librerías de imagen de terceros.

Arquitectura y estructura del proyecto: [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).

## 🔒 Privacidad

El único permiso es `POST_NOTIFICATIONS` (progreso del editor por lotes). Sin permisos de cámara ni almacenamiento: usa el selector de fotos del sistema y
el Storage Access Framework. La red solo se usa para Google Fonts, la descarga opcional de modelos de IA y la búsqueda de stock o stickers que tú inicias.

## 📄 Licencia

[Apache License 2.0](LICENSE). Si redistribuyes Pixa o un fork, conserva el [`NOTICE`](NOTICE) y menciona a
**«Pixa by DSK»** con un enlace a <https://www.dskmusic.com/dsk_dev_redirect.php>.

<div align="center">

Hecho con ❤️ por [DSK](https://www.dskmusic.com/dsk_dev_redirect.php)

</div>
