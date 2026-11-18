# Registro de cambios y guía de continuidad – CASTV (v41)

**Memoria del proyecto.** Pasar este archivo completo a cualquier asistente para continuar.

**Estados:**
- ✅ **Incorporado:** aplicado, subido a GitHub, compilado en verde y **confirmado por Ariel**.
- 🧪 **Pendiente de aplicar y probar:** parche detallado aquí esperando confirmación de Ariel.
- ⬜ **Por hacer.** | ⏳ **En pausa/diagnóstico.** | ❌ **Falló.**

**Reversión rápida:** `git log --oneline -15` → `git revert HASH` → `git push origin main`.
**Punto seguro base:** `~/castv-nuevo/castv-fresh` (rama `main`, etiqueta `v2.1-estable` / commit `d7010ac`).

---

## 🛡️ REGLAS ESTRICTAS PARA LA IA (OBLIGATORIAS)

1. **Cero rupturas:** Todo cambio debe ser modular, aditivo y verificado contra los nombres reales del código (`ServerPort.port`, `getLocalIpAddress()`, etc.) antes de entregarlo.
2. **Changelog PRIMERO con detalle del parche:** Antes de pasar código o un parche nuevo, se entrega el changelog con estado **🧪** detallando **exactamente qué parche se aplicará, qué código modifica, qué archivos toca y cómo probarlo**.
3. **PROHIBIDO marcar ✅ sin confirmación (Regla 7):** Nada se marca ✅ hasta que Ariel confirme que compiló en verde en GitHub Actions y diga *"ya funciona"* tras probarlo. Solo entonces se entrega el changelog actualizado a ✅.
4. **Un cambio a la vez:** No acumular parches grandes. Entorno del usuario: teléfono Android con **Termux** (comandos cortos uno por uno, cambios en `.patch` excluyendo `CHANGELOG.md`).
5. **VLC solo como inspiración (licencia GPLv2+):** NO copiar código, recursos, iconos, textos largos, nombre ni logo (cono) de VLC. Las ideas y la organización de pantallas se reimplementan con código propio (Material Icons u otros propios). Copiar código haría que CASTV tuviera que ser GPL con fuentes publicadas. Referencia local: `vlc-android-master.zip` (solo consulta).

---

## 📍 Estado Actual (v41)

- **Repo oficial:** `~/castv-nuevo/castv-fresh` (`github.com/arieleljefe/castv-app`, rama `main`).
- ✅ **Etapa 14-A (`ac51ab7`) y 14-C (`PortDialog.kt`, `TvSend.kt`):** Orden de código y mensajes de error claros confirmados.
- 🧪 **UI-1 pestaña "Más" (plan detallado, parche NO entregado):** esperando decisión de Ariel (ver "Siguiente" punto 1).
- ✅ **Respaldo Exportar/Importar JSON (17 nov 2026):** confirmado por Ariel.
- ✅ **Gestos en el reproductor de video (17 nov 2026):** confirmado por Ariel.
- ✅ **Etapa 13-B.2 Canales más cómodos (17 nov 2026):** confirmado por Ariel.
- ✅ **Historial de enlaces y favoritos sueltos (17 nov 2026):** confirmado por Ariel.
- ✅ **Etapa 13-B.1 Listas M3U (17 nov 2026):** confirmado por Ariel.
- ✅ **Reproducción base:** Archivos locales (mp3/mp4) y enlaces externos mp4 por `/relay` (incluye redirección 302) funcionan en TV Hisense HL1 y en móvil (`VideoPlayerActivity` con subtítulos `.srt/.vtt`, siguiente automático y botón 📺 TV).
- ❌ **HLS (`.m3u8`) al TV:** El TV Hisense no sigue los enlaces de listas HLS; HLS queda solo para el móvil.
- ⏳ **Pendiente en TV:** Diagnóstico de adelantar (*seek*) en enlaces por `/relay`, pausa/detener enlaces y salir con enlace activo.

---

## 🎯 Plan de Nuevas Ideas y Mejoras (Pros, Contras y Beneficio)

Prioridad actual: funciones probables en el **móvil** y mejoras de experiencia sin depender del TV.

1. **Gestión de Listas M3U (Etapa 13-B) ✅ — Beneficio: Muy Alto (9.5/10)**
   - *Qué:* Cargar listas `.m3u/.m3u8` (enlace o archivo) en segundo plano (`M3uParser.kt`), filtrar por `group-title` y buscar canales.
   - *Pros:* Convierte a CASTV en reproductor multimedia + IPTV completo; código modular.
   - *Contras:* Listas gigantes requieren carga paginada/segundo plano; canales en vivo HLS solo van al móvil.
2. **Historial de Enlaces y Favoritos Sueltos — Beneficio: Muy Alto (9/10)**
   - *Qué:* Guardar últimos 15-20 enlaces en `Store.kt` y marcar enlaces individuales con estrella.
   - *Pros:* Evita reescribir URLs; reutiliza `Store.kt` sin inflar el APK.
   - *Contras:* Enlaces temporales caducan (requiere botón de limpiar).
3. **Gestos en Reproductor de Video (`VideoPlayerActivity.kt`) ✅ — Beneficio: Alto (8.5/10)**
   - *Qué:* Deslizar derecha (brillo), izquierda (volumen) y doble toque lateral (±10s).
   - *Pros:* Comodidad moderna sin botones físicos; 100% testable en móvil.
   - *Contras:* Riesgo de toques accidentales si no se dejan márgenes seguros en los bordes.
4. **Respaldo Exportar/Importar (Backup JSON) ✅ — Beneficio: Alto (8/10)**
   - *Qué:* Guardar/restaurar favoritos, listas M3U y ajustes usando `Intent.ACTION_CREATE_DOCUMENT` / `OPEN_DOCUMENT`.
   - *Pros:* Protege configuraciones al reinstalar APKs de prueba; cero librerías extra.
   - *Contras:* Referencias a archivos locales pueden variar si se cambia de teléfono.
5. **Pulido Visual de Interfaz — Beneficio: Medio-Alto (7.5/10)**
   - *Qué:* Desplazamiento (*marquee*) en títulos largos, mini reproductor compacto y coherencia en temas Claro/Oscuro + 4 colores de acento.
   - *Pros:* Evita botones aplastados y aprovecha mejor la pantalla.
   - *Contras:* Requiere cuidar atributos `?attr/castvAccent` en XML para no romper temas.

**Descartado (no implementar):** Chromecast/AirPlay y contenido con DRM.
**Tamaño del APK (decisión de Ariel, 17 nov 2026):** ya **no es un impedimento** si la app engorda un poco. Por eso `libvlc`, `ffmpeg` y subtítulos con IA local pasan de "descartado" a **⏳ por evaluar** (se decidirá caso por caso, siempre un cambio a la vez).

---

## 🛠️ Parche en Curso / Próximo Paso

### ✅ Respaldo Exportar/Importar JSON — confirmado por Ariel el 17 nov 2026

- Hecho y probado: ⋮ → **Respaldo (exportar/importar)…** → 📤 Exportar (guarda `castv_respaldo_AAAA-MM-DD.json` donde se elija) y 📥 Importar (pide confirmación, **reemplaza** lo actual, valida el archivo antes de tocar nada y reinicia la pantalla con `recreate()` para aplicar tema/color/orden).
- Incluye: favoritos, listas de reproducción, historial y favoritos de enlaces, listas M3U, favoritos de canales y ajustes. **No incluye:** IP del TV, puntos de reanudación ni estadísticas. Límite de lectura: 5 MB.
- Archivos: `Backup.kt` (nuevo: `build()`, `restore()`, `readLimited()`), `Store.kt` (`reload()`), `MainActivity.kt` (menú id 17, `showBackupMenu()`, `doExport()`, `confirmImport()`, `doImport()`, códigos 4716/4717). Parche: `respaldo_json.patch`. Sin permisos ni cambios en el manifest.
- Nota: si suena algo local en el teléfono, se detiene al importar (por el `recreate()`).
- **Si falla:** `git revert` del commit "Respaldo exportar/importar (v39)".

### ✅ Gestos en el reproductor de video — confirmado por Ariel el 17 nov 2026

- Hecho y probado: deslizar vertical en la mitad **derecha** = brillo, en la mitad **izquierda** = volumen; **doble toque** en el tercio izquierdo/derecho = −10 s/+10 s (el centro no hace nada); cartelito (HUD) con el valor; toque simple muestra/oculta controles; márgenes seguros de 32 dp en los bordes.
- Archivo: `VideoPlayerActivity.kt` (`setupGestures()`, `inEdge()`, `seekBy()`, `showHud()`, `currentBrightness()`; el `VideoView` usa `setOnTouchListener` con un `GestureDetector`, y el toque simple se maneja a mano). Parche: `gestos_video.patch`.
- Notas: el brillo solo afecta a esa ventana (vuelve al normal al salir); el volumen es el del sistema (`STREAM_MUSIC`) y se queda donde se deje; en canales en vivo (sin duración) el doble toque no hace nada.
- **Si falla:** `git revert` del commit "Gestos en el reproductor (v37)".

### ✅ Etapa 13-B.2 — Canales más cómodos — confirmado por Ariel el 17 nov 2026

- Hecho y probado: la pantalla de canales **sigue abierta** al reproducir (📺 Al TV / 📱 En el móvil) y se puede cambiar de canal sin reabrirla; **★ favoritos de canales** (estrella en cada fila, favoritos primero al abrir/filtrar, chip **☆ Favs** para ver solo favoritos); los canales reproducidos quedan en el 🕘 Historial; el botón 📺 del reproductor del móvil manda el canal al TV.
- Archivos: `M3uActivity.kt` (`playChannel()`, `playOnPhone()`, `sendToTv()`, `onActivityResult`, `toggleFav()`, `toggleFavOnly()`, adaptador con ★) y `Store.kt` (`chFavs()`, `toggleChFav()`, clave JSON `chfav`). Parche: `canales_comodos.patch`. `MainActivity.kt` y `VideoPlayerActivity.kt` no se tocaron.
- **Si falla:** `git revert` del commit "Canales más cómodos (13-B.2)".
- **Limitación conocida:** `M3uActivity` no llama a `playLink` de `MainActivity`, así que **no detiene la música/video local** que estuviera sonando en el teléfono antes de enviar un canal (puede solaparse). Se arreglaría en un parche aparte.

### ✅ Historial de enlaces y favoritos sueltos — confirmado por Ariel el 17 nov 2026

- Hecho y probado: historial de los últimos 20 enlaces, botón **🕘 Historial** en "Reproducir enlace", ★ favoritos que no se borran, **📺 Al TV** / **📱 En el móvil** / **Quitar** desde el historial, **Borrar historial**.
- Archivos: `Store.kt` (`SavedLink`, `links()`, `addLink()`, `toggleLinkFav()`, `removeLink()`, `clearRecentLinks()`) y `MainActivity.kt` (`showLinkHistory()`, `showLinkActions()`, `Store.addLink` en `playLink`). Parche: `historial_enlaces.patch`.
- **Si falla:** `git revert` del commit "Historial de enlaces y favoritos sueltos".

### ✅ Etapa 13-B.1 — Listas M3U — confirmado por Ariel el 17 nov 2026

- Hecho y probado: ⋮ → **Listas M3U (canales)…**, lista Free-TV pre-rellenada la primera vez, buscador, filtro por grupo, copia guardada sin internet, ＋ / 🗑 para listas, y reproducir un canal **📺 Al TV** o **📱 En el móvil** (queda en el 🕘 Historial con el nombre del canal).
- Archivos: `M3uParser.kt` (nuevo), `M3uActivity.kt` (nuevo), `Store.kt` (`M3uList`, `m3uLists()`, `addM3uList()`, `removeM3uList()`, `lastM3u`), `AndroidManifest.xml` (`.M3uActivity`), `MainActivity.kt` (`reqM3u`, menú id 16, `playLink(..., titleOverride)`). Parche: `parser_m3u.patch`.
- **Si falla:** `git revert` del commit "Listas M3U (Etapa 13-B.1)".
- **Limitaciones conocidas (v1):** al tocar un canal se vuelve a la pantalla principal; sin logos ni favoritos de canales; solo http/https.

### ⏭️ Siguiente (por elegir uno; un cambio a la vez)

1. **🧪 UI-1 — Pestaña "Más" en la barra inferior** (plan detallado; **parche aún no entregado**; falta que Ariel diga "va").
   - **Qué:** barra inferior de 4 a **5 botones: Vídeo · Audio · Carpetas · Cola · Más**. "Más" abre una hoja (`BottomSheetDialog`) que sube desde abajo con las opciones del menú ⋮ ordenadas en grupos. Cada fila hace lo mismo que su opción del ⋮, y el **⋮ se queda como está** (no se quita nada).
   - **Grupos propuestos (ids del menú actual):** Reproducir y canales (15 enlace, 16 listas M3U, 10 elegir TV, 3 salida) · Biblioteca (1 ordenar, 2 duplicados, 4 mis listas, 5 artista, 6 álbum) · Reproductor (7 temporizador, 8 velocidad, 9 ecualizador) · Apariencia (12 tema, 13 color) · Datos (17 respaldo, 14 borrado) · Acerca (11 redes y contacto). Cualquier id nuevo cae en un grupo "Más".
   - **Cómo:** `activity_main.xml` (nuevo `navMore`, mismo estilo que los otros 4), `res/drawable/ic_nav_more.xml` (nuevo, icono propio de 4 puntos) y `MainActivity.kt`: las opciones del menú se mueven a `fillMainMenu(menu)` y las acciones a `runMenuAction(id)` (los usan el ⋮ y la hoja), más `showMoreSheet()` que lee las opciones de un `PopupMenu` auxiliar sin mostrarlo para no duplicar textos. Respeta Claro/Oscuro y los 4 colores de acento. No toca reproductor, `Store`, manifest ni permisos. Parche: `ui1_mas.patch`.
   - **Cómo probar:** (1) 5 botones sin recortes y la insignia de Cola sigue funcionando; (2) Más abre la hoja con scroll; (3) probar varias filas (enlace, M3U, tema, color, respaldo); (4) el ⋮ sigue igual; (5) Claro/Oscuro y otro color de acento; (6) girar el teléfono.
   - **Si falla:** `git revert` del commit "UI-1 pestaña Más (v40)".
   - **Riesgo:** al mover el menú a funciones nuevas, un error afectaría al ⋮ y a Más a la vez; por eso el parche cambia solo eso.
   - **Decisión pendiente de Ariel:** mantener **Cola** en la barra y dejar "Mis listas" dentro de Más (propuesta, no se pierde ningún acceso) **o** cambiar Cola por una pestaña **Listas** como VLC (parche aparte, después).
   - **Para retomar:** pasar `MainActivity.kt` actual y `activity_main.xml` (comando de abajo, "Cómo pasar los archivos de interfaz").
2. **Estilo de subtítulos** (tamaño, negrita, color, fondo, sombra, contorno). Solo `VideoPlayerActivity.kt`.
3. **Aviso para HLS al TV** (el Hisense no lo sigue): avisar en pantalla o dejarlo solo para el móvil.
4. **Detener reproducción local al enviar un canal desde M3U** (limitación de 13-B.2).
5. **Caché de Gradle en el workflow** (⬜, solo si vuelve a fallar por "429 Too Many Requests"). Necesita `.github/workflows/*.yml`.
- Se pedirán los archivos actuales necesarios antes de entregar nada.

**Cómo pasar los archivos de interfaz** (Termux, un solo comando; luego adjuntar `castv_interfaz.txt` desde Descargas):
```
cd ~/castv-nuevo/castv-fresh && { for f in app/src/main/java/com/castv/app/MainActivity.kt app/src/main/res/layout/activity_main.xml app/src/main/res/layout/item_file.xml app/src/main/res/values/*.xml; do echo "=== $f ==="; cat "$f"; echo; done; echo "=== LISTA DE RECURSOS ==="; ls app/src/main/res/drawable* app/src/main/res/layout; } > ~/storage/downloads/castv_interfaz.txt && ls -l ~/storage/downloads/castv_interfaz.txt
```

---

## 🎨 Visión de interfaz: parecida a VLC, sin plagio (⬜ por hacer, por fases)

Meta de Ariel: que CASTV tenga una interfaz con la **misma comodidad y organización que VLC**, pero con **identidad propia** (código, iconos, colores y nombres propios; ver Regla 5). Estructura observada en el zip de VLC, para tomar como guía:
- **Navegación inferior de 5 pestañas:** Vídeo · Audio · Explorar · Listas · Más.
- **Reproductor de video:** barra inferior con tiempo, barra de progreso y duración; botones anterior / retroceder / play / adelantar / siguiente; botones de tamaño de pantalla, rotación y opciones; **bloqueo con "deslizar para desbloquear"**; avisos en pantalla de brillo, volumen y salto; marcadores A-B y de capítulos sobre la barra.
- **Reproductor de audio:** mini reproductor abajo que se expande a pantalla completa.
- **Pestaña "Más":** historial, ajustes, acerca de (sustituiría parte del menú ⋮ actual).

**Corrección (18 nov 2026):** CASTV **ya tiene** barra inferior (Vídeo · Audio · Carpetas · Cola), pestañas superiores (PISTAS/VIDEOS · FAVORITOS · CONTINUAR) y mini reproductor (`player_bar`). Por eso UI-1 no crea la navegación: la acerca a la de VLC añadiendo "Más".

Fases propuestas (una por parche, cada una con su 🧪 antes del código):
- 🧪 **UI-1:** pestaña **Más** (hoja con las opciones del ⋮ en grupos); detalle completo en "Siguiente" punto 1. Opcional después: cambiar Cola por una pestaña Listas.
- ⬜ **UI-2:** HUD del reproductor de video (barra propia con anterior/retroceder/play/adelantar/siguiente, tamaño de pantalla, rotación, 🔒 bloqueo).
- ⬜ **UI-3:** mini reproductor expandible para audio y video.
- ⬜ **UI-4:** pestaña "Más" (historial, ajustes, respaldo, acerca de).
- Mantener los 4 colores de acento y tema Claro/Oscuro propios (no usar el naranja/cono de VLC como identidad).

---

## 💡 Banco de ideas futuras (inspiradas en VLC; todo ⬜ por hacer, sin copiar código)

**Reproductor de video (`VideoPlayerActivity.kt`)**
1. ⬜ **Estilo de subtítulos:** tamaño, negrita, color, fondo, sombra, contorno, codificación del texto, autocarga y idioma preferido (encaja con `subText`).
2. ⬜ **Deslizar horizontal** para adelantar/retroceder.
3. ⬜ **Mantener pulsado** para velocidad rápida.
4. ⬜ **Doble toque al centro** para pausar/reanudar.
5. ⬜ **Bloqueo de controles** contra toques accidentales.
6. ⬜ **Temporizador de apagado** en el reproductor de video.
7. ⬜ **Repetición A-B, marcadores, saltar a un tiempo y capítulos.**
8. ⬜ **Guardar el brillo** y **confirmar antes de reanudar**.
9. ⬜ **Zoom con pellizco y modos de tamaño** de pantalla.
10. ⬜ **Estadísticas de video** (útil para diagnóstico).
11. ⬜ **Picture-in-picture.**
12. ⏳ **Pistas de audio/subtítulos embebidos:** solo con libvlc.

**App en general**
13. ⬜ **"Qué hay de nuevo"** al actualizar y **consejos de gestos** la primera vez.
14. ⬜ **Ecualizador:** presets guardados por el usuario.
15. ⬜ **Control remoto web** (aprovechar el servidor del puerto 8000): controlar/subir archivos desde un navegador.
16. ⬜ **Widgets, atajos, Android Auto y canales de Android TV.**
17. ⬜ **Control parental con PIN.**
18. ⬜ **Manejo del foco de audio** (pausa en llamadas o al abrir otra app de sonido).
19. ⬜ **Vocabulario en español** de referencia (`values-es` de VLC) para mantener textos coherentes, escritos con palabras propias.

**⏳ Por evaluar (el tamaño ya no frena, pero cambia la arquitectura)**
- **libvlc** (LGPL): reproduce casi todo (mkv, avi, HLS, pistas embebidas) y podría resolver mejor HLS/códecs en el móvil. Implica rehacer la reproducción; usarlo exige cumplir su licencia LGPL (aviso y enlace al código de la biblioteca).
- **Explorador de red (SMB/FTP/UPnP):** depende de libvlc o de bibliotecas extra.
- **Renderers/cast vía libvlc:** relacionado con Chromecast, que sigue descartado.

**No hay nada reutilizable de VLC para M3U** (lo resuelve libvlc); se mantiene `M3uParser.kt`.

---

### Notas técnicas de la interfaz actual (para continuar sin volver a pedir todo)
- La interfaz principal está en **`res/layout/activity_main.xml`** (LinearLayout vertical): barra superior (▲ CASTV 2.1, `btnSearch`, `btnOutput`, `btnMenu`) → `searchBar` → pestañas `tabAll`/`tabFavs`/`tabResume` → `tvFilter` → `tvStatus` → `listFiles` + `tvEmpty` + `fabShuffle` → `<include player_bar>` → barra inferior con `navVideo`, `navAudio`, `navFolders`, `navQueue` (TextView con `drawableTop` y `drawableTint`, 11sp, peso 1).
- Iconos de navegación: `ic_nav_video`, `ic_nav_audio`, `ic_nav_folder`, `ic_nav_queue`. Fondo de la barra: `bg_bottom_nav`.
- Colores/temas: `?attr/castvAccent`, `castvAccentSoft`, `castvPlayingBg` (4 overlays: Cyan, Green, Orange, Purple); base `Theme.MaterialComponents.DayNight.NoActionBar` (así que la librería Material está disponible); colores `vlc_bg`, `vlc_surface`, `vlc_text`, `vlc_muted`; tema oscuro en `values-night/colors.xml`.
- `MainActivity.kt`: `updateTabs()` pinta activo/inactivo; `updateQueueBadge()` muestra "Cola (N)"; handlers: navVideo→`showMovies()`, navAudio→`showMusic()`, navFolders→`pickFolder()`, navQueue→`showQueue()`.
- Menú ⋮ (`showMainMenu()`, `PopupMenu`): ids 1 Ordenar · 2 Duplicados · 3 Salida · 4 Mis listas · 5 Artista · 6 Álbum · 7 Temporizador · 8 Velocidad · 9 Ecualizador · 10 Elegir TV · 11 Redes y contacto · 12 Tema · 13 Color · 14 Borrado (API 30+) · 15 Reproducir enlace · 16 Listas M3U · 17 Respaldo.
- Respaldo exportado por Ariel (`castv_respaldo_2026-11-17.json`) verificado: formato `castv` v1 válido (`castv_lib`: chfav, fav, links, m3u, m3uLast; `castv`: lastMusic, outPhone). El tema y el color no aparecen porque nunca se cambiaron (valores por defecto).

### Notas de este día
- **Build fallido por 429 (18 nov 2026):** un build falló al descargar el plugin de Kotlin desde Maven Central (HTTP 429, "Too Many Requests"), antes de compilar nada. No era error de código; se resolvió con **Re-run all jobs** en GitHub Actions. Si se repite, esperar 10-15 min y reintentar; el workflow actual no usa caché de Gradle (mejora posible, punto 5 de Siguiente).
- `playLink` aún manda HLS (`.m3u8`) al TV por `/relay`, aunque el Hisense no lo sigue (❌). Pendiente decidir si se avisa en pantalla o se deja solo para el móvil (otro parche, no incluido aquí).

---

## 📋 Historial Resumido de Etapas Confirmadas (✅)

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