# CASTV: CHANGELOG (cerebro) · v300

**Para retomar: Ariel pasa SOLO este archivo.** La IA lo lee completo y sigue sin preguntar datos fijos.
Archivos hermanos (solo si hacen falta): `HISTORIAL.md` (lo ya ✅), `MAPA.md` (arquitectura del código, se crea cuando Ariel pase el código).

Estados: ✅ confirmado por Ariel · 🧪 entregado/pendiente de probar · ⏳ en pausa · ⬜ por hacer · ❌ falló · ⛔ descartado.

---

## 1. ENTORNO FIJO (la IA usa esto tal cual, nunca lo pregunta)

| Dato | Valor |
|---|---|
| Usuario GitHub | `arieleljefe` |
| Repo | `castv-app` (rama `main`) → https://github.com/arieleljefe/castv-app |
| Visibilidad del repo | **Público** (Ariel, 23 nov). La API de Releases se puede leer sin token |
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

- **R12 Versiones:** no subir de versión ni proponer etiqueta con cambios pequeños; solo con cambios realmente grandes (decisión de Ariel, 23 nov). Al proponerla, dar la línea `git tag v<N> && git push origin v<N>`.

**Plantilla de entrada activa (sección 4):** `ID · estado · Qué · Archivos · Cómo probar · Siguiente paso`.

---

## 4. ESTADO VIVO

### ▶ RETOMAR AQUÍ (23 nov 2026, 21:00)
- **Hecho y ✅:** REMOTO-1, ATAJOS-1, CAPÍTULOS-1, VOCABULARIO-1/2, FIRMA-1, UPDATE-1, VERSION-2, **ESTILO-1**, **ESTILO-2**, **UPDATE-2** y **TV-2** (ver `HISTORIAL`). WIDGET-1 en pausa.
- **Decisión de Ariel (23 nov, 12:30):** quiere sobre todo **Actualización dentro de la app** y **Estilos y organización de pantallas**; carta blanca y repo público. Cola: ESTILO-1/2/3 › LISTA-VAL-1 › RECUP-1 (UPDATE-1 ya ✅).
- **FIRMA-1 ✅ (13:25):** build verde, huella del APK = huella de la clave (`4dd1f018003d62cbee40d558f5af56873eca1be31e2773d39974910121dc558e`), Ariel desinstaló, instaló el APK firmado, restauró el respaldo y "funciona todo bien". Desde ahora las versiones se instalan encima sin desinstalar. Los 4 secretos están en GitHub (se subieron con `gh` desde Termux; sesión `arieleljefe` iniciada). **Sin copia del `.jks` fuera del teléfono (decisión de Ariel):** copias en `~/castv-release.jks` (Termux) y en `~/storage/downloads/castv-release.jks`; si se pierden ambas, hay que crear otra clave y reinstalar desde cero. Recomendación dada: conservar el `.jks` de Download (sobrevive si se borra Termux) y borrar solo `castv-ks-b64.txt`, la carpeta `castv-firmado` y los `*_actual.*`.
- **Hoy NO hay etiqueta firmada:** la `v3.1` salió con la clave debug (TAG-3.1, antes de FIRMA-1). El primer Release firmado fue `v3.2` (instalada encima sin desinstalar).
- **UPDATE-1 ✅ (23 nov):** aplicado, build verde, instalado y "funciona bien de bien" (Ariel). Línea en `HISTORIAL`.
- **VERSION-2 ✅ (23 nov):** cabecera de la pantalla principal lee la versión de la app; probado con la actualización v3.2 → v3.3 ("está todo bien"). Línea en `HISTORIAL`.
- **ESTILO-1 ✅ (23 nov):** build verde, Ariel confirma "ya funciona". Línea en `HISTORIAL`. No salió etiqueta: no es un cambio grande.
- **ESTILO-2 ✅ (23 nov):** Ariel instaló la app, "funciona bien y se ve genial". Línea en `HISTORIAL`. Sin etiqueta (R12). Dato: `Store.kt` ganó la clave `chrecent` (últimos 12 canales vistos, no se guarda en incógnito).
- **UPDATE-2 ✅ (23 nov):** Ariel: "ya funciona". Aviso «🆕 Hay una versión nueva» al abrir la app (máx. cada 6 h; Actualizar ahora → Ajustes abre directo la búsqueda de UPDATE-1; Más tarde = 24 h por versión) e interruptor en Ajustes › Actualizaciones. Claves en preferencias `castv`: `upd_notify`, `upd_last`, `upd_later_tag`, `upd_later_until`. Línea en `HISTORIAL`.
- **TV-2 ✅ (23 nov):** Ariel: "ya funciona". Al pasar a modo TV busca los TVs del Wi-Fi: uno solo y sin TV elegido → se usa; el mismo ya elegido → confirma; varios o uno distinto → recuadro «📺 TVs en tu Wi-Fi» con Reintentar; ninguno → «No encontré TVs» (o aviso en la barra si ya había TV elegido). `pickTv()` ahora usa `connectTv(tv)`. Línea en `HISTORIAL`.
- **Regla de versiones (Ariel, 23 nov):** la etiqueta `v*` (versión nueva para la app) solo se propone con cambios realmente grandes (p. ej. ESTILO-2, RECUP-1). Los parches pequeños se suben al repo sin etiqueta; la última versión publicada sigue siendo `v3.3`. Siguiente etiqueta al tocar: `v3.4` (`git tag v3.4 && git push origin v3.4`). Para probar un parche sin etiqueta habría que ver `build.yml` (¿el push deja APK descargable?): se pide solo si Ariel lo necesita.
- **Ahora en 🧪: ESTILO-3 (parche `ESTILO-3.patch` entregado 23 nov, espera prueba de Ariel; Ariel dio el "Dale" y dejó elegir lo mejor).**
  - **Qué hace:** fila de chips de **ánimo** (desplazable) debajo del buscador en Canales: 😴 Relajarme · 🎭 Entretenimiento (antes «Reír»: la categoría `entertainment` es enorme y mezcla de todo) · 🧠 Aprender · 👨‍👩‍👧 Familia · 🎬 Cine y series · ⚽ Deportes · 📰 Informarme · 🎵 Música. Cada uno agrupa categorías de iptv-org (inglés y español, sin acentos) en `MOODS` al final de `M3uActivity.kt`. Tocar un chip filtra la lista abierta; tocarlo otra vez lo quita; el activo se marca con borde y texto de acento. Un chip sin canales en la lista no se muestra (si ninguno tiene, la fila se esconde). Ánimo y «Grupo» no se mezclan (elegir uno limpia el otro); con ★ Favs la fila se esconde; Continuar/Favoritos se esconden con un ánimo activo. Cambiar de lista o región limpia el ánimo.
  - **Cómo:** solo `M3uActivity.kt`. Máscara de ánimos por texto de grupo con caché (`moodMask`), recuento por ánimo recalculado solo cuando cambia la lista o los canales ocultos (`refreshMoods`), condición nueva en `applyFilter()` junto a `[GRUPOS-1]`. Sin cambios de formato guardado ni de layouts.
  - **Cómo probar:** abrir una lista con categorías (p. ej. 📰 Noticias o una región) → ver los chips; tocar 📰 Informarme: solo noticias y el estado dice «N de M canales»; tocarlo otra vez: vuelve todo; elegir un Grupo: el chip se apaga; ★ Favs: la fila se esconde; cambiar de lista: sin chip activo; probar en una región grande que no se sienta lenta.
  - **Siguiente paso:** al "ya funciona" de Ariel pasar ESTILO-3 a `HISTORIAL`; luego, mi recomendación: **RECUP-1** (recuperar canales en vivo que se caen).
- **Pendiente suelto de TV:** modelo real y versión del sistema (Ajustes › Soporte › Info de la TV).

### Cola de pendientes (uno a la vez, en este orden; ideas tomadas de `reproductor-IPTV` (MIT) y `kino-light` (Apache): solo ideas, código propio en Kotlin, sin nombre ni logo de Kino)
1. **ESTILO-3** 🧪 (entregado, a probar) · Canales por categorías y ánimo (idea de `movie-hub`). ESTILO-1, ESTILO-2, UPDATE-2 y TV-2 ya están ✅ en `HISTORIAL`. Antes de planear: pedir con plantilla A los archivos frescos y los layouts/`styles` que falten.
2. **LISTA-VAL-1** ⭐ Validar la lista antes de reemplazar: si la nueva llega vacía o dañada, conservar la anterior (ya se pasaron `M3uActivity.kt`, `M3uParser.kt`, `Store.kt`; falta el plan en 🧪).
3. **RECUP-1** ⭐ Recuperación automática de canales en vivo: reintentos con espera creciente y detector de atascos (toca `MediaServer.kt`/`/live`; cuidar bucles y probar con la TV).
4. Al final: idioma "Automático" de los subtítulos con IA (PAUSADO; pedir antes `jni_whisper.cpp` y `WhisperLib.kt`).

### Pendientes sueltos
- ⬜ Ideas de referencia NO elegidas (23 nov; con su contra): EPG-1 guía XMLTV (pantalla grande, XML pesado, emparejado de nombres falla) · XTREAM-1 importar con usuario y clave (solo si usa ese proveedor; guarda claves) · QR-1 pasar listas por QR/red local (abre una puerta en la red) · ATOMIC-1 guardado seguro de ajustes/favoritos (toca `Store.kt`) · FUENTES-1 salud y ranking de fuentes (Ariel prefiere ocultar a mano) · TESTS-1 pruebas automáticas en GitHub Actions · catch-up (exige EPG). Descartado: despliegue escalonado, espejos de descarga, telemetría, interfaz Compose/Android TV, paquetes webOS/Tizen/Roku.
- ⏳ COMPAT-TV-1: arreglo para la TV del amigo, solo cuando Ariel pruebe allá y pase el `/log` (el diagnóstico ya está ✅).
- ⬜ Modelo real y versión del sistema de la TV (el XML solo dice "SmartTV / Renderer"; `vidaa_support=1`). Ver en Ajustes › Soporte › Info de la TV.
- ⏳ WIDGET-1 (widget con ⏮ ⏯ ⏭): en pausa, Ariel no lo necesita ni lo usa en otras apps (22 nov). Plan guardado: `CastvWidget.kt` nuevo (`AppWidgetProvider`) + layout/xml/5 drawables propios + receiver en el manifest + 3 llamadas en `MainActivity` (`refreshNotification`, `refreshLinkNotification`, `postDefaultNotification`); los botones usan `ACT_PREV/TOGGLE/NEXT` igual que la notificación, así que solo funcionan con la app viva (límite: la reproducción vive en `MainActivity`, `CastvService` solo aloja el servidor).
- ⬜ Idea grande (sin planear): que la reproducción sobreviva a cerrar la app (mover el reproductor a un servicio); arreglaría notificación y widget.
- ⬜ Ideas: Android Auto / Android TV.
- ⏳ Pistas de audio/subtítulos embebidos: solo con libvlc/ffmpeg. El tamaño del APK ya no es impedimento (decisión de Ariel, 17 nov); se evalúa caso por caso, y solo si Ariel lo pide.
- Verificado 23 nov: temporizador de apagado y velocidad ya existen en música (`MainActivity.kt`); el temporizador también en video (GESTO-2). Velocidad en video: sin verificar.

### Descartado (⛔)
`Platzi-Download` y `Youtube_Downloader` (23 nov): no aportan a castv y descargan contenido de terceros; no se toman ideas de ellos.
Chromecast/AirPlay, DRM, subtítulos con IA en segundo plano con servicio, guardar el modelo de Whisper en Download, control por 36669 con certificado ajeno, flechas/OK/teclas por DLNA (la TV no expone ese servicio), `SetMute` como silencio (la TV lo ignora).
LOG-1 (ocultar claves en el `/log`): Ariel lo salta el 23 nov porque no comparte el log. Idea: `redact()` en `DebugLog.kt` que cambia por `[OCULTO]` el valor de `token`, `key`, `pass`, `secret`, `auth`, `sig`, `session`, `user`, `usuario:clave@` y `Bearer …` (no cubre claves en la ruta del enlace). Se retoma solo si Ariel empieza a compartir el log.

---

## 5. REFERENCIA RÁPIDA DEL CÓDIGO (hasta que exista MAPA.md)

Archivos conocidos en `app/src/main/java/com/castv/app/`: `MainActivity.kt` (pantalla principal, mini reproductor, Más, ecualizador, `playLink`), `VideoPlayerActivity.kt` (reproductor de video, gestos, PiP, subtítulos, foco de audio), `M3uActivity.kt` + `M3uParser.kt` + `Catalog.kt` + `Regions.kt` + `Logos.kt` (canales), `MediaServer.kt` (servidor, `/relay`, `/live`, buffer), `TvController.kt` (DLNA), `TvSend.kt`, `Store.kt` (datos), `Backup.kt`, `SettingsActivity.kt`, `AboutActivity.kt`, `ParentalPin.kt`, `PortDialog.kt`, `CastvService.kt`, `CastvUtils.kt`, `DebugLog.kt`, `Thumbs.kt`, `AudioExtractor.kt`, `WhisperModel.kt`, `WhisperLib.kt`, `SubtitleGen.kt`; C++ en `app/src/main/cpp/` (whisper.cpp v1.9.3).
Preferencias: `castv`, `castv_lib`, `castv_subs`, `castv_stats`.
