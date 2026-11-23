# CASTV: HISTORIAL (solo lo ✅ confirmado por Ariel) · una línea por ítem · v285

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
