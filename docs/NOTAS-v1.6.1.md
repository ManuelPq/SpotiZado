# SpotiZado 1.6.1 — el panel ya no se corta

versionCode 35 · versionName 1.6.1

## Qué pasó con la 1.6.0

Medía bien, pero el panel iba anclado **abajo** y nuestra propia tarjeta de Compose ocupa
ese tercio de la pantalla. Las líneas `form=`, `caja=`, `arriba=`, `docH=` y `url=` no se
perdieron: **las tapaba la app**.

Ahora el panel va arriba. Siete líneas, se leen enteras.

## Lo que ya sabemos de la 1.6.0 (cero interferencia)

    hojas=5  reglas=18  cruzadas=2  nodos=99
    bodyBg=rgb(18,18,18)  bodyFg=rgb(255,255,255)

- `cruzadas=2` son las hojas de `accounts.scdn.co`: **sí llegaron**. Si no hubieran cargado,
  no aparecerían como hojas; solo que su `cssRules` no es legible por origen.
- `bodyFg` blanco con cero CSS nuestro significa que **algo de Spotify sí se aplicó**.
- `nodos=99` es poco para una página de Next.js hidratada. Esa es la pregunta abierta.

## Qué hay que leer ahora

De las siete líneas, las dos que deciden:

    caja=<left>,<top> <ancho>x<alto>     ← ¿el campo tiene caja real y dónde?
    arriba=input#username =CAMPO|TAPA    ← ¿qué hay en su punto exacto?

Con eso y `nodos`, se acaba la conversación de suposiciones.
