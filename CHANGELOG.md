# CASTV: CHANGELOG (cerebro) · v356

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
| Workflow | `.github/workflows/build.yml` (compila `assembleRelease` firmado; etiqueta `v*` publica APK; paso de pruebas TESTS-1 antes de compilar) |
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

### ▶ RETOMAR AQUÍ (24 nov 2026)
- **Hecho y ✅:** **RECUP-1** (23 nov), REMOTO-1, ATAJOS-1, CAPÍTULOS-1, VOCABULARIO-1/2, FIRMA-1, UPDATE-1, VERSION-2, **ESTILO-1**, **ESTILO-2**, **UPDATE-2**, **TV-2**, **ESTILO-3**, **ZAP-1**, **LISTA-VAL-1**, **SPEED-1**, **TAG-3.5**, **VISTA-1 a VISTA-5** (logos, tarjetas y botones de Canales), **FAV-REPRO-1**, **FAV-REPRO-2**, **FAV-REPRO-3**, **ZAP-3**, **FUENTES-1**, **XTREAM-1** (24 nov), **EPG-1** (24 nov), **QR-1** (24 nov), **EPG-1b** (24 nov), **QR-2a** (24 nov), **BARRA-1**, **VIVO-1**, **GIRO-1**, **VERSION-3**, **TESTS-1b**, **SERVICIO-1a** y **SERVICIO-1b**, **AUTORESPALDO-1**, **ZAP-2**, **LOG-1**, **ATOMIC-1** y **TESTS-1** (ver `HISTORIAL`). WIDGET-1 en pausa.
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
- **Regla de versiones (Ariel, 23 nov):** la etiqueta `v*` solo se propone con cambios realmente grandes; los parches pequeños se suben a `main` sin etiqueta. Última etiqueta: **`v3.7`** (Ariel la subió el 23-24 nov tras ver las pruebas en verde; incluye AUTORESPALDO-1, ZAP-2, LOG-1, ATOMIC-1 y TESTS-1; TESTS-1b puede o no estar dentro según cuándo se etiquetó). Falta confirmar que su Release (`CASTV-v3.7.apk`) se publicó y que Ariel actualizó desde Ajustes › Actualizaciones. Antes: `v3.6` (commit `61fff98`, hasta SERVICIO-1b). Siguiente al tocar: `v3.8` (`git tag v3.8 && git push origin v3.8`) y solo con el build de `main` en verde. **Lección:** instalar solo APK de un Release (etiqueta), no el artefacto de un push, o el actualizador dirá «El APK no es más nuevo que el instalado»; no reinstalar una versión de código menor (exige desinstalar). Si una etiqueta sale con un commit roto: `git tag -d v<N> && git push origin :refs/tags/v<N>` y volver a etiquetar con el build verde.
- **LISTA-VAL-1 ✅ (23 nov):** Ariel: «Funciona todo». Una descarga vacía o dañada ya no pisa la copia guardada de la lista (se usa la anterior, aviso «lista nueva dañada (copia guardada)» y línea en el `/log`). Solo `M3uActivity.kt`. Línea en `HISTORIAL`. Sin etiqueta (R12). (La prueba pudo dejar la lista `prueba` y `~/t` en Termux: limpiar con 🗑 y `pkill -f http.server; rm -r ~/t`.)
- **ZAP-1 ✅ (23 nov):** Ariel: «Funciona bien con el TV». ⏮ ⏭ cambian de canal cuando hay un canal de lista en el TV (pantalla Reproduciendo y notificación). Solo vale para el TV: en modo teléfono ⏮⏭ no cambian de canal (ZAP-2 ✅ lo hizo también para el teléfono). Línea en `HISTORIAL`. Sin etiqueta (R12).
- **SPEED-1 ✅ (23 nov):** Ariel: «Ya funciona» (tras SPEED-1b, build verde, instalado desde Ajustes › Actualizaciones). Herramientas › «⏩ Velocidad» (0.5×–2×), por video y sin guardar; al soltar «mantener pulsado» vuelve a la velocidad elegida; no en canales en vivo. Solo `VideoPlayerActivity.kt`. Línea en `HISTORIAL`.
- **TAG-3.5 ✅ (23 nov):** `v3.5` publicada y firmada; el actualizador de la app funciona de nuevo. Línea en `HISTORIAL`.
- **SERVICIO-1a ✅ (23 nov):** Ariel: «Ya funciona». `PlayerCore.kt` nuevo guarda el `MediaPlayer` del teléfono y su estado; `MainActivity` los lee de ahí. Línea en `HISTORIAL`.
- **SERVICIO-1b ✅ (23 nov):** Ariel: «Ya todo funciona bien». La música del teléfono sigue sonando si Android cierra `MainActivity` («modo sin pantalla» en `PlayerCore`: avanza sola, cola/repetir/temporizador, notificación ⏮ ⏯ ⏭; al reabrir la app se recoge con `adoptHeadless()`). Límites: sin pantalla no hay ecualizador ni velocidad hasta reabrir. Línea en `HISTORIAL`. Etiqueta `v3.6` ya creada (ver Regla de versiones).
- **✅ (24 nov, Ariel: «Ya funciona bien»):** VISTA-1 a VISTA-5, FAV-REPRO-1, BARRA-1, VIVO-1, GIRO-1, VERSION-3 y TESTS-1b (el build salió en verde con las pruebas). Líneas en `HISTORIAL`. Barra del reproductor ahora: `título · ★ · 🔄 · ⋯ · 📺 TV`; en vivo solo «● EN VIVO»; 🔄 un toque alterna vertical/horizontal y mantenido = automático.
- **XTREAM-1 ✅ (24 nov):** Ariel: «Lo instalé y funciona bien». Listas con usuario y clave (botón «🔑 Xtream» en «＋ Lista», lista `xtream://<id>`, acceso en `castv_xt` fuera de respaldos, `/log` y respaldos sin claves). Límites que siguen: los favoritos, ocultos, Continuar e Historial de canales Xtream no viajan en respaldos y restaurar un respaldo completo borra los de Xtream; en el teléfono quedan con usuario y clave dentro del enlace. Línea en `HISTORIAL`. Sin etiqueta (R12).
- **EPG-1 ✅ (24 nov):** Ariel: «Funciona todo». Guía XMLTV apagada por defecto (Ajustes › Video); «▶ programa · hasta HH:MM» en Canales y tarjetas, «📅 Guía: ahora y siguiente» en el ⋯ del reproductor, botón 📅 para pegar una URL propia. Línea en `HISTORIAL`. Sin etiqueta (R12). Nota: la cabecera de Free-TV trae más de 100 guías y la app lee solo las 3 primeras (ver «Pendientes sueltos», EPG-1b).
- **QR-1 ✅ (24 nov):** Ariel: «ya instalé el qr1 y funciona bien». ＋ Lista › «📲 Desde otro dispositivo (QR)»: recuadro con QR y dirección de un solo uso; se cierra solo (5 min, al recibir la lista, al cerrar el recuadro, al salir de Canales o tras 5 claves malas); solo red local y enlaces M3U normales (con usuario y clave se rechazan: para eso está 🔑 Xtream); la clave no sale en el `/log`. Librería `zxing:core:3.5.3` y prueba `QrAddTest`. Línea en `HISTORIAL`. Sin etiqueta (R12). Queda sin probar a fondo (Ariel no lo dijo): cierre a los 5 min y que el `/log` no lleve la clave.
- **Decisión de Ariel (24 nov): «vamos por todos».** Orden: EPG-1b ✅ › **QR-2**; COMPAT-TV-1 sigue esperando la visita a la TV del amigo (no se puede hacer antes); la etiqueta `v3.9` solo cuando él la pida (R12). Ahora en 🧪: QR-2 (ver abajo).
- **EPG-1b ✅ (24 nov):** Ariel: «Ya funciona». Con una cabecera de más de 3 guías (Free-TV), la app elige las guías de los países de los `tvg-id` de los canales (hasta 3 países con 3+ canales, 2 guías por país, 6 descargas); sin coincidencia, las 3 primeras; el `/log` anota «EPG: guías elegidas». Línea en `HISTORIAL`. Sin etiqueta (R12).
- **QR-2a ✅ (24 nov):** Ariel: «Ya funciona». ＋ Lista › «📤 Enviar esta lista a otro móvil» (solo con una lista abierta): QR con `http://<IP>:<puerto>/send/<clave>`; el otro móvil pulsa «Recibir lista» y ve nombre y enlace con «Copiar enlace»; clave de un solo uso, 5 min, solo red local, sin la clave en el `/log`; no se envían listas Xtream ni enlaces con usuario y clave. Línea en `HISTORIAL`. Sin etiqueta (R12).
- **Ahora en 🧪: QR-2b (enviar el RESPALDO a otro móvil por QR), PARCHE ENTREGADO (24 nov; `QR-2b.patch`, se aplica sobre QR-2a), falta aplicar y probar.** (Ariel dijo «seguimos» con los archivos ya subidos y el plan de QR-2b presentado en el v354.) Archivos: `Qr.kt` (`QrSend` ahora también entrega respaldos), `MediaServer.kt` (misma ruta `/send`), `SettingsActivity.kt` y más pruebas en `QrSendTest.kt`. `Backup.kt`, `ShareProvider.kt`, `M3uActivity.kt` y `build.gradle` no cambian. `MainActivity.kt` tampoco: el botón va en Ajustes, no en el diálogo de respaldo de la pantalla principal (que no se había subido).
  - **Qué hace:** Ajustes › Respaldo › «Enviar respaldo a otro móvil» (con QR, por el Wi-Fi). Primero pide confirmación («¿Enviar tu respaldo a otro móvil?», con el aviso de qué se lleva quien abra la dirección); luego abre el recuadro con QR y `http://<IP>:<puerto>/send/<clave>`. El otro móvil ve «CASTV: respaldo para ti» (avisa que importarlo REEMPLAZA los datos de ese móvil) y el botón «📥 Descargar respaldo», que baja el archivo `castv-respaldo.json` desde su navegador; el respaldo se arma en ese momento (`Backup.build`, el mismo contenido que ya se comparte: sin claves Xtream, sin IP del TV ni PIN). Para usarlo: en CASTV del otro móvil, Ajustes › Respaldo › importar (el flujo de siempre; el botón intermedio evita que un navegador que «adelante» la página gaste la clave).
  - **Seguridad (igual que QR-2a):** clave de 24 caracteres y un solo uso; se cierra al entregar el respaldo, a los 5 min, al cerrar el recuadro, al salir de Ajustes o tras 5 claves malas; solo red local; la clave no sale en el `/log` (la ruta se atiende antes del registro; el `/log` solo anota «QR: puerta de envío de respaldo abierta/cerrada» y rechazos); la descarga lleva `Cache-Control: no-store` y `Referrer-Policy: no-referrer`. Una puerta de lista no entrega respaldos y al revés (cada una rechaza lo del otro tipo). Si el respaldo no se pudo armar, la puerta sigue abierta.
  - **Cómo probar:** 1) Ajustes › Respaldo › Enviar respaldo a otro móvil: sale la confirmación y luego el QR; 2) escanear con otro móvil del mismo Wi-Fi: página «respaldo para ti», «Descargar respaldo»: baja `castv-respaldo.json`; 3) el recuadro del teléfono se cierra y avisa «📤 Respaldo enviado»; 4) abrir la misma dirección otra vez: «No encontrado»; 5) en el otro móvil con CASTV: Ajustes › Respaldo › importar ese archivo (reemplaza sus datos: probar en uno donde no importe); 6) `http://192.168.100.43:8001/log`: sin la clave de la dirección.
  - **Riesgos:** (a) sin compilador de Kotlin (R4); (b) si falla una prueba de `QrSendTest` en Actions el build se detiene antes del APK: pasar el mensaje; (c) el respaldo es lo más delicado que sale del teléfono: mientras el QR está abierto, quien lo vea en tu Wi-Fi podría bajarlo (por eso hay confirmación, es de un solo uso y dura 5 min); (d) el archivo baja con el nombre fijo `castv-respaldo.json` (el navegador puede añadir «(1)» si ya existe); (e) si Android recrea Ajustes (cambio de tema o giro) la puerta se cierra: volver a abrirla.
  - **Siguiente paso:** Ariel aplica `QR-2b.patch` con la línea B+C, espera el build en verde, prueba y dice «ya funciona» → QR-2b ✅ a `HISTORIAL`. Después no queda nada elegido en la cola (COMPAT-TV-1 espera la visita a la TV del amigo; v3.9 solo cuando Ariel la pida). Sin etiqueta (R12).
- **Etiquetas:** `v3.8` publicada (build #246 en verde). Siguiente: `v3.9`, solo cuando Ariel la pida (R12): `git tag v3.9 && git push origin v3.9` con el build de `main` en verde (hoy #248).
- **Pendiente suelto de TV:** modelo real y versión del sistema (Ajustes › Soporte › Info de la TV).

### Cola de pendientes (uno a la vez, en este orden; ideas tomadas de `reproductor-IPTV` (MIT) y `kino-light` (Apache): solo ideas, código propio en Kotlin, sin nombre ni logo de Kino)
1. **QR-2b** 🧪 (enviar el respaldo por QR: parche entregado; QR-2a ✅; ver «Ahora en 🧪»).
COMPAT-TV-1 ⏳ espera la visita a la TV del amigo (no sugerirlo hasta que él lo diga).

### Pendientes sueltos
- ✅ QR-1, EPG-1 y XTREAM-1 hechos (ver `HISTORIAL`). ✅ QR-2a hecho; 🧪 QR-2b (respaldo): ver «Ahora en 🧪».
- ✅ EPG-1b hecho (24 nov; problema original: con Free-TV la cabecera trae más de 100 guías y la app lee solo las 3 primeras; idea: elegir las guías por el país de los `tvg-id` (`.it`, `.es`…), tope ~6 descargas).
- ⬜ Ideas de referencia NO elegidas (23 nov; con su contra; ATOMIC-1 y TESTS-1 ya se eligieron, ver 🧪): QR-1 pasar listas por QR/red local (abre una puerta en la red) · FUENTES-1 salud y ranking de fuentes (Ariel prefiere ocultar a mano) · catch-up (exige EPG). Descartado: despliegue escalonado, espejos de descarga, telemetría, interfaz Compose/Android TV, paquetes webOS/Tizen/Roku.
- ⏳ COMPAT-TV-1: arreglo para la TV del amigo, solo cuando Ariel pruebe allá y pase el `/log` (el diagnóstico ya está ✅; el `/log` ya oculta claves con LOG-1 ✅).
- ⬜ Modelo real y versión del sistema de la TV (el XML solo dice "SmartTV / Renderer"; `vidaa_support=1`). Ver en Ajustes › Soporte › Info de la TV.
- ⏳ WIDGET-1 (widget con ⏮ ⏯ ⏭): en pausa, Ariel no lo necesita ni lo usa en otras apps (22 nov). Plan guardado: `CastvWidget.kt` nuevo (`AppWidgetProvider`) + layout/xml/5 drawables propios + receiver en el manifest + 3 llamadas en `MainActivity` (`refreshNotification`, `refreshLinkNotification`, `postDefaultNotification`); los botones usan `ACT_PREV/TOGGLE/NEXT` igual que la notificación, así que solo funcionan con la app viva (límite: la reproducción vive en `MainActivity`, `CastvService` solo aloja el servidor).
- ✅ (hecho como SERVICIO-1) la reproducción de música del teléfono sobrevive a cerrar la app.
- ⬜ Ideas: Android Auto / Android TV.
- ⏳ Pistas de audio/subtítulos embebidos: solo con libvlc/ffmpeg. El tamaño del APK ya no es impedimento (decisión de Ariel, 17 nov); se evalúa caso por caso, y solo si Ariel lo pide.
- Verificado 23 nov: temporizador de apagado y velocidad ya existen en música (`MainActivity.kt`); el temporizador también en video (GESTO-2). Velocidad en video, verificado 23 nov: solo existe al mantener pulsado (1.5×/2×/3×), no hay selector (ver SPEED-1).

### Descartado (⛔)
Subtítulos con IA (Whisper), idioma «Automático» y mejoras: **CERRADO por Ariel (23 nov)**; no tocar ni proponer. Si algún día quiere mejorarlo, él avisa (al retomar, pedir antes `jni_whisper.cpp` y `WhisperLib.kt`).
`Platzi-Download` y `Youtube_Downloader` (23 nov): no aportan a castv y descargan contenido de terceros; no se toman ideas de ellos.
Chromecast/AirPlay, DRM, subtítulos con IA en segundo plano con servicio, guardar el modelo de Whisper en Download, control por 36669 con certificado ajeno, flechas/OK/teclas por DLNA (la TV no expone ese servicio), `SetMute` como silencio (la TV lo ignora).
LOG-1 ya no está descartado: se hizo y quedó ✅ el 23 nov (ver `HISTORIAL`).

---

## 5. REFERENCIA RÁPIDA DEL CÓDIGO (hasta que exista MAPA.md)

Archivos conocidos en `app/src/main/java/com/castv/app/`: `MainActivity.kt` (pantalla principal, mini reproductor, Más, ecualizador, `playLink`), `VideoPlayerActivity.kt` (reproductor de video, gestos, PiP, subtítulos, foco de audio), `M3uActivity.kt` + `M3uParser.kt` + `Catalog.kt` + `Regions.kt` + `Logos.kt` (canales), `MediaServer.kt` (servidor, `/relay`, `/live`, buffer), `TvController.kt` (DLNA), `TvSend.kt`, `Store.kt` (datos), `Backup.kt` (+ parciales para compartir), `AutoBackup.kt` (copia diaria), `ShareProvider.kt` (archivos para compartir), `SettingsActivity.kt`, `AboutActivity.kt`, `ParentalPin.kt`, `PortDialog.kt`, `CastvService.kt`, `CastvUtils.kt`, `Zap.kt` (ZAP-1/ZAP-2: lista de canales para ⏮ ⏭, en TV y en teléfono), `DebugLog.kt`, `Thumbs.kt`, `AudioExtractor.kt`, `WhisperModel.kt`, `WhisperLib.kt`, `SubtitleGen.kt`; C++ en `app/src/main/cpp/` (whisper.cpp v1.9.3).
Preferencias: `castv`, `castv_lib`, `castv_subs`, `castv_stats`.
