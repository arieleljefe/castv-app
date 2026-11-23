# CASTV: CHANGELOG (cerebro) · v281

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
| TV | Hisense HL1 (JVC en pantalla, Vidaa) · IP `192.168.100.14` · DLNA/UPnP puerto `38400` (solo MediaRenderer; XML en `/MediaServer/rendererdevicedesc.xml`). **Ignora `SetMute`: el silencio es `SetVolume 0`**; `GetVolume` da 0 hasta el primer `SetVolume` |
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

**B+C. Parche y changelog de golpe (LA QUE SE ENTREGA por defecto cuando hay parche; una sola línea, dos commits, un solo push):**
```
cd ~/castv-nuevo/castv-fresh && git apply --check ~/storage/downloads/<parche>.patch && git apply ~/storage/downloads/<parche>.patch && git add -A && git commit -m "<ID> <resumen>" && cp ~/storage/downloads/CHANGELOG_v<N>.md CHANGELOG.md && cp ~/storage/downloads/HISTORIAL_v<N>.md HISTORIAL.md && git add CHANGELOG.md HISTORIAL.md && git commit -m "Changelog <ID> (<resumen>)" && git push origin main && git status --short
```
(B y C sueltas se usan solo cuando hay parche sin changelog o changelog sin parche.)

**C. Subir changelog (en Download viene con versión: `CHANGELOG_v<N>.md` y `HISTORIAL_v<N>.md`, con el mismo número (el del título); en el repo se guardan siempre como `CHANGELOG.md` y `HISTORIAL.md`):**
```
cd ~/castv-nuevo/castv-fresh && git ls-files | grep -x CHANGELOG.md && cp ~/storage/downloads/CHANGELOG_v<N>.md CHANGELOG.md && cp ~/storage/downloads/HISTORIAL_v<N>.md HISTORIAL.md && git add CHANGELOG.md HISTORIAL.md && git commit -m "Changelog <ID> (<resumen>)" && git push origin main
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
- **R8 Mantenimiento del changelog:** en cada respuesta que cambie algo, entregar `CHANGELOG_v<N>.md` (el título sube de versión en cada entrega) y `HISTORIAL_v<N>.md` con el MISMO número (siempre se entregan los dos juntos) ya actualizados como archivos, más las líneas A/B/C que correspondan (si hay parche y changelog, UNA sola línea B+C). Al pasar a ✅: mover a `HISTORIAL.md` como UNA línea y borrar de la sección 4. El archivo lleva la versión en el nombre, pero no se crean notas por versión dentro del texto.
- **R9 Seguridad:** nunca guardar claves/certificados dentro del repo; no extraer ni copiar archivos de apps de terceros.
- **R10 Tono:** corto, directo, sin repetir lo ya dicho. Primero el resultado y el siguiente paso.

- **R11 Termux en el móvil:** comandos cortos; si una línea es muy larga el pegado se corta (queda un `>` esperando): partirla en 2 pasos (definir función, luego usarla) y recordar `CTRL`+`c` para salir. Las pruebas imprimen en pantalla (captura) o guardan en Download; Ariel NO borra Download hasta que la IA lo diga. Parche + changelog + historial = UNA sola línea (B+C), con `CHANGELOG_v<N>.md` y `HISTORIAL_v<N>.md` del mismo número.

**Plantilla de entrada activa (sección 4):** `ID · estado · Qué · Archivos · Cómo probar · Siguiente paso`.

---

## 4. ESTADO VIVO

### ▶ RETOMAR AQUÍ (23 nov 2026, 01:15)
- **Hecho y ✅:** REMOTO-1, ATAJOS-1 y CAPÍTULOS-1 (ver `HISTORIAL`). WIDGET-1 queda en pausa (no lo necesita).
- **Hecho y ✅:** REMOTO-1, ATAJOS-1, CAPÍTULOS-1 y VOCABULARIO-1.
- **Ahora:** VOCABULARIO-2 🧪: parche entregado (abajo); falta que Ariel lo suba, espere el build en verde y lo pruebe.
- **Pendiente suelto de TV:** modelo real y versión del sistema (Ajustes › Soporte › Info de la TV).

### VOCABULARIO-2 · 🧪 (parche `VOCABULARIO-2.patch`, 23 nov 2026) · Unificar textos de la app
- **Qué:** solo cambia texto visible, sin tocar lógica. Según `VOCABULARIO.md` (sección 2). Código de `castv-src.zip` (HEAD del 23 nov), ya leído: no hace falta pedir archivos.
- **Cambios (~35 líneas, 5 archivos):**
  - `MainActivity.kt`: "En el móvil" → "En el teléfono" (botones y avisos, 5 sitios: 1534, 1561, 1589, 1590, 1619); "Playlist vacía" → "Lista vacía" (1489, 1498); "aparato(s)" → "dispositivo(s)" (3591, 3601); "Borrado: a la papelera/permanente" → "Eliminado: …" (1131); "🗑 Eliminar lista" → "🗑 Quitar lista" (3550); "Vídeo" → "Video" (764).
  - `M3uActivity.kt`: "móvil" → "teléfono" (769, 799, 815, 816).
  - `VideoPlayerActivity.kt`: "Bajar y generar" → "Descargar y generar" (1208); "Bajando el modelo…" → "Descargando el modelo…" (1236); "▶ Reanudar" → "▶ Continuar" (512).
  - `SettingsActivity.kt`: "Vídeo" → "Video" en todas las entradas y en el subtítulo de sección (~17 sitios, mismo cambio en todas para que sigan agrupadas); "en el móvil" → "en el teléfono" (236, 1358); "Borrado: papelera o permanente" → "Eliminado: papelera o permanente" (363).
  - `res/layout/activity_main.xml`: texto "Vídeo" → "Video" (278).
- **Fuera de alcance:** los 4 avisos de error con texto técnico (sección 2.4), comentarios del código y nombres internos.
- **Al preparar el parche:** revisar los avisos vecinos de "Quitar lista" (el aviso al quitar debe decir "quitada", no "eliminada").
- **Riesgo:** bajo. Lo único delicado es "Vídeo" → "Video" en Ajustes, porque es el nombre que agrupa las entradas: se cambia en todas a la vez.
- **Cómo probar:** build en verde › Ajustes: aparece la sección "Video" con todas sus entradas › ⋮ › menú de Almacenamiento dice "Eliminado: …" › Listas: la opción dice "Quitar lista" y funciona › enlace con datos móviles: el aviso dice "teléfono" › subtítulos con IA: "Descargando el modelo…" › lo demás igual.
- **Entregado:** 5 archivos, 39 líneas cambiadas (+39/−39). Verificado con `git apply --check` + `git apply` sobre el código real (`castv-src.zip`); las llaves no cambian (el desfase 471/472 en `VideoPlayerActivity.kt` ya existía antes por llaves dentro de textos). El Kotlin no se compiló aquí.
- **Cambios extra respecto al plan:** el aviso "Lista eliminada" pasa a "Lista quitada" (coherente con "Quitar lista"). Se deja igual el título "Borrado" de la entrada de Ajustes › Almacenamiento (solo cambió su subtítulo y el menú).
- **Siguiente paso:** build en verde + prueba de Ariel; con el "ya funciona" pasa a ✅ y a `HISTORIAL`. Si falla el build, pasar el mensaje de GitHub Actions.

### Cola de pendientes (uno a la vez, en este orden)
1. Idioma "Automático" de los subtítulos con IA (PAUSADO hasta el final; pedir antes `jni_whisper.cpp` y `WhisperLib.kt`).

### Pendientes sueltos
- ⏳ COMPAT-TV-1: arreglo para la TV del amigo, solo cuando Ariel pruebe allá y pase el `/log` (el diagnóstico ya está ✅).
- ⬜ Modelo real y versión del sistema de la TV (el XML solo dice "SmartTV / Renderer"; `vidaa_support=1`). Ver en Ajustes › Soporte › Info de la TV.
- ⏳ WIDGET-1 (widget con ⏮ ⏯ ⏭): en pausa, Ariel no lo necesita ni lo usa en otras apps (22 nov). Plan guardado: `CastvWidget.kt` nuevo (`AppWidgetProvider`) + layout/xml/5 drawables propios + receiver en el manifest + 3 llamadas en `MainActivity` (`refreshNotification`, `refreshLinkNotification`, `postDefaultNotification`); los botones usan `ACT_PREV/TOGGLE/NEXT` igual que la notificación, así que solo funcionan con la app viva (límite: la reproducción vive en `MainActivity`, `CastvService` solo aloja el servidor).
- ⬜ Idea grande (sin planear): que la reproducción sobreviva a cerrar la app (mover el reproductor a un servicio); arreglaría notificación y widget.
- ⬜ Ideas: Android Auto / Android TV.
- ⏳ Pistas de audio/subtítulos embebidos: solo con libvlc/ffmpeg. El tamaño del APK ya no es impedimento (decisión de Ariel, 17 nov); se evalúa caso por caso, y solo si Ariel lo pide.
- Verificado 23 nov: temporizador de apagado y velocidad ya existen en música (`MainActivity.kt`); el temporizador también en video (GESTO-2). Velocidad en video: sin verificar.

### Descartado (⛔)
Chromecast/AirPlay, DRM, subtítulos con IA en segundo plano con servicio, guardar el modelo de Whisper en Download, control por 36669 con certificado ajeno, flechas/OK/teclas por DLNA (la TV no expone ese servicio), `SetMute` como silencio (la TV lo ignora).

---

## 5. REFERENCIA RÁPIDA DEL CÓDIGO (hasta que exista MAPA.md)

Archivos conocidos en `app/src/main/java/com/castv/app/`: `MainActivity.kt` (pantalla principal, mini reproductor, Más, ecualizador, `playLink`), `VideoPlayerActivity.kt` (reproductor de video, gestos, PiP, subtítulos, foco de audio), `M3uActivity.kt` + `M3uParser.kt` + `Catalog.kt` + `Regions.kt` + `Logos.kt` (canales), `MediaServer.kt` (servidor, `/relay`, `/live`, buffer), `TvController.kt` (DLNA), `TvSend.kt`, `Store.kt` (datos), `Backup.kt`, `SettingsActivity.kt`, `AboutActivity.kt`, `ParentalPin.kt`, `PortDialog.kt`, `CastvService.kt`, `CastvUtils.kt`, `DebugLog.kt`, `Thumbs.kt`, `AudioExtractor.kt`, `WhisperModel.kt`, `WhisperLib.kt`, `SubtitleGen.kt`; C++ en `app/src/main/cpp/` (whisper.cpp v1.9.3).
Preferencias: `castv`, `castv_lib`, `castv_subs`, `castv_stats`.
