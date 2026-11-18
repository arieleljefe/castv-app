# Registro de cambios y guía de continuidad – CASTV (v38)

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

---

## 📍 Estado Actual (v38)

- **Repo oficial:** `~/castv-nuevo/castv-fresh` (`github.com/arieleljefe/castv-app`, rama `main`).
- ✅ **Etapa 14-A (`ac51ab7`) y 14-C (`PortDialog.kt`, `TvSend.kt`):** Orden de código y mensajes de error claros confirmados.
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
4. **Respaldo Exportar/Importar (Backup JSON) — Beneficio: Alto (8/10)**
   - *Qué:* Guardar/restaurar favoritos, listas M3U y ajustes usando `Intent.ACTION_CREATE_DOCUMENT` / `OPEN_DOCUMENT`.
   - *Pros:* Protege configuraciones al reinstalar APKs de prueba; cero librerías extra.
   - *Contras:* Referencias a archivos locales pueden variar si se cambia de teléfono.
5. **Pulido Visual de Interfaz — Beneficio: Medio-Alto (7.5/10)**
   - *Qué:* Desplazamiento (*marquee*) en títulos largos, mini reproductor compacto y coherencia en temas Claro/Oscuro + 4 colores de acento.
   - *Pros:* Evita botones aplastados y aprovecha mejor la pantalla.
   - *Contras:* Requiere cuidar atributos `?attr/castvAccent` en XML para no romper temas.

**Descartado (no implementar):** Chromecast/AirPlay, contenido con DRM, ffmpeg integrado o subtítulos por IA local (engordan el APK y saturan el dispositivo).

---

## 🛠️ Parche en Curso / Próximo Paso

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

1. **Respaldo Exportar/Importar (JSON)** de favoritos (incluye `chfav`), listas M3U, historial y ajustes. `Store.kt` + `MainActivity.kt` (recomendado: protege datos al reinstalar APKs).
2. **Pulido visual** (títulos largos con desplazamiento, mini reproductor más compacto).
3. **Aviso para HLS al TV** (el Hisense no lo sigue): avisar en pantalla o dejarlo solo para el móvil.
4. **Detener reproducción local al enviar un canal desde M3U** (limitación de 13-B.2).
- Se pedirán los archivos actuales necesarios antes de entregar nada.

### Notas de este día
- `playLink` aún manda HLS (`.m3u8`) al TV por `/relay`, aunque el Hisense no lo sigue (❌). Pendiente decidir si se avisa en pantalla o se deja solo para el móvil (otro parche, no incluido aquí).

---

## 📋 Historial Resumido de Etapas Confirmadas (✅)

- **Gestos en el reproductor (17 nov 2026):** brillo, volumen y doble toque ±10 s (`VideoPlayerActivity.kt`).
- **Etapa 13-B.2 (17 nov 2026):** Canales más cómodos y ★ favoritos de canales (`M3uActivity.kt`, `Store.kt`).
- **Etapa 13-B.1 (17 nov 2026):** Listas M3U (`M3uParser.kt`, `M3uActivity.kt`, `Store.kt`).
- **Historial de enlaces + favoritos sueltos (17 nov 2026):** `SavedLink` en `Store.kt`, 🕘 Historial en "Reproducir enlace".
- **Etapa 14-C:** Extracción de `PortDialog.kt` y `TvSend.kt`.
- **Etapa 14-A / 14-B (`ac51ab7`):** `CastvUtils.kt` (texto/red) y mensajes de error claros.
- **Etapa 13-A.1 / 13-A.2 (`98bcf78`, `11d93df`):** Búsqueda de puerto libre (8000-8010) y servidor `/relay` para enlaces externos mp4 al TV.
- **Reproductor y UI:** Salida Teléfono/TV para video, subtítulos `.srt/.vtt`, siguiente automático, botón 📺 TV, selección múltiple con papelera Android, tema Claro/Oscuro, 4 colores de acento, carga en segundo plano y log en `/log`.
- **Etapa 11 (`d7010ac`):** Base estable v2.1.