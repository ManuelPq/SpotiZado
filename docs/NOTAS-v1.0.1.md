# SpotiZado v1.0.1 — notas de versión

**APK:** SpotiZado-v1.0.1-debug.apk (23091668 bytes)
**versionCode:** 16 · **versionName:** 1.0.1
**applicationId:** com.imparable.spoti.debug (mismo paquete: actualiza encima, no duplica)
**Commit:** pendiente de subida en esta misma operación

---

## 1. El único cambio respecto a 1.0.0

AdBlocker.SAFE_MODE pasa de true a false. Nada más se ha tocado: una versión, un cambio.

Esto reactiva las dos capas que llevaban inertes desde 0.3.0:

| Capa | Qué hace | Cómo queda |
|---|---|---|
| 1 — red | Corta hosts de anuncios y rastreadores antes de que salgan | 1.316 reglas (base 1.305 + spotify_ads 11), modo STRICT |
| 3 — neutralizador | Silencia y salta la reproducción cuando detecta un anuncio | SILENT_AND_SKIP, intervalo 700 ms, maxAdMs 40000 |

Modo STRICT es el total. Existe TRACKERS como opción prudente: ese NO bloquea los hosts de anuncios de Spotify, solo rastreadores. Los dos tienen interruptor en Ajustes, así que se apagan sin recompilar.

## 2. Por qué esto es una medida y no una promesa

El README del proyecto hermano SpotiDuck avisa de que las listas agresivas pueden bloquear endpoints esenciales del servidor y dejar las pistas sin sonar. Por eso 0.3.0 desactivó todo: para aislar el WebView y el DRM como sospechosos. Aquello apuntó al WebView, así que ahora se devuelve la función al bloqueo.

Si con esta versión el audio no suena, el sospechoso número uno es la capa 1: pon AdBlock en TRACKERS, y si sigue igual en Apagado. Dime en qué paso vuelve el sonido y ya sabemos quién es el culpable.

## 3. Qué comprobar en el móvil

1. Suena la música y no se oyen anuncios.
2. En modo Escritorio, la lista encadena y respeta el aleatorio.
3. Ya no se cuelga al aparecer un anuncio. El bucle de 1.0.0 era: anuncio, connect-state devuelve 404, recarga, el anuncio reinicia, y así sin fin.
4. Si algo falla: AdBlock a TRACKERS, luego a Apagado, y cuéntame el paso exacto.

## 4. Aviso legal

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify; el riesgo lo asume la cuenta que use la app. Experimento privado, no distribuible.

## 5. Seguridad

El token clásico de GitHub usado para los push sigue vigente: revócalo en GitHub, Settings, Developer settings, Tokens (classic).
