# SpotiZado 1.2.1 — el login vuelve a tener su script de visibilidad

versionCode 30 · versionName 1.2.1 · package com.imparable.spoti.debug

## Lo que muestra la captura del movil (v1.2.0)

    SpotiZado — Sin reproduccion
    [ aviso nuestro, no de Spotify ]
    Falta iniciar sesion
    v1.2.0 · campos=1 · pwd=no · visible=no · vw=384 · sw=384 · zoo...

El area del WebView esta **entera en negro**. No hay formulario, ni texto, ni imagenes:
nada de Spotify. El unico contenido en pantalla es nuestra propia interfaz (cabecera y
aviso).

## Que significa ese diagnostico

- `campos=1` — hay **un** campo en el DOM. La pagina se descargo y se parseo.
- `pwd=no` — no hay campo de contraseña: Spotify sirve la variante *passwordless*.
- `visible=no` — el campo existe pero **no se pinta**.
- `vw=384 · sw=384` — el ancho de la vista coincide con el del contenido: no hay
  desbordamiento, ni escala mal puesta, ni problema de maquetacion.

Conclusion: no es un fallo de login, ni de URL, ni de User-Agent, ni de viewport. Es
**contenido en el DOM que no se hace visible**.

## La causa: un fallo de cableado nuestro

`65_unhide.js` fuerza `opacity`, `visibility`, `overflow`, `clip-path`, `transform` y
`filter` en toda la cadena de ancestros hasta `<html>`. Su cabecera documenta la medicion
que lo justifica, hecha en la capa de calibracion de la 1.0.7:

    form pwd: left=24 top=274 w=336 h=52  OK en vista
    cadena: input(vis=visible op=1 disp=block) < div < div < form
    fondo:  html=rgba(0,0,0,0) body=rgb(18,18,18)  texto=240 car.

La caja estaba perfecta y no se pintaba nada. Al forzar la cadena, **el formulario
aparece**.

Pero `InjectionComposer.buildLoginScript()` cargaba solo:

    HOST_BRIDGE + LOGIN + CLASSIC

`UNHIDE` no estaba. Desde la 1.0.9 el script se aplicaba unicamente al reproductor, asi
que **la pagina de login nunca recibio el arreglo**: se quedo con la pantalla negra y el
formulario oculto, mientras el diagnostico seguia informando `visible=no` sin que nadie
lo leyera como lo que era.

## Cambios de esta version

1. `buildLoginScript()` carga tambien `65_unhide.js`:
   `HOST_BRIDGE + LOGIN + CLASSIC + window.__spotiCalibOn=<ajuste> + UNHIDE`.
2. Nuevo `62_classic.js`: boton fijo "Entrar con email y contraseña" que recarga la misma
   pagina con `?allow_password=1` (la servida por Spotify ya no trae campo de contraseña).
   No se inyecta si ya hay `input[type=password]`; reintenta 20 veces cada 700 ms.
3. `PlayerViewModel.openClassicLogin()` y el boton en el aviso.

La configuracion de WebView de la 1.2.0 **no se toca**: UA nativo en login, wide viewport
siempre, sin `setInitialScale`, cookies de terceros activas.

## Que hay que mirar en el movil

Al abrir, el aviso deberia pasar de `visible=no` a `visible=si` y aparecer el **formulario
de email**. Si sigue `visible=no`, el culpable esta fuera de la cadena que recorre el
script (un `clip-path` en un contenedor superior, un `transform` en un ancestro con
`filter`, o contenido inyectado en un `<iframe>` sombra), y el siguiente paso es el
informe de texto completo con **Datos** para ver la cadena medida.

Si `visible=si` pero sigue negro, entonces si es pintado/superficie y el paso es
`chrome://inspect` por USB.
