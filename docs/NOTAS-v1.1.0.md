# SpotiZado v1.1.0 — notas de versión

**versionCode:** 27 · **versionName:** 1.1.0 · `com.imparable.spoti.debug` (actualiza encima)

---

## 1. El ZIP de SpotiDuck v1.2.7 traía las respuestas

No es código fuente, es el APK — pero sus **assets vienen en claro**: `ad_state_hook.js`,
`desktop_spoof.js`, `css_hacks.css`, `spotify_bridge.js` (146 KB), `silent.mp3`. Y ahí
estaban las dos piezas que llevábamos toda la tarde sin encontrar.

**No se ha copiado código.** El proyecto de SpotiDuck no tiene licencia publicada, así que
lo que se toma es la *arquitectura* y se reimplementa. Se cita el origen en cada fichero.

## 2. El bug del login: era `allow_password=1`

Su asset `classic_login.js` contiene una sola línea, y es una advertencia explícita:

> *"Legacy `allow_password` URL parameters cause Spotify auth servers to reject login attempts."*

Nuestra `URL_LOGIN` llevaba `https://accounts.spotify.com/login?allow_password=1` **desde la
0.1.7**. Los servidores de autenticación rechazan esa petición → la página se sirve vacía →
el visor queda negro, aunque el DOM tenga los dos campos (`campos=2`, `pwd=sí`).

Eso explica **todo** lo que hemos visto: la caja perfecta (`left=24 w=336 OK en vista`), el
`body` pintado, el texto ausente, y que en Chrome se viera bien (Chrome no pide esa URL con
el parámetro; la app sí). No era el viewport, ni la composición de capas, ni un ancestro
oculto: **era el parámetro**.

**Corregido**: `URL_LOGIN = "https://accounts.spotify.com/login"`.

## 3. Bloqueo de anuncios por `file_id` (la forma que sí funciona)

La razón de fondo de que el bloqueo por host nunca funcionara: **el audio de los anuncios se
sirve desde los mismos hosts CDN que la música**. Por URL no se distinguen, y bloquear el
host se lleva la música por delante.

La vía que sí funciona, ahora implementada:

```
25_adstate.js  →  envuelve fetch y WebSocket
               →  lee los state_machine del reproductor
               →  pistas con content_type = AD  →  sus file_ids
               →  spotiNotify('adids', ...)
AdIdStore.kt   →  cola acotada (64) de IDs
shouldInterceptRequest  →  si la URL contiene un ID conocido, corta ESA petición (204)
```

Detalles que importan:

- La respuesta original de `fetch` se devuelve **intacta**: solo se observa.
- Puerta barata: si el texto no contiene `state_machine`, no se parsea. Tope de 8 MB.
- Es una capa **independiente** del bloqueo por host: funciona aunque el filtrado de red
  esté en Apagado.

## 4. Widevine: una sola sesión de CDM

De su `desktop_spoof.js` (SECTION 0): el CDM hardware de Qualcomm limita a **16 sesiones
concurrentes** (`ERROR_DRM_RESOURCE_BUSY`). El reproductor precarga 16-20 pistas al abrir
una lista y cada una pide su `MediaKeys` sin cerrarla → se agota el límite y aparece
*"Spotify no puede reproducir esto ahora"*.

Compartir una única instancia baja las sesiones hardware a 1. Ya aplicado en `00_env_spoof.js`.

## 5. Qué probar

1. **Instala y abre.** El login debería aparecer **sin hacer nada**: sin modo limpio, sin
   calibración, sin botón experimental. Ese era el parámetro.
2. Entra con email y contraseña. ¿Llega al reproductor y se ve la interfaz?
3. Si suena música, deja que llegue un anuncio: ahora debería cortarse **antes** de sonar,
   porque el audio llega por `file_id`. En **Diagnóstico** hay contadores nuevos de IDs
   capturados y de cortes.
4. Si algo falla, **Diagnóstico → Copiar informe** (texto, sin capturas).

## 6. Aviso legal

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify;
el riesgo lo asume la cuenta que use la app. Proyecto privado, no distribuible.
