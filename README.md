# CASTV

Reproductor para Android que **envía tus videos y música al TV por la red Wi-Fi** (DLNA/UPnP). Elige un archivo en el teléfono, y suena o se ve en la tele, con controles en el teléfono, en la notificación y en la pantalla de bloqueo.

Creado por **Ariel De Jesus Bueno**.

> Estado: versión estable **2.1** (Etapa 11). Probada con un TV JVC. Si tu TV usa otro sistema, puede que algún detalle cambie (ver [Problemas frecuentes](#problemas-frecuentes)).

---

## Qué hace

- Escanea los **videos** y la **música** del teléfono.
- Busca el TV automáticamente en la red (SSDP) y permite **elegir TV** si hay varios aparatos.
- Reproduce en el TV con **pausa, avance, retroceso (±10 s), barra de progreso y volumen**.
- Pestañas **Videos, Favoritos y Continuar** (retoma donde te quedaste).
- Vistas por **Carpetas**, **Artista** y **Álbum**, y orden configurable.
- **Cola de reproducción**, **listas propias**, aleatorio y repetir.
- **Temporizador de apagado**, **velocidad de reproducción** y **ecualizador** (en el teléfono).
- Controles en la **notificación** y en la **pantalla de bloqueo**, con carátula.
- Servidor interno y **registro de depuración** que se puede leer desde Termux.

## Requisitos

| Qué | Detalle |
|---|---|
| Teléfono | Android 7.0 o superior (minSdk 24) |
| TV | Con DLNA / UPnP (AVTransport) activado |
| Red | Teléfono y TV en la **misma red Wi-Fi** |
| Formatos para el TV | **mp3** para música y **mp4 (video H.264 + audio AAC)** para video |

Los demás formatos (`.opus`, `.m4a`, `.mkv`, `.flac`, etc.) muchos TV no los leen. La app los **salta** cuando envía al TV; puedes convertirlos (ver más abajo).

---

## Instalar (solo usar la app)

### Opción A: descargar el APK ya compilado

1. En este repositorio entra a **Actions** (Acciones) y abre el último run con check verde.
2. Al final de la página, en **Artifacts**, descarga `castv-apk`.
3. Descomprime el zip y abre `app-debug.apk` en el teléfono.
4. Si Android lo pide, permite **instalar apps de origen desconocido** para tu navegador o gestor de archivos.
5. Abre CASTV y acepta los permisos de **archivos/multimedia** y **notificaciones**.

### Opción B: usar un Release

Si el autor publica un APK en la sección **Releases**, descárgalo de ahí e instálalo igual.

---

## Usar la app

1. Conecta el teléfono y el TV a la misma Wi-Fi y enciende el TV.
2. Abre CASTV: busca el TV sola. Si hay varios aparatos, usa **⋮ → Elegir TV**.
3. Toca un video o canción: aparece "Reproduciendo en el TV".
4. Controla con la barra inferior, la notificación o la pantalla de bloqueo.

Mantén la app abierta o en segundo plano mientras reproduces: el teléfono funciona como servidor del archivo.

---

## Construir el proyecto (compilar tú mismo)

### Opción 1: con GitHub Actions (la más fácil, sin PC)

1. Haz un **fork** de este repositorio, o sube el código a uno tuyo.
2. Cada vez que subes cambios a la rama `main`, el workflow `.github/workflows/build.yml` compila el APK solo.
3. Descarga el resultado en **Actions → último run → Artifacts → castv-apk**.

También puedes lanzar la compilación a mano desde **Actions → Build CASTV APK → Run workflow**.

### Opción 2: Android Studio (PC)

1. Instala Android Studio (con JDK 17).
2. `File → Open` y selecciona la carpeta del proyecto.
3. Espera a que termine el *Gradle sync*.
4. Conecta el teléfono con depuración USB y pulsa **Run**, o usa `Build → Build APK(s)`.

### Opción 3: línea de comandos

Requiere **JDK 17** y el **Android SDK** (plataforma 34) con `ANDROID_HOME` configurado.

```bash
git clone https://github.com/arieleljefe/castv-app.git
cd castv-app
chmod +x gradlew
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Versiones del proyecto

- Android Gradle Plugin 8.2.0, Kotlin 1.9.22, Gradle 8.5
- compileSdk / targetSdk 34, minSdk 24
- Dependencias: AndroidX (core, appcompat, media, constraintlayout), Material Components y [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) 2.3.1

---

## Subir tus cambios desde Termux (flujo del autor)

```bash
cd ~/castv-restore/castv-app
git add -A
git commit -m "Descripción del cambio"
git push origin main
```

Al subir a `main`, GitHub compila el APK nuevo automáticamente.

---

## Cómo funciona por dentro

1. **Descubrimiento:** la app manda un mensaje SSDP `M-SEARCH` y lee la descripción UPnP del TV para obtener la ruta de control de `AVTransport`.
2. **Servidor:** levanta un servidor HTTP propio en el **puerto 8000** que sirve `/video/ID.mp4` y `/audio/ID.mp3`, con soporte de `Range` (206) y `HEAD`.
3. **Envío:** manda al TV las órdenes SOAP `Stop`, `SetAVTransportURI` y `Play` con la URL del servidor del teléfono.
4. **Control:** consulta posición y estado del TV (`GetPositionInfo`, `GetTransportInfo`) y envía `Pause`, `Play`, `Seek` y volumen (`RenderingControl`).

### Estructura del código

| Archivo | Para qué sirve |
|---|---|
| `MainActivity.kt` | Interfaz, listas, cola, controles, temporizador, ecualizador |
| `TvController.kt` | Búsqueda del TV y órdenes UPnP/SOAP |
| `MediaServer.kt` | Servidor HTTP que entrega los archivos al TV |
| `MediaScanner.kt` | Lee videos y música del teléfono; tipo MIME por extensión |
| `CastvService.kt` | Servicio en primer plano, notificación y pantalla de bloqueo |
| `Store.kt` | Favoritos, historial, listas y ajustes guardados |
| `Thumbs.kt` | Miniaturas y carátulas |
| `DebugLog.kt` | Registro de depuración en memoria |
| `castv_app.py` | Script antiguo en Python (ver aviso abajo) |

---

## Problemas frecuentes

### El TV dice "Archivo no compatible"

1. **Puerto 8000 ocupado.** Si algo más usa ese puerto (por ejemplo el script `castv_app.py` en Termux), el TV le pide el archivo a ese programa y no a la app. Compruébalo:
   ```bash
   curl -i http://127.0.0.1:8000/log
   ```
   Si la respuesta dice `Server: SimpleHTTP ... Python`, no es la app. Ciérralo con `pkill -f castv_app.py` y reinicia CASTV (Ajustes → Apps → CASTV → Forzar detención).
2. **Formato.** Para el TV usa mp3 o mp4 (H.264 + AAC). Comprueba un archivo:
   ```bash
   ffprobe -v error -show_entries stream=codec_type,codec_name -of csv=p=0 "archivo.mp4"
   ```
3. Audios de WhatsApp (`.opus`, `.m4a`): conviértelos a mp3.
   ```bash
   ffmpeg -i "audio.opus" -c:a libmp3lame -q:a 2 "audio.mp3"
   ```
   Video H.265 a H.264:
   ```bash
   ffmpeg -i "entrada.mp4" -c:v libx264 -c:a aac "salida.mp4"
   ```

### "Error al enviar al TV"

- Comprueba que teléfono y TV están en la misma red y el TV está encendido.
- Prueba **⋮ → Elegir TV**.
- Mira el registro (siguiente sección).

### Ver el registro de depuración

Con la app abierta, desde Termux:

```bash
curl -s http://127.0.0.1:8000/log                # ver todo
curl -s "http://127.0.0.1:8000/log?clear"        # limpiar
```

Muestra cada orden enviada al TV con su respuesta, la URL del archivo y cada petición que el TV le hace al teléfono.

### El build de GitHub Actions falla

Descarga los logs del run fallido y busca líneas que empiecen con `e: file://`: indican el archivo y la línea del error de Kotlin.

---

## Aviso sobre `castv_app.py`

Es la primera versión del proyecto, hecha en Python para Termux. **No la ejecutes mientras usas la app**: ambas usan el puerto 8000.

---

## Contribuir

Los *issues* y *pull requests* son bienvenidos. Si reportas un problema, incluye el modelo del TV, la versión de Android y la salida de `curl -s http://127.0.0.1:8000/log`.

## Licencia

Aún no se ha definido una licencia. Si quieres que otros puedan reutilizar el código, añade un archivo `LICENSE` (por ejemplo MIT) al repositorio.
