# CASTV: CHANGELOG (cerebro) · v337

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

### ▶ RETOMAR AQUÍ (23 nov 2026, 21:20)
- **Hecho y ✅:** **RECUP-1** (23 nov), REMOTO-1, ATAJOS-1, CAPÍTULOS-1, VOCABULARIO-1/2, FIRMA-1, UPDATE-1, VERSION-2, **ESTILO-1**, **ESTILO-2**, **UPDATE-2**, **TV-2**, **ESTILO-3**, **ZAP-1**, **LISTA-VAL-1**, **SPEED-1**, **TAG-3.5**, **SERVICIO-1a** y **SERVICIO-1b**, **AUTORESPALDO-1**, **ZAP-2**, **LOG-1**, **ATOMIC-1** y **TESTS-1** (ver `HISTORIAL`). WIDGET-1 en pausa.
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
- **Ahora en 🧪: TESTS-1b (entregado 23 nov; pendiente de aplicar y probar).** `TESTS-1` ✅ (el log de Actions mostró `:app:testDebugUnitTest` ejecutada y `BUILD SUCCESSFUL`; el paso añade ~1 min 40 s al build). TESTS-1b quita `continue-on-error: true` de `build.yml` para que una prueba rota detenga el build antes del APK. Archivo: `.github/workflows/build.yml` (`TESTS-1b.patch`).
  - **Cómo probar:** subir el parche y ver que el build sale verde con el paso «Pruebas automaticas (TESTS-1)» en verde. Si por una prueba mal escrita el build sale rojo: pasar el mensaje; para desbloquear de inmediato se puede volver a poner `continue-on-error: true` (`git revert HASH`).
  - **Siguiente paso:** build en verde y «ya funciona» de Ariel → TESTS-1b ✅ y a `HISTORIAL`. Sin etiqueta propia (R12).
- **Ahora en 🧪 también: VISTA-1 a VISTA-4 (pantalla Canales; entregados 23 nov, Ariel: «haz lo que tengas que hacer para mejorar la experiencia»; parches encadenados, un commit cada uno; solo `M3uActivity.kt`, que dibuja la pantalla por código).**
  - **VISTA-1 · logos legibles:** `styleLogo()` pone un fondo gris muy claro (`#F2F2F2`) redondeado detrás de cada logo, en tarjetas y en la lista; sin logo (icono gris de TV) no lleva fondo. Contra: un logo todo blanco sobre transparente casi no se verá (los de iptv-org suelen traer color). Si molesta, se cambia el color o se limita.
  - **VISTA-2 · tarjetas compactas:** tarjetas de 96 dp (antes 84), letra 11, logo 64×36, menos relleno y títulos de fila más juntos. Los nombres largos caben mejor en 2 líneas; la fila ahorra algo de alto, no mucho.
  - **VISTA-3 · botones de arriba:** la lista actual va en su propia fila, a lo ancho y con el nombre completo; debajo, `☆ Favs`, `👁 N` (con descripción «Canales ocultos»), `＋ Lista` y `🗑 Quitar`, a partes iguales (letra 13). El `👁 N` sigue sin texto largo para que quepan los cuatro.
  - **VISTA-4 · detalles:** «★ Favoritos» con la estrella del color de acento (igual que la lista), «🔎 Revisar» con más aire, y la fila de ánimos se difumina en el borde para que se note que se desliza.
  - **Cómo probar (con el APK instalado, por etiqueta):** 1) entrar a Canales: logos oscuros (Cinecanal, A&E, Telemundo) legibles; 2) «Continuar» y «Favoritos» más bajitas y con nombres más completos; 3) arriba: nombre de la lista completo y los cuatro botones con texto, sin cortarse; 4) estrella de «Favoritos» del color de acento; 5) todo sigue funcionando igual: tocar un canal, mantener pulsado para quitar, ★ de la lista, 👁 ocultos, ＋ y 🗑.
  - **Riesgos:** (a) sin compilador de Kotlin (R4): si el build falla, pasar el mensaje; (b) los cuatro botones pueden apretarse en pantallas muy pequeñas; (c) el fondo claro de los logos es cuestión de gusto: se ajusta.
  - **Siguiente paso:** Ariel prueba y dice «ya funciona» (o qué ajustar) → cada uno pasa a ✅ y a `HISTORIAL`. Sin etiqueta propia (R12).
- **Ahora en 🧪 también: FAV-REPRO-1 (★ en el reproductor; entregado 24 nov, Ariel pidió marcar favorito mientras mira un canal y dio el «Dale»).** Archivos: `VideoPlayerActivity.kt` y `M3uActivity.kt` (`FAV-REPRO-1.patch`).
  - **Qué hace:** barra de arriba del reproductor con una ★/☆ nueva (primer botón tras el título), solo con canales de lista (no con videos ni enlaces sueltos); llena = favorito. Usa `Store.toggleChFav` + `rememberChFav/forgetChFav`, igual que la lista, y sigue al cambiar de canal con ⏮ ⏭. Al volver a Canales, `M3uActivity.onResume` relee `Store.chFavs()` para que las ★ de la lista y de «Favoritos» salgan al día.
  - **Cómo probar:** 1) abrir un canal en el teléfono, tocar la pantalla: aparece ☆ en la barra; 2) tocarla: pasa a ★ y sale aviso; 3) ⏮ ⏭: la estrella cambia según el canal; 4) volver a Canales: ese canal tiene ★ y está en la fila Favoritos; 5) quitarla desde el reproductor y volver: desaparece; 6) un video del teléfono o un enlace suelto: no sale la ★.
  - **Riesgos:** (a) sin compilador de Kotlin (R4): si falla el build, pasar el mensaje; (b) la barra queda con 8 botones y el título aún más corto: se resuelve con BARRA-1 (abajo), propuesta aparte; (c) si abres un canal con una lista que no pasa por `Zap` (lista vacía), no sale la ★.
  - **Siguiente paso:** «ya funciona» de Ariel → ✅ y a `HISTORIAL`. Sin etiqueta (R12).
- **VISTA-1 a VISTA-4:** ya instalados (captura de Ariel, 24 nov: logos con fondo claro y 4 botones en Canales); falta su «ya funciona» explícito.
- **Propuesta aún sin etiquetar:** nada (v3.7 ya subida por Ariel).
- **Pendiente suelto de TV:** modelo real y versión del sistema (Ajustes › Soporte › Info de la TV).

### Cola de pendientes (uno a la vez, en este orden; ideas tomadas de `reproductor-IPTV` (MIT) y `kino-light` (Apache): solo ideas, código propio en Kotlin, sin nombre ni logo de Kino)
1. **TESTS-1b** (🧪, pendiente de aplicar). 2. **VISTA-1 a VISTA-4** (🧪, pendientes de probar). 3. **FAV-REPRO-1** (🧪, pendiente de aplicar). Después: Ariel elige de «Pendientes sueltos» o pide algo nuevo. COMPAT-TV-1 espera la visita a la TV del amigo (Ariel aún no tiene fecha, 23 nov: no sugerirlo hasta que él lo diga; el `/log` ya sale sin claves, LOG-1 ✅).

### Pendientes sueltos
- ⬜ Mejoras del reproductor propuestas (24 nov; Ariel pidió opinión, aún no eligió): **BARRA-1** barra superior con ★, 📺 TV y un «⋯» que agrupe 🔄 🔒 🖼 😴 🛠 💬 para que el título se lea completo · **VIVO-1** en canales en vivo ocultar el tiempo, la barra muerta y −10/+10 (dejar «● EN VIVO»; verificar antes si −10/+10 sirven con el buffer) · **GIRO-1** botón/gesto para pasar a horizontal con un toque · **ZAP-3** deslizar arriba/abajo para cambiar de canal.
- ⬜ Ideas de referencia NO elegidas (23 nov; con su contra; ATOMIC-1 y TESTS-1 ya se eligieron, ver 🧪): EPG-1 guía XMLTV (pantalla grande, XML pesado, emparejado de nombres falla) · XTREAM-1 importar con usuario y clave (solo si usa ese proveedor; guarda claves) · QR-1 pasar listas por QR/red local (abre una puerta en la red) · FUENTES-1 salud y ranking de fuentes (Ariel prefiere ocultar a mano) · catch-up (exige EPG). Descartado: despliegue escalonado, espejos de descarga, telemetría, interfaz Compose/Android TV, paquetes webOS/Tizen/Roku.
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
