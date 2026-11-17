# Registro de cambios y guía de continuidad – CASTV (v25)

Este archivo es **la memoria del proyecto**. Si hay que seguir con otro asistente (otro bot), se le pasa este archivo completo y con eso puede continuar.

**Cómo leer los estados:**

- ✅ **Incorporado:** el cambio ya está en el código y subido a GitHub.
- 🧪 **Subido, falta probar** (o falta confirmar que compile).
- ⏳ **Prueba pendiente** de una parte concreta.
- ⬜ **Por hacer.**
- ❌ **Falló** (se revirtió o se corrigió; se explica qué pasó).

**Cómo deshacer un cambio:**

```bash
git log --oneline -15          # busca el hash del cambio
git revert HASH                # crea un commit que lo deshace
git push origin main
```

**Si todo se rompe**, volver al último punto bueno: la rama `respaldo-antes-de-restaurar`, la etiqueta `v2.1-estable` (si ya se creó) o el commit `d7010ac` (Etapa 11).

---

## 📍 Dónde nos quedamos (cierre de la sesión del 17 nov 2026, ~12:35)

**Para quien siga (otro asistente o yo mismo más tarde): empieza aquí.**

**Hecho en esta sesión (con pruebas en el Hisense HL1):**

- ❌ **13-A.3 (HLS al TV)** registrado en la v22: el TV pide la lista `.m3u8` y no sigue los enlaces. HLS queda solo para el móvil.
- ✅ **Paso 0, enlace con redirección:** archive.org (`big_buck_bunny_720p_surround.mp4`, 61,9 MB) → 📺 Al TV: `RELAY redirige (302) -> https://dn710604.ca.archive.org/...`, luego `RELAY 206 ... 0-61878608/61878609`. **El video se ve en el TV** (confirmado por Ariel).
- ✅ `flower.mp4` de MDN sigue funcionando en el TV (`206`, 1 128 375 bytes). Ya estaba ✅ en 13-A.2.
- ❌ **Paso 0, adelantar (seek) con el control remoto del TV:** no funciona. En `/log` solo está el `206` inicial; **el TV no hizo ningún `GET` más con `range=bytes=N-`**. **En diagnóstico** (ver abajo y la entrada "Paso 0" en "Cambios pendientes de probar").
- ⚠️ **Enlaces de prueba que ya no sirven** (17 nov 2026): Blender `http` (301 → https → 404), Blender `https` directo (404) y Google `gtv-videos-bucket` (403). Usar **archive.org** (sección "Fuentes legales", A.5).

**Decisión de Ariel (17 nov 2026, ~12:50): el diagnóstico del seek (puntos 1 a 4 de abajo) queda ⏳ PENDIENTE.** Se hará más tarde; por ahora se sigue con las mejoras de la app ("Pulir la app", ver "Hoja de ruta"). No se ha corrido ningún `curl` de diagnóstico todavía y no hay parche escrito. Verificado en Termux: `castv-fresh` está al día con `origin/main` y `CHANGELOG.md` del repo ya coincide con la v23 (`git commit` dijo "nothing to commit"; `git push` dijo "Everything up-to-date"). La v25 añade además la sección "Prioridades acordadas" (abajo).

**Qué sigue, en este orden:**

1. **Diagnosticar el seek (sin parche).** Con un video largo enviado al TV (archive.org, A.5), pegar uno por uno:
   ```bash
   P=$(curl -s http://127.0.0.1:8000/log | grep -o '/relay/[A-Za-z0-9_-]*\.mp4' | tail -1); echo $P
   ```
   ```bash
   curl -s -D - -o /dev/null --max-time 3 -H "Range: bytes=30000000-" "http://127.0.0.1:8000$P"
   ```
   (Si el puerto no es 8000, usar 8001, 8002…). Mirar en la respuesta: `HTTP/1.1 206`, `Content-Range: bytes 30000000-61878608/61878609`, `Accept-Ranges: bytes` y una línea `contentFeatures.dlna.org` con `DLNA.ORG_OP=01`. **Anotar qué falta.**
2. **Prueba de control:** mandar al TV un **video local del teléfono** (no un enlace) y adelantar con el mismo control remoto. Si adelanta → el fallo es solo de `/relay`; si no → es del TV o de la información DLNA que manda la app.
3. **Prueba B pendiente:** adelantar con los botones/barra de **CASTV en el móvil** con un enlace al TV y mirar `/log` (¿sale una línea `SOAP Seek`?). Si no sale, la app no manda la orden `Seek` para enlaces.
4. **Según el resultado, un parche pequeño y aditivo** (todavía **no escrito**): cabeceras `Accept-Ranges` / `contentFeatures.dlna.org` en `/relay` (`MediaServer.kt`) y/o `Seek` en `TvController.kt` / `MainActivity.kt`. Comentarios `// [Etapa 13-A.4]`. No tocar `/video/` ni `/audio/`.
5. **Resto del Paso 0:** pausa/reanudar/detener con un enlace; salir de CASTV mientras suena un enlace.
6. **Paso 2 · Etapa 13-B** (listas M3U, partes B.1 a B.4), luego **Paso 3 · 13-C**. Ver "Plan de trabajo próximo".
7. Pendientes antiguos (aviso del puerto 8000, color de acento, siguiente video y botón TV, README + Release, Etapa 12): ver "Hoja de ruta".

**Archivos que hay que subir a la conversación para seguir** (el asistente no los tiene; sin ellos no se puede asegurar que compile). Copiarlos a Descargas con:

```bash
cd ~/castv-nuevo/castv-fresh && cp app/src/main/java/com/castv/app/{Store,MainActivity,MediaServer,TvController}.kt ~/storage/downloads/
```

Son: `MainActivity.kt` (obligatorio), `MediaServer.kt` y `TvController.kt` (para el seek), `Store.kt` (para 13-B). `VideoPlayerActivity.kt`, `DebugLog.kt` y `CastvService.kt` solo si se tocan.

**Cosas por confirmar (no se sabe):**

- Si Ariel **ya subió este changelog** (v22/v23) al repo: `cd ~/castv-nuevo/castv-fresh && pwd && git log --oneline -3`, luego `cp ~/storage/downloads/CHANGELOG_v23.md CHANGELOG.md && git add -A && git commit -m "Changelog v23" && git push origin main`. Si el push es rechazado (historias distintas con `~/castv-app`), **no usar `--force`**: ver el aviso de abajo.
- **Cuál es la copia buena del proyecto** (`~/castv-app` vs `~/castv-nuevo/castv-fresh`): sigue sin resolverse; ver el aviso de abajo.
- Para este changelog **no** se ha marcado ✅ nada que Ariel no haya confirmado (regla 7).

---

## 🎯 Prioridades acordadas (17 nov 2026, v25) — LEER ANTES DE EMPEZAR

**Objetivo de Ariel:** una app potente para **ambos usos**: (a) sus propios videos y música del teléfono, y (b) enlaces y canales (listas M3U). Principio: primero **fiable y con control completo**, luego funciones extra.

**Si esta lista choca con "Qué sigue" de arriba, manda esta lista** (el diagnóstico del seek sigue aplazado por decisión de Ariel y entra en la prioridad 2).

**Prioridad 0 · Base segura (antes de escribir funciones nuevas):**

1. ⬜ Confirmar la copia buena del proyecto. Ya verificado el 17 nov: `~/castv-nuevo/castv-fresh` está al día con `origin/main` (mismo remoto `arieleljefe/castv-app`). Falta comprobar `~/castv-app` (¿tiene commits sin subir, como la Etapa 12 `2c37468`?). No usar `push --force`.
2. ⬜ Ver que el último build de GitHub Actions salga verde.
3. ⬜ Probar lo ya subido y marcarlo ✅ solo si Ariel lo confirma: aviso del puerto 8000, color de acento, siguiente video automático, ⏮ ⏭ y botón 📺 TV.
4. ⬜ README + etiqueta `v2.1-estable` + Release (punto de vuelta seguro).

**Prioridad 1 · Orden del código (poco a poco, sin cambiar el comportamiento):** `MainActivity.kt` tiene casi toda la lógica. Al tocarlo, sacar partes a archivos propios (por ejemplo la lógica de cast y las pantallas) en parches pequeños. Así cada parche es más seguro. No hacerlo todo de una vez.

**Prioridad 2 · Control completo desde el móvil:** barra de progreso con seek, volumen, pausa/reanudar/detener y estado real del TV (posición y duración). Aquí entra el **diagnóstico del seek** (aplazado): primero los dos `curl` de "Qué sigue", luego la prueba de control con video local y la prueba B, y después un parche pequeño y aditivo (`// [Etapa 13-A.4]`). Cerrar también el resto del Paso 0 (pausa, detener, salir de CASTV mientras suena un enlace).

**Prioridad 3 · Segundo plano sólido (Etapa 12):** cola editable, retomar sesión, notificación con controles. Traer `Player.kt` a `castv-fresh` por partes, después de resolver la prioridad 0.

**Prioridad 4 · Enlaces y listas M3U (Etapa 13-B, partes B.1 a B.4):** pegar un enlace o una lista (por enlace o archivo), pestaña de canales, búsqueda y favoritos. Usar solo fuentes legales (sección "Fuentes legales"). Después, 13-C.

**Prioridad 5 · Varios TV:** detección automática, recordar el último TV, mostrar nombre y modelo, modo de prueba.

**Ideas para el futuro (sin fecha):**

- Subtítulos en el TV por DLNA (experimental; depende de que el TV los acepte).
- Convertir `.opus` / `.m4a` / `.mkv` dentro de la app con ffmpeg (el APK crece mucho; mientras tanto, convertir con Termux).
- Generar subtítulos escaneando videos (Vosk / Whisper) y letra sincronizada (LRCLIB).
- Mini reproductor más compacto y mensajes de error más claros al enviar al TV.
- HLS al TV: descartado por ahora (❌ 13-A.3); HLS queda solo para el móvil.

---

## ⚠️ Antes de seguir: confirmar cuál es la copia buena del proyecto (17 nov 2026)

En esta conversación se trabajó sobre **`~/castv-app`** (Etapa 11 = commit `b99ba6f`, arreglo `indexOf` = `1608196`, **Etapa 12 = `2c37468`**). Este changelog (v13) habla de **otra copia**: `~/castv-nuevo/castv-fresh` (Etapa 11 = `d7010ac`, arreglo `indexOf` = `dc6e415`) que ya tiene reproductor de video, temas, color de acento, borrado y nombres legibles.

- Esas funciones (VideoPlayerActivity, DebugLog, temas, acento, selección múltiple, papelera) **NO están** en la Etapa 12 de `~/castv-app`: ese zip se hizo a partir del zip de la Etapa 11.
- El push de la Etapa 12 a GitHub entró sin problema, así que ahora mismo `origin/main` apunta a `2c37468`. Si `castv-fresh` tiene commits propios sin subir, su próximo `git push` va a ser rechazado (historias distintas).
- **Antes de la Etapa 13, comprobar en las dos carpetas** (pegar uno por uno):

```bash
cd ~/castv-app && git log --oneline -3 && git remote -v
cd ~/castv-nuevo/castv-fresh && git log --oneline -3 && git remote -v
```

- Decidir cuál es la buena: lo normal es quedarse con **`castv-fresh`** (tiene más funciones) y **traer a mano la Etapa 12** (`Player.kt`) encima, por partes. Nunca hacer `push --force` sin tener la copia de seguridad.

---

## Contexto para otro asistente

### Qué es el proyecto

**CASTV** es una app de Android (Kotlin) que envía videos y música del teléfono a un **TV por Wi-Fi (DLNA/UPnP)**. El teléfono levanta un servidor HTTP propio en el **puerto 8000** y le ordena al TV, por SOAP (`Stop`, `SetAVTransportURI`, `Play`), que reproduzca `http://IP-del-teléfono:8000/video/ID.mp4` o `/audio/ID.mp3`. Autor: Ariel De Jesus Bueno.

- Repositorio: `github.com/arieleljefe/castv-app` (rama `main`). Versión estable base: Etapa 11 (v2.1).
- Versiones: Android Gradle Plugin 8.2.0, Kotlin 1.9.22, Gradle 8.5, compileSdk/targetSdk 34, minSdk 24, NanoHTTPD 2.3.1.
- Archivos principales (`app/src/main/java/com/castv/app/`): `MainActivity.kt` (interfaz y casi toda la lógica), `TvController.kt` (UPnP/SOAP), `MediaServer.kt` (servidor HTTP), `MediaScanner.kt` (lee los medios), `CastvService.kt` (servicio en primer plano), `Store.kt` (favoritos, listas, ajustes), `Thumbs.kt` (miniaturas), `DebugLog.kt` (registro y estado del servidor).
- `castv_app.py` es el script antiguo en Python. **No debe ejecutarse mientras se usa la app**: ocupa el puerto 8000 y el TV le pide los archivos a él (causó el error "Archivo no compatible").

### Datos importantes del TV

- El TV solo lee **mp3** y **mp4 (H.264 + audio AAC)**. Otros formatos (`.opus`, `.m4a`, `.mkv`, `.flac`) los rechaza; la app los salta al enviar al TV.
- Registro de depuración con la app abierta: `curl -s http://127.0.0.1:8000/log` (y `?clear`).

### Cómo se trabaja (importante)

1. **El usuario trabaja desde un teléfono con Termux.** Respuestas cortas, comandos para pegar **uno por uno**, y capturas de pantalla de lo que sale. Habla español, tono informal.
2. **El asistente no puede compilar.** La compilación la hace **GitHub Actions** al subir a `main`. El APK queda en Acciones → último run → Artifacts (`castv-apk`). Si el build sale con X roja, el usuario descarga los logs (zip) y los sube; el error está en `0_build.txt`, líneas con `e: file://` o `error:`.
3. **Los cambios se entregan como archivos `.patch`** que el usuario descarga a Descargas y aplica:
   ```bash
   cd ~/castv-restore/castv-app
   git apply --exclude=CHANGELOG.md ~/storage/downloads/ARCHIVO.patch
   cp ~/storage/downloads/CHANGELOG_vN.md CHANGELOG.md
   git add -A && git commit -m "Mensaje" && git push origin main
   ```
   Siempre se excluye `CHANGELOG.md` del parche y se entrega el changelog completo aparte (evita conflictos).
4. **Regla del changelog (pedida por Ariel):** siempre se entrega **primero** el changelog completo y actualizado, con lo hecho y lo que falta, antes que cualquier parche o respuesta larga, por si se acaban los tokens.
   **Regla anterior:** al terminar o integrar algo, se anota aquí con ✅. **Siempre se entrega primero el changelog actualizado**, antes que cualquier otra cosa, por si se acaban los tokens.
5. Probar siempre que el parche aplica antes de entregarlo (`git apply --check`), y entregar pasos de prueba con cada cambio.
6. **Notas en el código y detalle de novedades en el changelog:** En cada cambio o etapa nueva se deben poner comentarios claros directamente en el código (por ejemplo, `// [Etapa 13-A] ...`) indicando qué se modificó o añadió, y registrar en este changelog tanto la novedad exacta como la forma en que queda funcionando la app.
7. **Cuándo se entrega un changelog nuevo (regla de Ariel, 17 nov 2026, vale desde la v21):** se entrega **solo** (a) cuando se escribe o cambia el **plan** (como esta v21) y (b) cuando **Ariel confirma que un cambio funciona** ("ya funciona"). Entre una cosa y otra, los parches se entregan **sin** changelog nuevo. Mientras Ariel no confirme, la etapa queda en este archivo como ⬜ o 🧪 y **no** se marca ✅. Así no se pierde nada y el estado del archivo es siempre el último confirmado. Esta regla ajusta la 4: el changelog sigue yendo **primero** cuando toca entregarlo. **(c)** También se entrega cuando **Ariel lo pide expresamente** (17 nov 2026, v23: "pasa el changelog para seguir más tarde"); en ese caso se anota todo lo hecho, lo pendiente y lo que **no** está confirmado, sin marcar ✅ nada que Ariel no haya confirmado.

### Lecciones aprendidas (para no repetir errores)

- Un estilo de Android llamado `A.B.C` exige un estilo padre `A.B`; si no existe, el build falla. Se usaron nombres sin puntos (`AccentOverlayCyan`).
- `indexOf(speed)` con `speedValues` no compila; se usa `indexOfFirst { it == speed }`.
- Si `git apply` dice "already exists" o "does not apply", normalmente el parche **ya estaba aplicado**: revisar con `git diff --stat` y `git log --oneline -5`.
- Si `curl http://127.0.0.1:8000/log` responde `Server: SimpleHTTP ... Python`, es el script viejo: `pkill -f castv_app.py` y reiniciar CASTV.
- Kotlin: dentro de una subclase de `View` (p. ej. `MediaController`), nombres como `overlay` o `background` se resuelven contra la propia vista; usar nombres propios como `topBar`.
- Siempre confirmar con `pwd` y `git log` que se está dentro de `~/castv-nuevo/castv-fresh` antes de aplicar un parche; en `~` hay otros repos y se mezclan. Tras `git apply`, verificar con un `grep` de algo del parche antes de hacer commit.
- Nunca subir `.gradle/` ni `build/` a git (pasan de 100 MB y GitHub rechaza el push); `.gitignore` ya los excluye.
- En Termux, `ps` no siempre muestra procesos de otras sesiones; buscar por `python`.
- **`curl` al puerto 8000 sin respuesta con el aviso "puerto ocupado"** = el aviso es cierto y el servidor de CASTV no arrancó (algo ajeno lo ocupa, a veces otra app o una ejecución vieja de CASTV que Termux no ve). Se arregló buscando puerto libre (8000 a 8010, Etapa 13-A.1); el registro está en el puerto real: probar 8000, 8001, 8002…
- **Un enlace que el móvil reproduce no implica que el TV lo lea:** el TV recibe la URL por DLNA, con `http` casi siempre sí y con `https`, redirecciones o `.m3u8` casi siempre no. Para enlaces externos hace falta el intermediario `/relay` (Etapa 13-A.2).

- **Antes de aplicar un parche que usa nombres de otra parte del código** (como `ServerPort` o `getLocalIpAddress()`; el 17 nov el changelog decía `ServerState.port` y el nombre real era `ServerPort.port`: el código manda, no la memoria), buscarlos con `grep` en el proyecto: un nombre mal escrito solo se descubre al compilar en Actions.
- Si `cp ~/storage/downloads/ARCHIVO` dice "No such file", el archivo **no está en Descargas** (no se descargó o tiene otro nombre): comprobar con `ls ~/storage/downloads | grep CHANGELOG`. Si ya había un changelog viejo, el `cp` anterior deja ese y no el nuevo (pasó con v18/v19 el 17 nov).
- **Termux no deja escribir en `/tmp`** ("Permission denied"). Usar una carpeta propia, p. ej. `~/tmp-castv`. Por eso la primera vez no se copió nada y el commit decía "nothing to commit".
- Si el commit dice **"nothing to commit, working tree clean"** justo después de copiar archivos nuevos, la copia **no ocurrió**: comprobar con `ls` que existe un archivo nuevo del cambio (p. ej. `Player.kt`) antes del commit.
- Los zip de proyecto traen su propia carpeta `.git`; **no copiarla encima** del repo. El zip de la Etapa 12 ya sale sin `.git`.
- Un cambio grande hecho **sin poder compilar** (como la Etapa 12) puede traer errores de compilación; la verdad la dice GitHub Actions. Buscar `e: file` en los registros.
- En `~/castv-app` los cambios de la Etapa 12 se entregaron como **zip del proyecto completo** (más fácil que `.patch` cuando el parche "ya estaba aplicado" o no aplica). Pasos: descomprimir en `~/tmp-castv`, `cp -r ~/tmp-castv/castv-app/. ~/castv-app/`, `ls` para confirmar el archivo nuevo, `git add -A`, commit y push.

- **El Hisense HL1 no lee HLS por DLNA** (13-A.3, 17 nov 2026): pide la lista `.m3u8` y no sigue sus enlaces, aunque venga reescrita a `http` por `/relay`. En `/log` se reconoce porque, tras `RELAY HLS lista: N enlaces reescritos`, no aparece ningún `GET /relay/...` más. Para canales en vivo el destino es el móvil.

- **Enlaces de prueba que caen (17 nov 2026):** Blender `http` (301 → https → 404), Blender `https` directo (404) y Google `gtv-videos-bucket` (403) ya no sirven. **`RELAY: el origen respondió 403/404` en `/log` = el fallo es del origen del enlace**, no de la app ni del TV. Usar **archive.org** (`https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4`), que además redirige (302) y prueba la redirección del relay.
- **El Hisense hace dos `GET` por cada enlace:** uno con `ua=HISENSE-HL1... HbbTV/1.1.1` y `range=bytes=0-`, y otro con `ua=curl/7.19.5` y `range=-` (sonda del propio TV, misma IP 192.168.100.14). El `200` de la segunda no es un error.
- **Un `flower.mp4` (1,1 MB) no sirve para probar el seek:** el TV lo baja entero en una sola petición. Usar un video largo.
- **Cómo se ve en `/log` que "no adelanta":** tras el `RELAY 206 ... 0-FINAL/TOTAL` inicial no aparece ningún `GET` más con `range=bytes=N-` (N distinto de 0). Para distinguir si el que falla es el TV o la app, mirar también si sale una línea `SOAP Seek`.
- **Procedimiento del changelog:** `cp ~/storage/downloads/CHANGELOG_vN.md CHANGELOG.md` **sobrescribe a propósito**: cada versión es el archivo completo (no hay que fusionar nada). Siempre con `pwd` y `git log` antes.

### Texto para pegarle a otro asistente

> Soy Ariel y estoy desarrollando la app Android CASTV (repo `arieleljefe/castv-app`). Te paso el archivo CHANGELOG.md, que tiene el contexto, las reglas de trabajo, el historial y la hoja de ruta al final. Lee primero la sección "Contexto para otro asistente" y "Hoja de ruta". Trabajo desde Termux en el teléfono, así que dame comandos cortos para pegar uno por uno y los cambios como archivos .patch. Continúa por la sección "Próxima función".

---

## Plan de trabajo próximo (acordado con Ariel el 17 nov 2026: "quiero todo")

**Reglas de este plan:** un parche por paso, `git apply --check` antes de entregarlo, comentarios `// [Etapa X]` en el código, pasos de prueba con cada parche, y **changelog nuevo solo cuando Ariel confirme que funciona** (regla 7). Estado de todos los pasos de abajo: ⬜ por hacer.

**Archivos que hay que subir a la conversación para poder escribir los parches** (el asistente no los tiene; sin ellos no se puede asegurar que compile): `MainActivity.kt` (obligatorio para 13-B y 13-C), `Store.kt` (favoritos/listas, para 13-B), `VideoPlayerActivity.kt` (para HLS en móvil), `DebugLog.kt` y `CastvService.kt` (para el puerto y el arranque). `MediaServer.kt` y `TvController.kt` ya se tenían, pero **cambiaron** (Etapa 13-A.2 y 13-A.3): subir la versión actual de **las dos** (`TvController.kt` hace falta para diagnosticar el seek) para no escribir sobre una copia vieja. Comando para copiarlos: ver "Dónde nos quedamos" (arriba).

### Paso 0 · Terminar de probar 13-A.2 (sin parche) — **en curso** (17 nov 2026)

- ❌ **Adelantar (seek) en el TV** con el control remoto del TV: archive.org 720p (61,9 MB) → el relay respondió `RELAY 206 ... 0-61878608/61878609` (todo el archivo desde el principio) y **no hubo ningún `GET` posterior con `range=bytes=N-`**. Con `flower.mp4` no se puede probar (pesa 1,1 MB). **Diagnóstico pendiente** (⏳, pasos 1 a 4 de "Dónde nos quedamos").
  - **Hipótesis (sin confirmar):** (a) el relay no anuncia `Accept-Ranges: bytes` o `contentFeatures.dlna.org` con `DLNA.ORG_OP=01` (el TV entonces no intenta saltar); (b) el TV no adelanta con este tipo de envío DLNA aunque el archivo sea local; (c) la app no manda `Seek` por SOAP para enlaces. La entrada 13-A.2 dice que el relay reenvía `Range` y añade cabeceras DLNA, pero **no se ha verificado qué cabeceras salen**.
- ✅ **Enlace con redirección:** archive.org → `RELAY redirige (302) -> https://dn710604.ca.archive.org/...`, luego `206`, y el video se ve en el TV (confirmado por Ariel, 17 nov 2026, ~12:31).
- ⬜ **Pausa, reanudar y detener** con un enlace: el TV debe obedecer.
- ⬜ **Salir de CASTV mientras suena un enlace:** anotar si se corta (el teléfono hace de puente; debería seguir mientras el servicio esté activo).

### Paso 1 · Etapa 13-A.3: HLS (`.m3u8`) por `/relay` ❌ (el TV no lo lee; ver su entrada arriba)

- **Resultado (17 nov 2026):** el TV pide la lista y no pide los trozos, ni con lista maestra ni con lista directa. HLS queda solo para el móvil. Lo de abajo es el plan original.

- **Por qué:** los canales en vivo casi siempre son HLS y el TV los rechaza directos. El teléfono puede bajar la lista y los trozos por `https` y servirlos por `http`.
- **Cómo:** `/relay/<token>.m3u8` baja la lista del origen y **reescribe cada línea de URL** (trozos `.ts`/`.m4s`, listas hijas, `#EXT-X-KEY URI=`, `#EXT-X-MAP URI=`) a `http://IP:PUERTO/relay/<token-nuevo>.<ext>`, resolviendo las rutas relativas contra la URL base. Los trozos se sirven como el mp4: redirecciones, `Range`, MIME `video/mp2t` o el del origen. Lista maestra (varias calidades): reescribir también las listas hijas; si hay varias calidades, el parche puede **elegir la más baja o media** para no saturar el Wi-Fi (por decidir).
- **Archivos:** `MediaServer.kt` (ruta y reescritura), `MainActivity.kt` (`playLink`: HLS también por `/relay`, MIME `application/vnd.apple.mpegurl`).
- **Probar:** HLS de Mux (A.3 de "Fuentes legales") y de Apple (A.4) → 📺 Al TV; mirar `/log`: debe haber `GET` de la lista y luego de los trozos. En el móvil (reproductor propio) debe seguir funcionando igual.
- **Riesgos:** (1) **no se sabe si el Hisense/VIDAA lee HLS por DLNA**: si el TV pide la lista y la rechaza o no pide trozos, el HLS no va en este TV y se deja solo para el móvil (se anota ❌ con la razón); (2) listas en vivo se recargan cada pocos segundos: hay que reescribir cada vez, sin guardar en caché; (3) trozos cifrados (AES-128) funcionan solo si la clave también pasa por el relay; DRM no se puede.
- **Si falla:** `git revert` del commit de 13-A.3; los mp4 por `/relay` no se ven afectados si el parche es solo aditivo.

### Paso 2 · Etapa 13-B: listas M3U (canales) ⬜

Se divide en partes pequeñas para poder probar cada una:

- **B.1 · Leer una lista M3U y guardarla.** Parser de `#EXTINF` (nombre, `tvg-logo`, `group-title`) + línea de URL. Cargar desde **enlace** (descarga en segundo plano) y guardar en `Store` (lista de canales + origen + fecha). Probar con una lista **pequeña** (Free-TV/IPTV) mirando que cuente los canales en un aviso.
- **B.2 · Pestaña "Canales".** Nueva pestaña/sección con la lista de canales (nombre, grupo, logo si es fácil), con el mismo estilo que Música/Video. Al tocar un canal: manda al destino activo (TV por `/relay`, o el móvil).
- **B.3 · Buscador, grupos y favoritos de canales.** Reutiliza la búsqueda sin acentos, el orden A-Z/Z-A y la estrella de favoritos que ya existen; filtro por `group-title`.
- **B.4 · Cargar desde archivo** `.m3u`/`.m3u8` (selector del sistema) y **actualizar/borrar** una lista cargada; guardar varias listas con nombre.
- **Reglas de contenido:** solo se recomiendan fuentes de la sección "Fuentes legales" (Free-TV/IPTV primero, iptv-org por categoría o país, nunca el índice completo de golpe). Lo que se cargue es responsabilidad de quien lo carga; la app es solo un reproductor.
- **Riesgos:** listas muy grandes (miles) → leer y mostrar en segundo plano y por partes; muchos canales caídos o bloqueados por país (normal); depende de que **13-A.3** funcione para que los canales HLS suenen en el TV (**13-A.3 falló: los canales HLS irán solo al móvil**; en el TV solo irán los que sean mp4 u otro enlace directo que el TV sí lea por `/relay`).
- **Archivos:** `MainActivity.kt`, `Store.kt`, un archivo nuevo para el parser (p. ej. `M3uParser.kt`) y layouts/strings nuevos.
- **Si falla:** `git revert` del commit de la parte que falle (cada B.x es un commit aparte).

### Paso 3 · Etapa 13-C: mejoras para varios TV ⬜

- **C.1 · Recordar y mostrar el último TV:** nombre y modelo (del XML de descripción UPnP) en ⋮ → Elegir TV, y reconexión directa a ese TV al abrir.
- **C.2 · Modo de prueba de TV:** botón que manda un mp4 conocido por `/relay` y deja en `/log` el resultado (qué pidió el TV, `user-agent`, respuestas), para depurar un TV nuevo.
- **Contras:** solo probado con el Hisense (HL1); cada marca es distinta (códecs, MIME, https). No se puede garantizar que todos funcionen.

### Orden y criterio de "hecho"

Orden: **0 → 1 → 2 (B.1 a B.4) → 3**. Un paso se da por hecho solo cuando Ariel confirma que funciona (entonces se entrega el changelog con ✅ y resultado). Si un paso falla por límites del TV (p. ej. HLS), se anota ❌ con la causa y se sigue con el siguiente sin borrar lo aprendido.

---

## Cambios pendientes de probar

### 2026-11-17 · Paso 0: pruebas de 13-A.2 en el TV (12:28 a 12:33): redirección ✅, seek ❌ (en diagnóstico)

- **Qué se probó:** enlaces mp4 distintos con 📺 Al TV (Hisense HL1, IP 192.168.100.14; teléfono 192.168.100.43:8000), mirando `curl -s http://127.0.0.1:8000/log | tail -15`.
- **Resultados (17 nov 2026):**
  1. **12:28 · `flower.mp4` de MDN:** `ENLACE ... -> http://192.168.100.43:8000/relay/....mp4`, `SOAP Stop/SetAVTransportURI/Play -> 200`, el TV pidió `range=bytes=0-` y recibió `RELAY 206 video/mp4 longitud=1128375 range=bytes 0-1128374/1128375`; segunda petición (`ua=curl/7.19.5`, `range=-`) con `RELAY 200`. ✅ Se ve en el TV (confirmado por Ariel).
  2. **12:29 · Google `BigBuckBunny.mp4`:** `RELAY: el origen respondió 403` (dos veces). ❌ Fallo del origen: no se ve nada. No es del TV ni de la app.
  3. **12:30 · Blender `https://download.blender.org/.../BigBuckBunny_320x180.mp4`:** `RELAY: el origen respondió 404` (dos veces). ❌ Fallo del origen.
  4. **12:31 · archive.org `big_buck_bunny_720p_surround.mp4` (61 878 609 bytes):** `RELAY redirige (302) -> https://dn710604.ca.archive.org/0/items/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4`, `RELAY 206 video/mp4 longitud=61878609 range=bytes 0-61878608/61878609`; la sonda `curl/7.19.5` recibió `RELAY 200`. ✅ **El video se ve en el TV; la redirección funciona.**
  5. **Adelantar con el control remoto del TV (video de archive.org):** ❌ no adelanta. En `/log` no aparece ningún `GET` nuevo con `range=bytes=N-` ni `SOAP Seek`. (Prueba con los botones de CASTV en el móvil: **no hecha**.)
- **Conclusión provisional:** el relay funciona con redirecciones y con mp4 largos. El adelantar no funciona todavía y **no se sabe aún si es del relay, de la app o del TV**.
- **Siguiente:** diagnóstico del seek (ver "Dónde nos quedamos", pasos 1 a 3). No hay parche escrito todavía.
- **Si hace falta quitar algo:** no se cambió código en esta sesión; no hay nada que revertir.
- **Riesgo:** ninguno (solo pruebas).

### 2026-11-17 · Etapa 13-A.3: HLS (`.m3u8`) por `/relay` ❌ (probado en el TV: **no funciona**; el relay sí responde, el móvil no se ve afectado)

- **Qué:** `/relay/<token>.m3u8` baja la lista HLS, reescribe cada enlace (listas hijas, trozos `.ts`/`.m4s`, `#EXT-X-KEY`, `#EXT-X-MAP`, rutas relativas) para que apunten a `http://IP:PUERTO/relay/...`, y `playLink` manda también los `.m3u8` por `/relay` con MIME `application/vnd.apple.mpegurl`. Los trozos se servirían como el mp4 (redirecciones, `Range`, `video/mp2t`).
- **Archivos:** `MediaServer.kt` (`serveRelay` con extensión, `serveHlsPlaylist`, `rewriteM3u8`) y `MainActivity.kt` (`playLink`). Comentarios `// [Etapa 13-A.3]` en el código. Parche `etapa13a3_hls.patch`: `git apply --check` OK sobre los archivos subidos; el APK `castv-apk.zip` ya lo incluye.
- **Pruebas hechas (17 nov 2026, ~12:17 y 12:19, Hisense HL1):**
  1. **Lista maestra** (Mux `x36xhzz.m3u8`) → 📺 Al TV: `ENLACE: ... -> http://192.168.100.43:8000/relay/....m3u8`, `SOAP Stop/SetAVTransportURI/Play -> 200`, el TV pidió **dos veces** `GET /relay/....m3u8` (`range=bytes=0-` con `ua=HISENSE-HL1...` y otra con `ua=curl/7.19.5`) y la app respondió `RELAY HLS lista: 5 enlaces reescritos`. **Después no hubo ningún `GET` más** (ni listas hijas ni trozos).
  2. **Lista de medios directa** (Apple `bipbop_4x3/gear1/prog_index.m3u8`, una sola calidad) → 📺 Al TV: mismo comportamiento con `RELAY HLS lista: 181 enlaces reescritos`; el TV pidió la lista dos veces y **no pidió ningún trozo `.ts`**.
- **Conclusión:** el relay HLS funciona (baja, reescribe y sirve la lista), pero el **Hisense HL1 / VIDAA no sigue los enlaces de una lista HLS recibida por DLNA**, ni maestra ni directa. Por la regla del plan, **HLS queda solo para el móvil** (reproductor propio, ya funciona según Ariel) y se sigue con 13-B.
- **No se probó (por si algún día se retoma):** otro MIME (`application/x-mpegURL`), `protocolInfo` DLNA específico para HLS, o que el relay una los trozos en un solo flujo `video/mp2t` (muy pesado). Valor bajo: no se hace salvo que Ariel lo pida.
- **Qué deja funcionando la app:** mp4 por `/relay` al TV igual que en 13-A.2; `.m3u8` al TV sigue pasando por `/relay` (inofensivo, el TV simplemente no reproduce); `.m3u8` en el móvil igual que antes. `/video/` y `/audio/` sin cambios.
- **Si falla / se quiere quitar:** `git revert` del commit de 13-A.3 (es solo aditivo, no hace falta). Se recomienda **dejarlo**: no estorba y sirve de base si un TV futuro sí lee HLS (13-C.2, modo de prueba).
- **Riesgo:** bajo.

### 2026-11-17 · Etapa 13-A.2: intermediario `/relay` para que el TV reciba enlaces ✅ (commit `11d93df`; **probado: el TV reproduce el mp4 del enlace**, confirmado por Ariel el 17 nov 2026)

- **Por qué:** con la Etapa 13-A el teléfono manda el enlace al TV y el TV lo **acepta** (`SetAVTransportURI` responde 200, también el `Play`), pero **nunca pide el video**: en `/log` no aparece ningún `GET`. Con archivos locales sí funciona porque la URL es `http://IP-del-teléfono:PUERTO/video/ID.mp4`. El enlace de Blender (`http://download.blender.org/...`) responde **301 → https** y el destino da 404; el JVC VIDAA casi nunca lee `https` por DLNA ni sigue redirecciones.
- **Qué hace el parche:** para enlaces **mp4** (no `.m3u8`), la app ya no manda al TV la URL de internet, sino `http://IP-del-teléfono:PUERTO/relay/<enlace en base64>.mp4`. El TV pide eso al teléfono y el **teléfono baja el video** del enlace original: sigue redirecciones a mano (hasta 8, incluido http → https), reenvía la cabecera `Range` (así se puede adelantar), responde `HEAD`, copia el tipo MIME del origen (si no es video/audio usa `video/mp4`) y añade cabeceras DLNA. **HLS (`.m3u8`) sigue yendo directo** al TV; queda para después.
- **Archivos tocados:** `MediaServer.kt` (ruta `/relay`, `serveRelay`, clase `RelayStream` que cierra la conexión al origen cuando el TV deja de leer) y `MainActivity.kt` (`playLink`: arma `sendUrl` y anota `ENLACE: original -> enviado` en `/log`). Comentarios `// [Etapa 13-A.2]` en el código. `/video/` y `/audio/` no se tocan.
- **Revisión hecha el 17 nov 2026:**
  - ✅ `git apply --check` y `git apply` pasaron sin errores en `~/castv-nuevo/castv-fresh` (HEAD `98bcf78`, Etapa 13-A.1). Los comentarios `[Etapa 13-A.2]` están en `MainActivity.kt` (línea ~1166) y `MediaServer.kt`.
  - ✅ Los nombres que usa el parche **sí existen**: `ServerPort.port` (`DebugLog.kt` línea 34, `object ServerPort { @Volatile var port = 8000 }`) y `getLocalIpAddress(): String?` (`MainActivity.kt` línea 1279). No hace falta ninguna corrección.
  - ⚠️ **Corrección a este changelog:** la Etapa 13-A.1 decía que el puerto real está en `ServerState.port`. Es **`ServerPort.port`**; `ServerState` solo guarda `error` (aviso de puerto ocupado). Corregido abajo.
  - `TvController.setUriAndPlay(mediaUrl, title, mime, isMusic)` ya acepta cualquier URL: no se cambió.
- **Resultado de la prueba (17 nov 2026, 11:34):** con `flower.mp4` de MDN (`https`) → 📺 Al TV, el `/log` mostró `ENLACE: https://... -> http://192.168.100.43:8000/relay/....mp4`, `SOAP Stop/SetAVTransportURI/Play -> 200`, **el TV pidió `GET /relay/...` con `range=bytes=0-`** y la app respondió `RELAY 206 video/mp4 longitud=1128375 range=bytes 0-1128374/1128375`. Hubo además un segundo `GET` sin rango (`ua=curl/7.19.5`, lo hace el TV) con `RELAY 200`. **El video se ve en el TV.** Antes, en la misma sesión, un mp3 local al TV también funcionó (`206`/`200`): sin regresión en `/audio/`.
- **Dato nuevo:** el `user-agent` del TV es `HISENSE-HL1 ... HbbTV/1.1.1 ... CE-HTML/1.0` (el "JVC VIDAA" es de fábrica Hisense). Útil para depurar.
- **Conclusión:** la sospecha 1 era la buena: el TV no lee `https` ni sigue redirecciones; con el puente por `http` del teléfono sí. Con esto el TV **sí** lee enlaces mp4 externos.
- **Probar (pasos que se usaron / pendientes):**
  1. Aplicar (ver pasos en la sección de arriba, "Cómo se trabaja", punto 3), compilar en Actions y esperar el APK en verde.
  2. Con CASTV abierto: ⋮ → Reproducir enlace… → pega el mp4 de MDN `flower.mp4` o el de samplelib → **📺 Al TV**.
  3. `curl -s http://127.0.0.1:PUERTO/log` (PUERTO = 8000 o el siguiente libre): debe verse `ENLACE: https://... -> http://IP:PUERTO/relay/....mp4`, luego un `GET /relay/...` **del TV** y líneas `RELAY ... 200` o `206`.
  4. Probar adelantar en el TV (debería salir `206` y `Content-Range`). **Resultado 17 nov 2026: ❌ el TV no vuelve a pedir `Range` (ver Paso 0, en diagnóstico).**
  5. Probar el enlace de Blender (`http://download.blender.org/...`): en el log debe verse `RELAY redirige (301) -> https://...`. Si el destino da 404 es del origen, no de la app. **17 nov 2026:** Blender ya da 404 (http y https); la redirección se comprobó con archive.org: ✅ `RELAY redirige (302)` y el video se ve.
  6. Probar un archivo local al TV: debe seguir igual que antes.
- **Qué mirar si el TV sigue sin pedir nada:** si **sí** aparece `GET /relay` y el TV lo rechaza, el problema es el formato/MIME (sospecha 2); si **no** aparece ningún `GET`, el problema es la orden que mandamos (`protocolInfo`, DIDL) y habría que revisar `TvController`.
- **Limitaciones:** el teléfono gasta datos y batería (hace de puente); sin barra de progreso para enlaces; si el origen no manda tamaño, se envía en trozos (`chunked`) y algunos TV no adelantan.
- **Si falla:** `git revert 11d93df` (y `git push origin main`). Punto bueno anterior: `98bcf78` (13-A.1).
- **Riesgo:** medio. Añade una ruta nueva al servidor y hace conexiones a internet desde el teléfono; no debe tocar `/video/` ni `/audio/`.

### 2026-11-17 · Etapa 13-A.1: el servidor busca puerto libre (8000 a 8010) ✅ (probado: "Ya funciona")

- **Qué pasaba:** al abrir CASTV salía "Puerto 8000 ocupado" y `curl http://127.0.0.1:8000/log` no devolvía nada (ni respuesta ni `Server: SimpleHTTP ... Python`). Eso indica que **nadie atendía el puerto 8000**: el servidor de CASTV no arrancaba (`ServerState.start()` fallaba con `EADDRINUSE`) y el aviso era cierto. No era el script de Python. Algo ajeno a CASTV (otra app o una ejecución anterior de CASTV, posiblemente en otro espacio de Android) tenía el 8000 agarrado, y Termux no lo ve.
- **Arreglo:** el servidor prueba del **8000 al 8010** y usa el primer puerto libre; el puerto real queda en `ServerPort.port` (objeto en `DebugLog.kt`; `ServerState` solo guarda `error`) y **el enlace que se manda al TV usa ese puerto**. El 8000 solo aparecía en `CastvService.kt` (cerca de la línea 46) y `MainActivity.kt` (cerca de la línea 1265).
- **Archivos:** `CastvService.kt` (arranque del servidor con bucle de puertos), `DebugLog.kt` (`ServerPort.port`), `MainActivity.kt` (`mediaUrl` usa `ServerPort.port`). Comentarios `// [Etapa 13-A.1]` en el código.
- **Resultado:** funciona. Si el 8000 sigue ocupado, el aviso ya no debería salir y el servidor queda en el 8001 o el siguiente libre; el registro ahora se consulta en ese puerto: `curl -s http://127.0.0.1:8001/log` (cambiar el número si hace falta).
- **Pendiente:** averiguar **qué proceso ocupaba el 8000 en este móvil** (no es crítico, ya no estorba).
- **Si falla:** `git revert` del commit "Etapa 13-A.1".

### 2026-11-17 · Etapa 13-A: Reproducir enlace (mp4 / HLS) con selector de destino 🧪 (subido y compilado; **al móvil sí, al TV ❌ por ahora**: el TV rechaza los enlaces; ver 13-A.2)

- **Qué:** nuevo **⋮ → Reproducir enlace…**. Se pega una URL (si el portapapeles trae un `http(s)://`, se pre-rellena) y salen dos botones: **📺 Al TV** y **📱 En el móvil**.
  - **📺 Al TV:** la URL se manda directo por DLNA (`TvController.setUriAndPlay`), sin pasar por el servidor del puerto 8000. MIME: `video/mp4`, o `application/vnd.apple.mpegurl` si la URL termina en `.m3u8`. Antes detiene lo que suene.
  - **📱 En el móvil:** abre `VideoPlayerActivity` con la URL (un solo video, sin punto de reanudación ni subtítulos recordados). Desde ahí, el botón **📺 TV** manda el mismo enlace al TV (empieza desde el inicio; no se reanuda en el segundo actual).
  - Avisa si el enlace no empieza con http/https, si el TV no está encontrado, o si el TV rechaza el enlace.
- **Archivos:** `MainActivity.kt` (`askLinkAndPlay`, `playLink`, `isHls`, `linkTitle`, menú id 15, resultado `tvUrl`), `VideoPlayerActivity.kt` (extra `url`/`urlTitle`, ítem con `id = 0`, `sendToTv` con `tvUrl`). Comentarios `// [Etapa 13-A]` en el código. Sin cambios de permisos (ya hay INTERNET y `usesCleartextTraffic`).
- **Probar** (usa las fuentes de la sección "Fuentes legales", plan F):
  1. ⋮ → Reproducir enlace… → pega el enlace A.1 → **📱 En el móvil**: debe reproducirse.
  2. Mismo enlace → **📺 Al TV**: debe sonar en el TV. Si falla, `curl -s http://127.0.0.1:8000/log` y anota la respuesta del `SetAVTransportURI`.
  3. Enlace A.2 (`https`) al TV: anota si el JVC lo rechaza.
  4. Enlace HLS A.3 al móvil y luego al TV: si el TV falla y el móvil no, el JVC no lee HLS por DLNA.
  5. Dentro del reproductor con un enlace, tocar 📺 TV: debe volver a la lista y mandarlo al TV.
- **Limitaciones:** el TV recibe la URL directa; no hay barra de progreso ni control de posición para enlaces; las transmisiones en vivo no tienen duración.
- **Si falla:** `git revert` del commit "Etapa 13-A: reproducir enlace".
- **Riesgo:** bajo. Es código nuevo y aparte; no toca el envío de archivos locales al TV.

### 2026-11-17 · Etapa 12: segundo plano real, reanudar sesión y cola editable 🧪 (subido a `~/castv-app` como `2c37468`; falta ver el build de Actions y probar)

- **Qué:**
  1. **Segundo plano real:** el motor de reproducción (música del teléfono, cola, avance automático, notificación y botones) salió de la pantalla y vive en un objeto nuevo, `Player`, que arranca el servicio `CastvService`. Si se cierra la app desde recientes, la música del teléfono sigue y el avance automático también.
  2. **Reanudar sesión:** se guarda qué sonaba, el segundo en que iba, la cola y la lista con la que se avanza. Al abrir la app aparece en pausa con "↩ Sesión anterior…"; al tocar ▶ sigue desde ese punto. No se guarda nada hasta haber intentado recuperar la sesión anterior (evita borrarla al arrancar).
  3. **Cola editable:** mantener y arrastrar (o el asa ≡) para ordenar, deslizar a un lado para quitar, ▲▼ y ✕ como antes, y **Guardar lista** (guarda lo que suena más la cola como lista de reproducción). La cola ahora **sobrevive al cierre de la app**.
  4. **⋮ → Detener y salir:** detiene todo, para el servicio y cierra la app; la sesión queda guardada.
- **Archivos:** `Player.kt` (nuevo), `MainActivity.kt` (ahora es solo pantalla; las propiedades `queue`, `playlist`, `repeatMode`, etc. delegan en `Player`), `CastvService.kt` (arranca `Player.init` y `Player.startMonitor`), `res/layout/item_queue.xml` (asa ≡), `app/build.gradle` (recyclerview 1.3.2; versión 2.2 / código 3).
- **Probar:**
  1. Salida en Teléfono, pon música, cierra la app desde recientes: debe seguir sonando y pasar a la siguiente sola.
  2. Mete 3 canciones en la cola, arrástralas para cambiar el orden, quita una deslizándola, cierra la app y ábrela: la cola debe seguir igual.
  3. **Guardar lista** en la cola: debe aparecer en ⋮ → Mis listas.
  4. ⋮ → Detener y salir; abre otra vez: debe salir "Sesión anterior" en pausa; ▶ debe seguir desde el minuto guardado.
  5. Con salida en TV, repetir 1 y 4 (en el TV la pista actual sigue sola aunque se cierre la app; el avance a la siguiente depende del servicio activo).
  6. Notificación: Anterior / Pausa / Siguiente deben funcionar con la app cerrada.
- **Limitaciones conocidas:** no se compiló en el asistente (puede haber errores de build). Si el sistema mata el proceso, la música se corta y solo queda la sesión guardada para continuar. No incluye lo de `castv-fresh` (ver aviso de arriba).
- **Si falla:** `git revert 2c37468` (y `git push`). Punto bueno anterior: `1608196`.
- **Riesgo:** medio-alto. Es un cambio de arquitectura (se movieron unas 700 líneas de `MainActivity` a `Player`).

### 2026-11-17 · README, copia de seguridad y Release de GitHub 🧪 (README y workflow subidos ✅; falta la etiqueta y confirmar el Release)

- **Qué:** `README.md` actualizado (funciones nuevas, instalar desde **Releases**, cómo publicar una versión, tabla de archivos). El workflow `.github/workflows/build.yml` ahora, al subir una etiqueta que empiece con `v`, compila y **publica el APK en Releases** como `CASTV-<etiqueta>.apk` (con permiso `contents: write`).
- **Archivos:** `README.md`, `.github/workflows/build.yml`.
- **Pasos (después de subir los parches y con el build en verde):**
  1. Copia de seguridad: `cd ~ && zip -r ~/storage/downloads/castv-backup-$(date +%Y%m%d-%H%M).zip castv-nuevo/castv-fresh -x "*/build/*" "*/.gradle/*"` (si falta zip: `pkg install zip`).
  2. Etiqueta: `cd ~/castv-nuevo/castv-fresh && git tag -a v2.1-estable -m "Versión estable 2.1" && git push origin v2.1-estable`.
  3. En GitHub → Actions debe correr un build de la etiqueta; al terminar, en **Releases** aparece `CASTV-v2.1-estable.apk`.
- **Estado de la etiqueta:** el 17 nov 2026 el push de `v2.1-estable` fue rechazado porque **ya existía en GitHub** (de un intento anterior). Pendiente decidir: usar `v2.2` (recomendado) o reemplazarla con `git push origin v2.1-estable --force`. Antes mirar `git ls-remote --tags origin`. La copia de seguridad en zip ya se creó en Descargas.
- **Si falla:** si el Release no aparece, abrir el run de la etiqueta y revisar el paso "Publicar Release". Para borrar una etiqueta mala: `git tag -d v2.1-estable && git push origin :refs/tags/v2.1-estable`.
- **Riesgo:** bajo. Solo toca documentación y el workflow; el APK del Release es de depuración (firma debug), igual que el que ya se instala.

### 2026-11-17 · Reproductor de video: siguiente video, botón TV y subtítulos ✅ (subtítulos probados; falta confirmar siguiente video y botón TV)

- **Qué:** en el reproductor de video del teléfono:
  1. **Siguiente video automático:** al terminar pasa solo al siguiente de la lista; los botones ⏮ ⏭ de los controles saltan al anterior/siguiente. Si un video no se puede reproducir, avisa y sigue con el siguiente.
  2. **Botón 📺 TV:** manda el video actual al TV desde el mismo punto y cambia la salida a TV. El TV solo lee mp4/m4v; con otro formato avisa y no lo manda.
  3. **Subtítulos (💬):** elige un archivo `.srt` o `.vtt` (selector de archivos del sistema), se muestran abajo en pantalla. Se **recuerda el archivo por video** y se carga solo la próxima vez. Opciones para adelantar o retrasar 0,5 s si van desfasados, y para quitarlos. Lee UTF-8 y también Latin-1 (acentos en español).
- **Archivos:** `VideoPlayerActivity.kt` (reescrito), `MainActivity.kt` (`playVideoOnPhone`, resultado del reproductor).
- **Probar:**
  1. Salida en Teléfono, abre un video con varios después en la lista: al terminar debe pasar al siguiente. Prueba ⏭ y ⏮.
  2. Toca la pantalla: arriba aparecen el título y los botones 💬 y 📺. En un mp4, toca 📺: vuelve a la app y el TV empieza desde ese punto (icono de salida en TV).
  3. Con un video que no sea mp4 (ej. mkv), 📺 debe avisar "El TV solo lee mp4".
  4. 💬 → Elegir archivo… → un `.srt`. Deben verse las líneas. Cierra y abre el mismo video: deben cargarse solos.
  5. 💬 → Retrasar / Adelantar 0,5 s: la sincronía debe cambiar.
- **Limitaciones:** no lee subtítulos incrustados dentro del mkv/mp4 (solo archivos .srt/.vtt); los subtítulos son solo para el teléfono (al TV no se mandan); estilo fijo (texto blanco con fondo oscuro).
- **Corrección de build:** el primer build falló (`VideoPlayerActivity.kt:152 Unresolved reference: visibility`) porque dentro del `MediaController` el nombre `overlay` se confundía con `View.overlay`; se renombró a `topBar` (parche `fix_overlay.patch`).
- **Si falla:** `git revert` del commit "Reproductor: siguiente video, botón TV y subtítulos".
- **Riesgo:** medio-bajo. Solo toca el reproductor del teléfono y el retorno a la lista; con la salida en TV todo sigue igual.

### 2026-11-16 · Estabilidad (tanda 1) 🧪 (parcial)

- **Qué:** aviso cuando el puerto 8000 está ocupado; la CPU y el Wi-Fi se mantienen despiertos con la pantalla apagada.
- **Archivos:** `CastvService.kt`, `DebugLog.kt` (nuevo objeto `ServerState`), `MainActivity.kt`.
- **Probar:**
  1. Con `python castv_app.py` corriendo en Termux, abre CASTV: debe salir el aviso "Puerto 8000 ocupado".
  2. Sin nada en el puerto, reproduce al TV con la pantalla apagada 5 minutos: no debe cortarse.
  3. `curl -s http://127.0.0.1:8000/log` debe mostrar "Servidor iniciado en el puerto 8000".
- **Estado de las pruebas:** ✅ registro "Servidor iniciado" y ✅ pantalla apagada (probados, bien). ⏳ **Pendiente de probar:** el aviso de puerto 8000 ocupado (prueba 1).
- **Si falla:** `git revert` del commit "Aviso de puerto 8000 ocupado y bloqueos de energía".
- **Riesgo:** bajo. Posible mayor consumo de batería por los bloqueos de energía.

### 2026-11-16 · Color de acento a elegir 🧪 (subido; falta confirmar el build y probar)

- **Qué:** nuevo menú **⋮ → Color** con Cian (el de siempre, por defecto), Verde, Naranja y Morado. Cambia botones, barra de progreso, pestañas, fondo del mini reproductor y la fila que se está reproduciendo. Cada color tiene su versión para el tema claro y para el oscuro.
- **Archivos:** `res/values/attrs.xml` (nuevo), `res/values/colors.xml`, `res/values-night/colors.xml`, `res/values/styles.xml` (4 estilos de acento), 8 archivos de `layout/` y `drawable/` (`@color/accent` → `?attr/castvAccent`), `MainActivity.kt`.
- **Probar:**
  1. Con Cian la app debe verse **igual que antes** (en oscuro y en claro).
  2. ⋮ → Color → Verde / Naranja / Morado: revisa pestañas, barra de progreso, botón de play, mini reproductor y la canción que suena.
  3. Cierra y abre la app: debe recordar el color.
  4. Cambia también el tema Claro/Oscuro con cada color.
- **Si falla:** `git revert` del commit "Color de acento a elegir".
- **Riesgo:** medio en el aspecto. Cambia cómo se leen los colores (ahora vía tema), así que un fallo se vería como botones o textos sin color. No toca el TV.
- **Nota:** el icono de la app no cambia de color.
- **Corrección de build:** el primer build falló porque un estilo llamado con puntos (ThemeOverlay.Accent.Cyan) exige un estilo padre que no existe. Se renombraron a AccentOverlayCyan, AccentOverlayGreen, AccentOverlayOrange y AccentOverlayPurple.

---

## Historial

### 2026-11-17 · Papelera de Android (opcional) ✅

- **Qué:** nuevo menú **⋮ → Borrado: a la papelera / permanente** (solo Android 11 o superior). Por defecto **va a la papelera** de Android: se recupera desde Galería o Mis archivos durante unos 30 días. Con "permanente" funciona como antes. El aviso de confirmación dice cuál de los dos va a pasar. En Android 10 o anterior no existe papelera y sigue siendo permanente.
- **Archivos:** `MainActivity.kt` (`useTrash`, `toggleTrash`, `confirmDelete`, `deleteItems`, `finishDelete`, menú).
- **Probar:**
  1. ⋮ → debe decir "Borrado: a la papelera". Borra una canción de prueba: el aviso dice "¿Mover a la papelera?" y al final "🗑 En la papelera: 1".
  2. Abre Mis archivos (papelera) o Galería (videos → Papelera): debe estar ahí y se puede restaurar. Al restaurar, vuelve a aparecer en CASTV (cierra y abre la app).
  3. ⋮ → Borrado: permanente. Borra otra: el aviso dice "¿Eliminar?" y desaparece del todo.
- **Si falla:** `git revert` del commit "Papelera de Android opcional".
- **Riesgo:** bajo. Solo cambia la llamada del sistema que se usa al borrar.
- **Resultado:** probado en el teléfono, todo bien (confirmado por Ariel el 17 nov 2026).

### 2026-11-17 · Videos en el teléfono (salida Teléfono/TV para video y música) ✅

- **Qué:** el botón de salida (icono TV/teléfono, o ⋮ → Salida) ahora controla **música y videos**. Con "Teléfono", al tocar un video se abre un reproductor a pantalla completa en el teléfono (nueva `VideoPlayerActivity`) con controles, gira con el teléfono, mantiene la pantalla encendida y **recuerda el punto donde lo dejaste** (aparece en "Continuar"). Con "TV" todo sigue igual que antes. Si algo suena en el TV o en el teléfono, se detiene al pasar el video al teléfono.
- **Archivos:** `VideoPlayerActivity.kt` (nuevo), `AndroidManifest.xml` (nueva actividad), `MainActivity.kt` (`startPlayback`, texto del menú y avisos), `Store.kt` sin cambios.
- **Probar:**
  1. Pon la salida en Teléfono (icono cian). Toca un video: debe abrirse a pantalla completa y reproducirse con sonido.
  2. Toca la pantalla: salen los controles (play/pausa, barra para adelantar). Gira el teléfono: no debe reiniciarse.
  3. Atrás: vuelve a la lista. El video debe aparecer en "Continuar" con el tiempo guardado; al tocarlo ofrece "Continuar desde…".
  4. Pon la salida en TV y reproduce el mismo video: debe ir al TV como siempre.
  5. Con música sonando en el TV, pasa un video al teléfono: la música debe detenerse y no saltar a otra canción.
- **Limitaciones que tenía (ahora en la entrada de arriba):** no avanzaba al siguiente video, no había botón para pasar al TV, sin subtítulos.
- **Si falla:** `git revert` del commit "Videos en el teléfono".
- **Riesgo:** bajo-medio. No toca el envío al TV con la salida en TV. Los videos que el teléfono no pueda decodificar muestran un aviso y se cierra el reproductor.
- **Resultado:** probado en el teléfono, todo bien (confirmado por Ariel el 17 nov 2026).

### 2026-11-17 · Eliminar música y videos (selección múltiple) ✅

- **Qué:** mantén pulsada una fila para entrar en modo selección; toca más filas para marcarlas (se ven con ✓). Arriba salen el contador y los botones **Todo** y **🗑 Borrar**. Pide confirmación y borra del teléfono. También hay **🗑 Eliminar del teléfono…** en el menú ⋮ de cada fila (para uno solo). Funciona en **música y videos**. Al borrar se quitan de la lista, la cola, favoritos, listas, puntos guardados y estadísticas. Si suena lo que se borra, se detiene.
- **Archivos:** `MainActivity.kt` (selección, `deleteItems`, `afterDeleted`, `onActivityResult`), `Store.kt` (`forget`).
- **Android:** 11 o superior = una sola confirmación del sistema para todo el grupo (`MediaStore.createDeleteRequest`). Android 10 = confirmación archivo por archivo. Android 9 o anterior: intenta borrar directo y, si no hay permiso, lo cuenta como "sin permiso" (no se pide permiso de escritura).
- **Cambio de comportamiento:** la pulsación larga ya no abre el menú; ahora inicia la selección. El menú sigue en el botón ⋮.
- **Probar:**
  1. Pulsación larga en una canción de prueba: sale la barra con "1 seleccionado".
  2. Toca otras dos: contador 3 y ✓ en cada una. Botón **Todo** las marca todas. Atrás cancela la selección.
  3. Borrar 1 canción desde ⋮ → Eliminar: sale el aviso del sistema, confirma y desaparece de la lista.
  4. Selecciona 3 y bórralas (una sola confirmación del sistema).
  5. Borra la que está sonando en el TV: debe detenerse.
  6. Borra una que estaba en favoritos o en una lista: ya no debe aparecer ahí.
  7. Repite con un video.
- **Si falla:** `git revert` del commit "Eliminar archivos con selección múltiple".
- **Riesgo:** medio. **Borra archivos de verdad y no se puede deshacer.** Probar primero con archivos que no importen. No toca el envío al TV.
- **Resultado:** probado en el teléfono, funciona bien (confirmado por Ariel el 17 nov 2026).

### 2026-11-16 · Lista: nombres legibles, búsqueda y carga más fluida (tanda 2) ✅

- **Qué:**
  - Los archivos con código largo (ej. `7e6dce4b...`) se muestran como `Video Camera 16 nov 2026 (8bc7)`. Los de WhatsApp (`VID-20261110-WA0181`) como `WhatsApp Video: 10 nov 2026 (0181)`.
  - La búsqueda también encuentra por ese nombre legible (ej. buscar "whatsapp" o "camera").
  - La lista de videos/música se lee en **segundo plano**, para que la pantalla no se congele al abrir o cambiar entre Video y Audio.
- **Ya existía (no se cambió):** orden A-Z / Z-A y búsqueda sin acentos por varias palabras.
- **Archivos:** `MainActivity.kt` (`prettyTitle(item)`, `loadMedia`, `computeDups`).
- **Probar:**
  1. Abre la app: la lista debe aparecer sin congelar la pantalla.
  2. Los videos con código largo deben verse con fecha y carpeta.
  3. Busca "whatsapp" y "camera": deben salir resultados.
  4. Cambia entre Video y Audio varias veces rápido: no debe cerrarse ni mezclar listas.
  5. Reproduce uno al TV: el título que sale en la app debe ser el legible.
- **Si falla:** `git revert` del commit "Nombres legibles, búsqueda y carga de lista en segundo plano".
- **Resultado:** probado en el teléfono, todo bien.
- **Riesgo:** medio-bajo. Al abrir, la lista tarda un instante en aparecer (antes bloqueaba la pantalla).

### 2026-11-16 · Tema claro/oscuro a elegir (tanda 3) ✅

- **Qué:** nuevo menú **⋮ → Tema** con Sistema / Claro / Oscuro. El tema **Oscuro sigue siendo el de siempre** (es el valor por defecto). El tema Claro es nuevo.
- **Archivos:** `res/values/colors.xml` (ahora paleta clara), `res/values-night/colors.xml` (nuevo, paleta oscura actual), 17 iconos `ic_*.xml` (blanco fijo → `@color/vlc_text`), `bg_bottom_nav.xml`, `bg_chip.xml`, `item_bg_playing.xml`, `MainActivity.kt`.
- **Probar:**
  1. Con el tema Oscuro, la app debe verse **igual que antes**.
  2. ⋮ → Tema → Claro: revisa lista, mini reproductor, cola, menús, barra inferior e iconos. Anota cuáles se ven mal.
  3. Prueba Sistema cambiando el modo oscuro de Android.
- **Si falla:** `git revert` del commit "Tema claro/oscuro a elegir".
- **Riesgo:** medio en el aspecto (solo se puede comprobar viendo el teléfono). No toca la reproducción ni el TV.
- **Resultado:** probado en el teléfono, funciona bien.
- **Pendiente:** elegir color de acento (cian, verde, naranja, morado).

### 2026-11-16 · Registro de depuración visible desde Termux ✅

- **Qué:** la app guarda en memoria las órdenes al TV (con su respuesta HTTP), la URL enviada y cada petición del TV al teléfono. Se lee con `curl -s http://127.0.0.1:8000/log` (y `?clear` para limpiar).
- **Archivos:** `DebugLog.kt` (nuevo), `MediaServer.kt`, `TvController.kt`.
- **Commit:** `6738740`
- **Resultado:** gracias a esto se encontró que el puerto 8000 estaba ocupado por otro programa.

### 2026-11-16 · Saltar formatos que el TV no lee ✅

- **Qué:** al enviar al TV, si el archivo no es `mp3`, `mp4` o `m4v`, se salta al siguiente (máximo 10 seguidos) y se avisa en la barra de estado.
- **Archivos:** `MainActivity.kt`
- **Commit:** `0030620`
- **Motivo:** el TV solo lee mp3 y mp4; los `.opus` y `.m4a` de WhatsApp daban "Archivo no compatible".

### 2026-11-16 · Corregir `indexOf` en velocidad ✅

- **Qué:** `speedValues.indexOf(speed)` no compilaba en GitHub Actions; ahora usa `indexOfFirst { it == speed }`.
- **Archivos:** `MainActivity.kt` (líneas ~1690 y ~1709)
- **Commit:** `dc6e415`
- **Síntoma:** el build salía con X roja y error `Unresolved reference` en `MainActivity.kt`.

### 2026-11-16 · Restaurar Etapa 11 (v2.1) como versión estable ✅

- **Qué:** `main` se restauró a la Etapa 11 (`d7010ac`), que corresponde al APK que funcionaba. El código anterior quedó en la rama `respaldo-antes-de-restaurar`.
- **Commit:** `d7010ac` ("Etapa 11: listas, temporizador, ecualizador, velocidad, elegir TV y correcciones")

### Etapas anteriores

| Etapa | Descripción |
|---|---|
| 10 | Interfaz estilo VLC |
| 9 | Reproductor en pantalla de bloqueo y carátula |
| 8 | Controles en la notificación |
| 7 | Rediseño azul cian |

---

## Ideas, recomendaciones, pros y contras (17 nov 2026)

Resumen de lo hablado con Ariel. Orden = lo que recomiendo hacer primero. "Decisión" indica lo que ya decidió Ariel.

### 1. Reproducir un enlace (mp4 o HLS .m3u8) con selector de destino — ➡️ SIGUIENTE (Etapa 13, parte A)

- **Qué:** menú ⋮ → "Reproducir enlace…": al pegar una URL, la app pregunta dónde se quiere ver mediante dos botones claros (**📺 Al TV** o **📱 En el Móvil**) antes de iniciar la reproducción.
- **Cómo quedará funcionando la app:** al elegir **📺 Al TV**, la URL se envía directo por DLNA (`SetAVTransportURI`) sin pasar por el servidor local del puerto 8000; al elegir **📱 En el Móvil**, se abre directamente `VideoPlayerActivity` con el enlace cargado.
- **Pros:**
  - Cambio pequeño y modular (un diálogo con selector de destino y reutilización de `TvController.setUriAndPlay` y `VideoPlayerActivity`).
  - Flexibilidad inmediata sin tener que cambiar el interruptor general de salida antes de pegar el enlace.
  - Si va al TV, el televisor descarga el stream directo de internet sin gastar datos ni batería del teléfono; si va al móvil, aprovecha el soporte nativo de Android para HLS.
- **Contras / riesgos:**
  - Muchos TV DLNA **rechazan `https://`**; conviene probar primero con `http://`.
  - **No se sabe si el JVC VIDAA lee HLS** (`.m3u8`) por DLNA: hay que probarlo (si no lo soporta, solo funcionará en el móvil o con `.mp4` en el TV).
  - Los enlaces públicos caducan o cambian, y las transmisiones en vivo no tienen duración fija (la barra de progreso no aplica).
  - El tipo MIME debe enviarse correctamente en el DIDL-Lite (`video/mp4` para mp4 y `application/vnd.apple.mpegurl` para HLS).

### 2. Listas M3U (canales como si fueran archivos) — Etapa 13, parte B

- **Qué:** cargar una lista `.m3u`/`.m3u8` desde un enlace o desde un archivo, y que sus canales salgan en una pestaña con buscador, favoritos y orden, como los archivos del teléfono.
- **Pros:** reutiliza la lista, la búsqueda y los favoritos que ya existen; con fuentes oficiales es 100 % legal; el TV recibe el enlace directo.
- **Contras / riesgos:**
  - Las listas grandes (iptv-org tiene miles de canales) hay que bajarlas y leerlas en segundo plano.
  - Muchos canales están caídos, con bloqueo por país o con calidad irregular: es normal que unos funcionen y otros no.
  - Lo que se cargue es **responsabilidad de quien lo carga**; la app solo es un reproductor.
- **Cómo:** parser simple de `#EXTINF` (nombre, logo `tvg-logo`, grupo `group-title`) + la línea de la URL; guardar la lista cargada en `Store`; marcar canales favoritos.
- **Recomendación:** empezar con listas por categoría o país (cientos de canales) en vez del índice completo.

### 3. Mejoras para varios TV (bajo riesgo)

- **Ya existe:** búsqueda SSDP de todos los aparatos DLNA/UPnP; salen en ⋮ → Elegir TV (Samsung, LG, Sony, Philips, Android TV con DLNA, otros VIDAA).
- **Por hacer:** recordar el último TV elegido (ya se guarda la IP; mostrarlo claro), mostrar nombre y modelo, y un **modo de prueba** que mande un mp4 conocido y deje el resultado en `/log` para depurar un TV nuevo.
- **Pros:** poco código, útil si Ariel cambia de TV o la app se comparte.
- **Contras:** **solo probado con el JVC**; cada marca es más o menos estricta con el formato (códecs, MIME, https). No se puede garantizar que todos funcionen.

### 4. Lo que NO se puede (o no conviene)

| Idea | Por qué no |
|---|---|
| Netflix, Disney+, Prime y similares | Llevan **DRM**; no se pueden mandar por DLNA ni reproducir en una app propia. |
| YouTube hacia el TV | Usa Cast/DIAL, no DLNA. |
| Chromecast y AirPlay | Otros protocolos; soportarlos es un **proyecto grande aparte** (y cada uno con sus requisitos). |
| Servidor DLNA/NAS para explorar la red | **Descartado por Ariel (17 nov 2026):** es engorroso y puede fallar. |
| Listas o fuentes "piratas" / IPTV de pago revendido | No se arman ni se recomiendan. Canales premium (deportes de pago, HBO, etc.) en una lista gratis suelen ser no autorizados. |

### 5. Ideas de la Etapa 12 y otras (por decidir)

| Idea | Pros | Contras |
|---|---|---|
| **Subtítulos al TV por DLNA** | Seguirían en el TV | Experimental: depende de si el JVC acepta subtítulos externos; alternativa de quemarlos con ffmpeg hace el APK muy pesado. |
| **Crossfade / sin pausas entre pistas** | Se oye más profesional | Solo en el teléfono (el TV no lo permite); requiere dos reproductores a la vez. |
| **Normalizar volumen entre canciones** | Menos saltos de volumen | Poco fiable sin analizar cada archivo; solo teléfono. |
| **Widget en la pantalla de inicio** (play, pausa, siguiente) | Comodidad | Más código y pruebas; bajo valor si ya está la notificación. |
| **Exportar / importar listas y favoritos** | No se pierden al reinstalar o cambiar de teléfono | Hay que cuidar el formato (las claves son tipo+nombre+duración). |
| **Letra sincronizada (LRCLIB)** | Bonito para música | Necesita internet; solo en teléfono; aparcado. |
| **Generar subtítulos automáticos** (Vosk ~50 MB o Whisper ~75-150 MB) | Útil para videos sin subtítulos | Código nativo, APK mucho más grande y riesgo de build; aparcado. |
| **Convertir `.opus`/`.m4a` a mp3** en la app | El TV los leería | Obliga a incluir ffmpeg y el APK crece mucho; mientras tanto se convierte con Termux. |
| **Mini reproductor más compacto** | Más espacio para la lista | Es solo aspecto. |

---

## Fuentes legales para probar enlaces y canales gratis (17 nov 2026)

**Importante:** los enlaces cambian y los canales caen. Esto es para **probar el reproductor**, no una garantía. Todo lo que se cargue es responsabilidad de quien lo use. Nada de lo siguiente es de pago ni pirata.

### A. Enlaces de prueba técnica (los más seguros: están hechos para eso)

Ordenados del más fácil al más difícil. Si uno funciona en el TV, pasar al siguiente.

1. ❌ **CAÍDO (17 nov 2026: `http` → 301 → https → 404; `https` directo también 404).** mp4 sencillo (Big Buck Bunny, Blender, licencia CC BY 3.0), antes el mejor primer intento:
   `http://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4`
2. ❌ **403 desde el `/relay` (17 nov 2026).** mp4 de muestra (misma película, otro servidor):
   `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4`
3. **HLS de prueba de Mux (Big Buck Bunny en varias calidades, H.264/AAC):**
   `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`
4. **HLS de ejemplo de Apple:**
   `https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8`
5. ✅ **mp4 largo con redirección (archive.org, Big Buck Bunny 720p, 61,9 MB; funciona en el TV por `/relay`, 17 nov 2026).** El mejor para probar seek y redirecciones:
   `https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4`
6. ✅ **mp4 corto (MDN, 1,1 MB; funciona en el TV).** Solo sirve para comprobar que el TV lee un mp4 por `https`; no para el seek:
   `https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4`

- Si el TV rechaza los `https://`, probar el 1 (ya es `http://`). Si el 1 va y el 3 no, **el JVC no lee HLS por DLNA**.
- Otras películas abiertas de Blender (CC BY): *Sintel* (https://cloud.blender.org/films/sintel) y *Tears of Steel* (https://media.xiph.org/tearsofsteel/). Entrar a la página y copiar el enlace del archivo mp4.

### B. Listas de canales gratis (M3U)

| Lista | Qué es | Enlace |
|---|---|---|
| **Free-TV/IPTV** | Lista cuidada: solo canales que se ofrecen **oficialmente gratis** (antena abierta o internet), sin canales de pago, adultos o religiosos. Pocos canales, de calidad. **La mejor para empezar.** | `https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8` (repositorio: github.com/Free-TV/IPTV) |
| **iptv-org/iptv** | La colección más grande (miles de canales de todo el mundo), organizada por categoría, país e idioma. Son **enlaces enviados por usuarios** a transmisiones públicas; los autores dicen no controlar el destino. | Índice: `https://iptv-org.github.io/iptv/index.m3u` · Noticias: `https://iptv-org.github.io/iptv/categories/news.m3u` · Documentales: `https://iptv-org.github.io/iptv/categories/documentary.m3u` · Música: `https://iptv-org.github.io/iptv/categories/music.m3u` · EE. UU.: `https://iptv-org.github.io/iptv/countries/us.m3u` (todas las listas: `PLAYLISTS.md` en github.com/iptv-org/iptv; los de otros países y idiomas siguen el mismo patrón, ej. `countries/do.m3u`, `languages/spa.m3u`: si no abre, buscarlo en `PLAYLISTS.md`) |

- **Cómo usarlas con prudencia:** en iptv-org, quedarse con canales de **emisoras públicas o institucionales** y categorías como noticias, documentales, música, clima o ciencia. Si aparece un canal que normalmente es **de pago** (HBO, ESPN, deportes premium, películas recientes), saltarlo: probablemente no está autorizado.
- Para empezar, **cargar listas por categoría o país**, no el índice completo.

### C. Emisoras públicas y oficiales con transmisión gratis (ver en su sitio web)

Estas emisoras ofrecen su señal en vivo gratis en su propia página o app oficial. **No verifiqué hoy sus enlaces directos `.m3u8`** (cambian seguido); lo más práctico es encontrarlos dentro de las listas B o usar su sitio oficial.

- **NHK World-Japan** (Japón, inglés): su señal en vivo está disponible gratis en todo el mundo en su página.
- **DW (Deutsche Welle)**: noticias y documentales, con señal en español.
- **France 24**: noticias en francés, inglés y español.
- **NASA** (NASA+ y canal de NASA TV): lanzamientos, estación espacial, ciencia.
- **Al Jazeera English** y **Euronews**: noticias.
- Televisoras públicas de cada país (por ejemplo RTVE en España), según lo que ofrezcan en internet.

### D. Servicios gratis con anuncios (Pluto TV, Samsung TV Plus, Tubi, Plex, Roku Channel)

- Son **legales** dentro de sus propias apps, pero **no dan enlaces públicos estables**. Las listas de terceros que extraen sus canales pueden violar sus términos de uso y los enlaces caducan: **no los usaría** como fuente de la app.

### E. Qué evitar

- Servicios de "IPTV" de suscripción barata, revendedores, credenciales Xtream/Stalker, o listas que prometen todos los canales de pago y deportes. Eso es contenido no autorizado.

### F. Plan de prueba sugerido (anotar resultados aquí)

| # | Prueba | Resultado |
|---|---|---|
| 1 | mp4 (enlace A.1) → teléfono | ⏳ |
| 2 | mp4 (enlace A.1) → TV | ❌ directo (sin `GET`; Blender redirige 301 a https y da 404). Con `/relay` (13-A.2): Blender (http y https) da 404 y Google (A.2) da 403 (17 nov 2026): fallos del origen. ✅ Repetido con archive.org (A.5): se ve en el TV |
| 3 | mp4 con `https` → TV | ✅ con `/relay` (13-A.2): `flower.mp4` de MDN se ve en el TV (17 nov 2026) |
| 4 | HLS (A.3) → teléfono | ⏳ |
| 5 | HLS (A.3) → TV | ❌ directo: el TV lo rechaza. ❌ también con `/relay` (13-A.3, 17 nov 2026): el TV pide la lista (Mux y Apple `gear1`) y nunca los trozos; el HLS de Mux sí funciona en el móvil (según Ariel) |
| 6 | Un canal de Free-TV (en vivo) → TV | ⏳ |
| 7 | Un canal de iptv-org → TV | ⏳ |
| 8 | mp4 largo con redirección 302 (archive.org, A.5) → TV | ✅ `RELAY redirige (302)` + `206`; se ve (17 nov 2026) |
| 9 | Adelantar (seek) en el TV con el control remoto (archive.org) | ❌ el TV no pide ningún `Range` nuevo (17 nov 2026); en diagnóstico |
| 10 | Adelantar con los botones de CASTV (móvil) → TV | ⏳ no hecha |

Si el TV rechaza algo, mirar la respuesta en `curl -s http://127.0.0.1:8000/log` (línea del `SetAVTransportURI`) y anotarla.

---

## Plantilla para cada cambio nuevo

```
### AAAA-MM-DD · Título del cambio 🧪

- **Qué:**
- **Archivos:**
- **Probar:**
- **Si falla:** git revert del commit "..."
- **Riesgo:**
```

---

## Hoja de ruta (dónde vamos y qué falta)

Actualizada: 17 de noviembre de 2026 (v25). Cuando una función se termine, se mueve de columna y se anota arriba en el historial.

### ✅ Hecho y probado

- Restaurar la Etapa 11 (v2.1) como versión estable.
- Arreglo del build (`indexOf`).
- Saltar formatos que el TV no lee (solo mp3 y mp4).
- Registro de depuración (`curl -s http://127.0.0.1:8000/log`).
- Tema Sistema / Claro / Oscuro.
- Lista: nombres legibles, búsqueda por ellos y carga en segundo plano.
- Estabilidad: bloqueos de energía con la pantalla apagada y registro "Servidor iniciado".
- Eliminar música y videos con selección múltiple; papelera de Android opcional.
- Videos en el teléfono (salida Teléfono/TV para video y música, con punto guardado).
- **Etapa 13-A.2: enlaces mp4 (https) al TV con `/relay`** (`11d93df`, probado el 17 nov 2026).
- **Paso 0 (parcial): enlace con redirección (302) por `/relay` → TV** (archive.org, 17 nov 2026, confirmado por Ariel).
- Guía de problemas y soluciones (documento aparte).

### 🧪 Hecho, falta probar

- **Paso 0 pendiente (17 nov 2026):** adelantar (seek) en el TV ❌ **en diagnóstico, aplazado por Ariel (se retoma después de las mejoras)**; pausa/reanudar/detener ⬜; salir de CASTV mientras suena un enlace ⬜. Ver "Dónde nos quedamos".

- **Etapa 12** (segundo plano real, reanudar sesión, cola editable): subida como `2c37468` en `~/castv-app`. Falta ver el build de Actions y probar. **Antes, resolver cuál es la copia buena del proyecto** (ver aviso al inicio).
- **Reproductor de video:** subtítulos ✅ probados por Ariel. Falta probar: siguiente video automático, ⏮ ⏭ y botón 📺 TV.
- **README + Release** (`github_release.patch`): falta la etiqueta y ver que aparezca el Release.
- **Etapa 13-A.2 (`/relay`):** parche `etapa13a2_relay.patch` .
- **Aviso de puerto 8000 ocupado.** Prueba: con `python castv_app.py` corriendo, forzar detención de CASTV, abrirlo y esperar 2 segundos. Debe salir el aviso. Cerrar el script con `pkill -f castv_app.py`.
- **Color de acento a elegir** (Cian, Verde, Naranja, Morado). Parche `acento_color.patch` + corrección de nombres de estilos. Falta que el build salga verde y probarlo.

### ➡️ Próxima función

- **Etapa 13: reproducir enlace y listas M3U** (**13-A y 13-A.2 hechas: mp4 funciona en móvil y en TV (vía `/relay`). Pendiente: diagnosticar el adelantar (seek) en el TV (❌ el 17 nov; ver "Dónde nos quedamos"), pausa/reanudar/detener y salir de CASTV mientras suena (enlace con redirección ya ✅), y ~~HLS (13-A.3)~~ (❌ en el TV, solo móvil). Siguiente: ver "Plan de trabajo próximo" (Paso 0 pruebas, Paso 1 HLS por `/relay`, Paso 2 listas M3U, Paso 3 varios TV).** (decidido por Ariel el 17 nov 2026: "hagamos esto"). Requisitos antes de empezar: (1) confirmar la copia buena del proyecto, (2) ver el build de la Etapa 12. Orden: **A.** "Reproducir enlace…" (mp4 y HLS, al TV y al teléfono) → probar con las fuentes legales de la sección "Fuentes legales para probar" → **B.** listas M3U (por enlace o archivo) con pestaña de canales, búsqueda y favoritos → **C.** mejoras para varios TV (recordar último, nombre/modelo, modo de prueba). Detalles, pros y contras: sección "Ideas, recomendaciones, pros y contras".
- **Pulir la app** (acordado con Ariel). Orden sugerido:
  1. Terminar de probar lo pendiente (puerto 8000 ocupado, color de acento, siguiente video y botón TV).
  2. Revisar fallos o detalles que Ariel note usando la app a diario (lista, mini reproductor, notificación).
  3. (Hecho, falta probar) Papelera opcional; README + etiqueta + Release.
  4. Integrar piezas de la Etapa 12 que falten (cola editable, retomar sesión, segundo plano con `Player`), por partes.

### ⬜ Por agregar (en este orden)

1. (En curso) `README.md`, copia de seguridad con etiqueta `v2.1-estable` y Release del APK: ver "Cambios pendientes de probar".
4. Añadir un archivo `LICENSE` (por ejemplo MIT), si se quiere compartir el código.
5. **Subtítulos para videos.** Depende de que el TV los acepte por DLNA; hay que probarlo.
6. **Convertir `.opus` / `.m4a` a mp3 desde la app.** Pesado: obliga a incluir ffmpeg y el APK crece mucho. Mientras tanto se convierte con Termux.

### 💡 Ideas sin decidir

- **Propuestas del 17 nov 2026:** pasaron a la sección "Ideas, recomendaciones, pros y contras" (reproducir enlace y M3U ya están decididas como Etapa 13; servidor DLNA/NAS quedó **descartado** por Ariel).
- **Aparcadas por decisión de Ariel (17 nov 2026), "primero pulir la app":**
  - Subtítulos en el TV por DLNA (experimental; depende de si el JVC VIDAA acepta subtítulos externos; probar con `/log`). Alternativa: quemarlos con ffmpeg (APK pesado).
  - Generar subtítulos escaneando videos (Vosk ~50 MB sin internet, Whisper ~75-150 MB, o servicio en línea). Código nativo: riesgo de build.
  - Letra sincronizada de canciones desde internet (LRCLIB), solo en el teléfono.

- Mini reproductor más compacto.
- Mensajes de error más claros al enviar al TV.

### ℹ️ Ya existía en la app (no hay que hacerlo)

- Reconectar el TV automáticamente si se pierde.
- Orden A-Z / Z-A con letras de sección y búsqueda sin acentos por varias palabras.
- Miniaturas en segundo plano con caché y detección de duplicados.
