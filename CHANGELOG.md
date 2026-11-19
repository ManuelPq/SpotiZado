# Historial de versiones — SpotiZado

## [1.3.0]

**Hipotesis nueva, y con la prueba al lado.** Con la 1.2.1 el login clasico ya informa
`campos=2 · pwd=sí · visible=sí · vw=384 · sw=384 · zoom=1.0` y **la pantalla sigue negra**.
El formulario esta en el DOM, su caja cae dentro de la zona visible... y no se ve ni un
pixel. Mientras tanto, el boton que inyecta `62_classic.js` (`position:fixed`,
`z-index:2147483000`) **si se pinta**.

Eso descarta de golpe: DOM, viewport, escala, motor de pintado y WebView. Queda lo unico
que explica que lo nuestro se vea y lo suyo no: **hay una capa opaca por encima**. Nuestro
boton se ve porque va por delante de ella.

- Nuevo `66_stack.js`: mide con `document.elementsFromPoint()` en el centro del campo
  (devuelve la pila real de elementos bajo ese punto, de arriba abajo), recorre ademas los
  elementos `fixed`/`absolute` que cubren >=85 % de la ventana con fondo opaco, **retira
  esas capas** (`display:none`) y vuelve a medir para confirmar que el campo queda el
  primero de la pila. No toca el formulario ni sus valores.
- Se inyecta en el login despues de `65_unhide.js`. Con la calibracion activa escribe en
  pantalla: punto medido, pila antes/despues, capas encontradas y cuantas se quitaron.

Si el veredicto es «sin capa: nada tapa el campo», entonces la hipotesis cae y el siguiente
paso deja de ser adivinar: `chrome://inspect` por USB para ver el arbol pintado de verdad.

## [1.2.1]

**Causa raiz encontrada, y es un fallo nuestro de cableado.** El diagnostico de la 1.2.0
en el movil decia `campos=1 · pwd=no · visible=no`: el campo del formulario **existe en el
DOM** pero no se pinta. Y no se pintaba porque `65_unhide.js` — el script que fuerza la
visibilidad de la cadena de ancestros — **no se inyectaba en la pagina de login**.
`buildLoginScript()` cargaba solo `HOST_BRIDGE + LOGIN + CLASSIC`.

La cabecera de `65_unhide.js` ya documentaba la medicion que lo prueba: la caja del
formulario estaba correcta (`left=24 top=274 w=336 h=52`) y aun asi no se pintaba; al
forzar `opacity`/`visibility`/`overflow` en la cadena, **el formulario aparece**. Es decir:
el arreglo existia desde la 1.0.9 pero solo se aplicaba al reproductor. La pantalla negra
del login nunca lo recibio.

- `buildLoginScript()` pasa a cargar tambien `65_unhide.js`, con
  `window.__spotiCalibOn` puesto al valor del ajuste antes de cargarlo.
- Anadido `62_classic.js`: boton fijo "Entrar con email y contraseña" que navega a la
  misma pagina con `?allow_password=1` (la pagina servida por Spotify ya es *passwordless*,
  sin campo de contraseña). No se inyecta si ya hay `input[type=password]`.
- `PlayerViewModel.openClassicLogin()` y el boton correspondiente en el aviso.

Sin mas cambios: la configuracion de WebView de la 1.2.0 queda intacta.

## [1.1.1]

Extraído del **dex** del APK de SpotiDuck v1.2.7 (`strings` sobre `classes.dex`). Dos
diferencias con lo nuestro, las dos en la carga de la página de login:

- **URL**: la suya es
  `https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F`
  — sin `allow_password` (su `classic_login.js` ya avisaba de que ese parámetro hace que
  Spotify rechace el login) y **con `continue=`**. Sin destino, Spotify sirve una página
  vacía: la zona negra.

- **User-Agent fijado a mano**, no derivado del motor. Los suyos son los UA *reducidos* de
  Chrome (`Android 10; K`). El nuestro salía del WebView real y era el formato **legado**:
  `Mozilla/5.0 (Linux; Android 16; 24117RN76E Build/BP2A.250605.031.A3;) …`. Ese formato lo
  sirven navegadores antiguos, y es candidato a que Spotify responda con su página de
  "navegador no compatible" — vacía. Derivar del UA real era heredar el fallo.

## [1.1.0]

**El APK de SpotiDuck v1.2.7 (que aporta el usuario) trae los assets en claro, y de
ahí salen las dos piezas que faltaban.** No se copia código: la app tiene su licencia
sin publicar, así que se reimplementa a partir de la arquitectura observada.

- **BUG DEL LOGIN, encontrado.** Su asset `classic_login.js` dice literalmente:
  «Legacy `allow_password` URL parameters cause Spotify auth servers to REJECT login
  attempts». Nuestra `URL_LOGIN` llevaba `?allow_password=1` **desde la 0.1.7** — los
  servidores de autenticación rechazan la petición, la página se sirve vacía y por eso
  el visor quedaba en negro con los campos en el DOM (`campos=2`). Parámetro eliminado.

- **Bloqueo de anuncios por `file_id`, no por host.** El audio publicitario se sirve
  desde los mismos hosts CDN que la música: por URL no se distingue, y bloquear el host
  se lleva la música por delante. La solución observada: leer el `state_machine`, quedarse
  con los `file_ids` de las pistas con `content_type: AD`, y cortar ESA petición concreta.

  - `25_adstate.js`: envuelve `fetch` y `WebSocket`, lee los `state_machine`, extrae los
    `file_ids` y los manda al nativo. La respuesta original se devuelve intacta.
  - `AdIdStore.kt`: cola acotada de IDs + `matches(url)`.
  - `shouldInterceptRequest`: corta solo las peticiones de esos IDs, en una capa
    independiente del bloqueo por host.

- **Widevine: una sola sesión de CDM compartida.** El `desktop_spoof.js` de SpotiDuck
  documenta que el CDM hardware de Qualcomm limita a 16 sesiones concurrentes y que el
  reproductor precarga 16-20 pistas, cada una pidiendo su `MediaKeys` sin cerrarla
  (`ERROR_DRM_RESOURCE_BUSY` → «Spotify no puede reproducir esto ahora»). Compartir una
  instancia baja las sesiones hardware a 1.

## [1.0.11]

- **Informe de diagnóstico en TEXTO** (Diagnóstico → "Copiar informe"). Una pulsación copia
  al portapapeles TODO lo que hace falta para diagnosticar: versión de la app, versión de
  **Android System WebView**, modelo y API del teléfono, sesión, ajustes, URL, DRM,
  `viewport`/`documento`/`zoom`, campos y visibilidad del login, pista sonando, `deviceId`
  y estado del bloqueo.

  Motivo: diagnosticar con capturas tenía dos problemas, y el segundo era grave — cada
  captura responde a una sola pregunta, y las imágenes están **limitadas** por conversación.
  El texto no tiene límite, se puede citar y se puede comparar. A partir de aquí el
  diagnóstico se pide en texto, no en fotos.

- Botón **Compartir** (envía el informe por cualquier app) y **Copiar resumen** (una línea,
  para pegar en un chat sin que se corte).

## [1.0.10]

- **Causa nueva, y descarta el CSS**: en el dispositivo, el fondo de la página SÍ se pinta
  (`body` = `rgb(18,18,18)`, casi negro) y la rejilla inyectada también, pero el TEXTO y
  los controles de Spotify no. Mismo HTML en Chrome: correcto. Eso no es maquetación, es
  **composición de capas del WebView**.

- Tres medidas de renderizado: capa **HARDWARE** explícita para el WebView,
  `setOffscreenPreRaster(true)` para forzar el rasterizado, y se deja de pelear con el
  escalado automático en páginas que no son el reproductor.

- **Revertida una regresión mía de la 1.0.5**: las ventanas emergentes se atienden otra vez
  siempre. Al rechazar las que Spotify abre sin gesto, el flujo del login podía quedarse
  esperando y la página no avanzaba.

- **Nueva fila en Diagnóstico: versión de Android System WebView.** El README oficial de
  SpotiDuck documenta «pantalla en blanco» y su remedio es actualizar el System WebView
  desde Play Store; tener la versión a la vista evita diagnosticar a ciegas.

## [sin versión] — plan

- Añadido `docs/ESTRATEGIA-v1.1.md`: qué se reutiliza de Spotifuck y SpotiDuck (nada de
  código: Spotifuck no tiene licencia y SpotiDuck solo publica binarios), qué se aporta
  aquí, y el plan de bloqueo de anuncios en versiones de un solo cambio.

- El plan fija el orden: primero una **grabadora de huella de anuncio** (v1.1.0), porque
  detectar sin medir es adivinar; después detección por la API de Connect en lugar de
  selectores, que no existen en el reproductor móvil.

## [1.0.9]

- **El login funciona.** Verificado en el dispositivo: al forzar la visibilidad de la
  cadena de ancestros, el formulario aparece. El culpable era un ancestro del árbol que
  ocultaba el subárbol, fuera del alcance de la cadena que se medía (que llegaba solo
  hasta `form`) — coherente con `opacity` no heredada o un `overflow:hidden` con altura 0.

- **El arreglo pasa a ser permanente y automático**: `65_unhide.js` sustituye al script
  experimental y el ajuste queda **activado por defecto** (se puede apagar como salida de
  emergencia). Si la calibración está activa, además informa de cuántos nodos ha forzado.

- **El mismo arreglo se aplica ahora al REPRODUCTOR**, con un juego conservador: en el
  reproductor `overflow:hidden` y `transform` son parte del diseño (paneles, scroll), así
  que solo se toca lo que de verdad oculta — `opacity<1`, `visibility` distinta de visible,
  `clip-path`, y `overflow:hidden` en elementos de altura 0. Las capturas del reproductor
  enseñaban la cabecera dibujada y todo lo de abajo en negro: el mismo síntoma.

- El fondo magenta del visor vuelve a ser **solo de diagnóstico** (con la calibración).

## [1.0.8]

- **Reparar formulario de login** (Ajustes → Diagnóstico, experimental, apagada por defecto).
  La calibración de la 1.0.7 dio una cadena de estilos IMPECABLE:
  `input(vis=visible op=1 disp=block) < div < div < form`, con el campo en
  `left=24 top=274 w=336 h=52 OK en vista`, y sin embargo no se pinta nada.

  Eso solo lo produce algo por ENCIMA de `<form>` en el árbol, y la cadena medida
  llegaba justo hasta `form`: `opacity` no se hereda, así que un ancestro con
  `opacity:0` deja al hijo con `op=1`; un `overflow:hidden` con altura 0 recorta el
  subárbol mientras el rect del campo sigue siendo correcto; `clip-path` o `transform`
  lo apartan. Queda una tercera vía: texto oscuro sobre fondo oscuro.

  El script **mide la cadena completa** hasta `<html>` (opacidad, visibilidad,
  desbordamiento, altura, transform, filter), **repara** forzando esos valores en toda
  la cadena, y **vuelve a medir** después. Las dos medidas se pintan en pantalla, de modo
  que una sola captura dice si el culpable era un ancestro oculto o si hay que buscar por
  otro lado. Además el fondo del visor pasa a magenta en esa página: si la zona se vuelve
  magenta, la página no pinta nada; si sigue oscura, pinta y el contenido falta por otra
  razón.

## [1.0.7]

- **Modo limpio (sin inyección)** en Ajustes → Diagnóstico. Con él activo la app no
  inyecta NADA: ni spoof, ni control, ni sonda, ni calibración, ni ayudas de login. El
  visor queda como un navegador sin tocar. Es el EXPERIMENTO DE CONTROL.

  Lo pidió la lectura de la capa de calibración: la rejilla de la app SÍ se pintaba
  sobre la página mientras el formulario de Spotify NO, con geometría correcta
  (`left=24 w=336` sobre 384) y `OK en vista`. Eso deja dos culpables — el JavaScript
  que inyectamos o el motor WebView — y ninguna captura los separa. Este interruptor sí:
  si en modo limpio el login se ve, el culpable es nuestro JavaScript; si sigue negro,
  no es nuestro código.

  El corte es un único punto: todos los scripts entran por los métodos `build*` de
  `InjectionComposer`, que devuelven cadena vacía en modo limpio.

- **La calibración ahora dice POR QUÉ** no se ve el formulario: además de las medidas,
  informa del estilo CALCULADO del campo de contraseña y de sus tres ancestros
  (`visibility`, `opacity`, `display`) y del color de fondo real de la página. Un
  `vis=hidden`, un `op=0` o un `disp=none` en cualquier nivel lo explica sin más capturas.

## [1.0.6]

- **Capa de calibración** (Ajustes → Diagnóstico), apagada por defecto. Pinta sobre la
  página una rejilla de medida con coordenadas y un rótulo con los números reales del
  visor: `innerW/innerH` en píxeles CSS y físicos, altura y desplazamiento del documento,
  y la posición exacta del campo de contraseña (con aviso de si queda fuera de la vista).
  Además el fondo del visor pasa a magenta: si la zona oscura se vuelve magenta, el
  WebView no pinta nada de la página; si sigue oscura, la página sí pinta y el contenido
  está en otro sitio. Es la prueba que separa las dos posibilidades de un vistazo.

  Nació de una propuesta del usuario: en vez de seguir interpretando capturas a ciegas,
  fabricar el instrumento. La capa es `pointer-events:none` y no toca el DOM de Spotify:
  solo AÑADE una capa por encima cuando se activa a mano.

- **Incluye el arreglo de la 1.0.5**, que no se entregó por separado: fuera todo el
  código de la app en la página de login (`60_login.js` pasa a solo lectura), detección
  nativa de sesión al llegar al reproductor, y las ventanas emergentes automáticas dejan
  de redirigirse al mismo WebView.

## [1.0.5]

- **Se retira TODO el codigo de la app de la pagina de login.** El diagnostico de
  la 1.0.4 lo dejaba claro: `vw=384 sw=384 zoom=1.0`, layout correcto y SIN
  desbordamiento, formulario presente (`campos=2 pwd=si`) y la zona igualmente en
  negro. O sea: el problema nunca fue el viewport, y las unicas diferencias entre
  esta app y Chrome eran las ayudas que inyectabamos.

  `60_login.js` pasa a ser de solo lectura: ya NO pulsa botones, NO hace scroll,
  NO inserta elementos y NO toca estilos ni el viewport. Unica excepcion: el boton
  de consentimiento de Facebook, que bloquea el flujo si no se pulsa.

- **La sesion se detecta de forma nativa** cuando la navegacion llega a
  `open.spotify.com`, sin depender de ningun script.
- **Las ventanas emergentes automaticas dejan de redirigirse al mismo WebView**:
  solo se atienden las que abre el usuario con un toque. Una emergente automatica
  redirigida aqui navegaba la vista a una pagina vacia y la dejaba en negro.

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

## [1.4.0]

**Se acabo suponer: hay un testigo que dice donde se muere el pixel.**

Estado real con la 1.3.0, medido en el dispositivo: `campos=2 · pwd=sí · visible=sí ·
zoom=1.0` y la pantalla negra. Ademas comprobe en el **servidor**, con el UA de este
telefono, que la pagina que sirve Spotify es HTML plano: un `<form>`, un
`<input id="username">`, cero `content-visibility`, cero `contain`, cero `@layer`, cero
`color-mix`, cero `backdrop-filter`. No hay nada raro que el WebView no sepa pintar.

Lo que si se pinta es lo que inyectamos con `position:fixed` + `z-index:2147483000`. Y ahi
esta la pista que nunca se siguio: `position:fixed` con z-index alto obliga al motor a
crear una **capa de composicion propia**, mientras el contenido de la pagina vive en la
**capa principal** del documento. Si la principal no compone, se ve exactamente lo que
tenemos: fondos y capas propias si, texto y controles no.

Por eso la 1.4.0 va en la direccion contraria a todo lo probado:

- **La capa de pintado depende de la pagina.** Fuera del reproductor (login,
  consentimiento, redirecciones) se usa `LAYER_TYPE_SOFTWARE` — rasteriza en CPU y no
  depende del compositor de GPU. El reproductor mantiene `LAYER_TYPE_HARDWARE`. Hasta
  ahora estaba fijada `HARDWARE` para todo, en `create()`, y esa linea pisaba cualquier
  cambio de perfil: por eso nunca se habia probado.
- Nuevo `67_witness.js`: tres franjas identicas salvo el nivel de pintado —
  **A** en flujo normal (capa principal), **B** en `absolute` z-index 5, **C** en `fixed`
  z-index 10 (capa propia). Solo diagnostico, se quita en cuanto sepamos la respuesta.
- `67_witness.js` neutraliza tambien `content-visibility` y `contain` en la cadena del
  campo, los dos unicos modos de "mide pero no rasterices" que `65_unhide.js` no cubria.

**Como se lee el testigo.** Al abrir el login, arriba sale una banda roja (A), una azul (B)
y una amarilla (C):

- Se ven **A, B y C** → el documento pinta. El fallo es oclusion o subarbol no pintado, y
  el siguiente paso es `chrome://inspect` por USB.
- Se ven **solo C** (o C y B) → confirmado: la capa principal no compone. El renderizado
  software de esta version deberia arreglarlo; si no, el problema es de driver y la salida
  es otra.

## [1.5.0]

**El testigo cerro una puerta y abrio otra.** Medido en el dispositivo con la 1.4.0:

    A estatico  -> SE VE
    B absoluto  -> SE VE
    C fijo      -> SE VE

Las tres. El documento pinta, el motor pinta, la composicion de capas no tiene nada
roto. Y debajo de las franjas, negro absoluto con `campos=2 · pwd=sí`. Asi que el
problema **no** es que la capa principal no componga: era mi hipotesis y queda descartada.

(Correccion de un dato que venia arrastrando: `visible=sí` **no** prueba que el campo se
vea. La sonda solo comprobaba su rectangulo contra la ventana — geometria, no visibilidad.
Lei ese campo como si fuera una prueba de pintado y no lo era.)

**La diferencia que queda entre nuestros nodos y los de Spotify** es de donde sale su
pintado: los nuestros llevan **estilos en linea** (`style="..."`), los de Spotify dependen
de **sus hojas de estilo**, servidas desde `accounts.scdn.co` (comprobado en el HTML real
de la pagina, con el UA de este telefono).

Si esas hojas no se aplican, la pagina queda **sin estilos**: fondo transparente (se ve el
`#121212` que pusimos al WebView) y texto por defecto **negro**. Texto negro sobre fondo
negro. Y los campos de un formulario sin estilos, con ese fondo, tampoco se ven.

- Nuevo `68_ui.js`: mide si la pagina esta **estilada** — numero de hojas, reglas
  legibles, fondo y color calculados del `body`, `display` y `opacity` del `form`, estado
  de `document.fonts`, numero de nodos. Distingue «sin CSS» de «CSS aplicado pero oculto»,
  que son problemas opuestos y llevabamos toda la tarde tratandolos igual.
- Si no hay estilos (o el cuerpo sale transparente), inyecta un **CSS de respaldo** que
  hace legible el formulario: fondo oscuro, texto blanco, campos con borde y 44 px de alto,
  botones verdes. No pisa nada cuando el CSS de Spotify si esta.
- Saca el resultado en un **panel fijo** abajo. Nuestros elementos fijos se pintan (esta
  medido), asi que el panel es un canal fiable aunque la pagina este negra.
- Se retiran `66_stack.js` y `67_witness.js` del login: ya dieron su respuesta y solo
  añadian ruido.

## [1.6.0]

**Version de cero interferencia.** La 1.5.0 midio esto en el dispositivo:

    hojas=7 reglas=22 inaccesibles=2 nodos=102
    bodyBg=rgb(18,18,18) bodyFg=rgb(255,255,255)

Ese fondo y ese color **son de nuestro CSS de respaldo**, inyectado en la primera pasada
(cuando aun habia menos de 20 nodos) y ahi se quedo. O sea: no hay ninguna prueba de que
Spotify aplique su CSS, y ademas no se ve **ni una** de las cajas con borde y fondo que ese
respaldo dibuja. Una hipotesis que no explica el dato que tenemos delante no es una
hipotesis, es una excusa.

Asi que se quita del medio el unico sospechoso que nunca se habia apartado: **nosotros**.

- `buildLoginScript()` en la pagina de login pasa a ser `HOST_BRIDGE + MEASURE`, y nada mas.
  Desaparecen de ahi `65_unhide` (forzaba `opacity`, `visibility`, `clip-path`, `overflow`,
  `transform` y `filter` en toda la cadena de ancestros del campo), `62_classic` (añadia un
  enlace) y `68_ui` (inyectaba CSS).
- La capa de pintado del login pasa a **`LAYER_TYPE_NONE`**, el comportamiento por defecto
  del WebView, que es lo que hace Chrome. La 1.0.10 forzo HARDWARE y la 1.4.0 SOFTWARE; el
  testigo de la 1.4.0 demostro que el motor pinta bien, asi que la capa nunca fue el asunto.
- Nuevo `69_measure.js`: **solo lee**. Numero de hojas, reglas, nodos, `display`/`opacity`
  del form, caja del campo, **quien esta arriba en el centro del campo** segun
  `elementFromPoint`, colores calculados, `docH` y estado de fuentes. Lo pinta en un panel
  propio, sin tocar un solo nodo de la pagina.

**Lo que hay que entender del resultado.** El campo `arriba=` es el juez: dice si en el
punto exacto del campo lo primero que hay es el propio campo (`=CAMPO`) o si hay otra cosa
encima (`TAPA`). Y `nodos` dice si la pagina se construyo de verdad: 102 nodos no es una
pagina de login completa de Next.js; si sigue en ~100, el JavaScript de Spotify no esta
corriendo.

Si con cero interferencia sigue negra: el problema no es nuestro, y a partir de ahi no hay
nada que probar a ciegas. El siguiente paso deja de ser opcional — `chrome://inspect` por
USB, con el telefono conectado, para ver el arbol pintado real.

## [1.6.1]

**El panel salia cortado por culpa nuestra, no del WebView.** La 1.6.0 medía bien, pero el
panel iba anclado abajo (`bottom:0`) y **nuestra propia tarjeta de Compose** —capa nativa,
siempre por encima del WebView— ocupa el tercio inferior de la pantalla. Las líneas que
faltaban (`form=`, `caja=`, `arriba=`, `docH=`, `url=`) no se habían perdido: estaban
tapadas por la app.

- El panel pasa a `top:0`, justo debajo de la barra de la app, donde no hay ninguna capa
  nativa encima. Fuente 9 px y `max-height:58vh` para que quepan las siete líneas.
- Sin más cambios: la página sigue con **cero interferencia** (solo `HOST_BRIDGE + MEASURE`).

Un detalle que ya sabemos y conviene no perder de vista: `nodos=99` y `hojas=5 reglas=18
cruzadas=2`. Las 2 hojas «cruzadas» son las de `accounts.scdn.co`: **sí llegaron** (si no,
no serían hojas), solo que su `cssRules` no es legible por origen. Y 99 nodos es poco para
una página de Next.js ya hidratada, así que la pregunta abierta es si el JS de Spotify está
corriendo.
