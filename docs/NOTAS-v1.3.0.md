# SpotiZado 1.3.0 — la capa que tapa el contenido

versionCode 31 · versionName 1.3.0 · com.imparable.spoti.debug

## Lo que cambio entre la 1.2.0 y la 1.2.1

La 1.2.1 metio `65_unhide.js` en la pagina de login (antes solo corria en el reproductor).
Resultado en el movil:

| | 1.2.0 | 1.2.1 |
|---|---|---|
| campos | 1 | 2 |
| pwd | no | **sí** |
| visible | no | **sí** |

Y aparecio el boton verde en la pagina. O sea que el diagnostico se movio de verdad: el
formulario clasico se carga, esta en el DOM y su caja cae dentro de la vista.

Pero **la pantalla sigue negra**.

## La deduccion

Nuestro propio boton verde —inyectado por `62_classic.js` con `position:fixed` y
`z-index:2147483000`— **si se pinta**. Es el mismo documento, el mismo WebView, el mismo
motor y la misma superficie de pintado. Si lo nuestro se ve y lo de Spotify no, no es un
problema de pintado: **hay algo opaco por encima del contenido de Spotify**. Nuestro boton
se ve porque va por delante de esa capa.

## Que hace la 1.3.0

`66_stack.js`, inyectado en el login despues de `65_unhide.js`:

1. **Mide** con `document.elementsFromPoint()` en el centro del campo. Devuelve la pila real
   de elementos bajo ese punto, de arriba abajo. Todo lo que quede por encima del campo es
   candidato a taparlo.
2. **Busca cortinas**: elementos `fixed`/`absolute` que cubren >=85 % de la ventana con
   fondo opaco (alfa >= .85) — pantallas de carga, overlays de consentimiento, capas con
   `backdrop-filter`.
3. **Repara**: las retira con `display:none` (marcadas con `data-spoti-hidden`), salvo que
   contengan al propio formulario.
4. **Vuelve a medir** para comprobar que el campo queda el primero de la pila.

No toca el formulario, ni sus campos, ni sus valores. Solo aparta lo que se le pone delante.

## Que mirar

- Si ahora **aparece el formulario** (email + contraseña, o al menos el campo de email):
  arreglado, y seguimos con el bloqueo de anuncios.
- Si sigue negro: activa **Calibración** en los ajustes y reabre el login. Aparecera una
  franja verde abajo con `CAPAS v1.3.0 — <veredicto>`, la pila medida y las capas
  encontradas. Mandamela. Si dice «sin capa: nada tapa el campo», la hipotesis cae y el
  siguiente paso es `chrome://inspect` por USB — ahi ya se ve el arbol pintado de verdad,
  sin adivinar.

Un reproductor alternativo con bloqueo de anuncios infringe los ToS de Spotify; el riesgo
lo asume la cuenta que lo use. Proyecto privado, no distribuible.
