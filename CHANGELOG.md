# Registro de cambios y guía de continuidad – CASTV (v56)

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
3. **PROHIBIDO marcar ✅ sin confirmación:** Nada se marca ✅ hasta que Ariel confirme que compiló en verde en GitHub Actions y diga *"ya funciona"* tras probarlo. Solo entonces se entrega el changelog actualizado a ✅.
4. **Un cambio a la vez:** No acumular parches grandes. Entorno del usuario: teléfono Android con **Termux** (comandos cortos uno por uno, cambios en `.patch` sin incluir el changelog; el changelog se sube aparte, ver regla 6).
5. **SIEMPRE pasar los comandos de Termux (OBLIGATORIO, sin excepción):** Cada vez que se entregue un parche o cambio, la respuesta DEBE incluir los comandos completos para aplicarlo en Termux, **uno por uno, cada uno en su propio bloque de código copiable** (no en lista de texto ni en una sola línea corrida). Mínimo: `cd ~/castv-nuevo/castv-fresh` → `git apply ~/storage/downloads/<parche>.patch` → `git status --short` → `git add -A` → `git commit -m "..."` → `git push origin main`. Se deben dar aunque ya se hayan dado antes y aunque el cambio sea pequeño. Ariel lo pidió expresamente el 18 nov 2026.
6. **El changelog TAMBIÉN se sube al repo (OBLIGATORIO, por seguridad):** En cada entrega se pasa, además de los comandos del parche, el bloque de comandos de Termux para copiar el changelog nuevo a `~/castv-nuevo/castv-fresh/CHANGELOG.md` y subirlo (`cp` → `git add CHANGELOG.md` → `git commit` → `git push origin main`), cada comando en su propio bloque de código. Primero se confirma dónde está el archivo con `git ls-files | grep -i changelog`. Ariel lo pidió el 18 nov 2026.
7. **VLC solo como inspiración (licencia GPLv2+):** NO copiar código, recursos, iconos, textos largos, nombre ni logo (cono) de VLC. Las ideas y la organización de pantallas se reimplementan con código propio (Material Icons u otros propios). Copiar código haría que CASTV tuviera que ser GPL con fuentes publicadas. Referencia local: `vlc-android-master.zip` (solo consulta).
8. **Continuidad sin re-pasar contexto (OBLIGATORIO, Ariel 18 nov 2026):** en CADA respuesta que cambie algo (diagnóstico, plan, parche, resultado de una prueba), la IA entrega el **changelog actualizado como archivo** con lo que sigue ya anotado (estado 🧪/✅/⏳, qué se probó, qué falta y cuál es el siguiente paso), más los comandos de Termux para subirlo (regla 6). Así Ariel no tiene que pegar capturas ni explicar de nuevo en cada chat. La IA nunca pide "pásame la conversación"; si algo falta, lo busca en este archivo o pide solo el archivo de código concreto.

---

## 📍 Estado Actual (v55)

- **Repo oficial:** `~/castv-nuevo/castv-fresh` (`github.com/arieleljefe/castv-app`, rama `main`).
- ✅ **SEL-1 Acciones en la selección múltiple (18 nov 2026):** aplicada y probada en el teléfono; Ariel confirmó que **va bien**. El menú ⋮ de la barra de selección suma ➕ A la cola, ★/☆ Favoritos y 📃 A una lista; ver "Parche en Curso".
- 🧪 **PAUSA-1 El botón ⏸/▶ no pausa el enlace que va al TV (18 nov 2026):** plan aprobado por Ariel; **parche entregado** (`pausa_enlace_tv.patch`, 1 archivo, 8 líneas nuevas y 2 cambiadas, comprobado con `git apply --check` contra el `MainActivity.kt` real). **Falta que Ariel lo aplique, compile en verde y diga "ya funciona"**; ver "Parche en Curso".
- 🧪 **DOC-1 Limpieza de inconsistencias del changelog (18 nov 2026):** solo texto de este archivo (sin cambios en la app); esperando que Ariel lo revise y confirme; ver "Parche en Curso".
- ✅ **UI-S1 Estilo de subtítulos (18 nov 2026):** aplicada, compiló en verde y Ariel confirmó **"ya funciona"**. Opción 🎨 en el menú 💬 del reproductor: tamaño, negrita, color y fondo.
- ✅ **Etapa 14-A (`ac51ab7`) y 14-C (`PortDialog.kt`, `TvSend.kt`):** Orden de código y mensajes de error claros confirmados.
- ✅ **UI-1 pestaña "Más" (18 nov 2026):** aplicada, compiló y Ariel la probó en el teléfono ("todo bello"). Commit "UI-1 aplicado: pestaña Más".
- ✅ **UI-1b Pantalla de Ajustes en el ⋮ + Más sin opciones repetidas (18 nov 2026):** aplicada (commit `074af2b`), compiló y Ariel confirmó **"ya funciona"** con captura de la pantalla Ajustes; **Versión** muestra CASTV 2.1, igual que el encabezado.
- ⬜ **Idea de Ariel (18 nov 2026): más acciones en la selección múltiple** (añadir varios a la cola, a favoritos, a listas, etc.); ver "Siguiente" punto 6.
- ⬜ **Idea de Ariel (18 nov 2026): canales en vivo que SÍ se vean en el TV** (convertir o retransmitir el video desde la app); ver "Siguiente" punto 5.
- ✅ **Respaldo Exportar/Importar JSON (17 nov 2026):** confirmado por Ariel.
- ✅ **Gestos en el reproductor de video (17 nov 2026):** confirmado por Ariel.
- ✅ **Etapa 13-B.2 Canales más cómodos (17 nov 2026):** confirmado por Ariel.
- ✅ **Historial de enlaces y favoritos sueltos (17 nov 2026):** confirmado por Ariel.
- ✅ **Etapa 13-B.1 Listas M3U (17 nov 2026):** confirmado por Ariel.
- ✅ **Reproducción base:** Archivos locales (mp3/mp4) y enlaces externos mp4 por `/relay` (incluye redirección 302) funcionan en TV Hisense HL1 y en móvil (`VideoPlayerActivity` con subtítulos `.srt/.vtt`, siguiente automático y botón 📺 TV).
- ❌ **HLS (`.m3u8`) al TV:** El TV Hisense no sigue los enlaces de listas HLS; HLS queda solo para el móvil.
- ✅ **Prueba en TV (18 nov 2026, 1.º enlace del conejo, el del Historial):** adelantar y detener funcionaron bien (confirmado por Ariel).
- ⏳ **Prueba en TV (18 nov 2026, 2.º enlace, 1080p 60 fps):** el lag era por el peso del video (el 360p corre fluido, confirmado por Ariel). **Causa de que "al pausar se cierra el video" encontrada: ver PAUSA-1** (el botón ⏸ manda la siguiente canción al TV, no una pausa). El log mostró el relay sano (`RELAY 206/200`, 991017 bytes completos) y ningún `SOAP Pause`. Sigue ⏳ hasta que Ariel pruebe el parche. Falta aún: adelantar con un video largo y liviano (30 fps) y confirmar si al cerrar la app el video sigue en el TV.
- ⬜ **Mejora anotada (18 nov 2026):** el reproductor de la app no muestra progreso ni duración cuando el video va al TV (ver "Siguiente" punto 7).

---

## 🔗 Enlaces de prueba para el TV (Hisense HL1)

**Regla:** al pedir una prueba en el TV, la IA pasa siempre los enlaces completos, uno por bloque de código. Esta tabla se actualiza con lo que Ariel confirme; nada se marca ✅ ni ❌ sin su resultado.

| Estado | Enlace | Notas |
|---|---|---|
| ✅ | El conejo que ya funcionó antes | El enlace exacto no está anotado; está en 🕘 Historial de "Reproducir enlace". **Pendiente: pegarlo aquí.** |
| ❌ | `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4` | El TV no lo soporta (Ariel, 18 nov 2026). |
| ⚠️ | `http://distribution.bbb3d.renderfarming.net/video/mp4/bbb_sunflower_1080p_60fps_normal.mp4` | 1080p a 60 fps. Mucho lag (Ariel, 18 nov 2026); falta confirmar que fue este el enlace probado. El log del relay muestra que lo pidió completo, con los mismos rangos repetidos (el TV no daba abasto). |
| ✅ | `https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4` | Se ve fluido en el TV (Ariel, 18 nov 2026). Dura 10 s, no sirve para probar adelantar. Al pausar el TV cambiaba a una canción (ver PAUSA-1). |
| 🧪 | `http://distribution.bbb3d.renderfarming.net/video/mp4/bbb_sunflower_1080p_30fps_normal.mp4` | Se mandó al TV (log 11:03:28) y el relay respondió bien, pero **Ariel no ha confirmado cómo se vio**. 1080p a 30 fps, unos 10 min; más liviano que la versión de 60 fps. Sirve para probar adelantar y pausar. |

Evitar: versiones AV1 (la mayoría de los TV no las reproducen) y 4K.

---

## 🎯 Plan de Nuevas Ideas y Mejoras (Pros, Contras y Beneficio)

Prioridad actual: funciones probables en el **móvil** y mejoras de experiencia sin depender del TV.

1. **Gestión de Listas M3U (Etapa 13-B) ✅ — Beneficio: Muy Alto (9.5/10)**
   - *Qué:* Cargar listas `.m3u/.m3u8` (enlace o archivo) en segundo plano (`M3uParser.kt`), filtrar por `group-title` y buscar canales.
   - *Pros:* Convierte a CASTV en reproductor multimedia + IPTV completo; código modular.
   - *Contras:* Listas gigantes requieren carga paginada/segundo plano; canales en vivo HLS solo van al móvil.
2. **Historial de Enlaces y Favoritos Sueltos ✅ — Beneficio: Muy Alto (9/10)**
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

### 🧪 PAUSA-1 — El botón ⏸/▶ debe pausar el enlace que está en el TV — parche entregado, pendiente de aplicar y probar

- **Síntoma (Ariel, 18 nov 2026):** con un enlace de video en el TV, al tocar ⏸ el video "se cierra" y suena una canción del teléfono.
- **Causa (verificada leyendo `MainActivity.kt` real, `castv_tv.txt`):** `playLink(..., toTv = true)` manda el enlace por DLNA pero **no fija `playingItem`** (queda `null`). Los dos botones ⏸/▶ (`btnQuick`, mini reproductor, y `btnPlay`, barra expandida) hacen `if (playingItem == null) step(+1) else togglePlayPause()`. Con `playingItem == null`, el toque cuenta como "siguiente" y se envía `/audio/NNNNNN.mp3` al TV, que reemplaza el video. El log lo confirma: 8 veces aparece `ENVIANDO ... /audio/...mp3` a los 4-8 s de un video, y **nunca** `SOAP Pause`.
- **Lo que ya está bien (no tocar):** `togglePlayPause()` sí sabe pausar/reanudar el TV (`TvController.pause()` / `play()`), y el sondeo del TV (`getTransportState()` → `tvPlaying`) corre aunque `playingItem` sea `null`. El relay y los enlaces funcionan.
- **Cambio propuesto (aditivo, un solo archivo: `app/src/main/java/com/castv/app/MainActivity.kt`):**
  1. Campo nuevo `@Volatile private var linkOnTv = false`.
  2. En `playLink`: `linkOnTv = false` al empezar y `linkOnTv = true` solo si `TvController.setUriAndPlay(...)` devolvió `ok` (modo TV).
  3. En `stopCurrentPlayback()` y en `startPlayback()` (donde se fija `playingItem = item`): `linkOnTv = false`.
  4. En los dos botones: la condición pasa a `if (playingItem == null && !linkOnTv) step(+1) else togglePlayPause()`.
  5. **Añadido al entregar el parche (no estaba en el plan aprobado, pequeño y del mismo archivo):** en el botón ⏹ Detener, rama del TV, `linkOnTv = false`. Así, tras Detener, ▶ vuelve a comportarse como hoy (pasa a la siguiente canción) en vez de intentar reanudar algo que ya no existe.
  - **Dónde queda cada línea (verificado en el `MainActivity.kt` real de 2720 líneas):** campo junto a `playingItem` (línea ~179); `btnQuick` (~397) y `btnPlay` (~425); botón Detener (~435); `playLink`: reinicio tras `Store.addLink` (~1304) y `if (ok) linkOnTv = true` justo después de `TvController.setUriAndPlay` (~1333); `startPlayback` tras `playingItem = item` (~1381); `stopCurrentPlayback` tras `playingItem = null` (~2134). `startLocal` llama a `TvController.stop()` pero se llega a él desde `startPlayback`, que ya limpia la marca.
  - No se tocan `Store.kt`, `TvController`, el relay, el manifest ni layouts.
- **Riesgos / límites (v1):** (1) no puedo compilar Kotlin aquí; el build de GitHub Actions es la prueba final; (2) cuando el enlace **termina** en el TV, `linkOnTv` sigue en `true` hasta que se inicie otra cosa, así que un ▶ después del final llamará a `play()` del TV en vez de pasar a la siguiente canción (se afina en otro parche si molesta); (3) no incluye barra de progreso/duración para enlaces en el TV (eso es el punto 7 de "Siguiente"); (4) hay que comprobar en la prueba que el icono ⏸/▶ cambie bien, porque con `playingItem == null` la notificación puede no refrescarlo igual.
- **Cómo probar (con el enlace ✅ de 360p y el de 1080p a 30 fps):** (1) mandar el enlace al TV, tocar ⏸ del mini reproductor: el video **se queda en pausa** (no cambia a una canción) y el icono pasa a ▶; (2) tocar ▶: sigue desde donde estaba; (3) repetir con el ⏸ de la barra expandida; (4) con el de 1080p a 30 fps probar también adelantar, y mirar `/log`: debe salir `SOAP Pause`/`Play` y **ningún** `/audio/...mp3` nuevo; (5) con música local al TV, ⏸/▶ siguen igual que antes; (6) sin nada sonando, ▶ sigue arrancando la siguiente canción; (7) cerrar la app con el video en el TV y anotar si sigue sonando (pendiente de diagnóstico).
- **Aplicar (Termux, uno por uno; el `.patch` va en `~/storage/downloads/pausa_enlace_tv.patch`):** `cd ~/castv-nuevo/castv-fresh` → `git apply ~/storage/downloads/pausa_enlace_tv.patch` → `git status --short` (debe salir **1** archivo) → `git add -A` → `git commit -m "PAUSA-1 pausa del enlace en el TV"` → `git push origin main`.
- **Si falla:** `git revert` del commit "PAUSA-1 pausa del enlace en el TV".

### ✅ SEL-1 — Acciones en la selección múltiple — confirmado por Ariel el 18 nov 2026 ("lo acabo de probar y va bien")

- **Por qué:** idea de Ariel (18 nov 2026). Hoy, al mantener pulsado un archivo, la barra de selección solo ofrece **Todo** y **🗑 Borrar**. Ariel quiere hacer con varios archivos (música o videos) lo mismo que hoy se hace uno por uno desde el ⋮ del archivo.
- **Qué:** la barra de selección mantiene **Todo** y **🗑 Borrar** (siempre visibles) y suma, en el menú ⋮ de esa misma barra, tres acciones:
  - **➕ Añadir a la cola:** agrega los seleccionados al final de la cola, en el orden en que se ven en la lista. Los que ya están en la cola se saltan. Aviso: "➕ 5 añadidas a la cola" (y "· 2 ya estaban" si aplica).
  - **★ Añadir a favoritos / ☆ Quitar de favoritos:** el texto cambia solo. Si **algún** seleccionado aún no es favorito, la opción dice "★ Añadir a favoritos" y marca **solo a los que faltan**. Si **todos** ya son favoritos, dice "☆ Quitar de favoritos" y los quita. Así nunca se invierte a medias. Aviso con la cantidad.
  - **📃 Añadir a lista…:** abre la lista de listas (con "➕ Nueva lista…", igual que el ⋮ de un archivo) y añade todos los seleccionados; los que ya estaban en esa lista se saltan. Aviso: "➕ 5 añadidas a «Mi lista»".
- Al terminar cualquiera de las tres acciones **se sale de la selección** (`actionMode?.finish()`).
- **Archivo que toca (solo uno):** `app/src/main/java/com/castv/app/MainActivity.kt`. No se tocan `Store.kt`, el manifest, layouts ni el respaldo.
- **Código que modifica (todo aditivo, verificado contra el `MainActivity.kt` y `Store.kt` reales que pasó Ariel):**
  - `startSelection()`: en `onCreateActionMode` se agregan 3 opciones con ids 3, 4 y 5 y `SHOW_AS_ACTION_NEVER` (van al menú ⋮ de la barra); los ids 1 (Todo) y 2 (Borrar) no cambian. `onPrepareActionMode` pasa de `false` a poner el texto de la opción 4 (★/☆) según el estado de los seleccionados y devuelve `true`. `onActionItemClicked` agrega los casos 3, 4 y 5.
  - `toggleSelect()` y la acción **Todo**: una llamada `actionMode?.invalidate()` para que el texto ★/☆ se actualice al cambiar la selección.
  - Funciones nuevas: `selectedItems()` (los seleccionados en el orden de `currentList`), `bulkToQueue()`, `bulkFav()`, `bulkToList()`.
  - Reutiliza lo que ya existe: `queue`, `updateQueueBadge()`, `Store.isFav()`, `Store.toggleFav()`, `Store.listNames()`, `Store.inList()`, `Store.addToList()`, `promptNewList()`, `updateNowFav()`, `applyFilters()`, `toast()`, `prettyTitle()`.
  - Favoritos en bloque usa `Store.toggleFav()` solo en los que hay que cambiar y refresca **una sola vez** al final (si la vista es ⭐ Favoritos, `applyFilters()`; si no, `adapter.notifyDataSetChanged()`).
- **Riesgos:** (1) no puedo compilar Kotlin aquí; el parche se comprueba con `git apply --check` y revisando nombres reales, pero el build de GitHub Actions es la prueba final; (2) las 3 acciones nuevas quedan dentro del menú ⋮ de la barra (no como botones sueltos) para que no se aplasten en pantalla vertical; (3) la selección se identifica por `id`, igual que hoy (la lista visible es solo música o solo videos, así que no se mezclan); (4) con 400+ archivos seleccionados a una lista, cada `addToList` guarda en disco (`saveLists`); es aceptable en v1.
- **No incluye (⬜ para después):** **▶ Reproducir selección** y **Compartir**, ni "A continuación" en bloque. Se pueden sumar luego sin rehacer nada.
- **Cómo probar:** (1) mantener pulsado un archivo: la barra muestra Todo, 🗑 Borrar y el ⋮ con 3 opciones nuevas; (2) seleccionar 5 pistas → ⋮ → ➕ A la cola: la insignia "Cola (5)" y el aviso son correctos, y se sale de la selección; (3) repetirlo con las mismas 5: avisa que ya estaban; (4) seleccionar 5 → ★ Añadir a favoritos; luego seleccionar 5 otra vez: dice ☆ Quitar de favoritos y los quita; (5) seleccionar 3 favoritas + 2 no favoritas: dice ★ Añadir y deja las 5 como favoritas; (6) 📃 Añadir a lista… con una lista existente y con una nueva; (7) repetir todo con videos; (8) **Todo** + una acción; (9) salir de la selección con atrás; (10) **🗑 Borrar** sigue igual; (11) probar con Ordenar y un filtro de carpeta activos.
- **Aplicar (Termux, uno por uno):** `cd ~/castv-nuevo/castv-fresh` → `git apply ~/storage/downloads/seleccion_acciones.patch` → `git status --short` (debe salir **1** archivo) → `git add -A` → `git commit -m "SEL-1 acciones en selección múltiple"` → `git push origin main`.
- **Si falla:** `git revert` del commit "SEL-1 acciones en selección múltiple".
- **Resultado:** probado en el teléfono; las acciones nuevas de la barra de selección funcionan bien. Parche: `seleccion_acciones.patch`, commit "SEL-1 acciones en selección múltiple". Pendiente a futuro (⬜): ▶ Reproducir selección, Compartir y "A continuación" en bloque.

### 🧪 DOC-1 — Limpieza de inconsistencias del changelog — pendiente de revisar

- **Qué:** corregir 6 errores de texto de este archivo. **No toca la app**, ni código, ni manifest, ni parche `.patch`; solo cambia `CHANGELOG.md`. No se borra contenido: se corrige o se marca como superado.
- **Cambios exactos:** (1) Regla 3: se quita el "(Regla 7)" sobrante y la sección de VLC ahora cita la **Regla 7** (antes decía Regla 5); (2) "Notas técnicas": el menú ⋮ con `showMainMenu()` se aclara como reemplazado por Ajustes desde UI-1b, indicando dónde viven ahora los ids; (3) "Visión de interfaz": la pestaña Más y **UI-4** quedan ✅ (cubiertas por UI-1 y UI-1b); (4) Plan de ideas: el punto 2 (Historial de enlaces) lleva ✅; (5) Limitaciones de 13-B.1: se indica que dos ya se resolvieron en 13-B.2; (6) Banco de ideas: "Canales en vivo" (20) va antes de "Selección múltiple" (21).
- **Archivo que toca:** solo `CHANGELOG.md` (en la raíz del repo; se confirma con `git ls-files | grep -i changelog`).
- **Riesgos:** ninguno para la app. Único riesgo: que un cambio de texto cambie el sentido de algo; por eso se revisa antes de ✅.
- **Cómo probar:** abrir el archivo y comprobar los 6 puntos: (1) Regla 3 sin "(Regla 7)" y VLC dice "ver Regla 7"; (2) "Notas técnicas" explica que el ⋮ abre Ajustes; (3) UI-4 aparece ✅; (4) Historial de enlaces con ✅ en el plan; (5) 13-B.1 indica "resueltas en 13-B.2"; (6) en el banco de ideas, 20 va antes que 21.
- **Aplicar (Termux, uno por uno):** `cd ~/castv-nuevo/castv-fresh` → `git ls-files | grep -i changelog` → `cp ~/storage/downloads/CHANGELOG_v55.md ~/castv-nuevo/castv-fresh/CHANGELOG.md` → `git add CHANGELOG.md` → `git commit -m "Changelog v51 (limpieza DOC-1)"` → `git push origin main`.
- **Si falla:** `git revert` del commit "Changelog v51 (limpieza DOC-1)".

### ✅ UI-S1 — Estilo de subtítulos (v1) — confirmado por Ariel el 18 nov 2026 ("ya funciona")

- **Qué:** nueva opción **🎨 Estilo del subtítulo…** dentro del menú 💬 del reproductor de video. Abre un menú con: **Tamaño** (Pequeño 14 · Mediano 18 · Grande 24 · Muy grande 30), **Negrita** (sí/no), **Color del texto** (Blanco · Amarillo · Cian · Verde), **Fondo** (Semitransparente · Opaco · Sin fondo) y **Restablecer estilo**. Al cambiar algo se aplica al instante y sale 3 s un subtítulo de ejemplo ("Así se verán los subtítulos"). Con **Sin fondo** se añade una sombra negra automática para que se lea. Los valores por defecto (Mediano, blanco, semitransparente, sin negrita) dejan el aspecto idéntico al de hoy.
- **Archivo que toca (solo uno):** `app/src/main/java/com/castv/app/VideoPlayerActivity.kt`. No se tocan `Store.kt`, el manifest ni layouts.
- **Código que modifica (todo aditivo, verificado contra el archivo real):**
  - Campos nuevos: `previewing` y `hidePreview`.
  - `onCreate`: una llamada `applySubStyle()` justo después de crear `subText`.
  - `updateSub()`: una línea `if (previewing) return` para no pisar el ejemplo.
  - `showSubtitleMenu()`: una opción más (índice 4) que llama a `previewSub()` y `showStyleMenu()`. Las 4 opciones existentes no cambian de índice.
  - Funciones nuevas: `stSize()`, `stColor()`, `stBg()`, `stBold()`, `applySubStyle()`, `previewSub()`, `styleChanged()`, `pickStyle()`, `showStyleMenu()`.
  - `onDestroy()`: `handler.removeCallbacks(hidePreview)`.
- **Dónde se guarda:** en las mismas `SharedPreferences` `castv_subs` que ya usa el reproductor, con claves `style_size`, `style_bold`, `style_color`, `style_bg` (no chocan con las claves `s|...` de cada video). El estilo es **global** (vale para todos los videos).
- **Riesgos:** (1) no pude compilar Kotlin aquí; el parche se comprobó con `git apply --check` y revisando nombres reales, pero el build de GitHub Actions es la prueba final; (2) el menú de estilo se vuelve a abrir tras cada cambio para poder ajustar varias cosas seguidas; en pantalla horizontal puede tapar parte del ejemplo; (3) el estilo **no entra** en el respaldo JSON (`Backup.kt` no lo incluye en esta v1); (4) solo afecta al reproductor del **móvil**, no al TV.
- **Cómo probar:** (1) abrir un video con subtítulos y tocar 💬 → debe aparecer **🎨 Estilo del subtítulo…** y las otras 4 opciones deben seguir funcionando; (2) cambiar Tamaño, Negrita, Color y Fondo: se ve el ejemplo y luego el subtítulo real; (3) probar **Sin fondo** (sombra legible); (4) cerrar y reabrir otro video: el estilo se mantiene; (5) **Restablecer estilo** vuelve al aspecto de antes; (6) girar el teléfono; (7) los gestos y el 📺 TV siguen igual.
- **Aplicar (Termux, uno por uno):** `cd ~/castv-nuevo/castv-fresh` → `git apply ~/storage/downloads/subtitulos_estilo.patch` → `git status --short` (debe salir **1** archivo) → `git add -A` → `git commit -m "UI-S1 estilo de subtítulos"` → `git push origin main`.
- **Si falla:** `git revert` del commit "UI-S1 estilo de subtítulos".
- **Resultado:** probado en el teléfono; el estilo se aplica al instante, se mantiene entre videos y Restablecer vuelve al aspecto original. Parche: `subtitulos_estilo.patch`.
- **Pendiente para futuras versiones (⬜, NO en este parche):** contorno, codificación del texto, autocarga e idioma preferido, estilo por video, incluir el estilo en el respaldo (`Backup.kt` aún no lo guarda).

### ✅ UI-1b — Pantalla de Ajustes en el ⋮ — confirmado por Ariel el 18 nov 2026 ("ya funciona")

- **Por qué:** tras UI-1, el ⋮ y Más muestran las mismas opciones. Ariel quiere que el ⋮ sea para los **ajustes de la app**, como en VLC.
- **Qué:** el ⋮ abre una pantalla **Ajustes** con categorías:
  - **Interfaz:** Tema · Color de acento.
  - **Almacenamiento:** Borrado (papelera / permanente; solo Android 11+, igual que ahora).
  - **Respaldo:** Exportar · Importar.
  - **Acerca de:** Mis redes y contacto · versión de la app.
- **Más** queda solo con herramientas: Reproducir enlace, Listas M3U, Elegir TV, Salida, Ordenar, Duplicados, Mis listas, filtros de artista/álbum, Temporizador, Velocidad y Ecualizador. Se quitan de Más los ids 11, 12, 13, 14 y 17 (las funciones siguen en `MainActivity.kt`).
- **Futuro dentro de Ajustes (⬜, NO en este parche):** idioma de la app y ajustes de notificaciones. La pantalla se arma por categorías para poder sumarlos sin rehacerla.
- **Detalle exacto del parche entregado (verificado contra el `AndroidManifest.xml`, `M3uActivity.kt` y `MainActivity.kt` reales):**
  - `SettingsActivity.kt` (nuevo, construida en código como `M3uActivity`): encabezado "← Ajustes" y categorías Interfaz (Tema, Color de acento) · Almacenamiento (Borrado; solo Android 11+) · Respaldo · Acerca de (Mis redes y contacto, Versión). Aplica tema y color guardados al abrir (igual que `MainActivity`) y ajusta los iconos de la barra de estado a Claro/Oscuro.
  - `AndroidManifest.xml`: registra `.SettingsActivity` (`exported=false`).
  - `MainActivity.kt`: el ⋮ lanza `SettingsActivity` (`reqSettings = 4718`); **Respaldo** y **Mis redes** cierran Ajustes y `onActivityResult` abre `showBackupMenu()` / `showSocials()` (se reutiliza lo que ya funciona); si cambió el color, `onResume` recrea la pantalla (`accentAtStart`); se elimina `showMainMenu()` (ya nadie lo usa); `showMoreSheet()` deja fuera los ids 11, 12, 13, 14 y 17 y ya no tiene los grupos Apariencia, Datos y Acerca. `fillMainMenu` y `runMenuAction` se conservan.
  - El tema se cambia con `AppCompatDelegate` (recrea las pantallas solo, igual que antes); Borrado usa la misma preferencia `trash`.
  - Aplicar (Termux, uno por uno): `cd ~/castv-nuevo/castv-fresh` → `git apply ~/storage/downloads/ui1b_ajustes.patch` → `git status --short` (deben salir 3 archivos) → `git add -A` → `git commit -m "UI-1b pantalla de Ajustes"` → `git push origin main`.
- **Riesgos:** una pantalla nueva mal registrada cierra la app (el manifest se armó con el real); recrear pantallas al cambiar tema/color; Respaldo y Redes cierran Ajustes antes de abrir su diálogo (v1, a propósito, para no duplicar código); si se cambia el color y se toca Respaldo en la misma visita, el diálogo puede perderse al redibujar (se vuelve a tocar).
- **Cómo probar:** (1) el ⋮ abre Ajustes y atrás vuelve; (2) Tema y Color cambian y se ven al volver; (3) Borrado cambia y el aviso sale bien; (4) Exportar e Importar respaldo funcionan; (5) Redes y contacto abre los enlaces; (6) Más ya no repite nada de Ajustes; (7) Claro/Oscuro y girar el teléfono; (8) **Versión** dice lo mismo que el "2.1" del encabezado (si no, se ajusta `build.gradle` o el texto).
- **Si falla:** `git revert` del commit de UI-1b.
- **Resultado:** el ⋮ abre Ajustes con Interfaz, Almacenamiento, Respaldo y Acerca de; Más ya no repite nada. Parche: `ui1b_ajustes.patch`, commit `074af2b`. Pendiente a futuro: idioma y notificaciones dentro de Ajustes.

### ✅ UI-1 — Pestaña "Más" — confirmado por Ariel el 18 nov 2026

- Hecho y probado: barra inferior de 5 botones (Vídeo · Audio · Carpetas · Cola · **Más**); Más abre una hoja (`BottomSheetDialog`) con las opciones agrupadas. Archivos: `activity_main.xml` (`navMore`), `res/drawable/ic_nav_more.xml` (nuevo), `MainActivity.kt` (`fillMainMenu`, `runMenuAction`, `showMoreSheet`). Parche: `ui1_mas.patch`.
- **Problema detectado por Ariel:** el ⋮ y Más muestran las mismas opciones. Corregido en UI-1b (✅).

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
- **Limitaciones conocidas (v1):** al tocar un canal se volvía a la pantalla principal y no había favoritos de canales (**ambas resueltas en 13-B.2**); siguen vigentes: sin logos y solo http/https.

### ⏭️ Siguiente (por elegir uno; un cambio a la vez)

0. 🧪 **PAUSA-1: pausar/reanudar el enlace que está en el TV** (parche `pausa_enlace_tv.patch` entregado; **siguiente paso: Ariel lo aplica, espera el build verde y prueba con el enlace de 360p y el de 1080p a 30 fps**). Si sale bien: ✅ y se sigue con el punto 3 (detener reproducción local al enviar un canal M3U) o el diagnóstico de canales en vivo (punto 5, paso 0), a elección de Ariel.
1. ✅ **Estilo de subtítulos** (UI-S1, confirmado 18 nov 2026). Falta a futuro ⬜: contorno, codificación, idioma preferido, estilo por video y respaldo del estilo.
2. **Aviso para HLS al TV** (el Hisense no lo sigue): avisar en pantalla o dejarlo solo para el móvil.
3. **Detener reproducción local al enviar un canal desde M3U** (limitación de 13-B.2).
4. **Caché de Gradle en el workflow** (⬜, solo si vuelve a fallar por "429 Too Many Requests"). Necesita `.github/workflows/*.yml`.
5. ⬜ **Canales en vivo que funcionen de verdad en el TV** (idea de Ariel, 18 nov 2026; a pulir en el futuro; **sin parche ni plan aprobado todavía**). Hoy el diálogo de un canal dice "📺 Al TV (HLS: puede no funcionar)": el Hisense no sigue muchos canales HLS (`.m3u8`). Ariel quiere que la app los convierta o los prepare para que se reproduzcan bien en la TV. Este punto reemplaza al punto 2 (solo avisar) si se logra.
   - **Paso 0, diagnosticar antes de construir:** descubrir por qué falla el Hisense con HLS (¿no acepta `.m3u8`?, ¿códec de video/audio?, ¿tipo de contenido?, ¿segmentos cifrados?). Se usa el log de `/log` y un canal de prueba. Sin esto no se sabe qué arreglar. No está comprobado que el Hisense llegue a reproducirlo con ninguna de las opciones de abajo.
   - **Opción A, retransmitir sin convertir (la más ligera, probar primero):** el teléfono baja la lista `.m3u8`, junta los segmentos (normalmente MPEG-TS) y los sirve como una sola transmisión continua por el servidor `/relay` que ya existe, para que el TV la vea como un video normal. *Pros:* sin librerías nuevas, sin cambiar licencia, poco gasto de batería. *Contras:* solo sirve si el TV acepta el códec original (H.264/AAC) y el formato TS en vivo; no sirve con canales cifrados o en otros formatos de segmento; el teléfono debe quedarse encendido, en la misma Wi-Fi y con la app abierta.
   - **Opción B, convertir de verdad (transcodificar):** el teléfono recodifica el video a un formato que el TV acepte, con una librería tipo FFmpeg. *Pros:* funciona con más canales. *Contras:* gasta mucha batería y calor, puede ir con retraso, aumenta mucho el tamaño de la app y su **licencia (GPL/LGPL) podría obligar a cambiar la de CASTV** (ver regla 7); por eso va al final y solo si la Opción A no alcanza.
   - **Siempre:** si un canal no se puede preparar, se avisa con un mensaje claro (como en 14-B) y se ofrece "📱 En el móvil".
   - **Cómo probar (cuando exista):** un canal HLS en abierto de la lista Free-TV al TV; que arranque, que no se corte en 10 minutos y que "Detener" funcione.
   - **Para empezar se necesitará:** `TvController`/`TvSend.kt`, el servidor `/relay` de `MainActivity.kt` y `M3uActivity.kt` (se pedirán antes de entregar nada).
6. ✅ **Selección múltiple con más acciones** (idea de Ariel, 18 nov 2026; **hecha como SEL-1 ✅, confirmada el 18 nov 2026**; falta a futuro ⬜: ▶ Reproducir selección y Compartir). Hoy, al mantener pulsado un archivo, la barra de selección solo ofrece **Todo** y **🗑 Borrar**. Ariel quiere hacer con varios archivos a la vez lo mismo que hoy se hace uno por uno desde el ⋮ del archivo, tanto con música como con videos.
   - **Acciones propuestas** (se eligen con Ariel antes de construir): **➕ A la cola**, **★ Favoritos** (añadir o quitar), **📃 A una lista** y, si se quiere, **▶ Reproducir selección** o **Compartir** (el "etc." de Ariel).
   - **Cómo se vería (previsto):** la barra de selección mantiene "Todo" y "🗑 Borrar" y suma las acciones nuevas (las que no quepan van en un menú ⋮ de la misma barra). Al terminar una acción se sale de la selección y sale un aviso con la cantidad ("5 añadidas a la cola").
   - **Cosas a cuidar:** en el ⋮ de un archivo, Favoritos *alterna* (añade o quita); con varios se debe **añadir solo a los que aún no son favoritos** y ofrecer "Quitar" cuando todos ya lo son, para no invertir a medias; no duplicar pistas que ya están en la cola o en la lista; la selección sigue funcionando con Ordenar, Duplicados y filtros activos.
   - **Riesgos:** toca la barra de selección y el adaptador de `MainActivity.kt`; el cambio es aditivo y reutiliza las funciones que ya existen para un solo archivo (cola, favoritos, listas).
   - **Cómo probar (cuando exista):** seleccionar 5 pistas y mandarlas a la cola, a favoritos y a una lista; hacerlo igual con videos; "Todo" + acción; salir de la selección con atrás; ver que la insignia de Cola muestra el total correcto.
   - **Para empezar se necesitará:** `MainActivity.kt` actual (barra de selección y `showItemMenu`) y, si hace falta, `Store.kt` (se pedirán antes de entregar nada).
7. ⬜ **Progreso y duración en el reproductor cuando el video va al TV** (observación de Ariel, 18 nov 2026; **sin parche ni plan aprobado todavía**). Hoy no se ve la barra de progreso ni lo que dura el video. Falta saber si el TV informa su posición o si la app tendrá que calcularla. Se decide después del diagnóstico de pausa/adelantar.
   - **Para empezar se necesitará:** `TvSend.kt`, el servidor `/relay` de `MainActivity.kt` y `VideoPlayerActivity.kt` (se pedirán antes de entregar nada).
- Se pedirán los archivos actuales necesarios antes de entregar nada.

**Cómo pasar los archivos de interfaz** (Termux, un solo comando; luego adjuntar `castv_interfaz.txt` desde Descargas):
```
cd ~/castv-nuevo/castv-fresh && { for f in app/src/main/java/com/castv/app/MainActivity.kt app/src/main/res/layout/activity_main.xml app/src/main/res/layout/item_file.xml app/src/main/res/values/*.xml; do echo "=== $f ==="; cat "$f"; echo; done; echo "=== LISTA DE RECURSOS ==="; ls app/src/main/res/drawable* app/src/main/res/layout; } > ~/storage/downloads/castv_interfaz.txt && ls -l ~/storage/downloads/castv_interfaz.txt
```

---

## 🎨 Visión de interfaz: parecida a VLC, sin plagio (⬜ por hacer, por fases)

Meta de Ariel: que CASTV tenga una interfaz con la **misma comodidad y organización que VLC**, pero con **identidad propia** (código, iconos, colores y nombres propios; ver Regla 7). Estructura observada en el zip de VLC, para tomar como guía:
- **Navegación inferior de 5 pestañas:** Vídeo · Audio · Explorar · Listas · Más.
- **Reproductor de video:** barra inferior con tiempo, barra de progreso y duración; botones anterior / retroceder / play / adelantar / siguiente; botones de tamaño de pantalla, rotación y opciones; **bloqueo con "deslizar para desbloquear"**; avisos en pantalla de brillo, volumen y salto; marcadores A-B y de capítulos sobre la barra.
- **Reproductor de audio:** mini reproductor abajo que se expande a pantalla completa.
- **Pestaña "Más":** historial, ajustes, acerca de. ✅ Hecho en UI-1 (hoja Más) y UI-1b (el ⋮ abre Ajustes); el historial de enlaces vive en "Reproducir enlace".

**Corrección (18 nov 2026):** CASTV **ya tiene** barra inferior (Vídeo · Audio · Carpetas · Cola), pestañas superiores (PISTAS/VIDEOS · FAVORITOS · CONTINUAR) y mini reproductor (`player_bar`). Por eso UI-1 no crea la navegación: la acerca a la de VLC añadiendo "Más".

Fases propuestas (una por parche, cada una con su 🧪 antes del código):
- ✅ **UI-1:** pestaña **Más** (hoja con opciones en grupos), confirmada el 18 nov 2026. Opcional después: cambiar Cola por una pestaña Listas.
- ✅ **UI-1b:** el ⋮ es la pantalla **Ajustes** (tema, color, borrado, respaldo, acerca de), confirmada el 18 nov 2026. Falta a futuro: idioma y notificaciones.
- ⬜ **UI-2:** HUD del reproductor de video (barra propia con anterior/retroceder/play/adelantar/siguiente, tamaño de pantalla, rotación, 🔒 bloqueo).
- ⬜ **UI-3:** mini reproductor expandible para audio y video.
- ✅ **UI-4:** cubierto por UI-1 y UI-1b (pestaña Más y pantalla Ajustes con respaldo y acerca de); no queda trabajo aparte.
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

**Canales en vivo**
20. ⬜ **Canales HLS que se vean en el TV** (retransmitir o convertir desde la app): detalle y opciones en "Siguiente" punto 5.

**Selección múltiple**
21. ✅ **Acciones para varios archivos a la vez** (cola, favoritos, listas, etc.), además de Borrar: detalle en "Siguiente" punto 6.

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
- Acciones del menú (ids en `fillMainMenu`/`runMenuAction`; **desde UI-1b el ⋮ ya no las muestra: abre `SettingsActivity`, y `showMainMenu()` fue eliminado**; la hoja Más, `showMoreSheet()`, muestra todas salvo los ids 11, 12, 13, 14 y 17, que viven en Ajustes): ids 1 Ordenar · 2 Duplicados · 3 Salida · 4 Mis listas · 5 Artista · 6 Álbum · 7 Temporizador · 8 Velocidad · 9 Ecualizador · 10 Elegir TV · 11 Redes y contacto · 12 Tema · 13 Color · 14 Borrado (API 30+) · 15 Reproducir enlace · 16 Listas M3U · 17 Respaldo.
- Respaldo exportado por Ariel (`castv_respaldo_2026-11-17.json`) verificado: formato `castv` v1 válido (`castv_lib`: chfav, fav, links, m3u, m3uLast; `castv`: lastMusic, outPhone). El tema y el color no aparecen porque nunca se cambiaron (valores por defecto).

### Notas de este día

- **PAUSA-1 entregado (18 nov 2026):** Ariel aprobó el plan ("Pausa-1 primero") y pasó `MainActivity_actual.kt`. Se confirmó en el código real que los dos botones usaban `playingItem == null` y que `playLink` nunca fija `playingItem`; el parche agrega la marca `linkOnTv`. Se añadió el reinicio en Detener (punto 5 del plan). Nada se marca ✅ hasta la confirmación de Ariel.

- **Diagnóstico de la pausa (18 nov 2026):** el log de `/log` (puerto **8001**, no 8000; la IP del teléfono era 192.168.100.43) mostró que el relay y el enlace funcionan y que al "pausar" la app manda una canción al TV. La lectura del código lo confirma (ver PAUSA-1). Aprendizaje de proceso: `castv_log.txt` salió vacío (0 bytes) cuando se consultó desde Termux por la IP equivocada; se resolvió abriendo `http://192.168.100.43:8001/log` en el navegador. `castv_tv.txt` contiene el código (`TvSend.kt` y `MainActivity.kt`), no un log.
- **Pedido de Ariel (18 nov 2026):** que cada respuesta traiga el changelog con lo que sigue, para no tener que re-pasar contexto entre chats. Guardado como Regla 8.
- **SEL-1 (18 nov 2026):** Ariel pasó `castv_seleccion.txt` (`MainActivity.kt` y `Store.kt`) y se preparó el parche de selección múltiple, que Ariel probó y confirmó. Orden recomendado a partir de aquí: SEL-1 ✅ → detener reproducción local al enviar un canal M3U (punto 3) → diagnóstico de canales en vivo al TV (punto 5, paso 0) → aviso HLS solo si el diagnóstico no funciona → caché de Gradle solo si vuelve el 429.
- **Pedido de Ariel (18 nov 2026):** además de seleccionar y borrar varios archivos, poder también **añadir varios a la cola** (música o videos) y **añadir varios a favoritos**, "etc.". Guardado como "Siguiente" punto 6.
- **Pedido de Ariel (18 nov 2026, antes de dormir):** que al poner un canal "todo se reproduzca efectivamente en la tv"; la app debería convertir o preparar el video en vivo. Guardado como "Siguiente" punto 5 para pulir más adelante.
- **Observación (18 nov 2026, ⬜ pequeño):** `M3uActivity` no aplica el color de acento guardado (no hace `theme.applyStyle` como `MainActivity`), así que su ★ sale siempre cian. `SettingsActivity` sí lo aplica.
- **Build fallido por 429 (18 nov 2026):** un build falló al descargar el plugin de Kotlin desde Maven Central (HTTP 429, "Too Many Requests"), antes de compilar nada. No era error de código; se resolvió con **Re-run all jobs** en GitHub Actions. Si se repite, esperar 10-15 min y reintentar; el workflow actual no usa caché de Gradle (mejora posible, punto 5 de Siguiente).
- `playLink` aún manda HLS (`.m3u8`) al TV por `/relay`, aunque el Hisense no lo sigue (❌). Pendiente decidir si se avisa en pantalla o se deja solo para el móvil (otro parche, no incluido aquí).

---

## 📋 Historial Resumido de Etapas Confirmadas (✅)

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