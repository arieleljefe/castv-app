# Registro de cambios y guía de continuidad – CASTV (v35)

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

## 📍 Estado Actual (v35)

- **Repo oficial:** `~/castv-nuevo/castv-fresh` (`github.com/arieleljefe/castv-app`, rama `main`).
- ✅ **Etapa 14-A (`ac51ab7`) y 14-C (`PortDialog.kt`, `TvSend.kt`):** Orden de código y mensajes de error claros confirmados.
- ✅ **Historial de enlaces y favoritos sueltos (17 nov 2026):** confirmado por Ariel.
- ✅ **Reproducción base:** Archivos locales (mp3/mp4) y enlaces externos mp4 por `/relay` (incluye redirección 302) funcionan en TV Hisense HL1 y en móvil (`VideoPlayerActivity` con subtítulos `.srt/.vtt`, siguiente automático y botón 📺 TV).
- ❌ **HLS (`.m3u8`) al TV:** El TV Hisense no sigue los enlaces de listas HLS; HLS queda solo para el móvil.
- ⏳ **Pendiente en TV:** Diagnóstico de adelantar (*seek*) en enlaces por `/relay`, pausa/detener enlaces y salir con enlace activo.

---

## 🎯 Plan de Nuevas Ideas y Mejoras (Pros, Contras y Beneficio)

Prioridad actual: funciones probables en el **móvil** y mejoras de experiencia sin depender del TV.

1. **Gestión de Listas M3U (Etapa 13-B) — Beneficio: Muy Alto (9.5/10)**
   - *Qué:* Cargar listas `.m3u/.m3u8` (enlace o archivo) en segundo plano (`M3uParser.kt`), filtrar por `group-title` y buscar canales.
   - *Pros:* Convierte a CASTV en reproductor multimedia + IPTV completo; código modular.
   - *Contras:* Listas gigantes requieren carga paginada/segundo plano; canales en vivo HLS solo van al móvil.
2. **Historial de Enlaces y Favoritos Sueltos — Beneficio: Muy Alto (9/10)**
   - *Qué:* Guardar últimos 15-20 enlaces en `Store.kt` y marcar enlaces individuales con estrella.
   - *Pros:* Evita reescribir URLs; reutiliza `Store.kt` sin inflar el APK.
   - *Contras:* Enlaces temporales caducan (requiere botón de limpiar).
3. **Gestos en Reproductor de Video (`VideoPlayerActivity.kt`) — Beneficio: Alto (8.5/10)**
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

### ✅ Historial de enlaces y favoritos sueltos — confirmado por Ariel el 17 nov 2026

- Hecho y probado: historial de los últimos 20 enlaces, botón **🕘 Historial** en "Reproducir enlace", ★ favoritos que no se borran, **📺 Al TV** / **📱 En el móvil** / **Quitar** desde el historial, **Borrar historial**.
- Archivos: `Store.kt` (`SavedLink`, `links()`, `addLink()`, `toggleLinkFav()`, `removeLink()`, `clearRecentLinks()`) y `MainActivity.kt` (`showLinkHistory()`, `showLinkActions()`, `Store.addLink` en `playLink`). Parche: `historial_enlaces.patch`.
- **Si falla:** `git revert` del commit "Historial de enlaces y favoritos sueltos".

### 🧪 Etapa 13-B.1 — Listas M3U (`parser_m3u.patch`) — pendiente de aplicar y probar

- **Estado:** 🧪 entregado el 17 nov 2026; **no se marca ✅ hasta que Ariel confirme que compiló en verde y diga "ya funciona"**.
- **Qué hace:**
  1. Nueva entrada en ⋮: **"Listas M3U (canales)…"**. Abre una pantalla nueva (`M3uActivity`, hecha por código, sin XML).
  2. Arriba: la lista actual (toca para cambiar entre las guardadas), **＋** para añadir una (nombre + enlace) y **🗑** para quitarla. La **primera vez** el diálogo viene pre-rellenado con la lista gratuita **Free-TV** (`https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8`) para probar.
  3. **Buscador** de canales (sin acentos, varias palabras) y botón **Grupo** (filtra por `group-title`, con conteo).
  4. Se descarga en segundo plano (sigue redirecciones, límite 25 MB) y se **guarda una copia** en la caché de la app: si no hay internet, abre la copia y avisa "sin conexión (copia guardada)".
  5. Al tocar un canal: **📺 Al TV** (avisa "HLS: puede no funcionar" si es `.m3u8`) o **📱 En el móvil**. La pantalla se cierra y `MainActivity` lo reproduce con el `playLink` existente (el canal queda también en el 🕘 Historial de enlaces, con el nombre del canal como título).
- **Archivos (3 nuevos pequeños + 3 con cambios aditivos):**
  - `M3uParser.kt` (nuevo): `M3uChannel`, `parse()` (`#EXTINF`, `group-title`, `#EXTGRP`, solo http/https) y `download()`.
  - `M3uActivity.kt` (nuevo): pantalla, buscador, grupos, listas guardadas, devuelve el canal elegido.
  - `Store.kt`: `M3uList`, `m3uLists()`, `addM3uList()`, `removeM3uList()`, `lastM3u` (clave `m3u` / `m3uLast` en el `SharedPreferences` `castv_lib`). No cambia nada anterior.
  - `AndroidManifest.xml`: registra `.M3uActivity` (`exported=false`).
  - `MainActivity.kt` (4 retoques): código de petición `reqM3u = 4714`; entrada de menú id 16; rama `reqM3u ->` en `onActivityResult`; `playLink` recibe un parámetro opcional `titleOverride: String? = null` (las llamadas existentes no cambian).
- **No toca:** servidor, `/relay`, `TvController`, `VideoPlayerActivity`, layouts XML ni el historial de enlaces ya confirmado.
- **Verificado antes de entregar:** el parche aplica limpio (`git apply --check`) sobre `MainActivity.kt`, `Store.kt` y `AndroidManifest.xml` que Ariel adjuntó **más** `historial_enlaces.patch` ya aplicado. Nombres reales usados: `playLink`, `Store.init`, `DebugLog.add`, `R.attr.castvAccent`, `R.color.vlc_bg / vlc_text / vlc_muted`. No se pudo compilar aquí; el build de GitHub es la primera prueba.
- **Probar:**
  1. ⋮ → **Listas M3U (canales)…**: sale el diálogo con "Free-TV (gratis)"; toca **Guardar y abrir**.
  2. Debe decir "Descargando lista…" y luego "N canales". Escribe una palabra en el buscador: se filtra.
  3. Toca **Grupo** y elige uno: se filtra por grupo.
  4. Toca un canal → **📱 En el móvil**: se abre el reproductor (si el enlace es HLS, es el caso que sí funciona en el móvil).
  5. Prueba un canal con **📺 Al TV** (esperado: si es `.m3u8`, el Hisense puede no abrirlo; no es un fallo del parche).
  6. Vuelve a ⋮ → Reproducir enlace → 🕘 Historial: debe estar el canal con su nombre.
  7. Modo avión + reabrir **Listas M3U**: debe abrir la copia guardada con el aviso "sin conexión".
  8. **＋** con otra lista (cualquier enlace `.m3u`), cambia entre listas, y **🗑** para quitarla.
- **Limitaciones conocidas (v1):** al tocar un canal se vuelve a la pantalla principal (hay que reabrir Listas M3U para cambiar de canal; mantener abierta la pantalla sería 13-B.2); sin logos de canales; sin favoritos de canales; solo enlaces http/https (se ignoran rtmp, udp, etc.).
- **Si falla:** `git revert` del commit "Listas M3U (Etapa 13-B.1)".
- **Riesgo:** bajo-medio. Es casi todo código nuevo en archivos nuevos; en `MainActivity` solo cambian 4 puntos y el parámetro nuevo tiene valor por defecto.
- **Pasos en Termux:**
  ```bash
  cd ~/castv-nuevo/castv-fresh && pwd
  git apply --exclude=CHANGELOG.md ~/storage/downloads/parser_m3u.patch
  grep -c "reqM3u" app/src/main/java/com/castv/app/MainActivity.kt
  ls app/src/main/java/com/castv/app/M3uActivity.kt
  cp ~/storage/downloads/CHANGELOG_v35.md CHANGELOG.md
  git add -A && git commit -m "Listas M3U (Etapa 13-B.1)" && git push origin main
  ```
  (el `grep` debe dar 3).

### Siguiente después de este
- **13-B.2:** mantener abierta la pantalla de canales al reproducir y favoritos de canales (según lo que Ariel note al probar).

### Notas de este día
- `playLink` aún manda HLS (`.m3u8`) al TV por `/relay`, aunque el Hisense no lo sigue (❌). Pendiente decidir si se avisa en pantalla o se deja solo para el móvil (otro parche, no incluido aquí).

---

## 📋 Historial Resumido de Etapas Confirmadas (✅)

- **Historial de enlaces + favoritos sueltos (17 nov 2026):** `SavedLink` en `Store.kt`, 🕘 Historial en "Reproducir enlace".
- **Etapa 14-C:** Extracción de `PortDialog.kt` y `TvSend.kt`.
- **Etapa 14-A / 14-B (`ac51ab7`):** `CastvUtils.kt` (texto/red) y mensajes de error claros.
- **Etapa 13-A.1 / 13-A.2 (`98bcf78`, `11d93df`):** Búsqueda de puerto libre (8000-8010) y servidor `/relay` para enlaces externos mp4 al TV.
- **Reproductor y UI:** Salida Teléfono/TV para video, subtítulos `.srt/.vtt`, siguiente automático, botón 📺 TV, selección múltiple con papelera Android, tema Claro/Oscuro, 4 colores de acento, carga en segundo plano y log en `/log`.
- **Etapa 11 (`d7010ac`):** Base estable v2.1.