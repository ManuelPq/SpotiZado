# SpotiZado 1.8.0 — el diagnostico se lee en la tarjeta, no en la pagina

versionCode 37 · versionName 1.8.0

## Lo que salio mal en la 1.7.0

El panel se veia como **una linea de 2 px sin texto**, y alternaba amarillo y verde. Dos
causas, las dos mias:

1. **Dos paneles en el mismo sitio.** El de logs (borde amarillo) y el de medida (borde
   verde) iban los dos a `top:0` y se solapaban. Por eso el color cambiaba.
2. **En `document-start` no hay `<body>`.** El panel se colgaba de `<html>` antes de que el
   parser creara el cuerpo, y el texto nunca llegaba a dibujarse. Quedaba el borde.

## El arreglo

El diagnostico sale de la pagina y pasa a la **tarjeta nativa**, que es Compose y pinta
siempre. El canal es `evaluateJavascript`: devuelve el valor a Kotlin sin restriccion de
origen y sin depender del arbol de la pagina.

## De paso, un bug de verdad

El puente solo aceptaba `https://open.spotify.com`. Todo aviso enviado desde
`accounts.spotify.com` se descartaba **en silencio**. Llevabamos horas con la pagina donde
mas falta hacia el diagnostico siendo sorda. Ahora acepta los dos origenes.

## Lo que hay que mirar

Pulsa **Datos** en el aviso de la app. Al final hay un bloque nuevo:

    --- por que no se dibuja ---
    nodos=…  next=si|no  hidratado=si|no
    hojas=…  recursos=…
    erroresJS=…
    js=<primer error, con fichero y linea>
    csp=<primera violacion>
    rechazo=<primera promesa>
    consola=<primer console.error>

Los tres desenlaces siguen siendo los mismos, pero ahora uno de ellos viene con nombre:

- `hidratado=no` + `js=` con mensaje → **por fin sabremos que lo mata**.
- `nodos` alto + `hidratado=si` + `err=0` → la pagina vive: es pintado puro, y entonces el
  cable es obligatorio.
- `next=no` y `err=0` → el JS no llega a ejecutarse: problema de red o de CSP, y `recursos`
  lo dira.
