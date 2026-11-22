# CASTV: CHANGELOG (cerebro) · v263

**Para retomar: Ariel pasa SOLO este archivo.** La IA lo lee completo y sigue sin preguntar datos fijos.
Archivos hermanos (solo si hacen falta): `HISTORIAL.md` (lo ya ✅), `MAPA.md` (arquitectura del código, se crea cuando Ariel pase el código).

Estados: ✅ confirmado por Ariel · 🧪 entregado/pendiente de probar · ⏳ en pausa · ⬜ por hacer · ❌ falló · ⛔ descartado.

---

## 1. ENTORNO FIJO (la IA usa esto tal cual, nunca lo pregunta)

| Dato | Valor |
|---|---|
| Usuario GitHub | `arieleljefe` |
| Repo | `castv-app` (rama `main`) → https://github.com/arieleljefe/castv-app |
| Carpeta local del repo (Termux) | `~/castv-nuevo/castv-fresh` |
| Código Kotlin | `app/src/main/java/com/castv/app/` |
| Layouts / drawables / values | `app/src/main/res/layout/`, `res/drawable/`, `res/values/` |
| Manifest / Gradle | `app/src/main/AndroidManifest.xml`, `app/build.gradle` |
| Workflow | `.github/workflows/build.yml` (compila `assembleDebug`; etiqueta `v*` publica APK) |
| **Carpeta de intercambio (Download del móvil)** | `~/storage/downloads/` |
| Teléfono | Android + Termux · IP `192.168.100.43` (DHCP; si falla, revisar Ajustes › Wi-Fi) |
| App | puerto `8001` · log: `http://192.168.100.43:8001/log` (`?clear` para limpiar) |
| TV | Hisense HL1 (JVC en pantalla, Vidaa) · IP `192.168.100.14` · DLNA/UPnP puerto `38400` |
| Punto seguro | etiqueta `v2.1-estable` / commit `d7010ac` |
| Idioma | Español. Respuestas cortas (Ariel usa el móvil). |

**Reversión:** `git log --oneline -15` → `git revert HASH` → `git push origin main`.

---

## 2. PLANTILLAS DE TERMUX (SIEMPRE ESTAS, UNA SOLA LÍNEA, copiar y pegar)

Nunca cambiar la forma; solo sustituir lo que está en `<…>`.

**A. Pedir archivos de código** (la IA lista TODOS los que necesita para llegar al parche, en un solo pedido; ver regla R5):
```
cd ~/castv-nuevo/castv-fresh && cp app/src/main/java/com/castv/app/<A>.kt ~/storage/downloads/<A>_actual.kt && cp app/src/main/java/com/castv/app/<B>.kt ~/storage/downloads/<B>_actual.kt && ls -l ~/storage/downloads/*_actual.*
```
(Para manifest/layout/gradle usar su ruta de la tabla; el nombre destino siempre `<nombre>_actual.<ext>`.)

**B. Aplicar un parche y subirlo:**
```
cd ~/castv-nuevo/castv-fresh && git apply --check ~/storage/downloads/<parche>.patch && git apply ~/storage/downloads/<parche>.patch && git status --short && git add -A && git commit -m "<ID> <resumen>" && git push origin main
```

**C. Subir changelog (siempre con el nombre fijo `CHANGELOG.md` en Download):**
```
cd ~/castv-nuevo/castv-fresh && git ls-files | grep -x CHANGELOG.md && cp ~/storage/downloads/CHANGELOG.md CHANGELOG.md && cp ~/storage/downloads/HISTORIAL.md HISTORIAL.md && git add CHANGELOG.md HISTORIAL.md && git commit -m "Changelog <ID> (<resumen>)" && git push origin main
```
(La primera vez, si `git ls-files` no lo encuentra, quitar solo el tramo `git ls-files | grep -x CHANGELOG.md &&` y avisar.)

**D. Empaquetar todo el código (solo si hace falta ver mucho):**
```
cd ~/castv-nuevo/castv-fresh && git archive --format=zip -o ~/storage/downloads/castv-src.zip HEAD && ls -l ~/storage/downloads/castv-src.zip
```

**E. Limpiar archivos de prueba de Download (opcional, al cerrar un parche):**
```
rm -f ~/storage/downloads/*_actual.*
```

---

## 3. REGLAS PARA LA IA (obligatorias)

- **R1 Cero rupturas:** cambios aditivos y verificados contra nombres reales del código; revisar que cualquier acción `ACT_*` nueva se registre en el `IntentFilter` de `MainActivity`.
- **R2 Detalle antes del código:** primero el plan en 🧪 (qué cambia, archivos, cómo probar, riesgos) y esperar el "Dale" de Ariel. Excepción: si Ariel pide "todo de golpe", se entrega en parches encadenados, un commit por parche.
- **R3 Nunca marcar ✅ sin el "ya funciona" de Ariel** (build en verde + prueba en el teléfono).
- **R4 Un cambio a la vez**, parches `.patch` sin incluir el changelog; se verifican con `git apply --check` antes de entregar. Aquí no hay compilador de Kotlin: decirlo y pedir el mensaje de GitHub Actions si falla.
- **R5 Pedir archivos de una sola vez:** al empezar un objetivo, la IA decide TODOS los archivos que necesitará hasta el parche final (código, manifest, layouts, gradle, ruta de recursos) y los pide en UNA línea con la plantilla A. Si más tarde necesita otro, lo dice una vez con una línea nueva. Nunca pedir capturas ni "pásame la conversación"; Ariel solo sube archivos.
- **R6 Todo en Download:** cualquier archivo que Ariel deba subir o bajar (código, `.patch`, changelog, recursos) vive en `~/storage/downloads/`. La app NO guarda nada ahí (el modelo de Whisper se queda en carpeta interna).
- **R7 VLC solo como inspiración (GPL):** no copiar código, iconos, textos largos, nombre ni logo. Todo propio.
- **R8 Mantenimiento del changelog:** en cada respuesta que cambie algo, entregar `CHANGELOG.md` (y `HISTORIAL.md` si algo pasó a ✅) ya actualizados como archivos, más las líneas A/B/C que correspondan. Al pasar a ✅: mover a `HISTORIAL.md` como UNA línea y borrar de la sección 4. No crear notas por versión.
- **R9 Seguridad:** nunca guardar claves/certificados dentro del repo; no extraer ni copiar archivos de apps de terceros.
- **R10 Tono:** corto, directo, sin repetir lo ya dicho. Primero el resultado y el siguiente paso.

**Plantilla de entrada activa (sección 4):** `ID · estado · Qué · Archivos · Cómo probar · Siguiente paso`.

---

## 4. ESTADO VIVO

### REMOTO-1 · ⬜ · Control remoto dentro de CASTV (va primero)
- **Qué:** pantalla propia de control remoto en la app (cruceta/OK, atrás, inicio, menú, volumen, canal, encendido, teclado numérico, etc.), con iconos y código propios.
- **Diagnóstico cerrado (REMOTO-0):** el puerto 36669 (MQTT/TLS 1.2) exige certificado de cliente emitido por la CA de Hisense; con certificado propio responde `unknown ca`. **Descartado** para CASTV (R9). Borrar pruebas: `rm -f ~/castv_key.pem ~/castv_cert.pem`.
- **Decisión de Ariel (21 nov 2026):** seguir por DLNA (38400), sin nada externo. `TvController.kt` (509 líneas) hoy solo tiene AVTransport + RenderingControl (play, pausa, stop, seek, volumen, silencio); no hay teclas de navegación.
- **REMOTO-1-DIAG (cerrado, 22 nov 2026):** la ruta real del XML es `http://192.168.100.14:38400/MediaServer/rendererdevicedesc.xml` (`/description.xml` devuelve 0 bytes). La TV es solo MediaRenderer (nombre "TV del dormitorio") con 3 servicios: ConnectionManager (`/upnp/control/minguscmr`), AVTransport (`/upnp/control/mingusavtr`), RenderingControl (`/upnp/control/mingusrcr`). No hay servicio de teclas ni de control remoto. Conclusión: por DLNA no hay flechas/OK.
- **Siguiente paso (REMOTO-1a):** plan 🧪 entregado en el chat: pantalla de control con ▶⏸, seek, volumen y silencio mejor presentados (sin flechas/OK). Espera el "Dale" de Ariel; luego la IA pide `TvController.kt` y el layout/Activity que corresponda con la plantilla A.
- **Pendiente:** modelo real de la TV (el XML solo dice fabricante "SmartTV", modelo "Renderer"; la descripción trae `vidaa_support=1`). Se puede ver en Ajustes › Soporte › Info de la TV.

### ATAJOS-1 · 🧪 (solo plan, sin parche) · Atajos al mantener pulsado el icono
- **Qué:** atajos estáticos: Continuar último video, Música, Canales en vivo (Android 7.1+; `minSdk 24`).
- **Archivos:** `AndroidManifest.xml`, `res/xml/shortcuts.xml` (nuevo), `res/values/strings.xml`, `MainActivity.kt`.
- **Cómo probar:** mantener pulsado el icono; cada atajo abre lo correcto; abrir normal sigue igual.
- **Siguiente paso:** cuando le toque, la IA pide los archivos con la plantilla A y espera el "Dale".

### Cola de pendientes (uno a la vez, en este orden)
1. REMOTO-1 (arriba) → 2. ATAJOS-1 → 3. WIDGET-1 (widget con ⏮ ⏯ ⏭) → 4. CAPÍTULOS (el reproductor es `VideoView`/`MediaPlayer` y no lee capítulos: verificar y decir con honestidad si es posible sin cambiar de motor) → 5. Vocabulario en español (solo referencia) → 6. Idioma "Automático" de los subtítulos con IA (PAUSADO hasta el final; pedir antes `jni_whisper.cpp` y `WhisperLib.kt`).

### Pendientes sueltos
- ⏳ COMPAT-TV-1: arreglo para la TV del amigo, solo cuando Ariel pruebe allá y pase el `/log` (el diagnóstico ya está ✅).
- ⬜ Etiqueta `git tag v3.0 && git push origin v3.0` (publica `CASTV-v3.0.apk`), si Ariel aún no la puso.
- ⬜ Ideas: Android Auto / Android TV, widgets (ver WIDGET-1).

### Descartado (⛔)
Chromecast/AirPlay, DRM, subtítulos con IA en segundo plano con servicio, guardar el modelo de Whisper en Download, control por 36669 con certificado ajeno.

---

## 5. REFERENCIA RÁPIDA DEL CÓDIGO (hasta que exista MAPA.md)

Archivos conocidos en `app/src/main/java/com/castv/app/`: `MainActivity.kt` (pantalla principal, mini reproductor, Más, ecualizador, `playLink`), `VideoPlayerActivity.kt` (reproductor de video, gestos, PiP, subtítulos, foco de audio), `M3uActivity.kt` + `M3uParser.kt` + `Catalog.kt` + `Regions.kt` + `Logos.kt` (canales), `MediaServer.kt` (servidor, `/relay`, `/live`, buffer), `TvController.kt` (DLNA), `TvSend.kt`, `Store.kt` (datos), `Backup.kt`, `SettingsActivity.kt`, `AboutActivity.kt`, `ParentalPin.kt`, `PortDialog.kt`, `CastvService.kt`, `CastvUtils.kt`, `DebugLog.kt`, `Thumbs.kt`, `AudioExtractor.kt`, `WhisperModel.kt`, `WhisperLib.kt`, `SubtitleGen.kt`; C++ en `app/src/main/cpp/` (whisper.cpp v1.9.3).
Preferencias: `castv`, `castv_lib`, `castv_subs`, `castv_stats`.
