# CASTV: CHANGELOG (cerebro) · v311

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

### ▶ RETOMAR AQUÍ (23 nov 2026, 21:20)
- **Hecho y ✅:** **RECUP-1** (23 nov), REMOTO-1, ATAJOS-1, CAPÍTULOS-1, VOCABULARIO-1/2, FIRMA-1, UPDATE-1, VERSION-2, **ESTILO-1**, **ESTILO-2**, **UPDATE-2**, **TV-2**, **ESTILO-3** y **ZAP-1** (ver `HISTORIAL`). WIDGET-1 en pausa.
- **Decisión de Ariel (23 nov, 12:30):** quiere sobre todo **Actualización dentro de la app** y **Estilos y organización de pantallas**; carta blanca y repo público. Cola: ESTILO-1/2/3 › LISTA-VAL-1 › RECUP-1 (UPDATE-1 ya ✅).
- **FIRMA-1 ✅ (13:25):** build verde, huella del APK = huella de la clave (`4dd1f018003d62cbee40d558f5af56873eca1be31e2773d39974910121dc558e`), Ariel desinstaló, instaló el APK firmado, restauró el respaldo y "funciona todo bien". Desde ahora las versiones se instalan encima sin desinstalar. Los 4 secretos están en GitHub (se subieron con `gh` desde Termux; sesión `arieleljefe` iniciada). **Sin copia del `.jks` fuera del teléfono (decisión de Ariel):** copias en `~/castv-release.jks` (Termux) y en `~/storage/downloads/castv-release.jks`; si se pierden ambas, hay que crear otra clave y reinstalar desde cero. Recomendación dada: conservar el `.jks` de Download (sobrevive si se borra Termux) y borrar solo `castv-ks-b64.txt`, la carpeta `castv-firmado` y los `*_actual.*`.
- **Hoy NO hay etiqueta firmada:** la `v3.1` salió con la clave debug (TAG-3.1, antes de FIRMA-1). El primer Release firmado fue `v3.2` (instalada encima sin desinstalar).
- **UPDATE-1 ✅ (23 nov):** aplicado, build verde, instalado y "funciona bien de bien" (Ariel). Línea en `HISTORIAL`.
- **VERSION-2 ✅ (23 nov):** cabecera de la pantalla principal lee la versión de la app; probado con la actualización v3.2 → v3.3 ("está todo bien"). Línea en `HISTORIAL`.
- **ESTILO-1 ✅ (23 nov):** build verde, Ariel confirma "ya funciona". Línea en `HISTORIAL`. No salió etiqueta: no es un cambio grande.
- **ESTILO-2 ✅ (23 nov):** Ariel instaló la app, "funciona bien y se ve genial". Línea en `HISTORIAL`. Sin etiqueta (R12). Dato: `Store.kt` ganó la clave `chrecent` (últimos 12 canales vistos, no se guarda en incógnito).
- **UPDATE-2 ✅ (23 nov):** Ariel: "ya funciona". Aviso «🆕 Hay una versión nueva» al abrir la app (máx. cada 6 h; Actualizar ahora → Ajustes abre directo la búsqueda de UPDATE-1; Más tarde = 24 h por versión) e interruptor en Ajustes › Actualizaciones. Claves en preferencias `castv`: `upd_notify`, `upd_last`, `upd_later_tag`, `upd_later_until`. Línea en `HISTORIAL`.
- **TV-2 ✅ (23 nov):** Ariel: "ya funciona". Al pasar a modo TV busca los TVs del Wi-Fi: uno solo y sin TV elegido → se usa; el mismo ya elegido → confirma; varios o uno distinto → recuadro «📺 TVs en tu Wi-Fi» con Reintentar; ninguno → «No encontré TVs» (o aviso en la barra si ya había TV elegido). `pickTv()` ahora usa `connectTv(tv)`. Línea en `HISTORIAL`.
- **ESTILO-3 ✅ (23 nov):** Ariel: "ya funciona". Fila de chips de ánimo en Canales (😴 Relajarme · 🎭 Entretenimiento · 🧠 Aprender · 👨‍👩‍👧 Familia · 🎬 Cine y series · ⚽ Deportes · 📰 Informarme · 🎵 Música); `MOODS` al final de `M3uActivity.kt`. Línea en `HISTORIAL`.
- **Regla de versiones (Ariel, 23 nov):** la etiqueta `v*` (versión nueva para la app) solo se propone con cambios realmente grandes (p. ej. ESTILO-2, RECUP-1). Los parches pequeños se suben al repo sin etiqueta; la última versión publicada es `v3.4`. Siguiente etiqueta al tocar: `v3.5` (`git tag v3.5 && git push origin v3.5`). Para probar un parche sin etiqueta habría que ver `build.yml` (¿el push deja APK descargable?): se pide solo si Ariel lo necesita. **`v3.4` YA ESTÁ PUBLICADA** (23 nov: etiqueta y Release existen; el workflow exige la clave en las etiquetas, así que salió firmada). **Aviso:** la app instalada en el teléfono de Ariel es un build de `main` posterior a la etiqueta (código 217, nombre «3.1» porque sin etiqueta `build.gradle` usa el nombre por defecto), y el Release `v3.4` trae código 216; por eso el actualizador dice «El APK no es más nuevo que el instalado (216; instalado 217)». No es un fallo: la guarda de UPDATE-1 funciona, y el código instalado ya incluye todo lo de `v3.4`. Se arregla solo con la siguiente etiqueta (`v3.5`, su código será > 217). Mientras, UPDATE-2 seguirá avisando de `v3.4`: usar «Más tarde» o apagar el interruptor en Ajustes › Actualizaciones. **No reinstalar `v3.4`** (bajar de código exige desinstalar). **Lección:** cada push a `main` gasta número de ejecución; para que las actualizaciones dentro de la app funcionen, instalar solo APK de un Release (etiqueta), no el artefacto de un push.
- **Ahora en 🧪: LISTA-VAL-1 (ENTREGADO el 23 nov y probado en vivo: con el archivo vaciado salió «1 de 1 canales · lista nueva dañada (copia guardada)»; falta el «ya funciona» de Ariel, R3).** RECUP-1 ✅ (Ariel, 23 nov: «ya funciona»; línea en `HISTORIAL`; sin etiqueta, R12). Código visto: `castv-src.zip` (HEAD). Hallazgo en `M3uActivity.kt`: en `loadList` y en `loadRegion`, cada descarga que da HTTP 200 se escribe a la copia guardada (`cache.writeText`) SIN mirar qué trae; si llega vacía, una página de error o texto dañado, se pisa la copia buena y la lista queda en «no tiene canales». Solo cuando la descarga lanza error se usa la copia.
  - **Qué cambia (solo `M3uActivity.kt`, aditivo):** se interpreta la descarga ANTES de guardarla: si `M3uParser.parse` da 0 canales, se trata como fallo (se anota en el `/log`: `M3U: la lista nueva llegó vacía o dañada, se conserva la anterior`), NO se toca la copia y se usa la anterior. En `loadList` el pie dice «· lista nueva dañada (copia guardada)» en vez de «sin conexión». En `loadRegion` se hace igual por cada lista y el resumen suma « · N con copia anterior». Si no había copia anterior, todo queda como hoy («La lista no tiene canales…»). Se evita parsear dos veces: se reutiliza el resultado ya parseado.
  - **Archivos:** solo `M3uActivity.kt` (`M3uParser.kt` y `Store.kt` no se tocan).
  - **Riesgos:** (a) una lista que de verdad quedara vacía seguiría mostrando la anterior (se quita con «Quitar lista»); (b) no se rechazan listas que solo encogen (decisión: solo vacía o dañada, para no dar falsos rechazos); (c) sin compilador de Kotlin aquí (R4).
  - **Cómo probar:** 1) abrir una lista normal y una región: igual que antes; 2) prueba de lista dañada con un servidor propio en Termux (se dan los comandos al entregar): cargar una lista válida, vaciar el archivo, volver a cargar → debe mostrar los canales de antes con el aviso y en el `/log` la línea; 3) apagar datos/Wi-Fi y abrir una lista ya cargada: sigue mostrando «sin conexión (copia guardada)».
  - **Entregado:** `LISTA-VAL-1.patch` (solo `M3uActivity.kt`; `git apply --check` verificado contra HEAD; sin compilador de Kotlin, R4). Campo nuevo `cacheNote` (texto del pie); en región, contador `kept` («N con copia anterior»). Si no hay copia y la descarga llega dañada: «❌ La lista llegó vacía o dañada. Revisa el enlace.»
  - **Prueba con servidor propio (Termux, dos líneas):** `mkdir -p ~/t && printf '#EXTM3U\n#EXTINF:-1,Prueba Uno\nhttp://example.com/a.m3u8\n#EXTINF:-1,Prueba Dos\nhttp://example.com/b.m3u8\n' > ~/t/lista.m3u` y luego `cd ~/t && python3 -m http.server 9000 &`. En la app: añadir lista `http://192.168.100.43:9000/lista.m3u` (2 canales). Vaciarla con `: > ~/t/lista.m3u`, volver a abrir la lista: deben seguir los 2 canales con «lista nueva dañada (copia guardada)» y la línea en el `/log`. Al terminar: `kill %1`, «Quitar lista» y `rm -r ~/t`.
  - **Siguiente paso:** Ariel dice «ya funciona» → pasa al historial. Sin etiqueta (R12). (La prueba dejó la lista `prueba` y `~/t` en Termux: limpiar con 🗑 y `pkill -f http.server; rm -r ~/t`.)
- **ZAP-1 ✅ (23 nov):** Ariel: «Funciona bien con el TV». ⏮ ⏭ cambian de canal cuando hay un canal de lista en el TV (pantalla Reproduciendo y notificación). Solo vale para el TV: en modo teléfono ⏮⏭ no cambian de canal (por diseño; opción ZAP-2 si Ariel la pide: `Zap.kt`, `M3uActivity.kt`, `VideoPlayerActivity.kt`). Línea en `HISTORIAL`. Sin etiqueta (R12).
- **Ahora en 🧪: SPEED-1 (selector de velocidad del video; ENTREGADO el 23 nov, pendiente de probar).** Código visto: `VideoPlayerActivity.kt` (hoy la velocidad solo existe al mantener pulsado: `setFast`, `fastVals`, `PlaybackParams`).
  - **Qué cambia (solo `VideoPlayerActivity.kt`, aditivo):** Herramientas gana «⏩ Velocidad: 1×» (opciones 0.5×, 0.75×, 1×, 1.25×, 1.5×, 2×), con diálogo de una elección como «Volumen extra». Variable `curSpeed` (por video: vuelve a 1× al cambiar de video; no se guarda). `applySpeed()` usa `mp.playbackParams`, y si el video estaba en pausa lo deja en pausa (algunos teléfonos arrancan solos). Al soltar «mantener pulsado» vuelve a la velocidad elegida, no a 1×. Canales en vivo (sin duración): aviso «No disponible en canales en vivo». Capítulos pasa del índice 8 al 9 en el menú.
  - **Archivos:** solo `VideoPlayerActivity.kt` (`Store.kt` no se toca).
  - **Riesgos:** (a) el audio a 0.5× / 2× suena distinto según el teléfono; (b) toca `setFast` (gesto de mantener pulsado): se prueba que siga igual; (c) sin compilador de Kotlin aquí (R4).
  - **Cómo probar:** 1) Herramientas › Velocidad: elegir 1.5× y ver que acelera; 2) mantener pulsado y soltar: vuelve a 1.5×; 3) en pausa, cambiar velocidad: no arranca solo; 4) cambiar de video: vuelve a 1×; 5) Capítulos sigue abriendo (MKV con capítulos).
  - **Entregado:** `SPEED-1.patch` (solo `VideoPlayerActivity.kt`; `git apply --check` verificado contra el archivo actual; sin compilador de Kotlin, R4). Velocidad por video, sin guardar (decisión: la más simple). Variables `speedVals`, `curSpeed`; funciones `applySpeed()`, `showSpeedDialog()`, `speedText()`; `setFast(false)` vuelve a `curSpeed`. Menú: Velocidad = índice 8, Capítulos pasa a 9.
  - **Siguiente paso:** Ariel prueba y dice «ya funciona» (R3) o pasa el error de Actions. Sin etiqueta (R12).
- **Pendiente suelto de TV:** modelo real y versión del sistema (Ajustes › Soporte › Info de la TV).

### Cola de pendientes (uno a la vez, en este orden; ideas tomadas de `reproductor-IPTV` (MIT) y `kino-light` (Apache): solo ideas, código propio en Kotlin, sin nombre ni logo de Kino)
1. **SPEED-1** Selector de velocidad del video (0.5×–2×) en Herramientas (entregado, pendiente de probar).
2. Idioma "Automático" de los subtítulos con IA: **PAUSADO por Ariel (23 nov): no tocar ni proponer hasta que él lo mencione** (al retomar, pedir antes `jni_whisper.cpp` y `WhisperLib.kt`).

### Pendientes sueltos
- ⬜ Ideas de referencia NO elegidas (23 nov; con su contra): EPG-1 guía XMLTV (pantalla grande, XML pesado, emparejado de nombres falla) · XTREAM-1 importar con usuario y clave (solo si usa ese proveedor; guarda claves) · QR-1 pasar listas por QR/red local (abre una puerta en la red) · ATOMIC-1 guardado seguro de ajustes/favoritos (toca `Store.kt`) · FUENTES-1 salud y ranking de fuentes (Ariel prefiere ocultar a mano) · TESTS-1 pruebas automáticas en GitHub Actions · catch-up (exige EPG). Descartado: despliegue escalonado, espejos de descarga, telemetría, interfaz Compose/Android TV, paquetes webOS/Tizen/Roku.
- ⏳ COMPAT-TV-1: arreglo para la TV del amigo, solo cuando Ariel pruebe allá y pase el `/log` (el diagnóstico ya está ✅).
- ⬜ Modelo real y versión del sistema de la TV (el XML solo dice "SmartTV / Renderer"; `vidaa_support=1`). Ver en Ajustes › Soporte › Info de la TV.
- ⏳ WIDGET-1 (widget con ⏮ ⏯ ⏭): en pausa, Ariel no lo necesita ni lo usa en otras apps (22 nov). Plan guardado: `CastvWidget.kt` nuevo (`AppWidgetProvider`) + layout/xml/5 drawables propios + receiver en el manifest + 3 llamadas en `MainActivity` (`refreshNotification`, `refreshLinkNotification`, `postDefaultNotification`); los botones usan `ACT_PREV/TOGGLE/NEXT` igual que la notificación, así que solo funcionan con la app viva (límite: la reproducción vive en `MainActivity`, `CastvService` solo aloja el servidor).
- ⬜ Idea grande (sin planear): que la reproducción sobreviva a cerrar la app (mover el reproductor a un servicio); arreglaría notificación y widget.
- ⬜ Ideas: Android Auto / Android TV.
- ⏳ Pistas de audio/subtítulos embebidos: solo con libvlc/ffmpeg. El tamaño del APK ya no es impedimento (decisión de Ariel, 17 nov); se evalúa caso por caso, y solo si Ariel lo pide.
- Verificado 23 nov: temporizador de apagado y velocidad ya existen en música (`MainActivity.kt`); el temporizador también en video (GESTO-2). Velocidad en video, verificado 23 nov: solo existe al mantener pulsado (1.5×/2×/3×), no hay selector (ver SPEED-1).

### Descartado (⛔)
`Platzi-Download` y `Youtube_Downloader` (23 nov): no aportan a castv y descargan contenido de terceros; no se toman ideas de ellos.
Chromecast/AirPlay, DRM, subtítulos con IA en segundo plano con servicio, guardar el modelo de Whisper en Download, control por 36669 con certificado ajeno, flechas/OK/teclas por DLNA (la TV no expone ese servicio), `SetMute` como silencio (la TV lo ignora).
LOG-1 (ocultar claves en el `/log`): Ariel lo salta el 23 nov porque no comparte el log. Idea: `redact()` en `DebugLog.kt` que cambia por `[OCULTO]` el valor de `token`, `key`, `pass`, `secret`, `auth`, `sig`, `session`, `user`, `usuario:clave@` y `Bearer …` (no cubre claves en la ruta del enlace). Se retoma solo si Ariel empieza a compartir el log.

---

## 5. REFERENCIA RÁPIDA DEL CÓDIGO (hasta que exista MAPA.md)

Archivos conocidos en `app/src/main/java/com/castv/app/`: `MainActivity.kt` (pantalla principal, mini reproductor, Más, ecualizador, `playLink`), `VideoPlayerActivity.kt` (reproductor de video, gestos, PiP, subtítulos, foco de audio), `M3uActivity.kt` + `M3uParser.kt` + `Catalog.kt` + `Regions.kt` + `Logos.kt` (canales), `MediaServer.kt` (servidor, `/relay`, `/live`, buffer), `TvController.kt` (DLNA), `TvSend.kt`, `Store.kt` (datos), `Backup.kt`, `SettingsActivity.kt`, `AboutActivity.kt`, `ParentalPin.kt`, `PortDialog.kt`, `CastvService.kt`, `CastvUtils.kt`, `Zap.kt` (ZAP-1: lista de canales para ⏮ ⏭), `DebugLog.kt`, `Thumbs.kt`, `AudioExtractor.kt`, `WhisperModel.kt`, `WhisperLib.kt`, `SubtitleGen.kt`; C++ en `app/src/main/cpp/` (whisper.cpp v1.9.3).
Preferencias: `castv`, `castv_lib`, `castv_subs`, `castv_stats`.
