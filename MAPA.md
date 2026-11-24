# CASTV: MAPA del código (arquitectura) · creado el 24 nov 2026 desde `castv-src.zip` (~18 700 líneas)

Lo lee la IA para no pedir archivos de más. Se actualiza cuando cambia la estructura (no en cada parche).

## Pantallas (Activity) y qué hace cada una
| Archivo | Líneas | Qué es |
|---|---|---|
| `MainActivity.kt` | ~4100 | Pantalla principal: pestañas Video/Audio/Cola/Más, mini reproductor, música del teléfono, TV (DLNA), notificación, hoja «Más», ecualizador, `playLink`. Recibe las órdenes `ACT_*` |
| `VideoPlayerActivity.kt` | ~2000 | Reproductor de video del teléfono (también canales en vivo): gestos, barra propia, PiP, subtítulos, Herramientas, ★ de canal, ⏮ ⏭ por `Zap` |
| `M3uActivity.kt` | ~1700 | Canales: listas M3U/Xtream, regiones, ánimos, favoritos, Continuar, ocultos, ⚠ salud, 🔎 Revisar, guía EPG, QR, barra del canal en el TV. **Se arma todo por código (sin layout XML)** |
| `SettingsActivity.kt` | ~1700 | Ajustes (lista de entradas `Entry(sección, título, subtítulo, acción)`, buscador, subpáginas) |
| `AboutActivity.kt` | ~290 | Acerca de |

## Servidor, TV y red
- `MediaServer.kt` (nanohttpd, puerto 8001, rango 8000–8010): `/relay`, `/live` (HLS→TS con buffer 16 MB y recuperación), `/log`, `/qr/<clave>`, `/send/<clave>`.
- `TvController.kt` (DLNA/UPnP: buscar TV, `setUriAndPlay`, pausa, volumen), `TvSend.kt` (arma el enlace `/relay` o `/live`), `CastvUtils.kt` (`CastvNet.localIpAddress`, textos), `PortDialog.kt`, `CastvService.kt` (aloja el servidor en segundo plano).
- `Qr.kt` (`QrAdd`, `QrSend`, `QrImage`: QR de un solo uso, 5 min, red local).

## Datos
- `Store.kt`: todo lo guardado (preferencias `castv`, `castv_lib`, `castv_subs`, `castv_stats`, `castv_xt`, `castv_bad`). Métodos `@Synchronized`; si un dato no se puede leer se guarda copia en `castv_bad`.
- `Backup.kt` (+ parciales), `AutoBackup.kt` (copia diaria, últimas 3), `ShareProvider.kt`.
- `M3uParser.kt` (`M3uChannel`, `parse`, `url-tvg`), `Catalog.kt` (≈45 listas iptv-org), `Regions.kt`, `Logos.kt` (`bgFor` mide el brillo), `Xtream.kt`, `Epg.kt` (guía XMLTV, copia 12 h), `MediaScanner.kt` (`MediaItem`), `Thumbs.kt`, `Chapters.kt`.
- `PlayerCore.kt`: `MediaPlayer` del teléfono, su estado y «modo sin pantalla» (SERVICIO-1). `Zap.kt`: lista de canales para ⏮ ⏭ y **canal que está en el TV** (`nowTitle`, `nowUrl`).
- `Updater.kt` (actualiza desde Releases de GitHub), `DebugLog.kt` (`/log`, `redact()`), `ParentalPin.kt`.
- IA (cerrada): `WhisperLib.kt`, `WhisperModel.kt`, `SubtitleGen.kt`, `AudioExtractor.kt`; C++ en `app/src/main/cpp/`.

## Cómo se hablan las pantallas (broadcasts internos, `setPackage(packageName)`)
Definidos en `MainActivity` (companion) y registrados en su `notifReceiver`:
`ACT_PREV`, `ACT_TOGGLE`, `ACT_NEXT` (⏮ ⏯ ⏭; con canal en el TV cambian de canal vía `Zap`), `ACT_STOP_LOCAL` (detener lo del teléfono al mandar algo al TV), `ACT_FAV` (★ del canal que está en el TV), `ACT_CHANNEL_ON_TV` (extras `title`, `live`, `url`: un canal acaba de empezar en el TV; lo reciben `MainActivity` y `M3uActivity`).
Toda `ACT_*` nueva debe ir también en el `IntentFilter` de `MainActivity` (regla R1).
Extras de `Intent`: `M3uActivity` acepta `"q"` (texto a buscar al abrir).

## Layouts (`app/src/main/res/layout/`)
`activity_main.xml` (barra superior, buscador, atajo «Buscar en Canales», pestañas, lista, mensaje vacío + botón, 🔀, `player_bar`, navegación), `player_bar.xml` (mini reproductor y panel expandido), `item_file.xml`, `item_queue.xml`, `footer_credits.xml`. Canales, Ajustes y el reproductor de video no usan XML.
Estilos: `values/dimens.xml`, `styles.xml` (`TextAppearance.Castv.*`, tema Material oscuro, acento `?attr/castvAccent` con 4 overlays).

## Pruebas (GitHub Actions, antes de compilar; una prueba rota detiene el build)
`DebugLogRedactTest`, `M3uParserTest`, `BackupTest`, `QrAddTest`, `QrSendTest`, `EpgPickTest`.

## Build
`app/build.gradle` (Material 1.11, appcompat 1.6.1, core-ktx 1.12, nanohttpd 2.3.1, zxing 3.5.3), `.github/workflows/build.yml` (etiqueta `v*` → Release firmado; `versionCode` = n.º de ejecución).
