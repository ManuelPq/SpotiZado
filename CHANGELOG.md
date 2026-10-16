# Historial de versiones — SpotiZado

## [1.0.4]

- **Revertido un cambio mio no medido**: en la 1.0.3 quite el tamano declarado
  (1920x1080) "de limpieza" mientras arreglaba el reproductor, y la pagina de login
  se quedo en negro con el formulario dentro (`campos=2`, `pwd=si`). Vuelve tal cual
  estaba en la 1.0.2. El forzado del meta viewport a 1920 NO vuelve: eso si era el
  fallo, y sigue fijado a `device-width`.

- **El formulario de login se coloca en pantalla en el acto**: se elimina el scroll
  suave (que dejaba el WebView a medio camino, mostrando una zona vacia) y se calcula
  la posicion a un tercio desde arriba, sin animacion.

- **La linea de sonda ya no se corta**: pasa a dos lineas e incluye el zoom, que es lo
  que permitia diagnosticar el desbordamiento.
- **"Recargar" siempre disponible** en el aviso de login.

## [1.0.3]

- **La interfaz vuelve a caber en la pantalla.** La 1.0.2 medía
  `Vista / documento 1920x978 / 1920`, `Escala 1.0`: una página de 1920 px CSS
  sin reducir dentro de una ventana de 384 dp. Solo cabía la esquina superior
  izquierda, de ahí el scroll en los dos sentidos y la zona negra.

  Dos causas, las dos localizadas: `00_env_spoof.js` falseaba el tamaño a
  1920x1080 y forzaba `meta viewport width=1920`, y `applyProfile` remataba con
  `setInitialScale(100)`, que anula el encaje automático del WebView. Ahora el
  tamaño que ve el JS es el real, el viewport se fija a `width=device-width` y
  la escala la decide el WebView.

- **Modo Móvil por defecto** otra vez (`GuiMode.MOBILE`): es la interfaz que
  Spotify diseña para un teléfono. Escritorio sigue disponible en Ajustes.
- **El aviso de login ya no tapa el formulario**: los datos largos van plegados
  tras un botón "Datos"; antes la tarjeta ocupaba media pantalla.

## [1.0.2]

- **La app deja de pedir login cuando la sesion ya esta iniciada.** `noticeFor()` mostraba
  "Falta iniciar sesion" para cualquier pagina de `accounts.spotify.com` sin campo de
  contrasena, incluida `accounts.spotify.com/es/status`, que dice literalmente
  "Iniciaste sesion como <usuario> · Cerrar sesion". El cartel tapaba medio visor sin motivo.

  Dos cambios en el mismo arreglo: `detectSession()` en `60_login.js` reconoce la sesion
  desde la propia pagina (texto visible o widget de cuenta del reproductor) y emite
  `session-detected`, que `PlayerBridge` ya sabia interpretar; y el aviso de login queda
  condicionado a `!state.settings.loggedIn`.

## [1.0.1]

- **Se reactiva el bloqueo de anuncios**: `AdBlocker.SAFE_MODE` pasa de `true` a `false`.
  Capa 1 (red): 1.316 reglas en modo `STRICT`. Capa 3 (neutralizador): `SILENT_AND_SKIP`.
  Unico cambio de la version: nada mas se toco.

## 1.0.0 — 2026-10-16 — version de pulido
- **Vista de escritorio por defecto**: el reproductor web de PC reproduce la lista
  entera y permite el aleatorio. En la vista movil, al acabar una cancion habia que
  darle a reproducir otra vez.
- **Banner de arranque eliminado**: la banda con las verificaciones tecnicas ya no
  ocupa sitio; esos datos siguen en Diagnostico.
- **Barra nativa (gris) apagada por defecto**, con un interruptor en Ajustes para
  reactivarla. Llego a mostrar el nombre del dispositivo («2k1979 | Spotify») en vez
  de la pista.
- **El cuelgue con anuncios, arreglado**: era nuestro. `publishReload()` recargaba la
  pagina sin guarda; durante un anuncio Spotify responde 404 en `connect-state`, el
  script pedia recargar, el anuncio reiniciaba y volvia el 404: bucle infinito. Ahora
  hay limite (2 recargas cada 3 min, minimo 8 s entre ellas) y el JS exige bloqueo
  sostenido (4 fallos y mas de 15 s) y que no haya reproduccion avanzando.
- El lector de estado descarta titulos que son nombre de dispositivo.

## 0.4.0 — 2026-10-16 — control por la API de Connect
**Causa raiz del player nativo muerto.** Todos los comandos de `20_control.js`
pulsaban selectores del reproductor de ESCRITORIO
(`button[data-testid=control-button-playpause]`, `div[data-testid=playback-progressbar]`).
El modo que hace sonar la musica es el MOVIL, y el reproductor web movil no tiene
esos selectores: cada orden devolvia `false` y el bucle de estado no leia nada. De ahi
la barra nativa mostrando «Sin reproduccion» y sin respuesta al toque.

**Solucion portada de Spotifuck** (y de su derivado SpotiDuck, que funciona): mandar
las ordenes por la API de **Spotify Connect** al propio `device_id` del WebView, en
lugar de simular clics. Es ademas el «truco» descrito por el usuario: dentro de
Spotify, decirle que el reproductor es el WebView. Resuelve tambien el lio de los
«dos reproductores» de la misma cuenta.

Nuevo `assets/webplayer/27_connect.js`:
- Endpoints usados: `resume`, `pause`, `next`, `skip_prev` y `/player/transfer`.
  `playFromUri` (endpoint `play`) ya existia y se reutiliza. Para seek, repeat,
  shuffle y favorito se mantiene el clic del DOM y se informa del fallo: inventar
  nombres de endpoint ocultaria el problema en vez de resolverlo.
- Cada comando intenta la API y cae al clic anterior si no hay credenciales o falla.
- Estado con **fuentes genericas**, sin depender del DOM de escritorio:
  `navigator.mediaSession.metadata` (pista, artista, album, caratula),
  el elemento `<audio>` real (posicion y duracion exactas), `document.title` y
  heuristica de `aria-label` para el estado de reproduccion.
- `transferHere()` mueve la reproduccion a nuestro reproductor.

Ademas: `loadWithOverviewMode` pasa a ser condicional (quedaba en `true` y era el
ultimo reescalado activo en el login).

## 0.3.2 — 2026-10-16
**Ajuste «Modo del reproductor» conectado.** Defecto: `guiMode` (Movil/Escritorio) solo
se usaba en la interfaz; en el codigo habia `desktop = true` y `desktopMode = true`
hardcodeados en tres sitios, asi que elegir «Movil» no cambiaba nada. Ahora manda el
ajuste. El CSS de escritorio (`40_layout.js`) solo se inyecta en modo escritorio.

**Datos del usuario que motivan el experimento:** cuenta **gratuita**, y el
reproductor web **si suena** en Chrome del telefono. Descartado DRM (`widevine OK`),
cuenta y red. El README de SpotiDuck advierte de «errores al cargar la reproduccion
en WebViews moviles» con cuentas gratuitas. Por eso el modo por defecto pasa a ser
**Movil**: el reproductor web movil es el que Spotify disena para navegadores de
telefono.

Sonda: `ua` (lo que Spotify ve como navegador), `audio` (posicion/readyState/pausa o
«sin medio»), `clientW`, `metaVp`, `dpr` y `bodyText`.

## 0.3.1 — 2026-10-16
**El arreglo del «no se usa el 100 % de pantalla».** Medido en el dispositivo:
`Vista / documento = 1920x978 / 420`. `innerWidth` valia 1920 (nuestro spoof) pero el
layout real media 420: el JS de Spotify creia tener 1920 px y servia la interfaz de
ESCRITORIO, mientras el CSS se maquetaba a 420 por el meta viewport de Spotify. La UI
se salia de la pantalla. Ahora se fuerza el layout viewport a 1920 para que coincida
con el spoof, y el WebView escala 1920 -> 420.

Sonda ampliada: `clientW` (ancho de layout), `metaVp` (que dice el meta viewport),
`dpr` y `bodyText` (primer texto de la pagina, revela errores de reproduccion).

Datos confirmados por el usuario: `Widevine = widevine OK`, `Boton de play = si`,
`Elementos de audio/video = 0` (el stream no arranca).

## 0.3.0 — 2026-10-16 — build de diagnostico
- **Banda de estado de una linea** en lugar de la tarjeta de aviso, que en las
  capturas del dispositivo ocupaba el 35-40 % del alto: nuestra propia interfaz le
  robaba la pantalla al reproductor. Plegada ocupa una linea; con «Datos» muestra
  version, modo seguro, DRM, ready, play, modos de bloqueo, campos, pwd, vis, vw,
  sw y zoom.
- **Viewport movil normal en el login**: `useWideViewPort` era `true` siempre, lo
  que en una pagina movil activaba el modo panoramico (980 px) y reducia el
  formulario a una franja. Ahora `true` solo en el reproductor.
- **Modo seguro** (`AdBlocker.SAFE_MODE = true`): filtrado de red y neutralizacion
  desactivados para aislar si alguna capa propia impide la reproduccion. El README
  de SpotiDuck advierte de que un filtro agresivo bloquea endpoints y las pistas no
  suenan; nuestra capa 1 iba en STRICT con 1.316 reglas.

## 0.2.1 — 2026-10-16
**Escala del login.** Causa raíz: `loadWithOverviewMode = true` tambien en la pagina
de login, lo que hacia que el WebView redujera la pagina ENTERA para encajarla. El
formulario quedaba a ~5 % de la pantalla. Medido en el dispositivo: `vw=384`, `sw=384`
(maquetacion correcta), pero la ESCALA VISUAL era el problema. Ahora `overview` es
`true` solo en el reproductor. Sonda ampliada con `visualViewport.scale` y con el
estado de **Widevine (DRM)**.

**Bloqueo de anuncios, defecto propio.** `trackMissing()` abria el estado de anuncio
cuando el texto de la pista estaba vacio, algo NORMAL mientras una cancion carga:
falso positivo -> `muteAll(true)` -> musica muda. Ademas se sustituia
`window.AudioContext`, lo que puede romper el arranque de audio de Spotify. Corregido:
solo la senal del DOM confirma anuncio (2 ticks), el silencio se aplica solo en modo
silenciar, no se toca ningun constructor global, y un watchdog a 8 s sale del estado
y restaura el audio siempre.

## 0.2.0 — 2026-10-16
Rebranding a **SpotiZado** + icono nuevo (verde #1ED860, adaptativo, 5 densidades).
Línea base declarada: se adopta el método de «una versión = un cambio, ningún cambio
sin medida». Punto de partida para portar el motor JS de Spotifuck.

## 0.1.7 — 2026-10-15
`ensurePasswordVisible()`: si el campo de contraseña queda fuera de la vista, se trae
al centro. Defecto detectado antes de entregar: la llamada se añadió sin la definición
(habría lanzado `ReferenceError`); corregido y verificado.

## 0.1.6 — 2026-10-15
Añadida la llamada de `ensurePasswordVisible()` (definición ausente; no entregado).

## 0.1.5 — 2026-10-15
**Causa raíz del login.** Faltaban tres ajustes del WebView que Spotifuck sí tenía:
`setSupportZoom(true)`, `setSupportMultipleWindows(true)`,
`setJavaScriptCanOpenWindowsAutomatically(true)`. Con `useWideViewPort` y
`loadWithOverviewMode`, la página se maquetaba a 980 px y sin zoom no había forma de
encogerla a los 384 px de la pantalla. Sonda de **visibilidad real**
(`getBoundingClientRect`) además de existencia en el DOM. Añadido `onCreateWindow`.

## 0.1.4 — 2026-10-15
El spoof de escritorio había quedado como código muerto al arreglar el login; la
recarga con perfil de escritorio comparaba contra un valor ya modificado y nunca se
disparaba. Se guarda `loadedWithDesktop` (con qué perfil se pidió esa carga).

## 0.1.3 — 2026-10-15
Perfil de página: el reproductor usa perfil de escritorio; login y demás, UA móvil con
escala automática. El aviso pasa a banda **fuera** de la caja del WebView. El perfil se
fija **antes** de cargar. Sonda con `campos`/`pwd`/`texto`.

## 0.1.2 — 2026-10-15
El spoof de escritorio se aplicaba en todas las páginas (forzaba `innerWidth=1920` en el
login). El bloqueo de red actuaba también en el login y cortaba el consentimiento GDPR
(`fundingchoicesmessages.google.com`). Lista blanca 24 → 26 reglas. User-Agent
construido desde el motor real del WebView.

## 0.1.1 — 2026-10-15
`URL_LOGIN` con `?allow_password=1`. El controlador se inyecta siempre en el
reproductor (antes dependía de un único botón). Aviso descartable con acción.

## 0.1.0 — 2026-10-15
Primera compilación. Cadena montada desde cero: Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21,
JDK Temurin 21, SDK 36.
