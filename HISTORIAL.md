# CASTV: HISTORIAL (solo lo ✅ confirmado por Ariel) · una línea por ítem · v383

Formato: `ID · fecha · qué · archivos principales`. Se consulta solo si algo falla o hay que revertir (`git log --oneline` + `git revert HASH`).
Los commits exactos están en git; el detalle largo de cada parche vive en el changelog v261 antiguo (guardado como referencia).

## Base y TV
- Base estable v2.1 · commit `d7010ac` · etiqueta `v2.1-estable`.
- 13-A.1/13-A.2 · 17 nov · puerto libre 8000-8010 y `/relay` mp4 al TV · `MediaServer.kt`.
- 14-A/14-B/14-C · 17-18 nov · `CastvUtils.kt`, `PortDialog.kt`, `TvSend.kt`, mensajes de error.
- PAUSA-1 · 18 nov · ⏸/▶ controlan el enlace en el TV (`linkOnTv`) · `MainActivity.kt`.
- PROG-1/1d · 18 nov · título, tiempo y duración de enlaces en el TV (lee duración con `MediaMetadataRetriever`) · `MainActivity.kt`.
- LIVE-1a/1b/1c/1d/1f · 18 nov · canales HLS al TV como TS continuo `/live` con AES-128 · `MediaServer.kt`, `TvSend.kt`.
- LIVE-2 · 18 nov · un flujo por canal con buffer 16 MB y `Range` (sin lag) · `MediaServer.kt`.
- MUS-1 · 18 nov · une audio y video cuando el audio va en lista aparte (`TsMux`) · `MediaServer.kt`.
- GZIP-1 · 18 nov · `Accept-Encoding: identity` y un reintento · `MediaServer.kt`.
- LOCAL-1 · 18 nov · detiene lo local al mandar canal al TV (`ACT_STOP_LOCAL`).
- NOW-1/1b · 18 nov · barra y notificación muestran el canal del TV (`ACT_CHANNEL_ON_TV`).
- CANAL-BG · 18 nov · el canal sigue con la app en segundo plano (ya lo hacía `CastvService`).
- CHECK-1 + COMPAT-TV-1 (diagnóstico) · 20 nov · revisar qué canales responden; log de la TV · `M3uActivity.kt`, `MediaServer.kt`, `Store.kt`, `TvController.kt`.
- SALIDA-INI-1 · 19 nov · abrir en modo Teléfono · `MainActivity.kt`, `SettingsActivity.kt`.
- FOCO-1 y FOCO-2 · 21 nov · pausa con llamadas/alarmas/otra app y al desconectar audífonos · `VideoPlayerActivity.kt`, `MainActivity.kt`.
- ATAJOS-1 · 22 nov · atajos dinámicos al mantener pulsado el icono: Continuar video, Música, Canales en vivo (con PIN); `launchMode=singleTop` · `MainActivity.kt`, `AndroidManifest.xml`, `strings.xml`, `ic_sc_*.xml`.
- REMOTO-1 (REMOTO-0, DIAG, 1a, 1b) · 22 nov · diagnóstico: la TV solo es MediaRenderer, sin teclas; volumen y 🔇 en la pantalla Reproduciendo; silencio = `SetVolume 0` con restauración porque la TV ignora `SetMute` · `MainActivity.kt`, `TvController.kt`.

- FIRMA-1 · 23 nov · firma estable del APK: clave propia `castv` (RSA 4096) guardada en GitHub Secrets, `assembleRelease` firmado, huella SHA-256 impresa en el build (`4dd1f018…dc558e`); instalado una sola vez desinstalando; las siguientes versiones se instalan encima · `app/build.gradle`, `.github/workflows/build.yml`.
- RECUP-1 · 23 nov · los canales en vivo se recuperan solos: reintento de la lista con espera creciente, fragmentos que fallan se saltan, detector de atascos (25 s) y reapertura del origen (6 intentos) con el flujo continuo hacia la TV · `MediaServer.kt`.

## Canales y catálogo
- 13-B.1 / 13-B.2 · 17 nov · Listas M3U, favoritos de canales · `M3uParser.kt`, `M3uActivity.kt`, `Store.kt`.
- Historial de enlaces y favoritos sueltos · 17 nov · `Store.kt`, `MainActivity.kt`.
- CATALOGO-1/2 · 18 nov · listas iptv-org preinstaladas (≈45) · `Catalog.kt`.
- REGIONES-1a/1b/1b2/1c · 18-19 nov · regiones, "Toda la región", logos · `Regions.kt`, `Logos.kt`, `M3uActivity.kt`.
- FAV-1 · 18 nov · ⭐ favoritos global · `Store.kt`, `M3uActivity.kt`.
- ACENTO-1 · 18 nov · color de acento en Canales.
- OCULTAR-1 · 19 nov · quitar/restaurar canales · AVISO-HLS y AVISO-HLS-2 · aviso si el canal no se puede preparar.
- GRUPOS-1 + PULIDO-3 · 19 nov · grupos en español, etiquetas, icono sin logo · `M3uActivity.kt`.
- RED-1/RED-1b · 19 nov · aviso con datos móviles · `MainActivity.kt`, `M3uActivity.kt`.

## Interfaz y Ajustes
- VOCABULARIO-1 · 23 nov · lista de referencia de términos y diferencias de texto de la app, sin cambiar código · `VOCABULARIO.md`.
- VOCABULARIO-2 · 23 nov · textos unificados: teléfono (no "móvil"), dispositivo, Video, Quitar lista, Eliminado, Descargando, Continuar · `MainActivity.kt`, `M3uActivity.kt`, `VideoPlayerActivity.kt`, `SettingsActivity.kt`, `activity_main.xml`.
- UI-1/UI-1b · 18 nov · pestaña Más y pantalla Ajustes · `SettingsActivity.kt`.
- UI-5 · 18 nov · Canales en la barra inferior, Carpetas en Más.
- AJUSTES-2a/2b/2c, AJUSTES-3 · 19 nov · buscador, subpáginas, registro, TV/historial · `SettingsActivity.kt`.
- ACERCA-1/2 y REDES-1 · 19 nov · `AboutActivity.kt` (licencia: todos los derechos reservados).
- ICONO-1 · 18 nov · nuevo icono.
- PULIDO-1/2a/2b/2c/2e, MAS-1 · 19 nov · diálogos, avisos, mini reproductor, hoja Más con iconos.
- UI-3a/3b · 19 nov · carátula grande y pantalla completa "Reproduciendo".
- PUERTO-1, BIBLIO-1, AUDIO-1, PARENTAL-1/2, CARPETAS-1, BACKUP-1/2 · 19 nov · `ParentalPin.kt`, `Backup.kt`, `SettingsActivity.kt`.
- VERSION-1 · 19 nov · versión 3.0.
- TAG-3.1 · 23 nov · etiqueta `v3.1` sobre `main` (la `v3.0` ya existía en un commit viejo, `8f41c34`); publica `CASTV-v3.1.apk` · `build.yml`.
- TAG-3.4 · 23 nov · etiqueta `v3.4` publicada (cambio grande: ESTILO-2, UPDATE-2, TV-2, ESTILO-3); Release firmado `CASTV-v3.4.apk` (código 216) · `build.yml`.
- SEL-1/SEL-2 · 18-19 nov · acciones en selección múltiple.
- RONDA-2/3/4 · 19-20 nov · incógnito, visto, PiP, presets EQ, No vistos, marcadores, confirmar antes de continuar, tiempo visto · `VideoPlayerActivity.kt`, `Store.kt`, `MainActivity.kt`.

## Reproductor de video
- Gestos (17 nov), GESTO-1/GESTO-2/NOVEDADES-1, GESTOS-AJ-1 · 19 nov · deslizar para adelantar, mantener pulsado (velocidad rápida), doble toque al centro, zoom con pellizco, guardar brillo, temporizador de apagado, "Qué hay de nuevo" · `VideoPlayerActivity.kt`.
- UI-2a/2b/2c/2d/2e · 19 nov · bloqueo, barra propia, tamaño de pantalla, botón Atrás.
- UI-S1 + SUB-DELAY-1 · 18-19 nov · estilo y retraso de subtítulos.
- HERRAMIENTAS-VIDEO · 19 nov · saltar a tiempo, A-B, volumen extra, información.
- CAPITULOS-1 · 23 nov · Herramientas › "📑 Capítulos (N)…" lista y salta a capítulos de MKV/WebM y MP4/M4V/MOV (`chpl`) · `Chapters.kt`, `VideoPlayerActivity.kt`.
- Respaldo JSON · 17 nov · `Backup.kt`.

## Subtítulos con IA (Whisper)
- IA-1 (extraer audio), IA-2a (whisper.cpp compila), IA-2b (descarga modelo y transcribe), IA-3 (generar `.srt` desde 💬), IA-4 (idioma y modelo Rápido/Preciso), IA-5 (borrar modelo) · 20-21 nov · `AudioExtractor.kt`, `WhisperLib.kt`, `WhisperModel.kt`, `SubtitleGen.kt`, `jni_whisper.cpp`, `build.gradle`, `build.yml`.
- CACHE-1 · 19 nov · caché de Gradle en el workflow.
- UPDATE-1 · 23 nov · actualizar desde la app (Ajustes › Actualizaciones: lee el último Release público, descarga, comprueba SHA-256/firma/versionCode y abre el instalador; `versionCode` = n.º de ejecución, `versionName` = etiqueta) · `Updater.kt`, `file_paths.xml`, `SettingsActivity.kt`, `AndroidManifest.xml`, `build.gradle`, `build.yml`.
- VERSION-2 · 23 nov · la cabecera de la pantalla principal lee `versionName` de la app (ya no "3.0" fijo) · `activity_main.xml`, `MainActivity.kt`.
- ESTILO-1 · 23 nov · tamaños de texto, radios y espaciados reunidos en `values/dimens.xml` y estilos `TextAppearance.Castv.*` (sin cambiar cómo se ve nada) · `dimens.xml`, `styles.xml`, `activity_main.xml`, `item_file.xml`, `item_queue.xml`, `player_bar.xml`, `footer_credits.xml`, `bg_chip/bg_thumb/bg_search/bg_dialog.xml`.
- ESTILO-2 · 23 nov · filas de tarjetas **Continuar** (últimos 12 canales vistos, clave `chrecent`) y **Favoritos** arriba de Canales; se esconden al buscar/filtrar · `M3uActivity.kt`, `Store.kt`.
- UPDATE-2 · 23 nov · aviso «Hay una versión nueva» al abrir la app (cada 6 h máx.; Actualizar ahora abre Ajustes directo en la búsqueda; Más tarde = 24 h) + interruptor en Ajustes › Actualizaciones · `MainActivity.kt`, `SettingsActivity.kt`.
- TV-2 · 23 nov · al pasar a modo TV busca los TVs del Wi-Fi: uno solo se usa, varios salen en un recuadro para elegir, ninguno avisa con Reintentar; `connectTv()` compartida con ⋮ › Elegir TV · `MainActivity.kt`.
- ESTILO-3 · 23 nov · fila de chips de ánimo en Canales (Relajarme, Entretenimiento, Aprender, Familia, Cine y series, Deportes, Informarme, Música) que filtra por categorías de iptv-org; no se mezcla con «Grupo» · `M3uActivity.kt`.
- ZAP-1 · 23 nov · ⏮ ⏭ cambian de canal (circular, respetando búsqueda/grupo/ánimo) cuando hay un canal de lista en el TV, en la pantalla Reproduciendo y en la notificación; solo TV, no teléfono · `Zap.kt`, `M3uActivity.kt`, `MainActivity.kt`.
- LISTA-VAL-1 · 23 nov · una descarga de lista vacía o dañada ya no pisa la copia guardada: se conserva la anterior con aviso y línea en el `/log` · `M3uActivity.kt`.
- SPEED-1 (+ SPEED-1b) · 23 nov · Herramientas › Velocidad del video (0.5×–2×), por video y sin guardar; «mantener pulsado» vuelve a la velocidad elegida; no en canales en vivo; 1b corrige `FloatArray.indexOf` que rompía el build · `VideoPlayerActivity.kt`.
- TAG-3.5 · 23 nov · etiqueta `v3.5` publicada y firmada (RECUP-1, ZAP-1, LISTA-VAL-1, SPEED-1); el actualizador de la app vuelve a funcionar · `build.yml`.
- SERVICIO-1a · 23 nov · `PlayerCore.kt` guarda el reproductor del teléfono, su estado y el foco de audio fuera de la pantalla; `MainActivity` los lee de ahí (sin cambio visible) · `PlayerCore.kt`, `MainActivity.kt`.
- SERVICIO-1b · 23 nov · la música del teléfono sigue sonando si Android cierra la pantalla principal («modo sin pantalla» en `PlayerCore`: pasa de pista sola, cola, repetir, temporizador, notificación ⏮ ⏯ ⏭; al reabrir la app se recoge con `adoptHeadless()`) · `PlayerCore.kt`, `MainActivity.kt`.
- AUTORESPALDO-1 · 23 nov · copia automática diaria al abrir la app (`filesDir/autobackup/castv_auto_AAAA-MM-DD.json`, 1 al día, últimas 3, no se guarda si pesa <30 % de la anterior) + Respaldo › «Restaurar copia automática…»; Respaldo › «Compartir…» (completo, solo historial o solo favoritos de canales) por la hoja de Android con aviso de claves; los parciales (v2, `part`) se AÑADEN al importar y no borran nada; el aviso del importador dice si reemplaza o añade · `Backup.kt`, `AutoBackup.kt`, `ShareProvider.kt`, `share_paths.xml`, `AndroidManifest.xml`, `MainActivity.kt`.
- ZAP-2 · 23 nov · ⏮ ⏭ cambian de canal también cuando el canal suena en el teléfono (barra del reproductor y teclas de audífonos; dan la vuelta, respetan búsqueda/grupo/ánimo/ocultos; la lista va por memoria en `Zap`); en modo canales un canal roto avisa y se queda, y uno que termina cierra el reproductor; 📺 TV desde el reproductor deja el contexto para ⏮ ⏭ en el TV · `Zap.kt`, `M3uActivity.kt`, `VideoPlayerActivity.kt`.
- LOG-1 · 23 nov · el `/log` oculta claves: `redact()` en `DebugLog.kt` cambia por `[OCULTO]` el valor de `token`, `key`, `apikey`, `pass`, `password`, `secret`, `auth`, `sig`, `session`, `user`, `usuario`, `usuario:clave@` y `Bearer …`, también codificados dentro de otro enlace; no cubre claves en la ruta del enlace · `DebugLog.kt`.
- ATOMIC-1 · 23 nov · guardado seguro en `Store.kt`: los métodos que leen y escriben son `@Synchronized` y, si un dato guardado (`links`, `m3u`, `chfav`, `chfavmeta`, `chrecent`, `chhidden`, `lists`, marcadores) no se puede leer, se guarda una copia del texto en `castv_bad` y una línea en el `/log` antes de que una escritura lo pise; sin cambio visible · `Store.kt`.
- TESTS-1 · 23 nov · pruebas automáticas (JUnit) en GitHub Actions: 7 de `DebugLog.redact` y 7 de `M3uParser.parse`, paso «Pruebas automaticas (TESTS-1)» antes de compilar; confirmado en verde con el log del run 239 · `DebugLogRedactTest.kt`, `M3uParserTest.kt`, `app/build.gradle`, `build.yml`.
- VISTA-1 a VISTA-4 · 24 nov · pantalla Canales: fondo claro tras cada logo, tarjetas de 96 dp, lista actual en su fila con 4 botones (☆ Favs, 👁 N, ＋ Lista, 🗑 Quitar), estrella de Favoritos con el color de acento y fila de ánimos difuminada · `M3uActivity.kt`
- VISTA-5 · 24 nov · logos claros (blancos) con fondo oscuro `#2B3040` y los demás con `#F2F2F2`: `Logos.bgFor()` mide el brillo del logo (muestra ~24×24, umbral 185) y ajusta el fondo al llegar el logo · `Logos.kt`, `M3uActivity.kt`
- FAV-REPRO-1 · 24 nov · ★/☆ en la barra del reproductor del teléfono para marcar el canal como favorito mientras se mira (usa `Store.toggleChFav` + `rememberChFav/forgetChFav`); al volver, Canales relee las ★ en `onResume` · `VideoPlayerActivity.kt`, `M3uActivity.kt`
- BARRA-1 · 24 nov · barra del reproductor: título, ★, 🔄, ⋯ y 📺 TV; el menú ⋯ agrupa 🔒 Bloquear, 🖼 Tamaño, 😴 Temporizador (con el tiempo restante), 🛠 Herramientas y 💬 Subtítulos · `VideoPlayerActivity.kt`
- VIVO-1 · 24 nov · en canales y enlaces en vivo (`id == 0L`, sin duración) se ocultan tiempo, barra y −10/+10 y queda «● EN VIVO»; con videos del teléfono vuelven · `VideoPlayerActivity.kt`
- GIRO-1 · 24 nov · 🔄 con un toque alterna vertical ↔ horizontal; mantener pulsado vuelve al giro automático · `VideoPlayerActivity.kt`
- VERSION-3 · 24 nov · `versionName` por defecto 3.8 en `app/build.gradle` (con etiqueta `v*` manda la etiqueta); la causa de «3.1» era instalar el APK de un push · `app/build.gradle`
- TESTS-1b · 24 nov · `build.yml` sin `continue-on-error`: una prueba rota detiene el build antes del APK; build en verde con v3.8 · `.github/workflows/build.yml`
- FAV-REPRO-2 · 24 nov · ★/☆ también en canales abiertos desde Continuar, Historial o enlaces (`currentChannel()` arma el canal con título + enlace, y logo/grupo si ya estaba en «Continuar»); videos del teléfono sin ★ · `VideoPlayerActivity.kt`
- FAV-REPRO-3 · 24 nov · ★ del mini reproductor, de «Reproduciendo» y botón «Favorito» en la notificación marcan el canal que suena en el TV (`ACT_CHANNEL_ON_TV` lleva `url`, `tvChannelNow()`, `tvFavNow`, nueva `ACT_FAV` en el `IntentFilter`) · `MainActivity.kt`, `M3uActivity.kt`, `Zap.kt`
- ZAP-3 · 24 nov · con canales de una lista, deslizar en la franja central (1/3 a 2/3 del ancho) arriba = canal siguiente y abajo = anterior (70 dp, cambia al soltar; reusa `stepOrZap`); interruptor Ajustes › Video «Deslizar al centro: cambiar de canal» (`g_zap`) · `VideoPlayerActivity.kt`, `SettingsActivity.kt`
- FUENTES-1 · 24 nov · salud de canales: el teléfono anota fallos y aciertos (error del reproductor, `probeLive`, 🔎 Revisar, TV acepta); con 2 fallos seguidos sale «⚠» en tarjetas y lista; botón «⚠ N» junto a 🔎 Revisar para ocultar a mano los marcados u «Olvidar avisos»; clave `chhealth` (tope 600), sin anotar en incógnito · `Store.kt`, `M3uActivity.kt`, `VideoPlayerActivity.kt`, `Zap.kt`
- XTREAM-1 · 24 nov · listas Xtream con usuario y clave: botón «🔑 Xtream» en «＋ Lista», lista `xtream://<id>` con el acceso en `castv_xt` (fuera de respaldos, autocopia y archivos para compartir), `/log` tapa usuario, clave y tokens, y los respaldos quitan los enlaces con claves; favoritos, ocultos, Continuar e Historial de canales Xtream no viajan en respaldos · `Xtream.kt`, `M3uActivity.kt`, `Backup.kt`, `DebugLog.kt`
- EPG-1 · 24 nov · guía XMLTV, apagada por defecto (Ajustes › Video): «▶ programa · hasta HH:MM» en Canales y tarjetas, «📅 Guía: ahora y siguiente» en el ⋯ del reproductor y botón 📅 con URL manual; fuente: URL manual › Xtream (`xmltv.php`) › `url-tvg` de la lista (hasta 3); emparejado por `tvg-id` o nombre; copia de 12 h en `cacheDir`; con datos móviles solo a mano · `Epg.kt`, `M3uParser.kt`, `Xtream.kt`, `M3uActivity.kt`, `VideoPlayerActivity.kt`, `SettingsActivity.kt`
- QR-1 · 24 nov · listas desde otro dispositivo por QR: ＋ Lista › «📲 Desde otro dispositivo (QR)» abre un recuadro con QR y `http://<IP>:<puerto>/qr/<clave>` (clave de 24 caracteres, un solo uso; se cierra a los 5 min, al recibir la lista, al cerrar, al salir de Canales o tras 5 claves malas); solo red local y enlaces http/https sin usuario ni clave; la clave no sale en `/log`; comprueba que el servidor responde antes de mostrar el QR; librería `zxing:core:3.5.3` y prueba `QrAddTest` · `Qr.kt`, `MediaServer.kt`, `M3uActivity.kt`, `app/build.gradle`, `QrAddTest.kt`
- EPG-1b · 24 nov · con una cabecera de más de 3 guías (Free-TV), se eligen las guías de los países de los `tvg-id` de los canales (hasta 3 países con 3+ canales, 2 guías por país, 6 descargas); sin coincidencia, las 3 primeras; `/log` anota «EPG: guías elegidas»; `epgUrls(text, limit)` · `Epg.kt`, `M3uParser.kt`, `EpgPickTest.kt`
- QR-2a · 24 nov · enviar la lista abierta a otro móvil por QR: ＋ Lista › «📤 Enviar esta lista a otro móvil» abre un recuadro con QR y `http://<IP>:<puerto>/send/<clave>` (24 caracteres, un solo uso; se cierra a los 5 min, al entregar, al cerrar, al salir de Canales o tras 5 claves malas); el otro móvil pulsa «Recibir lista» y ve nombre y enlace con «Copiar enlace»; solo red local; no se envían listas Xtream ni enlaces con usuario y clave; la clave no sale en `/log` · `Qr.kt` (`QrSend`), `MediaServer.kt`, `M3uActivity.kt`, `QrSendTest.kt`
- QR-2b · 24 nov · enviar el respaldo a otro móvil por QR: Ajustes › Respaldo › «Enviar respaldo a otro móvil» (confirmación previa), QR con `http://<IP>:<puerto>/send/<clave>`; el otro móvil baja `castv-respaldo.json` con «Descargar respaldo» (se arma al bajarlo con `Backup.build`: sin claves Xtream, IP del TV ni PIN); un solo uso, 5 min, solo red local, sin clave en `/log`; `QrSend` entrega listas o respaldos, cada puerta rechaza lo del otro tipo · `Qr.kt`, `MediaServer.kt`, `SettingsActivity.kt`, `QrSendTest.kt`
- TAG-3.9 · 24 nov · etiqueta `v3.9` publicada e instalada desde Ajustes › Actualizaciones (EPG-1, XTREAM-1, QR-1, EPG-1b, QR-2a, QR-2b); Release `CASTV-v3.9.apk` · `build.yml`
- TV-INFO-1 · 24 nov · datos de la TV tomados de su pantalla «Acerca de»: código de servicio `VA0250UD1`, software `V0100.06.13J.N0711`; sirve para COMPAT-TV-1 · `CHANGELOG.md`
- IP-1 · 24 nov · comprobado: la app detecta sola la IP del teléfono y la del TV (Ajustes › Emisión al TV); la IP del teléfono cambia (DHCP, MAC aleatoria), por eso el changelog ya no la fija · `CHANGELOG.md`
- DUP-1 · 24 nov · «Duplicados» detecta también copias con sufijo `_1`…`_99` o ` (1)`…` (99)` en el nombre (misma duración ±2 s; conserva la más antigua; no borra nada) · `MainActivity.kt` (`dupKey`, `dupSuffixRe`, `computeDups`)
- HLS-TEXTO-1 · 24 nov · el selector de un canal HLS dice solo «📺 Al TV» (sin «puede no funcionar»); AVISO-HLS-2 sigue igual · `M3uActivity.kt`
- VOCAB-3 · 24 nov · «móvil» → «teléfono» en lo visible (enviar lista/respaldo por QR, páginas que abre el otro dispositivo, mensajes de Xtream) y «Borrado» → «Eliminado» en Ajustes › Almacenamiento; «Datos móviles» se queda · `M3uActivity.kt`, `SettingsActivity.kt`, `Qr.kt`
- ARTISTA-1 · 24 nov · un artista o álbum que solo trae «?» cuenta como «sin dato» y ya no sale en «Filtrar por artista» · `MediaScanner.kt` (`cleanArtist`)
- ALMAC-1 · 24 nov · revisado en el código: `toggleTrash()` guarda, avisa y repinta; el desajuste del video era por dos toques seguidos, no un fallo · sin cambios
- EPG-REGION-1 · 24 nov · la guía funciona con «🌐 Toda la región»: se juntan las guías de las cabeceras (`url-tvg`) de todas sus listas (más de 3 se eligen por país como EPG-1b), copia propia de 12 h (`region:<nombre>`); el 📅 abre «Guía de la región» con «Actualizar ya» (la URL propia sigue siendo por lista) · `M3uActivity.kt`, `Epg.kt`, `M3uParser.kt` (`mergeGuides`), `EpgPickTest.kt`
- VOCAB-4 · 24 nov · el aviso de la papelera en Más dice «Lo eliminado…» igual que Ajustes; «Borrar» como botón de acción no se cambió · `MainActivity.kt`
- VOCABULARIO-3 · 24 nov · `VOCABULARIO.md` creado en la raíz del repo con los términos decididos · `VOCABULARIO.md`
- EPG-BUSCAR-1 · 24 nov · con la guía activada, el buscador de Canales también encuentra por el programa que dan ahora (solo mira el programa si el nombre no coincidió); probado: Free-TV › Italia, «noti» → Mediaset Italia («Dentro La Notizia») · `M3uActivity.kt` (`applyFilter`)
- TESTS-2 · 24 nov · 11 pruebas automáticas del respaldo (`BackupTest`: claves respaldadas, `scrub` sin enlaces con usuario y clave, unión de parciales, `describe`, `readLimited`); `keep/scrub/merge*` pasan a `internal` y `build.gradle` suma `org.json:json:20231013` para pruebas; build en verde · `Backup.kt`, `app/build.gradle`, `BackupTest.kt`
- EPG-AGENDA-1 · 24 nov · en el reproductor, ⋯ › «📅 Guía: ahora y lo que sigue» muestra también «Más tarde:» con hasta 6 programas más («HH:MM  título») de las próximas ~24 h · `Epg.kt` (`upcoming`), `VideoPlayerActivity.kt` (`showGuideNow`)
- INSETS-1 · 24 nov · los controles del reproductor (barra de abajo, de arriba y subtítulos) ya no quedan bajo ☰ ○ < en pantalla completa: se mide el tamaño real de las barras con `getInsetsIgnoringVisibility` y se repiten los márgenes al crear la barra · `VideoPlayerActivity.kt`
- CANALES-1 · 24 nov · Canales más compacto: la lista actual va junto a la flecha ← (sin la fila del título), los chips de ánimo van dentro de la lista y se van al bajar, y los botones dicen «📅 Guía» y «⚠ N fallan» · `M3uActivity.kt`
- AVANZADO-1 · 24 nov · Ajustes › Avanzado junta las tres pruebas de IA (audio, Whisper, transcripción) en una fila «Pruebas de IA…» con menú · `SettingsActivity.kt`
- FAB-1 · 24 nov · el botón 🔀 se corre 60 dp del borde derecho para no tapar el ⋮ de las filas de Pistas y Videos con la lista quieta · `activity_main.xml`
- VACIO-1 · 24 nov · pantallas vacías con ayuda: «Nada por continuar» explica y ofrece «📡 Abrir Canales»; Favoritos vacío ofrece «Ver todos» · `MainActivity.kt`, `activity_main.xml`
- BUSCA-1 · 24 nov · al escribir en el buscador sale «Buscar «texto» en Canales ›» y Canales abre con ese texto (respeta el PIN) · `MainActivity.kt`, `M3uActivity.kt`, `activity_main.xml`
- DESHACER-1 · 24 nov · quitar un canal ya no pregunta: aviso con DESHACER (devuelve también la ★) · `M3uActivity.kt`
- CANAL-MINI-1 · 24 nov · barra en Canales con el canal que está en el TV y ⏮ ⏯ ⏭ ☆ ✕ · `M3uActivity.kt`, `MainActivity.kt`, `Zap.kt`
- MAPA-1 · 24 nov · `MAPA.md` con la arquitectura del código

