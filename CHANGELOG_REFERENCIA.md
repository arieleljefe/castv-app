# CASTV – REFERENCIA (v250)

Datos de consulta que casi no cambian: enlaces de prueba del TV, notas técnicas de la interfaz y catálogo M3U. Se pide solo cuando hace falta.

---

## 🔗 Enlaces de prueba para el TV (Hisense HL1)

**Regla:** al pedir una prueba en el TV, la IA pasa siempre los enlaces completos, uno por bloque de código. Esta tabla se actualiza con lo que Ariel confirme; nada se marca ✅ ni ❌ sin su resultado.

| Estado | Enlace | Notas |
|---|---|---|
| ✅ | El conejo que ya funcionó antes | El enlace exacto no está anotado; está en 🕘 Historial de "Reproducir enlace". **Pendiente: pegarlo aquí.** |
| ❌ | `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4` | El TV no lo soporta (Ariel, 18 nov 2026). |
| ⚠️ | `http://distribution.bbb3d.renderfarming.net/video/mp4/bbb_sunflower_1080p_60fps_normal.mp4` | 1080p a 60 fps. Mucho lag (Ariel, 18 nov 2026); falta confirmar que fue este el enlace probado. El log del relay muestra que lo pidió completo, con los mismos rangos repetidos (el TV no daba abasto). |
| ✅ | `https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4` | Se ve fluido en el TV (Ariel, 18 nov 2026). Dura 10 s, no sirve para probar adelantar. Con PAUSA-1 la pausa funciona bien (Ariel, 18 nov 2026). |
| ⚠️ | `http://distribution.bbb3d.renderfarming.net/video/mp4/bbb_sunflower_1080p_30fps_normal.mp4` | **Sigue con lag en el TV** (Ariel, 18 nov 2026, confirmado otra vez a las 12:09). 1080p a 30 fps, unos 10 min. Pausa funciona. Con PROG-1 muestra el tiempo pero no la duración. No sirve para probar adelantar por el lag. |
| ✅ | `https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4` | Probado (Ariel, 18 nov 2026): el log da `DURACION enlace: 10 s`. Antes decía: Sin probar. 720p, 10 s. Sirve para ver si el TV informa la duración (debería salir 0:10). Para probar adelantar aún falta un enlace largo y liviano. |

Evitar: versiones AV1 (la mayoría de los TV no las reproducen) y 4K.

---


---

### Notas técnicas de la interfaz actual (para continuar sin volver a pedir todo)
- La interfaz principal está en **`res/layout/activity_main.xml`** (LinearLayout vertical): barra superior (▲ CASTV 2.1, `btnSearch`, `btnOutput`, `btnMenu`) → `searchBar` → pestañas `tabAll`/`tabFavs`/`tabResume` → `tvFilter` → `tvStatus` → `listFiles` + `tvEmpty` + `fabShuffle` → `<include player_bar>` → barra inferior con `navVideo`, `navAudio`, `navFolders`, `navQueue` (TextView con `drawableTop` y `drawableTint`, 11sp, peso 1).
- Iconos de navegación: `ic_nav_video`, `ic_nav_audio`, `ic_nav_folder`, `ic_nav_queue`. Fondo de la barra: `bg_bottom_nav`.
- Colores/temas: `?attr/castvAccent`, `castvAccentSoft`, `castvPlayingBg` (4 overlays: Cyan, Green, Orange, Purple); base `Theme.MaterialComponents.DayNight.NoActionBar` (así que la librería Material está disponible); colores `vlc_bg`, `vlc_surface`, `vlc_text`, `vlc_muted`; tema oscuro en `values-night/colors.xml`.
- `MainActivity.kt`: `updateTabs()` pinta activo/inactivo; `updateQueueBadge()` muestra "Cola (N)"; handlers: navVideo→`showMovies()`, navAudio→`showMusic()`, navFolders→`pickFolder()`, navQueue→`showQueue()`.
- Acciones del menú (ids en `fillMainMenu`/`runMenuAction`; **desde UI-1b el ⋮ ya no las muestra: abre `SettingsActivity`, y `showMainMenu()` fue eliminado**; la hoja Más, `showMoreSheet()`, muestra todas salvo los ids 11, 12, 13, 14 y 17, que viven en Ajustes): ids 1 Ordenar · 2 Duplicados · 3 Salida · 4 Mis listas · 5 Artista · 6 Álbum · 7 Temporizador · 8 Velocidad · 9 Ecualizador · 10 Elegir TV · 11 Redes y contacto · 12 Tema · 13 Color · 14 Borrado (API 30+) · 15 Reproducir enlace · 16 Listas M3U · 17 Respaldo.
- Respaldo exportado por Ariel (`castv_respaldo_2026-11-17.json`) verificado: formato `castv` v1 válido (`castv_lib`: chfav, fav, links, m3u, m3uLast; `castv`: lastMusic, outPhone). El tema y el color no aparecen porque nunca se cambiaron (valores por defecto).


---

## 📦 Anexo: Catálogo Maestro v2 de listas M3U (texto pegado por Ariel el 18 nov 2026)

Todas las URL salen de `https://iptv-org.github.io/iptv/`. Los códigos son de dos letras en minúsculas. Total: 48 listas, sin repetidas.

- **Índices maestros (4; propuesta: NO preinstalar, repiten todo):** `index.m3u` (todo el mundo), `index.category.m3u`, `index.language.m3u`, `index.country.m3u`.
- **Por país:** `countries/<código>.m3u`
  - **América Latina (20):** do (República Dominicana), ar, bo, br (Brasil, portugués), cl, co, cr, cu, ec, sv, gt, hn, mx, ni, pa, py, pe, pr, uy, ve.
  - **Europa (7):** es, fr, gb, de, it, ru, pt.
  - **Norteamérica (2):** us, ca.
  - **Asia y Medio Oriente (5):** jp, kr, cn, in, ae.
  - **África y Oceanía (2):** za, au.
- **Categorías (8):** `categories/<nombre>.m3u` con movies (Películas), series, sports (Deportes), news (Noticias), music (Música), kids (Infantil), documentary (Documentales), animation (Animación y anime).
- **Notas técnicas del texto original:** (1) `firstRun` en `Store.kt` con República Dominicana y Latinoamérica o el índice por categorías; (2) parser en hilos secundarios leyendo `group-title`, `tvg-logo` y `tvg-name`; (3) pestañas por región/país y por categoría en `M3uActivity.kt`; (4) HLS por el proxy local con buffer de 16 MB (hoy 10 MB, ya ✅ fluido). Estado de cada punto en CATALOGO-1 y REGIONES-1.

