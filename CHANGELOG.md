# Registro de cambios y guía de continuidad – CASTV (v8)

Este archivo es **la memoria del proyecto**. Si hay que seguir con otro asistente (otro bot), se le pasa este archivo completo y con eso puede continuar.

**Cómo leer los estados:**

- ✅ **Incorporado:** el cambio ya está en el código y subido a GitHub.
- 🧪 **Subido, falta probar** (o falta confirmar que compile).
- ⏳ **Prueba pendiente** de una parte concreta.
- ⬜ **Por hacer.**
- ❌ **Falló** (se revirtió o se corrigió; se explica qué pasó).

**Cómo deshacer un cambio:**

```bash
git log --oneline -15          # busca el hash del cambio
git revert HASH                # crea un commit que lo deshace
git push origin main
```

**Si todo se rompe**, volver al último punto bueno: la rama `respaldo-antes-de-restaurar`, la etiqueta `v2.1-estable` (si ya se creó) o el commit `d7010ac` (Etapa 11).

---

## Contexto para otro asistente

### Qué es el proyecto

**CASTV** es una app de Android (Kotlin) que envía videos y música del teléfono a un **TV por Wi-Fi (DLNA/UPnP)**. El teléfono levanta un servidor HTTP propio en el **puerto 8000** y le ordena al TV, por SOAP (`Stop`, `SetAVTransportURI`, `Play`), que reproduzca `http://IP-del-teléfono:8000/video/ID.mp4` o `/audio/ID.mp3`. Autor: Ariel De Jesus Bueno.

- Repositorio: `github.com/arieleljefe/castv-app` (rama `main`). Versión estable base: Etapa 11 (v2.1).
- Versiones: Android Gradle Plugin 8.2.0, Kotlin 1.9.22, Gradle 8.5, compileSdk/targetSdk 34, minSdk 24, NanoHTTPD 2.3.1.
- Archivos principales (`app/src/main/java/com/castv/app/`): `MainActivity.kt` (interfaz y casi toda la lógica), `TvController.kt` (UPnP/SOAP), `MediaServer.kt` (servidor HTTP), `MediaScanner.kt` (lee los medios), `CastvService.kt` (servicio en primer plano), `Store.kt` (favoritos, listas, ajustes), `Thumbs.kt` (miniaturas), `DebugLog.kt` (registro y estado del servidor).
- `castv_app.py` es el script antiguo en Python. **No debe ejecutarse mientras se usa la app**: ocupa el puerto 8000 y el TV le pide los archivos a él (causó el error "Archivo no compatible").

### Datos importantes del TV

- El TV solo lee **mp3** y **mp4 (H.264 + audio AAC)**. Otros formatos (`.opus`, `.m4a`, `.mkv`, `.flac`) los rechaza; la app los salta al enviar al TV.
- Registro de depuración con la app abierta: `curl -s http://127.0.0.1:8000/log` (y `?clear`).

### Cómo se trabaja (importante)

1. **El usuario trabaja desde un teléfono con Termux.** Respuestas cortas, comandos para pegar **uno por uno**, y capturas de pantalla de lo que sale. Habla español, tono informal.
2. **El asistente no puede compilar.** La compilación la hace **GitHub Actions** al subir a `main`. El APK queda en Acciones → último run → Artifacts (`castv-apk`). Si el build sale con X roja, el usuario descarga los logs (zip) y los sube; el error está en `0_build.txt`, líneas con `e: file://` o `error:`.
3. **Los cambios se entregan como archivos `.patch`** que el usuario descarga a Descargas y aplica:
   ```bash
   cd ~/castv-restore/castv-app
   git apply --exclude=CHANGELOG.md ~/storage/downloads/ARCHIVO.patch
   cp ~/storage/downloads/CHANGELOG_vN.md CHANGELOG.md
   git add -A && git commit -m "Mensaje" && git push origin main
   ```
   Siempre se excluye `CHANGELOG.md` del parche y se entrega el changelog completo aparte (evita conflictos).
4. **Regla del changelog (pedida por Ariel):** siempre se entrega **primero** el changelog completo y actualizado, con lo hecho y lo que falta, antes que cualquier parche o respuesta larga, por si se acaban los tokens.
   **Regla anterior:** al terminar o integrar algo, se anota aquí con ✅. **Siempre se entrega primero el changelog actualizado**, antes que cualquier otra cosa, por si se acaban los tokens.
5. Probar siempre que el parche aplica antes de entregarlo (`git apply --check`), y entregar pasos de prueba con cada cambio.

### Lecciones aprendidas (para no repetir errores)

- Un estilo de Android llamado `A.B.C` exige un estilo padre `A.B`; si no existe, el build falla. Se usaron nombres sin puntos (`AccentOverlayCyan`).
- `indexOf(speed)` con `speedValues` no compila; se usa `indexOfFirst { it == speed }`.
- Si `git apply` dice "already exists" o "does not apply", normalmente el parche **ya estaba aplicado**: revisar con `git diff --stat` y `git log --oneline -5`.
- Si `curl http://127.0.0.1:8000/log` responde `Server: SimpleHTTP ... Python`, es el script viejo: `pkill -f castv_app.py` y reiniciar CASTV.
- Siempre confirmar con `pwd` y `git log` que se está dentro de `~/castv-nuevo/castv-fresh` antes de aplicar un parche; en `~` hay otros repos y se mezclan. Tras `git apply`, verificar con un `grep` de algo del parche antes de hacer commit.
- Nunca subir `.gradle/` ni `build/` a git (pasan de 100 MB y GitHub rechaza el push); `.gitignore` ya los excluye.
- En Termux, `ps` no siempre muestra procesos de otras sesiones; buscar por `python`.

### Texto para pegarle a otro asistente

> Soy Ariel y estoy desarrollando la app Android CASTV (repo `arieleljefe/castv-app`). Te paso el archivo CHANGELOG.md, que tiene el contexto, las reglas de trabajo, el historial y la hoja de ruta al final. Lee primero la sección "Contexto para otro asistente" y "Hoja de ruta". Trabajo desde Termux en el teléfono, así que dame comandos cortos para pegar uno por uno y los cambios como archivos .patch. Continúa por la sección "Próxima función".

---

## Cambios pendientes de probar

### 2026-11-17 · Videos en el teléfono (salida Teléfono/TV para video y música) 🧪 (subiendo; falta confirmar el build y probar)

- **Qué:** el botón de salida (icono TV/teléfono, o ⋮ → Salida) ahora controla **música y videos**. Con "Teléfono", al tocar un video se abre un reproductor a pantalla completa en el teléfono (nueva `VideoPlayerActivity`) con controles, gira con el teléfono, mantiene la pantalla encendida y **recuerda el punto donde lo dejaste** (aparece en "Continuar"). Con "TV" todo sigue igual que antes. Si algo suena en el TV o en el teléfono, se detiene al pasar el video al teléfono.
- **Archivos:** `VideoPlayerActivity.kt` (nuevo), `AndroidManifest.xml` (nueva actividad), `MainActivity.kt` (`startPlayback`, texto del menú y avisos), `Store.kt` sin cambios.
- **Probar:**
  1. Pon la salida en Teléfono (icono cian). Toca un video: debe abrirse a pantalla completa y reproducirse con sonido.
  2. Toca la pantalla: salen los controles (play/pausa, barra para adelantar). Gira el teléfono: no debe reiniciarse.
  3. Atrás: vuelve a la lista. El video debe aparecer en "Continuar" con el tiempo guardado; al tocarlo ofrece "Continuar desde…".
  4. Pon la salida en TV y reproduce el mismo video: debe ir al TV como siempre.
  5. Con música sonando en el TV, pasa un video al teléfono: la música debe detenerse y no saltar a otra canción.
- **Limitaciones conocidas:** no avanza solo al siguiente video; no pasa a TV desde ese reproductor (hay que volver y cambiar la salida); sin subtítulos.
- **Si falla:** `git revert` del commit "Videos en el teléfono".
- **Riesgo:** bajo-medio. No toca el envío al TV con la salida en TV. Los videos que el teléfono no pueda decodificar muestran un aviso y se cierra el reproductor.

### 2026-11-16 · Estabilidad (tanda 1) 🧪 (parcial)

- **Qué:** aviso cuando el puerto 8000 está ocupado; la CPU y el Wi-Fi se mantienen despiertos con la pantalla apagada.
- **Archivos:** `CastvService.kt`, `DebugLog.kt` (nuevo objeto `ServerState`), `MainActivity.kt`.
- **Probar:**
  1. Con `python castv_app.py` corriendo en Termux, abre CASTV: debe salir el aviso "Puerto 8000 ocupado".
  2. Sin nada en el puerto, reproduce al TV con la pantalla apagada 5 minutos: no debe cortarse.
  3. `curl -s http://127.0.0.1:8000/log` debe mostrar "Servidor iniciado en el puerto 8000".
- **Estado de las pruebas:** ✅ registro "Servidor iniciado" y ✅ pantalla apagada (probados, bien). ⏳ **Pendiente de probar:** el aviso de puerto 8000 ocupado (prueba 1).
- **Si falla:** `git revert` del commit "Aviso de puerto 8000 ocupado y bloqueos de energía".
- **Riesgo:** bajo. Posible mayor consumo de batería por los bloqueos de energía.

### 2026-11-16 · Color de acento a elegir 🧪 (subido; falta confirmar el build y probar)

- **Qué:** nuevo menú **⋮ → Color** con Cian (el de siempre, por defecto), Verde, Naranja y Morado. Cambia botones, barra de progreso, pestañas, fondo del mini reproductor y la fila que se está reproduciendo. Cada color tiene su versión para el tema claro y para el oscuro.
- **Archivos:** `res/values/attrs.xml` (nuevo), `res/values/colors.xml`, `res/values-night/colors.xml`, `res/values/styles.xml` (4 estilos de acento), 8 archivos de `layout/` y `drawable/` (`@color/accent` → `?attr/castvAccent`), `MainActivity.kt`.
- **Probar:**
  1. Con Cian la app debe verse **igual que antes** (en oscuro y en claro).
  2. ⋮ → Color → Verde / Naranja / Morado: revisa pestañas, barra de progreso, botón de play, mini reproductor y la canción que suena.
  3. Cierra y abre la app: debe recordar el color.
  4. Cambia también el tema Claro/Oscuro con cada color.
- **Si falla:** `git revert` del commit "Color de acento a elegir".
- **Riesgo:** medio en el aspecto. Cambia cómo se leen los colores (ahora vía tema), así que un fallo se vería como botones o textos sin color. No toca el TV.
- **Nota:** el icono de la app no cambia de color.
- **Corrección de build:** el primer build falló porque un estilo llamado con puntos (ThemeOverlay.Accent.Cyan) exige un estilo padre que no existe. Se renombraron a AccentOverlayCyan, AccentOverlayGreen, AccentOverlayOrange y AccentOverlayPurple.

---

## Historial

### 2026-11-17 · Eliminar música y videos (selección múltiple) ✅

- **Qué:** mantén pulsada una fila para entrar en modo selección; toca más filas para marcarlas (se ven con ✓). Arriba salen el contador y los botones **Todo** y **🗑 Borrar**. Pide confirmación y borra del teléfono. También hay **🗑 Eliminar del teléfono…** en el menú ⋮ de cada fila (para uno solo). Funciona en **música y videos**. Al borrar se quitan de la lista, la cola, favoritos, listas, puntos guardados y estadísticas. Si suena lo que se borra, se detiene.
- **Archivos:** `MainActivity.kt` (selección, `deleteItems`, `afterDeleted`, `onActivityResult`), `Store.kt` (`forget`).
- **Android:** 11 o superior = una sola confirmación del sistema para todo el grupo (`MediaStore.createDeleteRequest`). Android 10 = confirmación archivo por archivo. Android 9 o anterior: intenta borrar directo y, si no hay permiso, lo cuenta como "sin permiso" (no se pide permiso de escritura).
- **Cambio de comportamiento:** la pulsación larga ya no abre el menú; ahora inicia la selección. El menú sigue en el botón ⋮.
- **Probar:**
  1. Pulsación larga en una canción de prueba: sale la barra con "1 seleccionado".
  2. Toca otras dos: contador 3 y ✓ en cada una. Botón **Todo** las marca todas. Atrás cancela la selección.
  3. Borrar 1 canción desde ⋮ → Eliminar: sale el aviso del sistema, confirma y desaparece de la lista.
  4. Selecciona 3 y bórralas (una sola confirmación del sistema).
  5. Borra la que está sonando en el TV: debe detenerse.
  6. Borra una que estaba en favoritos o en una lista: ya no debe aparecer ahí.
  7. Repite con un video.
- **Si falla:** `git revert` del commit "Eliminar archivos con selección múltiple".
- **Riesgo:** medio. **Borra archivos de verdad y no se puede deshacer.** Probar primero con archivos que no importen. No toca el envío al TV.
- **Resultado:** probado en el teléfono, funciona bien (confirmado por Ariel el 17 nov 2026).

### 2026-11-16 · Lista: nombres legibles, búsqueda y carga más fluida (tanda 2) ✅

- **Qué:**
  - Los archivos con código largo (ej. `7e6dce4b...`) se muestran como `Video Camera 16 nov 2026 (8bc7)`. Los de WhatsApp (`VID-20261110-WA0181`) como `WhatsApp Video: 10 nov 2026 (0181)`.
  - La búsqueda también encuentra por ese nombre legible (ej. buscar "whatsapp" o "camera").
  - La lista de videos/música se lee en **segundo plano**, para que la pantalla no se congele al abrir o cambiar entre Video y Audio.
- **Ya existía (no se cambió):** orden A-Z / Z-A y búsqueda sin acentos por varias palabras.
- **Archivos:** `MainActivity.kt` (`prettyTitle(item)`, `loadMedia`, `computeDups`).
- **Probar:**
  1. Abre la app: la lista debe aparecer sin congelar la pantalla.
  2. Los videos con código largo deben verse con fecha y carpeta.
  3. Busca "whatsapp" y "camera": deben salir resultados.
  4. Cambia entre Video y Audio varias veces rápido: no debe cerrarse ni mezclar listas.
  5. Reproduce uno al TV: el título que sale en la app debe ser el legible.
- **Si falla:** `git revert` del commit "Nombres legibles, búsqueda y carga de lista en segundo plano".
- **Resultado:** probado en el teléfono, todo bien.
- **Riesgo:** medio-bajo. Al abrir, la lista tarda un instante en aparecer (antes bloqueaba la pantalla).

### 2026-11-16 · Tema claro/oscuro a elegir (tanda 3) ✅

- **Qué:** nuevo menú **⋮ → Tema** con Sistema / Claro / Oscuro. El tema **Oscuro sigue siendo el de siempre** (es el valor por defecto). El tema Claro es nuevo.
- **Archivos:** `res/values/colors.xml` (ahora paleta clara), `res/values-night/colors.xml` (nuevo, paleta oscura actual), 17 iconos `ic_*.xml` (blanco fijo → `@color/vlc_text`), `bg_bottom_nav.xml`, `bg_chip.xml`, `item_bg_playing.xml`, `MainActivity.kt`.
- **Probar:**
  1. Con el tema Oscuro, la app debe verse **igual que antes**.
  2. ⋮ → Tema → Claro: revisa lista, mini reproductor, cola, menús, barra inferior e iconos. Anota cuáles se ven mal.
  3. Prueba Sistema cambiando el modo oscuro de Android.
- **Si falla:** `git revert` del commit "Tema claro/oscuro a elegir".
- **Riesgo:** medio en el aspecto (solo se puede comprobar viendo el teléfono). No toca la reproducción ni el TV.
- **Resultado:** probado en el teléfono, funciona bien.
- **Pendiente:** elegir color de acento (cian, verde, naranja, morado).

### 2026-11-16 · Registro de depuración visible desde Termux ✅

- **Qué:** la app guarda en memoria las órdenes al TV (con su respuesta HTTP), la URL enviada y cada petición del TV al teléfono. Se lee con `curl -s http://127.0.0.1:8000/log` (y `?clear` para limpiar).
- **Archivos:** `DebugLog.kt` (nuevo), `MediaServer.kt`, `TvController.kt`.
- **Commit:** `6738740`
- **Resultado:** gracias a esto se encontró que el puerto 8000 estaba ocupado por otro programa.

### 2026-11-16 · Saltar formatos que el TV no lee ✅

- **Qué:** al enviar al TV, si el archivo no es `mp3`, `mp4` o `m4v`, se salta al siguiente (máximo 10 seguidos) y se avisa en la barra de estado.
- **Archivos:** `MainActivity.kt`
- **Commit:** `0030620`
- **Motivo:** el TV solo lee mp3 y mp4; los `.opus` y `.m4a` de WhatsApp daban "Archivo no compatible".

### 2026-11-16 · Corregir `indexOf` en velocidad ✅

- **Qué:** `speedValues.indexOf(speed)` no compilaba en GitHub Actions; ahora usa `indexOfFirst { it == speed }`.
- **Archivos:** `MainActivity.kt` (líneas ~1690 y ~1709)
- **Commit:** `dc6e415`
- **Síntoma:** el build salía con X roja y error `Unresolved reference` en `MainActivity.kt`.

### 2026-11-16 · Restaurar Etapa 11 (v2.1) como versión estable ✅

- **Qué:** `main` se restauró a la Etapa 11 (`d7010ac`), que corresponde al APK que funcionaba. El código anterior quedó en la rama `respaldo-antes-de-restaurar`.
- **Commit:** `d7010ac` ("Etapa 11: listas, temporizador, ecualizador, velocidad, elegir TV y correcciones")

### Etapas anteriores

| Etapa | Descripción |
|---|---|
| 10 | Interfaz estilo VLC |
| 9 | Reproductor en pantalla de bloqueo y carátula |
| 8 | Controles en la notificación |
| 7 | Rediseño azul cian |

---

## Plantilla para cada cambio nuevo

```
### AAAA-MM-DD · Título del cambio 🧪

- **Qué:**
- **Archivos:**
- **Probar:**
- **Si falla:** git revert del commit "..."
- **Riesgo:**
```

---

## Hoja de ruta (dónde vamos y qué falta)

Actualizada: 17 de noviembre de 2026 (v8). Cuando una función se termine, se mueve de columna y se anota arriba en el historial.

### ✅ Hecho y probado

- Restaurar la Etapa 11 (v2.1) como versión estable.
- Arreglo del build (`indexOf`).
- Saltar formatos que el TV no lee (solo mp3 y mp4).
- Registro de depuración (`curl -s http://127.0.0.1:8000/log`).
- Tema Sistema / Claro / Oscuro.
- Lista: nombres legibles, búsqueda por ellos y carga en segundo plano.
- Estabilidad: bloqueos de energía con la pantalla apagada y registro "Servidor iniciado".
- Eliminar música y videos con selección múltiple (borrado permanente).
- Guía de problemas y soluciones (documento aparte).

### 🧪 Hecho, falta probar

- **Videos en el teléfono.** Parche `video_telefono.patch`. Falta build verde y probar.
- **Aviso de puerto 8000 ocupado.** Prueba: con `python castv_app.py` corriendo, forzar detención de CASTV, abrirlo y esperar 2 segundos. Debe salir el aviso. Cerrar el script con `pkill -f castv_app.py`.
- **Color de acento a elegir** (Cian, Verde, Naranja, Morado). Parche `acento_color.patch` + corrección de nombres de estilos. Falta que el build salga verde y probarlo.

### ➡️ Próxima función

- Por decidir con Ariel. Candidatas:
  1. **Papelera en vez de borrado permanente** (cambio chico: `createTrashRequest`), si se quiere poder recuperar.
  2. **Integrar piezas de la Etapa 12** que aún no están (cola editable, retomar la sesión al abrir la app, reproducir en segundo plano con el motor `Player`). La música en el teléfono ya existe en esta línea. Está en el zip `castv-backup-20261116-1723.zip`, en otra línea de código (`Player.kt`). Esa línea **no compiló** en GitHub (error de Kotlin sin ver las líneas `e: file://`), así que hay que traerla por partes.
  3. Subir `README.md`, copia de seguridad + etiqueta `v2.1-estable` y Release del APK (ver lista de abajo).
  4. Subtítulos para videos.

### ⬜ Por agregar (en este orden)

1. Subir `README.md` al repositorio (el archivo ya está hecho).
2. Copia de seguridad: zip en Descargas + etiqueta `v2.1-estable` en GitHub.
3. Publicar el APK como **Release** de GitHub, para que se descargue fácil.
4. Añadir un archivo `LICENSE` (por ejemplo MIT), si se quiere compartir el código.
5. **Subtítulos para videos.** Depende de que el TV los acepte por DLNA; hay que probarlo.
6. **Convertir `.opus` / `.m4a` a mp3 desde la app.** Pesado: obliga a incluir ffmpeg y el APK crece mucho. Mientras tanto se convierte con Termux.

### 💡 Ideas sin decidir

- Mini reproductor más compacto.
- Mensajes de error más claros al enviar al TV.

### ℹ️ Ya existía en la app (no hay que hacerlo)

- Reconectar el TV automáticamente si se pierde.
- Orden A-Z / Z-A con letras de sección y búsqueda sin acentos por varias palabras.
- Miniaturas en segundo plano con caché y detección de duplicados.
