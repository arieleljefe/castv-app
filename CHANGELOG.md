# CASTV: CHANGELOG (cerebro) · v434

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
| Teléfono | Android + Termux · **la IP cambia** (DHCP + MAC aleatoria; el 24 nov era `192.168.100.20`, antes `.43`). La app la detecta sola; verla en Ajustes › Emisión al TV › «IP del teléfono». Nunca dar una IP fija por sentada |
| App | puerto `8001` (rango 8000–8010) · log: `http://<IP del teléfono>:8001/log` (`?clear` para limpiar) |
| TV | Hisense HL1 (JVC en pantalla, Vidaa) · IP `192.168.100.14` · código de servicio `VA0250UD1`, software `V0100.06.13J.N0711` (Ariel, 24 nov) · DLNA/UPnP puerto `38400` (solo MediaRenderer; XML en `/MediaServer/rendererdevicedesc.xml`). **Ignora `SetMute`: el silencio es `SetVolume 0`**; `GetVolume` da 0 hasta el primer `SetVolume` |
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

- **R12 Versiones:** las etiquetas SÍ se proponen, pero agrupadas: no una por cada cambio pequeño, sino cuando se junta un lote de cambios (o al cerrar un objetivo) para instalarlos todos de una vez (aclaración de Ariel, 24 nov). La IA lo propone al terminar el lote, con la línea `git tag v<N> && git push origin v<N>`, solo con el build de `main` en verde; entre lotes, los parches van a `main` sin etiqueta.

**Plantilla de entrada activa (sección 4):** `ID · estado · Qué · Archivos · Cómo probar · Siguiente paso`.

---

## 4. ESTADO VIVO

### ▶ RETOMAR AQUÍ (26 nov 2026, 00:05)
- **SEGUNDO-PLANO-1 · 🧪 entregado (`SEGUNDO-PLANO-1.patch`; Ariel: «Dale», 26 nov 00:01)** · Problema: el canal en vivo al TV se corta al salir de la app o bloquear el teléfono («error de red»). Del `/log` (25 nov): a las 23:52:00 «LIVE2 atasco: sin datos 25 s»; la TV cerró su conexión durante el hueco y no volvió; la app se reconectó sola en 3 s pero ya sin cliente («canal sin clientes por 30 s: se cierra»); Ariel reenvió el canal a mano. Hipótesis (no confirmada: el log no anota la pantalla): con la pantalla apagada el Wi-Fi del teléfono entra en ahorro; `CastvService` ya tenía `WakeLock` y `WifiLock`, pero el candado Wi-Fi era solo `FULL_LOW_LATENCY` en Android 10+, que solo actúa con la app a la vista y la pantalla encendida. Cambio: segundo candado `FULL_HIGH_PERF` (Android 10+) y diagnóstico en el `/log`: «SEGUNDO PLANO (motivo): Android N fabricante modelo; cpu, wifi, wifiAlto, sinRestriccionDeBateria, ahorroDeBateria, pantallaEncendida», «PANTALLA apagada/encendida» y «el servicio se detiene». Archivo: `CastvService.kt` (no cambia nada de lo que se envía al TV). Probar: canal al TV, bloquear la pantalla 2-3 min, desbloquear; luego pasar el `/log` (puerto 8000–8010, con la app abierta). Riesgo: bajo (aditivo). Aquí no hay compilador de Kotlin: si falla el build, pasar el mensaje de GitHub Actions. En Android 14+ el candado de alto rendimiento puede no hacer nada: el diagnóstico lo dirá.
  - **Pendientes de este tema (no entregados; esperan el resultado):** (a) rellenar huecos con paquetes TS nulos para que la TV no cierre la conexión en atascos cortos (riesgo medio). (b) **PAUSA-LIVE-1:** con la TV en pausa más de 30 s el canal se cierra y al reanudar la TV pide la posición vieja (3,6 MB) en un buffer nuevo, así que espera más de 1 min (log 23:42:12–23:43:53); idea: subir `idleTooLong` de 30 s a ~3 min en `MediaServer.kt` (solo si Ariel lo pide). (c) Ariel probará a mano CASTV en «Sin restricciones» de batería (Ajustes de Android › Apps › CASTV › Batería).
- **LOTE v3.14 (25 nov; Ariel: «Dale» a las ideas 1, 2 y 4).** Archivos recibidos y revisados (`MainActivity`, `M3uActivity`, `VideoPlayerActivity`, `PlayerCore`, `TvController`, `TvSend`, `Zap`, `ReminderReceiver`, `AndroidManifest`). **«Dale» al plan recibido (25 nov, 00:44): los 3 parches entregados juntos, un commit cada uno, pendientes de probar.** Orden: VOLUMEN-TV-1 › AVISO-TV-1 › FUNDIDO-1, un parche y un commit por ítem. La etiqueta `v3.14` (R12) se propone al cerrar el lote e incluye también TABS-1, ÁLBUMES-2 y TOP-CANALES-1.
  1. **VOLUMEN-TV-1 · 🧪 entregado (`VOLUMEN-TV-1.patch`)** · Los botones de volumen del teléfono ya suben/bajan el TV en `MainActivity.onKeyDown`, pero solo con música/video del teléfono (`playingItem != null`). Falta: (a) canales y enlaces en el TV (`linkOnTv`) y (b) la pantalla Canales (`M3uActivity`, sin `onKeyDown`). Cambio: condición `playingItem != null || linkOnTv`; `onKeyDown` en `M3uActivity` cuando `Zap.nowTitle != null`; `TvController.lastVol` (último volumen conocido, porque `GetVolume` da 0 hasta el primer `SetVolume`). Archivos: `MainActivity.kt`, `M3uActivity.kt`, `TvController.kt`. Probar: canal al TV, ya en Canales y en Reproduciendo, subir/bajar con los botones (pasos de 5, aviso «🔊 Volumen del TV: N»). Riesgo: bajo; si el volumen es desconocido, la primera pulsación parte de 20.
  2. **AVISO-TV-1 · 🧪 entregado (`AVISO-TV-1.patch`)** · Al tocar el aviso de EPG-RECORDA-1, si la salida está en TV y el TV responde, el canal se manda al TV; si no, se abre en el teléfono como hasta ahora. Cambio: la notificación abre `MainActivity` con `remind_url` y `remind_title`; `MainActivity` (`onCreate`/`onNewIntent`) decide según `outPhone` y espera hasta 6 s a que aparezca el TV; `Zap.sendUrl()` nuevo (misma lógica que `Zap.send`, sin lista). Archivos: `ReminderReceiver.kt`, `MainActivity.kt`, `Zap.kt`. Probar: aviso de un programa a 2 min, con salida TV y con salida Teléfono, y con la app cerrada. Riesgo: medio (arranque en frío; sin TV tras 6 s cae al teléfono con aviso). Sin `ACT_*` nuevas.
  3. **FUNDIDO-1 · 🧪 entregado (`FUNDIDO-1.patch`; 10 s, `fadeSecs` en `MainActivity`, `fadeRunnable` en `PlayerCore`, 10 000 ms en `VideoPlayerActivity`)** · El temporizador de dormir baja el volumen en ~10 s antes de pausar, en vez de cortar: música del teléfono (`checkSleep`), música sin pantalla (`PlayerCore.sleepRunnable`), TV (rampa de `SetVolume` y luego Pause, restaurando el volumen anterior) y video (`VideoPlayerActivity.sleepNow` con `mpRef.setVolume`). «Al terminar la pista/video» no cambia. Si algo cancela, pausa o reanuda durante el fundido, se restaura el volumen. Archivos: `MainActivity.kt`, `PlayerCore.kt`, `VideoPlayerActivity.kt`. Probar: temporizador de 15 min en cada caso (se puede probar acortando con el menú de 15 min y esperando; o con el TV). Riesgo: medio-bajo (volumen que quede en 0 si algo falla: por eso se restaura al terminar).
  4. **LIMPIA-1 · ⬜** plantilla E al cerrar el lote (los `*_actual.*` de hoy siguen en Download).

### Anterior (24 nov 2026, noche)
- **LOTE v3.13 (24 nov; Ariel: «me gustan todas»; archivos recibidos y código revisado, 24 nov noche).** «Dale» recibido («Sigamos», 24 nov): orden CALIDAD-1 › EPG-RECORDA-1 › TOP-1, un parche por ítem. Etiqueta `v3.13` al cerrar el lote (R12). LIVE-3 ✅ y EPG-RECORDA-1 ✅ (ver `HISTORIAL`) entran en `v3.13` junto con CALIDAD-1 y TOP-1 ✅; solo queda LIMPIA-1, y después se propone la etiqueta `v3.13`. Ariel no ha confirmado si la `v3.13` ya salió.
  1. **CAIDOS-1 · ⛔ ya existe (24 nov):** tras 🔎 Revisar, `finishCheck()` ofrece «Ocultar» todos o «Ver cuáles»; además el botón ⚠ «N fallan» (`showFailedDialog()`). Sin parche.
  2. **TEMP-TV-1 · ⛔ ya existe (24 nov):** `pauseAll()` manda Pause al TV cuando no hay reproducción local, y `checkSleep()` corre en el bucle del TV. Duda sin confirmar: en canales en vivo el TV podría ignorar Pause (habría que mandar Stop). Solo si Ariel lo ve fallar.
  3. **CALIDAD-1 · ✅ (24 nov, Ariel: «Ya funciona»)** · ver `HISTORIAL`. Sin etiqueta (R12; entra en `v3.13`).
  4. **EPG-RECORDA-1 · ✅ (24 nov, Ariel: «Ya funciona»)** · ver `HISTORIAL`. Sin etiqueta (R12; entra en `v3.13`).
  5. **TOP-1 · ✅ (24 nov, Ariel: «Ya funciona»)** · ver `HISTORIAL`. Sin etiqueta (R12; entra en `v3.13`).
  6. **TABS-1 · ✅ (24 nov, Ariel: «Funciona todo bien»)** · ver `HISTORIAL`. Sin etiqueta (R12; entra en `v3.14`).
  7. **ÁLBUMES-2 · ✅ (24 nov, Ariel: «Todo funciona bien»)** · ver `HISTORIAL`. Sin etiqueta (R12; entra en `v3.14`).
  8. **TOP-CANALES-1 · ✅ (24 nov, Ariel: «Todo funciona bien»)** · ver `HISTORIAL`. Sin etiqueta (R12; entra en `v3.14`).
  9. **LIMPIA-1 · ⬜** (pendiente de v3.12) plantilla E; ✅ con el «listo» de Ariel.
- **Nota (24 nov, 23:21):** la captura de Ariel muestra la app en **3.13** instalada (la etiqueta `v3.13` ya salió); él aún no lo confirmó con palabras ni dijo «listo» de LIMPIA-1. Los cambios siguientes entran en `v3.14`.
- **PARA MAÑANA (cierre del 24 nov, 23:45):** todo ✅ y subido hasta TOP-CANALES-1; nada en 🧪. Pendientes: (1) LIMPIA-1: Ariel corre la plantilla E y dice «listo». (2) Etiqueta `v3.14` (R12) con el build de `main` en verde: `git tag v3.14 && git push origin v3.14`; incluiría TABS-1, ÁLBUMES-2 y TOP-CANALES-1 (la `v3.13` ya está instalada). (3) COMPAT-TV-1 ⏳ espera la TV del amigo (no sugerirlo antes). Sin ideas nuevas en cola: al retomar, Ariel pasa solo este changelog y se pregunta qué quiere hacer. Archivos nuevos de hoy: `item_group.xml`, `item_album_card.xml`, `item_group_grid.xml`; nuevo archivo de datos `castv_top` (Más vistos, fuera de respaldos).
- COMPAT-TV-1 ⏳ sigue esperando la visita a la TV del amigo.

### Anterior (lote v3.12, cerrado)
- **LOTE v3.12 (24 nov; Ariel: «me gustan todas», «Dale» a BUSCA-TODO-1 ✅).** Un parche por ítem, un commit cada uno; **TAG-3.12 ✅ (24 nov, Ariel: «Ya funciona»):** etiqueta publicada e instalada; línea en `HISTORIAL`. Lote cerrado salvo LIMPIA-1 (R12). La etiqueta `v3.11` (lote anterior) está propuesta y Ariel aún no dijo si la subió.
  1. **SRT-1 · ⛔ ya existe (24 nov):** `VideoPlayerActivity.showSubtitleMenu()` ya trae «Elegir archivo (.srt / .vtt)…» (`ACTION_OPEN_DOCUMENT`), más retraso y estilo. Sin parche.
  2. **BUSCA-TODO-1 · ✅ (24 nov, Ariel: «Ya funciona»)** · ver `HISTORIAL`. Sin etiqueta (R12).
  3. **M3U-EXPORT-1 · ✅ (24 nov, Ariel: «Ya funciona»)** · ver `HISTORIAL`. Sin etiqueta (R12).
  4. **TESTS-4 · ✅ (24 nov, Ariel: «Ya funciona»)** · ver `HISTORIAL`. Entró en `v3.12`.
  5. **LIMPIA-1 · ⬜ (en curso: plantilla E entregada; ✅ con el «listo» de Ariel)** Al cerrar el lote, plantilla E (`rm -f ~/storage/downloads/*_actual.*`).
  - **Archivos ya recibidos (24 nov, 13:49):** `M3uActivity`, `Store`, `Catalog`, `Epg`, `ShareProvider`, `CastvUtils`, `M3uParser`, `VideoPlayerActivity`, `SubtitleGen`, `build.gradle`, `strings.xml`; más `Xtream` y `Backup`. Si Ariel retoma en otro chat, hay que volver a pedirlos (plantilla A).
- **DECISIÓN DE ARIEL (24 nov, noche): «todo, vamos por todos menos COMPAT-TV-1»** (y «Dale» al plan). Orden: BORRAR-1 ✅ › WIDGET-1 ✅ › etiqueta `v3.10` ✅ al final (con el build de `main` en verde; incluirá ICONOS-1 a 10, BORRAR-1 y WIDGET-1) › Android Auto/TV solo evaluar, sin código hasta que Ariel diga. COMPAT-TV-1 sigue ⏳.
- **LOTE v3.11 (24 nov, Ariel: «todo lo otro vamos a darle»), en este orden, un parche por ítem (R2: «todo de golpe» = parches encadenados, un commit por parche); la etiqueta `v3.11` se propone al cerrar el lote (R12):**
  - EPG-DEFECTO-1 ✅, SINPANTALLA-1 ✅, **XTFAV-1 ✅** y **TESTS-3 ✅** (24 nov, ver `HISTORIAL`; Ariel: «Funciona»). **Lote v3.11 cerrado:** se propone la etiqueta `v3.11` (R12) con el build de `main` en verde; incluye EPG-DEFECTO-1, SINPANTALLA-1, XTFAV-1 y TESTS-3.
- **BORRAR-1 y TAG-3.10 ✅ (24 nov):** Ariel: «Ya funciona» (Release `CASTV-v3.10.apk` instalado; incluye ICONOS-1 a 10, BORRAR-1 y WIDGET-1). «Borrar» → «Eliminar» en historial, selección, diálogos de Ajustes y marcador; «Borrar datos» se deja. Líneas en `HISTORIAL`. Nada a medias; quedan COMPAT-TV-1 ⏳ (espera la TV del amigo) y nada más (Android Auto/TV descartado por el momento, 24 nov).
- **WIDGET-1 ✅ (24 nov):** Ariel: «ya instalé el widget1, funciona bien». Widget de inicio «CASTV» con título, estado y ⏮ ⏯ ⏭ (tocar el título abre la app); los botones usan `ACT_PREV/TOGGLE/NEXT` y solo responden con la app viva (con la app cerrada vuelve a «Abre la app para reproducir»). Línea en `HISTORIAL`. Sin etiqueta aún: entra en `v3.10`.
- **ICONOS (24 nov):** zonas 1 a 10 ✅ (ICONOS-10 confirmado por Ariel: «Ya funciona»). Serie cerrada: se dejan con emoji, por decisión, los avisos/HUD, los botones de diálogo (los iconos no se tiñen igual que el texto en un botón), los selectores Artista/Álbum/Carpeta y los títulos; Ajustes no lleva emoji en sus menús. Si Ariel quiere más iconos, él lo pide.
- **AHORA-TV · ⛔ ya existe (24 nov):** al revisar el código, «lo que dan ahora en mis favoritos» ya está: con la guía activada (Ajustes › Video) las tarjetas de ★ Favoritos muestran «▶ programa» y las filas «▶ programa · hasta HH:MM» (EPG-1). Lo único que falta es que la guía viene apagada; no se cambia.
- **Barra «Ningún archivo seleccionado» (24 nov): ⛔ sin cambios.** Revisado `player_bar.xml` y `MainActivity.kt`: `updateMiniCompact()` (PULIDO-2b) ya la compacta sin nada sonando (esconde la barra de progreso y la ☆, queda título + ▶ + ˄) y su ▶ (`btnQuick`) arranca la música; esconderla quitaría ese atajo y ahorra poco espacio. Si Ariel la quiere distinta, él lo pide. Interfaz de hoy cerrada: INSETS-1, CANALES-1, AVANZADO-1 y FAB-1 ✅. Sin etiqueta (R12).
- **CIERRE (24 nov, tarde):** todo ✅ y subido hasta ICONOS-10; nada en 🧪. Ariel: «sigamos más tarde». Pendientes: COMPAT-TV-1 (espera la visita a la TV del amigo; no sugerirlo antes), etiqueta `v3.10` solo si Ariel la pide (R12; incluiría también ICONOS-1 a 10), duda abierta de los botones «Borrar» (recomendado dejarlos), ideas sin pedir: Android Auto/TV. Al retomar: Ariel pasa solo este changelog y se pregunta qué quiere hacer.
- **PARA MAÑANA (cierre del 24 nov, 03:3x):** todo ✅ y subido hasta EPG-AGENDA-1; nada en 🧪. Pendientes: (1) COMPAT-TV-1 espera la visita a la TV del amigo (Ariel pasará el `/log`; no sugerirlo antes). (2) Etiqueta `v3.10`: solo si Ariel la pide (R12); incluiría HLS-TEXTO-1, VOCAB-3/4, ARTISTA-1, EPG-REGION-1, EPG-BUSCAR-1, EPG-AGENDA-1, TESTS-2. (3) Duda abierta: botones «Borrar historial / marcador / modelo» (recomendado dejarlos como «Borrar»; Ariel no respondió). (4) Ideas sin pedir: Android Auto/TV, EPG-1c descartada. Al retomar: Ariel pasa este changelog; la IP del teléfono sale de Ajustes › Emisión al TV (el 24 nov era `192.168.100.20`); Free-TV elige guías de Italia, Polonia y Reino Unido (los canales de otros países no muestran «▶ programa»).
- **Hecho y ✅:** **ÁLBUMES-2** y **TOP-CANALES-1** (24 nov), **TABS-1** (24 nov), **TOP-1** (24 nov), **CALIDAD-1** (24 nov), **EPG-RECORDA-1** (24 nov), **LIVE-3** (24 nov, canal en vivo al TV sin cortes; Ariel: «Ya funciona bien»), **XTFAV-1** y **TESTS-3** (24 nov), **TAG-3.10**, **BORRAR-1** y **WIDGET-1** (24 nov), **ICONOS-10** (24 nov), **ICONOS-9** (24 nov), **ICONOS-6/7/8** (24 nov), **ICONOS-3/4/5** (24 nov), **INSETS-1**, **CANALES-1**, **AVANZADO-1**, **FAB-1**, **VACIO-1**, **BUSCA-1**, **DESHACER-1**, **CANAL-MINI-1**, **MAPA-1**, **ICONOS-1** e **ICONOS-2** (24 nov), **RECUP-1** (23 nov), REMOTO-1, ATAJOS-1, CAPÍTULOS-1, VOCABULARIO-1/2, FIRMA-1, UPDATE-1, VERSION-2, **ESTILO-1**, **ESTILO-2**, **UPDATE-2**, **TV-2**, **ESTILO-3**, **ZAP-1**, **LISTA-VAL-1**, **SPEED-1**, **TAG-3.5**, **VISTA-1 a VISTA-5** (logos, tarjetas y botones de Canales), **FAV-REPRO-1**, **FAV-REPRO-2**, **FAV-REPRO-3**, **ZAP-3**, **FUENTES-1**, **XTREAM-1** (24 nov), **EPG-1** (24 nov), **QR-1** (24 nov), **EPG-1b** (24 nov), **QR-2a** (24 nov), **QR-2b** (24 nov), **BARRA-1**, **VIVO-1**, **GIRO-1**, **VERSION-3**, **TESTS-1b**, **TAG-3.9** (24 nov), **DUP-1** (24 nov), **HLS-TEXTO-1**, **VOCAB-3**, **ARTISTA-1**, **EPG-REGION-1**, **VOCAB-4**, **VOCABULARIO-3**, **EPG-BUSCAR-1**, **TESTS-2**, **EPG-AGENDA-1** (24 nov), **SERVICIO-1a** y **SERVICIO-1b**, **AUTORESPALDO-1**, **ZAP-2**, **LOG-1**, **ATOMIC-1** y **TESTS-1**, y **WIDGET-1** (24 nov) (ver `HISTORIAL`).
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
- **Regla de versiones (Ariel, 23 nov):** la etiqueta `v*` solo se propone con cambios realmente grandes; los parches pequeños se suben a `main` sin etiqueta. Última etiqueta: **`v3.9`** (ver «Etiquetas»); antes `v3.7` (Ariel la subió el 23-24 nov tras ver las pruebas en verde; incluye AUTORESPALDO-1, ZAP-2, LOG-1, ATOMIC-1 y TESTS-1; TESTS-1b puede o no estar dentro según cuándo se etiquetó). Falta confirmar que su Release (`CASTV-v3.7.apk`) se publicó y que Ariel actualizó desde Ajustes › Actualizaciones. Antes: `v3.6` (commit `61fff98`, hasta SERVICIO-1b). Siguiente al tocar: `v3.8` (`git tag v3.8 && git push origin v3.8`) y solo con el build de `main` en verde. **Lección:** instalar solo APK de un Release (etiqueta), no el artefacto de un push, o el actualizador dirá «El APK no es más nuevo que el instalado»; no reinstalar una versión de código menor (exige desinstalar). Si una etiqueta sale con un commit roto: `git tag -d v<N> && git push origin :refs/tags/v<N>` y volver a etiquetar con el build verde.
- **LISTA-VAL-1 ✅ (23 nov):** Ariel: «Funciona todo». Una descarga vacía o dañada ya no pisa la copia guardada de la lista (se usa la anterior, aviso «lista nueva dañada (copia guardada)» y línea en el `/log`). Solo `M3uActivity.kt`. Línea en `HISTORIAL`. Sin etiqueta (R12). (La prueba pudo dejar la lista `prueba` y `~/t` en Termux: limpiar con 🗑 y `pkill -f http.server; rm -r ~/t`.)
- **ZAP-1 ✅ (23 nov):** Ariel: «Funciona bien con el TV». ⏮ ⏭ cambian de canal cuando hay un canal de lista en el TV (pantalla Reproduciendo y notificación). Solo vale para el TV: en modo teléfono ⏮⏭ no cambian de canal (ZAP-2 ✅ lo hizo también para el teléfono). Línea en `HISTORIAL`. Sin etiqueta (R12).
- **SPEED-1 ✅ (23 nov):** Ariel: «Ya funciona» (tras SPEED-1b, build verde, instalado desde Ajustes › Actualizaciones). Herramientas › «⏩ Velocidad» (0.5×–2×), por video y sin guardar; al soltar «mantener pulsado» vuelve a la velocidad elegida; no en canales en vivo. Solo `VideoPlayerActivity.kt`. Línea en `HISTORIAL`.
- **TAG-3.5 ✅ (23 nov):** `v3.5` publicada y firmada; el actualizador de la app funciona de nuevo. Línea en `HISTORIAL`.
- **SERVICIO-1a ✅ (23 nov):** Ariel: «Ya funciona». `PlayerCore.kt` nuevo guarda el `MediaPlayer` del teléfono y su estado; `MainActivity` los lee de ahí. Línea en `HISTORIAL`.
- **SERVICIO-1b ✅ (23 nov):** Ariel: «Ya todo funciona bien». La música del teléfono sigue sonando si Android cierra `MainActivity` («modo sin pantalla» en `PlayerCore`: avanza sola, cola/repetir/temporizador, notificación ⏮ ⏯ ⏭; al reabrir la app se recoge con `adoptHeadless()`). Límite que tenía (resuelto en SINPANTALLA-1 ✅): sin pantalla no había ecualizador ni velocidad. Línea en `HISTORIAL`. Etiqueta `v3.6` ya creada (ver Regla de versiones).
- **✅ (24 nov, Ariel: «Ya funciona bien»):** VISTA-1 a VISTA-5, FAV-REPRO-1, BARRA-1, VIVO-1, GIRO-1, VERSION-3 y TESTS-1b (el build salió en verde con las pruebas). Líneas en `HISTORIAL`. Barra del reproductor ahora: `título · ★ · 🔄 · ⋯ · 📺 TV`; en vivo solo «● EN VIVO»; 🔄 un toque alterna vertical/horizontal y mantenido = automático.
- **XTREAM-1 ✅ (24 nov):** Ariel: «Lo instalé y funciona bien». Listas con usuario y clave (botón «🔑 Xtream» en «＋ Lista», lista `xtream://<id>`, acceso en `castv_xt` fuera de respaldos, `/log` y respaldos sin claves). Los límites de respaldos (favoritos, ocultos, Continuar e Historial de Xtream) se resolvieron en XTFAV-1 ✅; en el teléfono los enlaces de canal siguen con usuario y clave dentro. Línea en `HISTORIAL`. Sin etiqueta (R12).
- **EPG-1 ✅ (24 nov):** Ariel: «Funciona todo». Guía XMLTV apagada por defecto (Ajustes › Video); «▶ programa · hasta HH:MM» en Canales y tarjetas, «📅 Guía: ahora y siguiente» en el ⋯ del reproductor, botón 📅 para pegar una URL propia. Línea en `HISTORIAL`. Sin etiqueta (R12). Nota: la cabecera de Free-TV trae más de 100 guías y la app lee solo las 3 primeras (ver «Pendientes sueltos», EPG-1b).
- **QR-1 ✅ (24 nov):** Ariel: «ya instalé el qr1 y funciona bien». ＋ Lista › «📲 Desde otro dispositivo (QR)»: recuadro con QR y dirección de un solo uso; se cierra solo (5 min, al recibir la lista, al cerrar el recuadro, al salir de Canales o tras 5 claves malas); solo red local y enlaces M3U normales (con usuario y clave se rechazan: para eso está 🔑 Xtream); la clave no sale en el `/log`. Librería `zxing:core:3.5.3` y prueba `QrAddTest`. Línea en `HISTORIAL`. Sin etiqueta (R12). Queda sin probar a fondo (Ariel no lo dijo): cierre a los 5 min y que el `/log` no lleve la clave.
- **Decisión de Ariel (24 nov): «vamos por todos».** Orden: EPG-1b ✅ › **QR-2**; COMPAT-TV-1 sigue esperando la visita a la TV del amigo (no se puede hacer antes); la etiqueta `v3.9` ya salió (ver abajo).
- **EPG-1b ✅ (24 nov):** Ariel: «Ya funciona». Con una cabecera de más de 3 guías (Free-TV), la app elige las guías de los países de los `tvg-id` de los canales (hasta 3 países con 3+ canales, 2 guías por país, 6 descargas); sin coincidencia, las 3 primeras; el `/log` anota «EPG: guías elegidas». Línea en `HISTORIAL`. Sin etiqueta (R12).
- **QR-2a ✅ (24 nov):** Ariel: «Ya funciona». ＋ Lista › «📤 Enviar esta lista a otro móvil» (solo con una lista abierta): QR con `http://<IP>:<puerto>/send/<clave>`; el otro móvil pulsa «Recibir lista» y ve nombre y enlace con «Copiar enlace»; clave de un solo uso, 5 min, solo red local, sin la clave en el `/log`; no se envían listas Xtream ni enlaces con usuario y clave. Línea en `HISTORIAL`. Sin etiqueta (R12).
- **QR-2b ✅ (24 nov):** Ariel: «Ya funciona». Ajustes › Respaldo › «Enviar respaldo a otro móvil»: confirmación, QR con `http://<IP>:<puerto>/send/<clave>`, el otro móvil baja `castv-respaldo.json` con «Descargar respaldo» (avisa que importarlo reemplaza sus datos); mismas reglas que QR-2a (un solo uso, 5 min, red local, sin clave en el `/log`); el respaldo no lleva claves Xtream, IP del TV ni PIN. Línea en `HISTORIAL`. Sin etiqueta (R12).
- **TAG-3.9 ✅ (24 nov):** Ariel: «Ya estamos en la última versión» (Release `CASTV-v3.9.apk` instalado desde la app). Incluye EPG-1, XTREAM-1, QR-1, EPG-1b, QR-2a y QR-2b (lo anterior ya estaba en `v3.8`). Línea en `HISTORIAL`. Nada más a medias; sigue pendiente solo COMPAT-TV-1 (espera la TV del amigo).
- **HLS-TEXTO-1, VOCAB-3 y ARTISTA-1 ✅ (24 nov):** Ariel: «Ya funciona». Líneas en `HISTORIAL`. Sin etiqueta (R12). Nada a medias; solo queda COMPAT-TV-1 (espera la TV del amigo).
- **EPG-REGION-1, VOCAB-4 y VOCABULARIO-3 ✅ (24 nov):** Ariel: «Ya funciona». Líneas en `HISTORIAL`. Sin etiqueta (R12). Nada a medias; solo queda COMPAT-TV-1 (espera la TV del amigo). Duda abierta: si los botones «Borrar historial / marcador / modelo» pasan a «Eliminar» (Ariel no ha respondido).
- **EPG-BUSCAR-1 y TESTS-2 ✅ (24 nov):** Ariel: «Funciona» (en Free-TV › Italia, buscar «noti» encontró Mediaset Italia por su programa «Dentro La Notizia»; build con TESTS-2 en verde). Líneas en `HISTORIAL`. Sin etiqueta (R12). HILO-1 descartado: `mediaUrl` lo usa un solo hilo. **Dato:** con Free-TV la app eligió las guías IT1, RAKUTEN_IT1, PL1, RAKUTEN_PL1 y UK1; las dos RAKUTEN dan HTTP 404 (idea EPG-1c: saltarse las que den 404 y usar otras del mismo país; Ariel aún no respondió si la quiere).
- **EPG-AGENDA-1 ✅ (24 nov):** Ariel: «Funciona» (Mediaset Italia: «Ahora», «Después» y «Más tarde:» con 5 programas). Línea en `HISTORIAL`. Sin etiqueta (R12). EPG-1c descartada (una guía con 404 ya se salta sola). Nada a medias; solo queda COMPAT-TV-1 (espera la TV del amigo).
- **DUP-1 ✅ (24 nov):** Ariel: «Ya funciona». «Duplicados» ya detecta copias con sufijo `_1` o ` (1)` en el nombre. Línea en `HISTORIAL`. Sin etiqueta (R12).
- **Etiquetas:** última `v3.12` ✅ (publicada e instalada). Siguiente: `v3.14` (la `v3.13` ya aparece instalada), cuando haya un lote de cambios listo (R12; la IA la propone al cerrarlo): `git tag v3.14 && git push origin v3.14` con el build de `main` en verde.
- **TV-INFO-1 ✅ (24 nov):** Ariel pasó la pantalla «Acerca de» de la TV: código de servicio `VA0250UD1`, software `V0100.06.13J.N0711` (extendida `J.N0711`), IP `192.168.100.14`. Línea en `HISTORIAL`. No se guardan MAC ni números de serie/ESN.

### Cola de pendientes (uno a la vez, en este orden; ideas tomadas de `reproductor-IPTV` (MIT) y `kino-light` (Apache): solo ideas, código propio en Kotlin, sin nombre ni logo de Kino)
Nada elegido por Ariel aparte de lo que está en 🧪. COMPAT-TV-1 ⏳ espera la visita a la TV del amigo (no sugerirlo hasta que él lo diga).

### Notas de revisión (24 nov; salen de los videos, las capturas y el código; Ariel pidió hacerlo todo esa misma noche)
- **ALMAC-1 · ✅ verificado en código, sin parche** · `toggleTrash()` guarda la opción, muestra el aviso y vuelve a pintar la fila (`render()`); el desajuste del video se explica por dos toques seguidos (los avisos se ponen en cola). No es un fallo.

**Verificado en los videos (24 nov, v3.9):** sin cierres ni errores; Actualizaciones («Estás al día», 3.9 = GitHub v3.9), Respaldo y QR, Acerca de, Más, Ordenar, Artista/Álbum, Temporizador, Velocidad, Incógnito, reproductor de música y de video, canal en vivo con «● EN VIVO», Herramientas, revisión de 2235 canales y chips de ánimo. «Duplicados: ninguno» en esos videos es anterior a DUP-1 ✅.
**Lecciones (24 nov):** (a) Si `cp` dice «No such file or directory» al subir el changelog, las tarjetas del chat aún no se descargaron a Descargas: comprobar con `ls -l ~/storage/downloads/*_v<N>.md` antes de repetir la línea. (b) La IP del teléfono cambia: usar la de Ajustes › Emisión al TV, nunca una fija. (c) Ariel puede pasar videos largos cortados en partes (`parte_000.mp4`…): se miran por fotogramas; si falta una parte, pedirla.
### Pendientes sueltos
- ✅ QR-1, EPG-1 y XTREAM-1 hechos (ver `HISTORIAL`). ✅ QR-2a y QR-2b hechos (ver `HISTORIAL`).
- ✅ EPG-1b hecho (24 nov; problema original: con Free-TV la cabecera trae más de 100 guías y la app lee solo las 3 primeras; idea: elegir las guías por el país de los `tvg-id` (`.it`, `.es`…), tope ~6 descargas).
- ⛔ Catch-up descartado (24 nov): Ariel no tiene cuenta Xtream y en listas M3U no existe archivo; si algún día usa Xtream, él avisa. Ya hechas: QR-1, FUENTES-1, ATOMIC-1, TESTS-1. Descartado: despliegue escalonado, espejos de descarga, telemetría, interfaz Compose/Android TV, paquetes webOS/Tizen/Roku.
- ⏳ COMPAT-TV-1: arreglo para la TV del amigo, solo cuando Ariel pruebe allá y pase el `/log` (el diagnóstico ya está ✅; el `/log` ya oculta claves con LOG-1 ✅).
- ✅ Modelo y versión de la TV: hecho (TV-INFO-1, ver `HISTORIAL`).
- ✅ WIDGET-1 hecho (24 nov, ver `HISTORIAL`).
- ✅ (hecho como SERVICIO-1) la reproducción de música del teléfono sobrevive a cerrar la app.
- ⛔ Android Auto / Android TV: descartado por el momento (Ariel, 24 nov); si algún día lo quiere, él avisa.
- ⛔ Pistas de audio/subtítulos embebidos con libvlc/ffmpeg: descartado (Ariel, 24 nov: no quiere meter libvlc ni ffmpeg).
- Verificado 23 nov: temporizador de apagado y velocidad ya existen en música (`MainActivity.kt`); el temporizador también en video (GESTO-2). Velocidad en video, verificado 23 nov: solo existe al mantener pulsado (1.5×/2×/3×), no hay selector (ver SPEED-1).

### Descartado (⛔)
Widget con la app cerrada (mover la reproducción a un servicio) y libvlc/ffmpeg/pistas embebidas (24 nov, decisión de Ariel): no proponer. WIDGET-1 se queda como está (✅).
Android Auto / Android TV (24 nov, decisión de Ariel: «por el momento»): no evaluar ni proponer; si lo quiere, él avisa.
Subtítulos con IA (Whisper), idioma «Automático» y mejoras: **CERRADO por Ariel (23 nov)**; no tocar ni proponer. Si algún día quiere mejorarlo, él avisa (al retomar, pedir antes `jni_whisper.cpp` y `WhisperLib.kt`).
`Platzi-Download` y `Youtube_Downloader` (23 nov): no aportan a castv y descargan contenido de terceros; no se toman ideas de ellos.
Catch-up (24 nov, sin cuenta Xtream), Chromecast/AirPlay, DRM, subtítulos con IA en segundo plano con servicio, guardar el modelo de Whisper en Download, control por 36669 con certificado ajeno, flechas/OK/teclas por DLNA (la TV no expone ese servicio), `SetMute` como silencio (la TV lo ignora).
LOG-1 ya no está descartado: se hizo y quedó ✅ el 23 nov (ver `HISTORIAL`).

---

## 5. REFERENCIA RÁPIDA DEL CÓDIGO (hasta que exista MAPA.md)

Archivos conocidos en `app/src/main/java/com/castv/app/`: `MainActivity.kt` (pantalla principal, mini reproductor, Más, ecualizador, `playLink`), `VideoPlayerActivity.kt` (reproductor de video, gestos, PiP, subtítulos, foco de audio), `M3uActivity.kt` + `M3uParser.kt` + `Catalog.kt` + `Regions.kt` + `Logos.kt` (canales), `MediaServer.kt` (servidor, `/relay`, `/live`, buffer), `TvController.kt` (DLNA), `TvSend.kt`, `Store.kt` (datos), `Backup.kt` (+ parciales para compartir), `AutoBackup.kt` (copia diaria), `ShareProvider.kt` (archivos para compartir), `SettingsActivity.kt`, `AboutActivity.kt`, `ParentalPin.kt`, `PortDialog.kt`, `CastvService.kt`, `CastvUtils.kt`, `Zap.kt` (ZAP-1/ZAP-2: lista de canales para ⏮ ⏭, en TV y en teléfono), `DebugLog.kt`, `Thumbs.kt`, `AudioExtractor.kt`, `WhisperModel.kt`, `WhisperLib.kt`, `SubtitleGen.kt`; C++ en `app/src/main/cpp/` (whisper.cpp v1.9.3).
Preferencias: `castv`, `castv_lib`, `castv_subs`, `castv_stats`.
