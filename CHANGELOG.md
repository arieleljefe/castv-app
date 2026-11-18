# Registro de cambios y guía de continuidad – CASTV (v33)

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

## 📍 Estado Actual (v33)

- **Repo oficial:** `~/castv-nuevo/castv-fresh` (`github.com/arieleljefe/castv-app`, rama `main`).
- ✅ **Etapa 14-A (`ac51ab7`) y 14-C (`PortDialog.kt`, `TvSend.kt`):** Orden de código y mensajes de error claros confirmados.
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

### 🧪 Historial de enlaces y favoritos sueltos (`historial_enlaces.patch`) — pendiente de aplicar y probar

- **Estado:** 🧪 entregado el 17 nov 2026; **no se marca ✅ hasta que Ariel confirme que compiló en verde y diga "ya funciona"**.
- **Qué hace:**
  1. Cada enlace que se reproduce desde ⋮ → "Reproducir enlace…" (al TV o al móvil) se guarda en un historial: los **20 más recientes** (los repetidos suben al principio).
  2. En el diálogo "Reproducir enlace", el botón de la izquierda (antes "Cancelar") ahora es **🕘 Historial** (se cancela tocando fuera del diálogo o con atrás).
  3. En el historial se ve cada enlace con `★` si es favorito, título y dominio. Al tocar uno salen: **📺 Al TV**, **📱 En el móvil**, **★ Marcar / ☆ Quitar favorito**, **🗑 Quitar del historial**.
  4. Los **favoritos no se borran nunca** por antigüedad; el botón **Borrar historial** quita solo los que no son favoritos. Los favoritos salen primero.
- **Archivos que toca (solo 2, cambios aditivos):**
  - `Store.kt`: nueva clase `SavedLink(url, title, fav, t)` y funciones `links()`, `addLink()`, `toggleLinkFav()`, `removeLink()`, `clearRecentLinks()`. Guarda en el `SharedPreferences` existente (`castv_lib`, clave `links`, JSON). No cambia nada de lo anterior.
  - `MainActivity.kt`: `askLinkAndPlay()` (botón neutral), dos funciones nuevas `showLinkHistory()` y `showLinkActions()`, y una línea `Store.addLink(url, title)` en `playLink()` tras validar el enlace.
- **No toca:** servidor, `/relay`, `TvController`, reproductor de video, layouts XML ni el manifiesto.
- **Verificado antes de entregar:** el parche aplica limpio (`git apply --check`) sobre los `MainActivity.kt` y `Store.kt` que Ariel adjuntó el 17 nov 2026; nombres reales usados: `playLink`, `linkTitle`, `askLinkAndPlay`, `toast`, `AlertDialog` (androidx), `Uri`. No se pudo compilar aquí; el build de GitHub es la primera prueba.
- **Probar:**
  1. ⋮ → Reproducir enlace… → pega un mp4 y toca "📱 En el móvil". Cierra el reproductor.
  2. Abre otra vez ⋮ → Reproducir enlace… → **🕘 Historial**: debe estar el enlace con su dominio.
  3. Tócalo → **★ Marcar favorito**: vuelve a la lista y sale con ★ arriba.
  4. Reproduce 3 enlaces más: los más nuevos van arriba (después de los favoritos).
  5. **Borrar historial**: quedan solo los favoritos.
  6. Tócalo → **📺 Al TV** y **🗑 Quitar del historial** funcionan.
  7. Cierra la app y ábrela: el historial se conserva.
- **Si falla:** `git revert` del commit "Historial de enlaces y favoritos sueltos".
- **Riesgo:** bajo. Es aditivo y no cambia cómo se reproduce nada.
- **Pasos en Termux:**
  ```bash
  cd ~/castv-nuevo/castv-fresh && pwd
  git apply --exclude=CHANGELOG.md ~/storage/downloads/historial_enlaces.patch
  grep -c "showLinkHistory" app/src/main/java/com/castv/app/MainActivity.kt
  cp ~/storage/downloads/CHANGELOG_v33.md CHANGELOG.md
  git add -A && git commit -m "Historial de enlaces y favoritos sueltos" && git push origin main
  ```
  (el `grep` debe dar un número mayor que 0).

### Siguiente después de este
- **Etapa 13-B.1 Parser M3U** (`M3uParser.kt`), reutilizando el historial de enlaces para guardar las listas.

### Notas de este día
- `playLink` aún manda HLS (`.m3u8`) al TV por `/relay`, aunque el Hisense no lo sigue (❌). Pendiente decidir si se avisa en pantalla o se deja solo para el móvil (otro parche, no incluido aquí).

---

## 📋 Historial Resumido de Etapas Confirmadas (✅)

- **Etapa 14-C:** Extracción de `PortDialog.kt` y `TvSend.kt`.
- **Etapa 14-A / 14-B (`ac51ab7`):** `CastvUtils.kt` (texto/red) y mensajes de error claros.
- **Etapa 13-A.1 / 13-A.2 (`98bcf78`, `11d93df`):** Búsqueda de puerto libre (8000-8010) y servidor `/relay` para enlaces externos mp4 al TV.
- **Reproductor y UI:** Salida Teléfono/TV para video, subtítulos `.srt/.vtt`, siguiente automático, botón 📺 TV, selección múltiple con papelera Android, tema Claro/Oscuro, 4 colores de acento, carga en segundo plano y log en `/log`.
- **Etapa 11 (`d7010ac`):** Base estable v2.1.