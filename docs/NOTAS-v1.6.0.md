# SpotiZado 1.6.0 — cero interferencia

versionCode 34 · versionName 1.6.0 · com.imparable.spoti.debug

## Lo que dijo la 1.5.0

    hojas=7  reglas=22  inaccesibles=2  nodos=102
    bodyBg=rgb(18, 18, 18)  bodyFg=rgb(255, 255, 255)

Dos cosas, y las dos importantes:

1. **Ese fondo y ese color de texto son míos.** Es el CSS de respaldo que inyecta `68_ui.js`.
   Se aplicó en la primera pasada —cuando la página aún tenía menos de 20 nodos— y se quedó.
   Conclusión: **no hay ninguna prueba de que Spotify esté aplicando su CSS**.
2. **Y aun así no se ve ni una de las cajas que mi respaldo dibuja** con borde gris y fondo
   `#2a2a2a`. Eso rompe la teoría del CSS: si mi propio CSS tampoco produce nada visible, el
   problema está por debajo del CSS.

## Qué se quita

En la página de login ya no se inyecta nada que pueda alterarla:

| Antes | Qué hacía | Ahora |
|---|---|---|
| `65_unhide.js` | forzaba `opacity`, `visibility`, `clip-path`, `overflow`, `transform`, `filter` en toda la cadena del campo | **fuera** |
| `62_classic.js` | añadía un enlace al DOM | **fuera** |
| `68_ui.js` | inyectaba CSS de respaldo | **fuera** |
| `LAYER_TYPE_SOFTWARE` | forzaba rasterizado en CPU | **`LAYER_TYPE_NONE`** (el defecto, como Chrome) |

Queda `69_measure.js`: lee y pinta su propio panel. No modifica ni un nodo existente.

## El juez: la línea `arriba=`

    arriba=input#username =CAMPO    → el campo es lo primero en su punto: debería verse
    arriba=div.xxx TAPA             → hay algo encima: oclusión confirmada
    arriba=sin-campo                → el campo no tiene caja: no existe de verdad

Y `nodos=` dice si la página se construyó: **102 nodos no son una página de login completa
de Next.js**. Si sigue en ~100, el JavaScript de Spotify no está corriendo y el HTML que
vemos es solo el esqueleto servido.

## Qué mirar

1. **¿Sale el formulario?** → arreglado, y el culpable éramos nosotros. Seguimos con el adblock.
2. **Panel visible, `arriba=… =CAMPO`, sigue negro** → no es CSS ni oclusión. Sin más pruebas
   ciegas: `chrome://inspect` por USB.
3. **`nodos` sigue ~100** → el JS de la página no corre. Eso apunta a que el motor no ejecuta
   los chunks de `accounts.scdn.co` — y ahí el arreglo es otro.

Un reproductor alternativo con bloqueo de anuncios infringe los ToS de Spotify; el riesgo lo
asume la cuenta que lo use. Proyecto privado, no distribuible.
