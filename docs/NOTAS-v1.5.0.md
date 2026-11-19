# SpotiZado 1.5.0 — ¿está la página estilada?

versionCode 33 · versionName 1.5.0 · com.imparable.spoti.debug

## Lo que dijo el testigo

| Franja | Cómo se pinta | Resultado |
|---|---|---|
| A `estatico` | flujo normal → capa principal | **se ve** |
| B `absoluto` | `absolute` z-index 5 | **se ve** |
| C `fijo` | `fixed` z-index 10 → capa propia | **se ve** |

Las tres se ven. Eso **descarta mi hipótesis**: la capa principal compone bien, el motor
pinta, la composición de capas está sana. Debajo de las franjas, negro, con `campos=2`.

Y una corrección honesta: `visible=sí` nunca probó que el campo se viera. La sonda solo
compara su rectángulo con la ventana — es geometría, no visibilidad. Lo leí como si fuera
una prueba de pintado y no lo era.

## La única diferencia que queda

| | Nuestros elementos | Los de Spotify |
|---|---|---|
| Estilos | **en línea** (`style="…"`) | **hojas de estilo externas** |
| Origen | el propio script | `accounts.scdn.co/authn-web/_next/static/css/…` |
| Se pintan | sí | no |

Comprobado en el HTML real de la página (con el UA de este teléfono): son 2 hojas CSS y 16
scripts, todos en `accounts.scdn.co`. `accounts.scdn.co` **no** está en la lista de bloqueo
(`allow.txt` tiene `accounts.spotify.com`, `encore.scdn.co`, `i.scdn.co`… pero no ese host).
Que no esté bloqueado no significa que llegue: puede fallar por CSP, por la política de
línea base de WebView o por el propio `SAFE_MODE`.

## Qué pasa si el CSS no se aplica

La página queda **sin estilos**: body transparente → se ve el `#121212` que le pusimos al
WebView; el texto por defecto es **negro**. Negro sobre negro. Los campos de un formulario
sin estilos, contra ese fondo, tampoco se ven. Encaja con todo lo medido, incluido que en
Chrome del teléfono se vea bien.

## Qué hace la 1.5.0

1. **Mide si la página está estilada**: nº de hojas, reglas legibles, `body` fondo y color
   calculados, `display`/`opacity` del `form`, estado de `document.fonts`, nº de nodos.
2. **CSS de respaldo** si no hay estilos: fondo `#121212`, texto blanco, campos con borde
   visible y 44 px de alto, botones verdes. Es lo que hace legible el formulario aunque
   Spotify no aplique su CSS.
3. **Panel fijo abajo** con la medición. Se pinta porque es nuestro y los nuestros se pintan
   (está medido). Borde verde = CSS de Spotify aplicado; rojo = se usó el de respaldo.
4. Se quitan `66_stack` y `67_witness` del login: ya contestaron.

## Qué mirar

- **¿Sale el formulario de email?** Arreglado el login, seguimos con el bloqueo de anuncios.
- **Borde rojo y panel dice `hojas=0`** → confirmado: el CSS no llega. Entonces la solución
  deja de ser cosmética: hay que averiguar por qué (CSP o política del WebView) y el arreglo
  pasa por ahí.
- **Borde verde y sigue negro** → el CSS sí está aplicado, así que el problema está en el
  árbol pintado. Ahí ya solo queda `chrome://inspect` por USB.
- Mándame el panel en captura. Son cuatro líneas.

Un reproductor alternativo con bloqueo de anuncios infringe los ToS de Spotify; el riesgo
lo asume la cuenta que lo use. Proyecto privado, no distribuible.
