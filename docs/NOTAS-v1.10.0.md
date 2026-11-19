# SpotiZado 1.10.0 — el motor pinta; falta saber por qué no pinta ESTA página

versionCode 39 · versionName 1.10.0

## La prueba de texto: superada

La página de prueba (`Probar texto`) se ve **entera**: `HOLA` a 44 px, el párrafo
«Texto de prueba 1 2 3», la caja verde con su texto dentro, el borde rojo y el bloque azul.

**El WebView de este teléfono rasteriza glifos perfectamente.** Queda descartado el motor.

## La página de Spotify: viva

De la tarjeta Datos con la 1.9.0:

    nodos=98  next=si  hidr=si  hojas=5  rec=27  err=0
    csp=CSP script-src wasm-eval
    "¡Hola de nuevo! Correo electrónico Continuar o Continuar con Google…"

Next.js cargó, React hidrató, 27 recursos, **cero errores JS**, y el texto completo está en el
DOM. La única violación de CSP es `script-src wasm-eval`, que es del propio Spotify y no impide
pintar.

## Los tres modos que quedan (y son el mismo problema)

El subárbol existe, tiene texto y el motor decide no dibujarlo:

1. **Opacidad animada congelada.** El login entra con una animación de opacidad. Si las
   animaciones no avanzan —escalas de animación a 0 en Opciones de desarrollador, o el WebView
   considerándose no visible— el elemento se queda en su estado **inicial**: `opacity: 0`.
   Para siempre.
2. **`content-visibility: hidden/auto`** con contención: el motor mide pero salta el pintado.
3. **`contain: paint/size`** con altura 0 en un ancestro: recorta a nada.

## Qué hace esta versión

`71_paint.js` **mide y repara**, en ese orden:

- Mide `opacity` de `body` y `#__next`, animaciones totales/en curso, `content-visibility`,
  `contain`, quién está arriba en el centro de la pantalla, y qué elementos de la cadena
  llevan opacidad < 1 o contención.
- Aplica una reparación que cubre los tres: animaciones a duración ~0 con
  `fill-mode: forwards` (saltan a su estado **final**, que en una entrada por opacidad es 1),
  `content-visibility: visible`, `contain: none`, y opacidad 1 en `html/body/#__next/main`.
- Vuelve a medir y da un **veredicto**: `reparado: oculto por opacidad/animacion`,
  `sigue oculto tras reparar` o `no estaba oculto: buscar oclusion`.

La tarjeta **Datos** muestra ahora: `op=`, `nextOp=`, `anim=`, `cv=`, `centro=`, el veredicto y
la lista de sospechosos.

## Qué mirar

- **¿Aparece el formulario?** Si el veredicto dice *reparado*, la causa era la animación
  congelada y hay que dejarla desactivada de forma permanente.
- **`no estaba oculto`** → no es opacidad. Entonces `centro=` dirá qué elemento hay en mitad de
  la pantalla, y con eso se cierra.
- Los valores `op=` y `nextOp=` son la respuesta en crudo: si alguno es `0`, está dicho.

Un reproductor alternativo con bloqueo de anuncios infringe los ToS de Spotify; el riesgo lo
asume la cuenta que lo use. Proyecto privado, no distribuible.
