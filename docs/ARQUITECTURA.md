# Pixa by DSK

Editor de imágenes nativo para Android: un "mini Photoshop" de bolsillo con capas,
selección, ajustes, texto, dibujo, retoque, posts sociales, memes, unir fotos,
editor por lotes y más.

- **Paquete:** `com.dskmusic.pixa`
- **Nombre corto en el lanzador:** Pixa
- **Lenguaje y UI:** Kotlin + Jetpack Compose + Material 3 (tema dinámico y claro/oscuro)
- **Arquitectura:** MVVM con corrutinas y `Flow`
- **minSdk 29 · compileSdk 35 · targetSdk 35 · Java/Kotlin JVM target 17**
- **Idiomas:** español e inglés (`values/` y `values-es/`, selector dentro de la app)
- **Sin anuncios, sin trackers, sin analíticas, sin red salvo las fuentes de Google**

---

## 1. Pasos de compilación

1. Instala **Android Studio Panda** (o posterior) con el **SDK de Android 35** y un
   **JDK 17** (el que trae el propio Android Studio sirve).
2. Abre la carpeta del proyecto con *File → Open*. Elige la **raíz**, la que contiene
   `settings.gradle.kts`, no la carpeta `app`.
3. La primera sincronización de Gradle descargará las dependencias. Hace falta conexión
   a Internet solo en esa primera vez.
4. Comprueba `local.properties`. Si no existe o apunta a otra ruta, Android Studio lo
   regenera solo; también puedes escribirlo a mano:
   ```properties
   sdk.dir=C:\\ruta\\a\\tu\\Android\\Sdk
   ```
5. Selecciona la configuración **app** y pulsa *Run* con un dispositivo o emulador con
   **Android 10 (API 29) o superior**.

Desde la línea de órdenes (opcional, el wrapper ya está incluido):

```bash
./gradlew :app:assembleDebug      # APK de depuración
./gradlew :app:assembleRelease    # APK de lanzamiento (R8 + shrink de recursos)
./gradlew :app:bundleRelease      # AAB para Play
```

> El `release` lleva `isMinifyEnabled = true` e `isShrinkResources = true`. Las reglas
> que hacen falta están en `app/proguard-rules.pro` (serialización de `kotlinx`, modelos
> de ML Kit y WorkManager). Para publicar hay que añadir tu propio `signingConfig`: el
> proyecto no incluye ningún almacén de claves.

---

## 2. Estructura

```
Pixa by DSK/
├── settings.gradle.kts        includes :app y :ml, repositorios centralizados
├── build.gradle.kts           plugins declarados sin aplicar (raíz)
├── gradle/libs.versions.toml  catálogo de versiones (única fuente de verdad)
├── gradle/wrapper/            wrapper de Gradle 8.9
├── tools/
│   ├── strings.tsv            textos ES/EN en una sola tabla
│   └── genstrings.py          genera values/strings.xml y values-es/strings.xml
├── ml/                        módulo aislado de ML Kit Subject Segmentation
└── app/
    ├── src/main/AndroidManifest.xml
    ├── src/main/res/          drawables vectoriales, layout del widget, XML, strings
    └── src/main/java/com/dskmusic/pixa/
        ├── MainActivity.kt    única Activity; traduce intents y atajos a destinos
        ├── PixaApp.kt         Application + AppContainer (inyección a mano)
        ├── core/              toda la lógica sin Android UI
        │   ├── bitmap/        creación, decodificación y almacén de bitmaps en memoria
        │   ├── draw/          motor de pinceles
        │   ├── export/        exportación PNG/JPG/WEBP/PDF e información de imagen
        │   ├── history/       pila de deshacer/rehacer
        │   ├── image/         ajustes, filtros, efectos, desenfoques, AGSL, retoque
        │   ├── ml/            detección de caras (ML Kit face detection)
        │   ├── model/         documento, capas, especificaciones, serialización
        │   ├── project/       formato .pixa, índice de proyectos y recientes
        │   ├── qr/            generador de códigos QR (ZXing core)
        │   ├── render/        texto, formas, overlays, marcos, posts sociales
        │   ├── selection/     herramientas de selección y hormigas marchantes
        │   ├── text/          fuentes descargables y medición de texto
        │   └── transform/     recorte, giro, perspectiva, escalado
        ├── data/              DataStore de ajustes, paletas y datos del usuario
        ├── ui/                una carpeta por pantalla, más common/ y theme/
        ├── widget/            widget de pantalla de inicio (Glance)
        └── work/              WorkManager: lotes y limpieza de la papelera
```

### Principio de diseño

Hay **una sola implementación de dibujo** por función: la vista previa en pantalla y la
exportación final llaman al mismo código de `android.graphics`, solo con un tamaño de
lienzo distinto. Así una vista previa nunca puede descuadrarse respecto al archivo
exportado. Compose entra por `drawIntoCanvas { it.nativeCanvas }`.

### Textos

`values/strings.xml` y `values-es/strings.xml` **se generan**; no se editan a mano. Para
cambiar o añadir un texto, edita `tools/strings.tsv` (una línea por clave, campos
separados por `|||`) y ejecuta:

```bash
python tools/genstrings.py .
```

Las dos tablas salen siempre con las mismas claves, así que no puede faltar una
traducción.

---

## 3. Permisos y privacidad

El manifest declara **un solo permiso**: `POST_NOTIFICATIONS`, y solo para avisar del
progreso del editor por lotes.

- **Cámara:** no se declara `android.permission.CAMERA`. Las fotos se toman delegando en
  la app de cámara del sistema (`ACTION_IMAGE_CAPTURE`), que pide su propio permiso en el
  momento de usarla.
- **Galería y almacenamiento:** no se declara ningún permiso. Se usan el selector de fotos
  del sistema y el *Storage Access Framework*, que conceden acceso solo al archivo elegido.
- **Red:** la app no tiene `INTERNET` propio más allá de lo que requieren las bibliotecas.
  Lo único que sale a la red son las *Downloadable Fonts* de Google, bajo petición del
  usuario al elegir una tipografía, la descarga opcional del modelo de recorte de ML Kit y la del modelo de mejora con IA (Swin2SR x2, ~8 MB, la primera vez que se usa; después funciona sin red).
- **Nada de trackers, anuncios, analíticas ni telemetría.**

---

## 4. Tipografías: sin ficheros `.ttf`

El proyecto **no empaqueta ninguna fuente**. El texto usa *Downloadable Fonts* a través
del proveedor oficial de Google Play Services:

- El array de certificados lo declara la propia app en `app/src/main/res/values/font_certs.xml`
  (es el fichero oficial de Google, tal cual). `ui-text-google-fonts` no trae ningún
  recurso y `FontRequest` no admite peticiones sin certificados, así que tiene que venir
  del lado de la app.
- Si el proveedor no está disponible, el renderizador cae en las familias del sistema
  (`sans-serif`, `serif`, `monospace`…), así que la app nunca se queda sin texto.

---

## 5. Dependencias y por qué están

| Dependencia | Para qué |
|---|---|
| `androidx.core`, `appcompat` | Base y `AppCompatDelegate` para el idioma por app |
| Compose BOM + `ui`, `material3`, `material-icons-extended` | Toda la interfaz |
| `ui-text-google-fonts` | Fuentes descargables sin empaquetar `.ttf` |
| `lifecycle-*`, `activity-compose`, `navigation-compose` | ViewModels, estado y navegación |
| `datastore-preferences` | Ajustes, paletas y datos del usuario |
| `work-runtime-ktx` | Editor por lotes y limpieza automática de la papelera |
| `glance-appwidget`, `glance-material3` | Widget de pantalla de inicio en Compose |
| `kotlinx-serialization-json` | Formato `.pixa` (documento + capas) |
| `kotlinx-coroutines-android` | Trabajo fuera del hilo principal |
| `exifinterface` | Leer y **borrar** metadatos EXIF |
| `documentfile` | Carpetas de salida elegidas por el usuario |
| `coil-compose` | Miniaturas de las rejillas, con caché; evita escribir un gestor propio |
| `mlkit face-detection` | Censurar caras automáticamente (modelo empaquetado, sin red) |
| `play-services-mlkit-subject-segmentation` | Recorte del sujeto, aislado en el módulo `:ml` (modelo en Play Services, no en el APK) |
| `play-services-base` | `ModuleInstall`: descarga con progreso real del modelo opcional |
| `zxing core` | Generar códigos QR. **Solo `core`**: sin escáner ni cámara |

Todo el resto (filtros, desenfoques, degradados, formas, texto curvado, marcos,
exportación a PDF) está escrito con `android.graphics` y
`RenderEffect`/AGSL, sin bibliotecas de terceros.

### Versiones

La combinación del catálogo está elegida por estabilidad probada con Android Studio Panda:

| Pieza | Versión | Motivo |
|---|---|---|
| AGP | 8.7.2 | Última estable que Panda soporta sin avisos de actualización |
| Gradle | 8.9 | La que pide AGP 8.7.x |
| Kotlin | 2.0.21 | Trae el plugin de compilador de Compose como plugin de Gradle |
| Compose BOM | 2024.12.01 | Alineada con Kotlin 2.0.21 |
| compileSdk / targetSdk | 35 | Android 15 |
| minSdk | 29 | `android.graphics.BlendMode` y `Canvas` de hardware sin rutas antiguas |

> `minSdk 29` es una decisión de diseño, no una comodidad: toda la composición de capas
> usa `BlendMode` (API 29) en lugar de `PorterDuff`, que no cubre los modos de fusión que
> necesita un editor con capas.

---

## 6. Qué hay en cada pantalla

| Pantalla | Contenido |
|---|---|
| **Inicio** | Proyectos y imágenes separados en pestañas, recientes, papelera, buscador |
| **Editor** | Capas, selección, ajustes, texto, dibujo, formas, QR, retoque, marcos, historial |
| **Posts sociales** | 12 plataformas × 12 formatos (texto, foto, hilo, historia, chat…) |
| **Memes** | Plantillas, texto arriba/abajo con contorno |
| **Por lotes** | Aplicar una receta a muchas imágenes con WorkManager |
| **Unir fotos** | Tira vertical u horizontal con separación y fondo |
| **Ajustes** | Tema, idioma, calidad por defecto, marca de agua, carpeta de salida |
| **Tutorial** | Primeros pasos y gestos |

El **widget** de pantalla de inicio ofrece tres accesos: lienzo nuevo, última edición y
cámara.

---

### Looks, efectos y mejora con IA

- **Filtros:** los básicos más ~40 presets de fábrica por categoría (retrato, paisaje, comida,
  cine, película analógica, retro, neón y pastel, cielo), definidos como datos en
  `core/image/FactoryPresets.kt`. Todos llevan deslizador de intensidad.
- **LUT 3D `.cube`:** se importan desde el panel de filtros (categoría *LUTs*), se copian a
  `files/luts/` y se aplican con interpolación trilineal (`core/image/CubeLut.kt`).
- **Efectos nuevos** (`core/image/MoreEffects.kt`): grano, viñeta, aberración cromática, bloom,
  tilt-shift, bokeh, fuga de luz, bordes quemados, boceto, cómic, óleo, acuarela, vidriera,
  dispersión, líneas de velocidad, lluvia, nieve, destellos, doble exposición, anaglifo,
  mapa de calor, ruido de sensor, tonos divididos y cuatro de cielo. Los de cielo usan una
  heurística de color y posición, no un modelo de segmentación.
- **Ajustes de detalle** (`core/image/Detail.kt`): claridad, textura, dehaze, reducción de
  ruido y nitidez solo en bordes. Los radios dependen del tamaño de la imagen para que la
  vista previa y el resultado final coincidan.
- **Cortina antes/después:** `CompareControls` en los paneles de ajustes, filtros y efectos;
  el compuesto se arma en `EditorViewModel.renderOnce` con `core/render/Curtain.kt`.
- **Sección IA** (`ui/editor/panels/AiPanel.kt`, `ml/`): modelos que corren en el dispositivo con
  ONNX Runtime y se descargan bajo demanda. Antes de cada tarea, `AiPreflightDialog` informa del
  peso a descargar y del tiempo estimado (velocidades medidas en el propio móvil, `AiStats`); el
  aviso de ocupado muestra progreso y tiempo restante y se puede cancelar, también la descarga.
  `AiModelsDialog` (en la sección y en Ajustes) lista, descarga y borra los modelos.

  | Función | Modelo | Peso |
  |---|---|---|
  | Mejorar detalle / ×2 | Swin2SR lite | 8 MB |
  | Quitar ruido | SCUNet (ruido real) | 77 MB |
  | Seleccionar por toque | MobileSAM | 45 MB |
  | Desenfoque de retrato / niebla | Depth Anything V2 Small (cuantizado) | 27 MB |
  | Colorear | DDColor tiny | 135 MB |
  | Eliminar objetos | MI-GAN | 28 MB |
  | Mejorar caras | GFPGAN 1.4 | 340 MB |
  | Copiar texto (OCR) y escáner | ML Kit vía Play Services | — |

  **Quitar fondo** es la primera opción de la sección IA (ya no está en la barra inferior): se
  analiza la imagen con el modelo elegido (ML Kit, MediaPipe, U2-Net lite, Silueta, U2-Net, ISNet,
  ISNet anime, MODNet o RMBG 1.4, cada uno con su leyenda) y después se ajustan umbral, dureza,
  expandir/contraer, suavizado, refinado de pelo (filtro guiado), limpieza de halo, resultado
  (transparente, color o fondo desenfocado) e invertir, con vista previa en directo (`BgCompose`).

  **Direcciones de descarga:** viven solo en `ml/src/main/assets/ai_models.json`. Cada archivo
  admite varias URL (se prueban en orden, con comprobación de peso) y `mirror_base` añade tu
  servidor como respaldo para todos (o como primera opción con `mirror_first`). El espejo debe
  conservar los nombres de archivo. Si el JSON falta, se usan las URL que trae `AiModels.kt`.

## 7. Formato `.pixa`

Un proyecto es un ZIP con un `document.json` (estructura, capas, transformaciones,
máscaras, estilos) y los mapas de bits de las capas ráster en PNG. Las capas de texto,
forma y degradado se guardan como datos, así que siguen siendo **editables** al reabrir el
proyecto. Una *imagen* exportada, en cambio, es un archivo plano: la app lo deja claro en
la interfaz para que no se confundan los dos conceptos.

---

## 8. Licencia

Proyecto personal de DSK Music. Las bibliotecas de terceros conservan sus respectivas
licencias (Apache 2.0 en AndroidX, ML Kit y ZXing).
