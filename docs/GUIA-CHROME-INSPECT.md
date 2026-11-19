# Guía: ver el WebView por USB (chrome://inspect)

Sirve para responder de una vez la pregunta abierta: **¿el JavaScript de Spotify está
corriendo dentro de nuestro WebView, y si no, qué error lo mata?**

El APK de depuración ya trae `WebView.setWebContentsDebuggingEnabled(true)` (solo en builds
`DEBUG`, que es la que usas). No hay que instalar nada más en la app.

## Lo que hace falta

- Un **ordenador** con Chrome o Edge de escritorio.
- Un **cable USB** de datos (no vale uno de solo carga).
- El móvil con la **1.6.1** instalada y la app abierta en la pantalla del login.

## Pasos

### 1. Activar depuración USB en el móvil

Ajustes → Información del teléfono → toca **7 veces** en "Número de compilación"
→ vuelve atrás → **Opciones de desarrollador** → activa **Depuración USB**.

Al conectar el cable, el móvil pedirá **"¿Permitir depuración USB?"** → Permitir
(marca "Recordar siempre").

### 2. Abrir el inspector en el ordenador

Chrome → barra de direcciones → `chrome://inspect/#devices`

Debe aparecer tu dispositivo con una entrada parecida a:

    com.imparable.spoti.debug
      https://accounts.spotify.com/login?continue=...   inspect

Pulsa **inspect**.

### 3. Qué mirar, en este orden

**a) Consola (pestaña Console).** Es lo primero. Busca errores en rojo. Los candidatos:

| Mensaje que aparezca | Qué significa |
|---|---|
| `Refused to load ... because it violates Content Security Policy` | El CSP de Spotify está bloqueando sus propios chunks |
| `SyntaxError` / `Unexpected token` en `main-*.js` | El motor no entiende el JS (motor viejo o chunk corrupto) |
| `Failed to load resource: net::ERR_...` | Las peticiones no salen (red, DNS, o nuestro bloqueo) |
| `Uncaught TypeError: Cannot read properties of null` | El JS corre pero choca con algo del entorno |

**b) Elements (pestaña Elements).** Busca `<form>` y `<input id="username">`. Si existen,
selecciónalos y mira la columna **Computed**: `display`, `visibility`, `opacity`, y el
`background-color` del `body`. Con el ratón encima en el árbol, el elemento se resalta en la
pantalla del móvil: si se resalta **en negro**, el problema es estilo; si se resalta **donde
se ve negro pero el CSS dice que hay algo**, es pintado.

**c) Network.** Recarga (F5 dentro del inspector) y mira si los `.js` de
`accounts.scdn.co` salen en **200** o en rojo. Eso cierra la duda de si los chunks llegan.

**d) Application → Cookies.** Que existan cookies de `accounts.spotify.com`.

### 4. Qué me tienes que mandar

Con esto me basta, en texto o captura:

1. La **consola entera** (los errores en rojo, con su mensaje completo).
2. El resultado de `nodos`: en la consola escribe
   `document.querySelectorAll('*').length` y dale Enter.
3. En la consola:
   `document.querySelector('input#username')` → ¿devuelve un elemento o `null`?
4. En Network: cuántos `.js` están en rojo y cuál es el primero.

## Si el dispositivo no aparece en chrome://inspect

- Otro cable USB (el más común con diferencia).
- En el móvil: Opciones de desarrollador → **Revocar autorizaciones de depuración USB** y
  volver a aceptar el diálogo.
- En Linux hace falta `adb` y reglas udev; en Windows, el driver del fabricante.
- Alternativa sin cable: en el móvil, con la app abierta, abre Chrome y ve a
  `chrome://inspect` — no funciona; el inspector necesita el escritorio.
