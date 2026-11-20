# Registro de cambios y guía de continuidad – CASTV (v250, CORE)

**Memoria del proyecto.** Pasar **solo este archivo** a cualquier asistente para continuar (ver regla 11). Los otros dos (ARCHIVO y REFERENCIA) se piden solo si hace falta un detalle viejo.

**Estados:** ✅ **Incorporado** (aplicado, subido, compilado en verde y **confirmado por Ariel**) · 🧪 **Pendiente de aplicar y probar** · ⬜ **Por hacer** · ⏳ **En pausa/diagnóstico** · ❌ **Falló o descartado**.
**Reversión rápida:** `git log --oneline -15` → `git revert HASH` → `git push origin main`.
**Punto seguro base:** `~/castv-nuevo/castv-fresh` (rama `main`, etiqueta `v2.1-estable` / commit `d7010ac`).

**Índice:** ➡️ Próximo paso · 📡 Datos fijos · 🛡️ Reglas · 📍 Estado actual · 📌 Pendientes y bloqueados · 🔎 Marcas corregidas en v250 · 💡 Banco de ideas · 📋 Historial · 🗂️ Dónde está cada cosa

---

## ➡️ Próximo paso único

1. 🧪 **v250 = reestructurar el changelog en 3 archivos** (este CORE + ARCHIVO + REFERENCIA). Sin código. Con el "ya funciona" de Ariel (que subió bien al repo y que le sirve) pasa a ✅.
2. ⬜ **Siguiente cambio de código (propuesto):** **foco de audio** (pausa en llamadas o al abrir otra app de sonido). Antes de código: detalle en 🧪 y espera el "Dale" (regla 2). Se piden `MainActivity.kt` y `VideoPlayerActivity.kt` actuales en UNA línea de Termux (regla 9).
3. Después del banco de ideas: idioma **"Automático"** de los subtítulos con IA (antes de código, pedir `jni_whisper.cpp` y `WhisperLib.kt`).

---

## 📡 Datos fijos del entorno de Ariel (la IA los usa tal cual, sin pedirlos de nuevo)

- **IP del teléfono (Wi-Fi, DHCP): `192.168.100.43`** (Ariel, 18 nov 2026: "casi siempre es la misma"). Si algún enlace no responde, se revisa en Ajustes → Wi-Fi → la red → Dirección IP y se actualiza esta línea.
- **Puerto del servidor de la app:** `8001` (si la app muestra otro, usar ese).
- **Log de la app:** `http://192.168.100.43:8001/log` (abrir en el navegador del teléfono). **Limpiar el log:** `http://192.168.100.43:8001/log?clear`.
- **Prueba sin TV desde el mismo teléfono:** `http://127.0.0.1:8001/...` (equivale a la IP de arriba).
- **IP de la TV (vista en el log, 18 nov 2026):** `192.168.100.14`, puerto de control 38400.
- **TV:** Hisense HL1 (marca JVC en pantalla). **Teléfono:** Android con Termux; repo en `~/castv-nuevo/castv-fresh`.
- Los comandos y enlaces que entregue la IA llevan esta IP escrita completa; se evita el texto "192.168.100.43".

---


---

## 🛡️ REGLAS ESTRICTAS PARA LA IA (OBLIGATORIAS)

1. **Cero rupturas:** Todo cambio debe ser modular, aditivo y verificado contra los nombres reales del código (`ServerPort.port`, `getLocalIpAddress()`, etc.) antes de entregarlo.
2. **Changelog PRIMERO con detalle del parche:** Antes de pasar código o un parche nuevo, se entrega el changelog con estado **🧪** detallando **exactamente qué parche se aplicará, qué código modifica, qué archivos toca y cómo probarlo**.
3. **PROHIBIDO marcar ✅ sin confirmación:** Nada se marca ✅ hasta que Ariel confirme que compiló en verde en GitHub Actions y diga *"ya funciona"* tras probarlo. Solo entonces se entrega el changelog actualizado a ✅.
4. **Un cambio a la vez:** No acumular parches grandes. Entorno del usuario: teléfono Android con **Termux** (comandos encadenados con `&&` en UNA sola línea copiable, ver reglas 5, 6 y 9; cambios en `.patch` sin incluir el changelog; el changelog se sube aparte, ver regla 6).
5. **SIEMPRE pasar los comandos de Termux, EN UNA SOLA LÍNEA (OBLIGATORIO, Ariel 18 nov 2026, 19:55; sustituye la versión "uno por uno"):** cada vez que se entregue un parche o cambio, la respuesta DEBE incluir **un único bloque de código con una sola línea** que encadene todo con `&&` para copiar y pegar una sola vez. Plantilla del parche: `cd ~/castv-nuevo/castv-fresh && git apply ~/storage/downloads/<parche>.patch && git status --short && git add -A && git commit -m "..." && git push origin main` (si `git apply` falla, la cadena se detiene y no sube nada). Se da siempre, aunque el cambio sea pequeño. Si un parche depende de otro, se indica el orden en el mismo mensaje.
6. **El changelog TAMBIÉN se sube al repo, EN UNA SOLA LÍNEA (OBLIGATORIO, por seguridad):** en cada entrega se pasa, además de la línea del parche, **una línea** para copiar el changelog nuevo a `CHANGELOG.md` del repo y subirlo. Plantilla: `cd ~/castv-nuevo/castv-fresh && git ls-files | grep -i changelog && cp ~/storage/downloads/CHANGELOG_vNNN.md CHANGELOG.md && git add CHANGELOG.md && git commit -m "Changelog vNNN (...)" && git push origin main` (el `git ls-files | grep` confirma que el archivo está en el repo; si no aparece, la cadena se detiene). Ariel lo pidió el 18 nov 2026.
7. **VLC solo como inspiración (licencia GPLv2+):** NO copiar código, recursos, iconos, textos largos, nombre ni logo (cono) de VLC. Las ideas y la organización de pantallas se reimplementan con código propio (Material Icons u otros propios). Copiar código haría que CASTV tuviera que ser GPL con fuentes publicadas. Referencia local: `vlc-android-master.zip` (solo consulta).
8. **Continuidad sin re-pasar contexto (OBLIGATORIO, Ariel 18 nov 2026):** en CADA respuesta que cambie algo (diagnóstico, plan, parche, resultado de una prueba), la IA entrega el **changelog actualizado como archivo** con lo que sigue ya anotado (estado 🧪/✅/⏳, qué se probó, qué falta y cuál es el siguiente paso), más los comandos de Termux para subirlo (regla 6). Así Ariel no tiene que pegar capturas ni explicar de nuevo en cada chat. La IA nunca pide "pásame la conversación"; si algo falta, lo busca en este archivo o pide solo el archivo de código concreto.
9. **Pedir archivos de código también en UNA SOLA LÍNEA (OBLIGATORIO, Ariel 18 nov 2026, 19:55):** cuando se necesiten archivos del repo, se da **una sola línea** que los copie todos a `~/storage/downloads/` con `&&` y liste el resultado. Plantilla: `cd ~/castv-nuevo/castv-fresh && cp app/src/main/java/com/castv/app/A.kt ~/storage/downloads/A_actual.kt && cp app/src/main/java/com/castv/app/B.kt ~/storage/downloads/B_actual.kt && ls -l ~/storage/downloads/*_actual.kt`. Nunca un comando por bloque.

10. **Los archivos que la IA le pide o le entrega a Ariel van a la carpeta Download del teléfono (OBLIGATORIO, Ariel 21 nov 2026):** todo lo que Ariel tenga que subir al chat o bajar del chat (archivos de código del repo, parches `.patch`, changelogs, recursos que la IA necesite) debe quedar en `~/storage/downloads/` (la carpeta Download), donde Ariel lo ve y lo sube. Los comandos de las reglas 5, 6 y 9 ya lo cumplen y deben seguir así. **Aclaración de Ariel (21 nov 2026): la app NO tiene que guardar nada en Download; el modelo de Whisper se queda en la carpeta interna de la app y así está bien.**

---

11. **Estructura en 3 archivos y tamaño (OBLIGATORIO, v250):** el changelog vive en **CORE** (`CHANGELOG_vNNN.md`, este archivo, meta ≤ 25 KB), **ARCHIVO** (`CHANGELOG_ARCHIVO_vNNN.md`, detalle histórico de parches, planes y notas por versión, solo lectura) y **REFERENCIA** (`CHANGELOG_REFERENCIA_vNNN.md`, enlaces de prueba del TV, notas técnicas de la interfaz y catálogo M3U). La IA lee **solo el CORE** y pide los otros solo si hace falta algo concreto. Cuando Ariel confirma un cambio (✅), la IA deja **1-2 líneas** en el Historial del CORE y **mueve el detalle del parche al ARCHIVO**; no se duplica la misma información en varias secciones. Si el CORE pasa de ~25 KB, se archiva lo viejo antes de seguir. Para subir los tres al repo se usa una sola línea (ver abajo, regla 6 ampliada).
    - **Regla 6 ampliada:** `cd ~/castv-nuevo/castv-fresh && git ls-files | grep -i changelog && cp ~/storage/downloads/CHANGELOG_vNNN.md CHANGELOG.md && cp ~/storage/downloads/CHANGELOG_ARCHIVO_vNNN.md CHANGELOG_ARCHIVO.md && cp ~/storage/downloads/CHANGELOG_REFERENCIA_vNNN.md CHANGELOG_REFERENCIA.md && git add CHANGELOG.md CHANGELOG_ARCHIVO.md CHANGELOG_REFERENCIA.md && git commit -m "Changelog vNNN (...)" && git push origin main`. Si solo cambió el CORE, basta la plantilla original de la regla 6.

---

## 📍 Estado actual (v250)

- ✅ **Subtítulos con IA completos (IA-1 a IA-5, confirmados por Ariel el 20-21 nov 2026):** Whisper (`whisper.cpp`) en el teléfono; extracción de audio (`AudioExtractor.kt`), modelo **Rápido (`base`, ~143 MB)** o **Preciso (`small`, ~466 MB)**, idioma Español/English/Português/Français/Italiano/Deutsch; funciona con videos del teléfono y con enlaces/canales (IA-3); filas en **Ajustes › Subtítulos**: "Idioma de los subtítulos con IA", "Modelo de voz (IA)" y "Borrar modelo de voz (IA)". Los modelos NO vienen en el APK: se bajan al almacenamiento interno de la app solo si Ariel los elige (la app **no** guarda nada en Download, aclaración de Ariel). Línea de diagnóstico en el `/log`: `IA-4: idioma XX, modelo base|small, N frases, audio X s en Y s`.
- ✅ **Versión 3.0** (VERSION-1) y todo lo de la tanda del 18-19 nov confirmado (ver Historial). La etiqueta de publicación `v3.0` está **sin confirmar** (ver Pendientes).
- **Última decisión de Ariel (v249):** descartado generar subtítulos en segundo plano con servicio; orden = primero lo que queda del banco de ideas, después el idioma "Automático".
- **Ritmo de trabajo:** un cambio a la vez (regla 4), siempre 🧪 con detalle y "Dale" antes de código (regla 2); nada pasa a ✅ sin "ya funciona" de Ariel (regla 3).

---

## 📌 Pendientes y bloqueados (todo lo que NO es ✅, en un solo lugar)

**⬜ Por hacer (a elección de Ariel, uno a la vez):**
- **Foco de audio** (propuesto primero; no hay rastro de `AudioFocus` en el changelog).
- **Capítulos del archivo** (el reproductor actual no los lee; sin verificar).
- **Control remoto web** (usar el servidor del puerto 8000/8001 para controlar/subir desde un navegador).
- **Widgets, atajos, Android Auto y canales de Android TV.**
- **Vocabulario en español de referencia** (`values-es` de VLC, con palabras propias).
- **Idioma "Automático" de los subtítulos con IA** (después del banco; pedir antes `jni_whisper.cpp` y `WhisperLib.kt`).
- **Pendiente de Ariel (aparte):** `git tag v3.0 && git push origin v3.0` **si aún no lo hizo** (publica `CASTV-v3.0.apk`). Se dejó anotado desde v196 y nunca aparece confirmado: **preguntarle**.

**⏳ En pausa / por evaluar:**
- **COMPAT-TV-1:** "error de red" al mandar un canal a la TV de un amigo. Diagnóstico ✅ (CHECK-1 + `compat1.patch`); el arreglo se retoma **solo cuando Ariel vuelva a esa casa**: reenviar el canal, abrir `http://192.168.100.43:8001/log` justo después del error y pasar el texto, más marca/modelo de la TV y si algo del teléfono sí funcionó.
- **libvlc** (LGPL; reproduce casi todo y daría pistas embebidas y mejor HLS, pero implica rehacer la reproducción; exige cumplir LGPL), **explorador de red (SMB/FTP/UPnP)** y **renderers/cast vía libvlc**.
- **Pistas de audio/subtítulos embebidos:** solo con libvlc.
- **Licencias / pantalla "Acerca de" completa (ACERCA-1):** Ariel pidió **omitirlo hasta que él lo mencione**; la IA no lo propone ni lo recuerda. (La pantalla ya existe con REDES-1 y ACERCA-2 ✅.)

**❓ Decisión abierta:** `playLink` todavía manda HLS (`.m3u8`) al TV por `/relay`, aunque el Hisense no lo soporta. Falta decidir si se avisa en pantalla o se deja HLS solo para el móvil.

**❌ Descartado o no soportado (para no volver a proponerlo):**
- Chromecast/AirPlay y contenido con DRM.
- Generar subtítulos en segundo plano con servicio (v249).
- DESCARGAS-1 (mala lectura de la IA, descartado por Ariel).
- Canales HLS directos al TV Hisense ("Archivo no compatible"): HLS va como TS continuo con LIVE-1/LIVE-2/MUS-1 (✅); el `.m3u8` directo no.
- Copiar código, recursos o nombre de VLC (regla 7).

---

## 🔎 Marcas corregidas en v250 (para que Ariel las vea y las vete)

Estas marcas estaban desfasadas en v249 (decían ⬜ o 🧪 aunque Ariel ya había confirmado la entrega en otra sección del mismo archivo). Se actualizaron **solo con evidencia escrita en el propio changelog**; si Ariel no está de acuerdo con alguna, se vuelve a 🧪.
- ✅ **Control parental con PIN** (banco #17): PARENTAL-1/2 confirmados en v196.
- ✅ **Catálogo preinstalado y regiones** (banco #22): CATALOGO-1/2, REGIONES-1a y 1c.
- ✅ **Ajustes por secciones con buscador** (banco #26): AJUSTES-2a/2b/2c.
- ✅ **HUD del video (UI-2), mini reproductor expandible (UI-3), Canales en la barra inferior (UI-5):** UI-2a/2b/2c/2d, UI-3a/3b y UI-5 confirmados (la sección "Visión de interfaz" los tenía ⬜).
- ✅ **Encabezado "SUBTÍTULOS CON IA"** (estaba 🧪): IA-1 a IA-5 ya confirmados.
- **Punteros viejos eliminados:** "pasar solo `CHANGELOG_v196/v226/v235.md`" ya no aplica; ahora se pasa solo el CORE vigente.
- **Sin cambio y a vigilar:** fechas del changelog dicen "21 nov 2026" en entradas que quizá se escribieron el 20; si importa, Ariel lo corrige.

---

## 💡 Banco de ideas (inspirado en VLC; código propio, regla 7)

**Reproductor de video:** ✅ estilo de subtítulos (UI-S1) · ✅ deslizar para adelantar/retroceder, mantener pulsado, doble toque (GESTO-2) · ✅ bloqueo de controles (UI-2a) · ✅ temporizador de apagado · ✅ repetición A-B, marcadores y saltar a un tiempo (HERRAMIENTAS-VIDEO, MARCAS-1) · ✅ guardar brillo y confirmar antes de reanudar (CONFIRMAR-1) · ✅ zoom con pellizco · ✅ estadísticas/información del video (RONDA-3) · ✅ picture-in-picture (RONDA-2) · ⬜ **capítulos** · ⏳ pistas embebidas (libvlc).
**App en general:** ✅ "Qué hay de nuevo" y consejos de gestos (NOVEDADES-1) · ✅ ecualizador con presets (EQ-PRE-1) · ✅ control parental con PIN (PARENTAL-1/2) · ✅ ajustes por secciones con buscador (AJUSTES-2) · ⬜ **foco de audio** · ⬜ **control remoto web** · ⬜ **widgets, atajos, Android Auto, Android TV** · ⬜ **vocabulario en español de referencia**.
**Canales en vivo:** ✅ canales HLS visibles en el TV (LIVE-1, LIVE-2, MUS-1) · ✅ catálogo preinstalado y regiones (CATALOGO-1/2, REGIONES-1) · ✅ icono nuevo (ICONO-1) · ✅ canal en la barra y notificación (NOW-1) · ✅ Canales en la barra inferior y Carpetas en Más (UI-5) · ✅ selección múltiple (cola, favoritos, listas, borrar).
**No hay nada reutilizable de VLC para M3U:** se mantiene `M3uParser.kt`.

---

## 📋 Historial resumido de etapas confirmadas (✅)

- **IA-5 borrar modelo de voz (21 nov 2026):** fila "Borrar modelo de voz (IA)" en Ajustes › Subtítulos (`SettingsActivity.kt`) y `present()/usedBytes()/delete()/clearPartials()` en `WhisperModel.kt`; no deja borrar con `SubtitleGen.running`; `ia5_borrar_modelo.patch`.

- **IA-4 idioma y modelo de la transcripción con IA (21 nov 2026):** `WhisperModel.kt` con modelos Rápido/Preciso, `selected/lang/label` y espacio libre; dos filas en Ajustes › Subtítulos (`SettingsActivity.kt`); `SubtitleGen.kt` usa idioma y modelo elegidos y anota `IA-4:` en el `/log`; `ia4_ajustes.patch`.

- **NOW-1 barra y notificación con el canal del TV (18 nov 2026):** `ACT_CHANNEL_ON_TV` (aviso de `M3uActivity.sendToTv()` a `MainActivity`), `linkTitleNow`/`linkIsLive`, "En vivo" en la barra y `refreshLinkNotification()` solo con Pausa; `now1b_filtro.patch` registró la acción en el `IntentFilter`.
- **ICONO-1 nuevo icono (18 nov 2026):** `drawable-nodpi/ic_launcher_tv.jpg` (512×512) como `foreground` en `mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml`; el `monochrome` sigue con el vector.
- **CATALOGO-1 listas preinstaladas de iptv-org (18 nov 2026):** `Catalog.kt` (nuevo), `Store.installCatalog()` y `distinctBy` en `M3uActivity.loadList()`; 30 listas más Free-TV, una sola vez por versión.
- **MUS-1 video + audio unidos en canales con audio aparte (18 nov 2026):** `joinAudio()`, `refreshAudio()` y la clase `TsMux` en `MediaServer.kt`; más la corrección de fragmentos viejos al arrancar un canal en vivo.
- **LIVE-2 un flujo por canal con buffer y `Range` (18 nov 2026):** `hubs`/`LiveHub`/`HubStream`/`NullTsStream` y `serveLive()` con 206 en `MediaServer.kt`; canales en vivo fluidos en la TV.
- **LOCAL-1 detener lo local al mandar un canal M3U al TV (18 nov 2026):** aviso interno `ACT_STOP_LOCAL` (`MainActivity.kt`, `M3uActivity.kt`).
- **LIVE-1a/1b/1c/1d/1f canales en vivo al TV (18 nov 2026):** ruta `/live` (`MediaServer.kt`, `TvSend.liveUrl`), `playLink` y `M3uActivity.sendToTv()` mandan HLS como TS continuo con descifrado AES-128, sin `chunked`.
- **PROG-1d duración del enlace en el TV (18 nov 2026):** `linkDurSec`, `linkGen`, `readLinkDuration()` con `MediaMetadataRetriever` en `MainActivity.kt`; `poll()` usa esa duración si el TV manda 0.
- **PAUSA-1 pausa del enlace en el TV (18 nov 2026):** campo `linkOnTv` en `MainActivity.kt`; ⏸/▶ controlan el enlace del TV y Detener lo limpia.
- **SEL-1 acciones en la selección múltiple (18 nov 2026):** ➕ A la cola, ★/☆ Favoritos y 📃 A una lista en el ⋮ de la barra de selección (`MainActivity.kt`).
- **UI-S1 estilo de subtítulos (18 nov 2026):** 🎨 en el menú 💬; tamaño, negrita, color y fondo guardados en `castv_subs` (`VideoPlayerActivity.kt`).
- **UI-1b pantalla de Ajustes (18 nov 2026):** `SettingsActivity.kt` nuevo, registro en el manifest, ⋮ abre Ajustes, Más sin repetidos.
- **UI-1 pestaña Más (18 nov 2026):** `navMore`, `ic_nav_more.xml`, `showMoreSheet()` en `MainActivity.kt`.
- **Respaldo Exportar/Importar JSON (17 nov 2026):** `Backup.kt`, `Store.reload()`, menú id 17 en `MainActivity.kt`.
- **Gestos en el reproductor (17 nov 2026):** brillo, volumen y doble toque ±10 s (`VideoPlayerActivity.kt`).
- **Etapa 13-B.2 (17 nov 2026):** Canales más cómodos y ★ favoritos de canales (`M3uActivity.kt`, `Store.kt`).
- **Etapa 13-B.1 (17 nov 2026):** Listas M3U (`M3uParser.kt`, `M3uActivity.kt`, `Store.kt`).
- **Historial de enlaces + favoritos sueltos (17 nov 2026):** `SavedLink` en `Store.kt`, 🕘 Historial en "Reproducir enlace".
- **Etapa 14-C:** Extracción de `PortDialog.kt` y `TvSend.kt`.
- **Etapa 14-A / 14-B (`ac51ab7`):** `CastvUtils.kt` (texto/red) y mensajes de error claros.
- **Etapa 13-A.1 / 13-A.2 (`98bcf78`, `11d93df`):** Búsqueda de puerto libre (8000-8010) y servidor `/relay` para enlaces externos mp4 al TV.
- **Reproductor y UI:** Salida Teléfono/TV para video, subtítulos `.srt/.vtt`, siguiente automático, botón 📺 TV, selección múltiple con papelera Android, tema Claro/Oscuro, 4 colores de acento, carga en segundo plano y log en `/log`.
- **Etapa 11 (`d7010ac`):** Base estable v2.1.

---

**Otras entregas ✅ confirmadas por Ariel (solo IDs; el detalle está en el ARCHIVO):** IA-1, IA-2b, IA-3 (20-21 nov) · CHECK-1, SALIDA-INI-1, SUB-DELAY-1, GESTOS-AJ-1, REDES-1, ACERCA-2, RONDA-2/3/4 (EQ-PRE-1, PiP, incógnito, MARCAS-1, CONFIRMAR-1, TIEMPO-1, LIMPIEZA-1), HERRAMIENTAS-VIDEO (19-20 nov) · GESTO-2, NOVEDADES-1, VERSION-1 (3.0), PUERTO-1, BIBLIO-1, AUDIO-1, PARENTAL-1/2, CARPETAS-1, BACKUP-1, AVISO-HLS-2, SEL-2 (v195-v196) · UI-2a/2b/2c/2d, UI-3a/3b, AJUSTES-2a/2b/2c, REGIONES-1a/1c, CATALOGO-2, FAV-1, ACENTO-1, OCULTAR-1, RED-1b, PULIDO-1 (18-19 nov).

---

## 🗂️ Dónde está cada cosa

- **`CHANGELOG_ARCHIVO_v250.md`:** todo el detalle histórico tal como estaba en v249 (planes, parches, listas de prueba, notas por versión v131-v235, estados anteriores v195/v198, Visión de interfaz). Solo lectura.
- **`CHANGELOG_REFERENCIA_v250.md`:** enlaces de prueba del TV (Hisense HL1), notas técnicas de la interfaz actual y Anexo con el catálogo maestro v2 de listas M3U.
