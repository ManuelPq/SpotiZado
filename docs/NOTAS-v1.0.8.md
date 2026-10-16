# SpotiZado v1.0.8 — notas de versión

**versionCode:** 23 · **versionName:** 1.0.8 · **applicationId:** com.imparable.spoti.debug (actualiza encima)

---

## 1. Lo que dijo la calibración (esto es lo importante)

La capa de calibración funcionó. Cuatro lecturas reales:

```
form pwd: left=24 top=274 w=336 h=52   OK en vista
cadena: input(vis=visible op=1 disp=block) < div(...) < div(...) < form(...)
fondo:  html=rgba(0,0,0,0) body=rgb(18,18,18)
texto=240 car.
```

Traducido: **la caja del formulario está perfecta.** Colocada, en vista, con estilos
calculados visibles, opacos y en `block` en sus cuatro niveles. El recuadro verde que
pinta mi rejilla marca exactamente dónde está — y dentro no se ve nada.

Así que el contenido **no está mal colocado: no se pinta**. Y eso ya no es un problema
de CSS del formulario.

## 2. Solo queda una vía: algo por encima de `<form>`

La cadena que medí llegaba **hasta `form`**, y ahí se acababa. Pero el culpable puede
estar más arriba, y por eso no aparece:

- **`opacity:0` en un ancestro** — `opacity` no se hereda, así que el hijo sigue
  diciendo `op=1` en su computed. Encaja perfecto con lo medido.
- **`overflow:hidden` con altura 0** en un ancestro — recorta el subárbol, mientras el
  `getBoundingClientRect()` del campo sigue devolviendo su caja correcta.
- **`clip-path` o `transform`** en un ancestro — lo apartan o lo borran.
- Y una tercera vía: **texto oscuro sobre fondo oscuro** (`color` casi igual a `background`).

## 3. El instrumento nuevo: mide, repara y vuelve a medir

**Ajustes → Diagnóstico → "Reparar formulario de login (experimental)".** Apagada por defecto.
Cambia el ajuste y vuelve a abrir el reproductor.

Al activarla, en la página de login:

1. **Mide la cadena COMPLETA** hasta `<html>`: opacidad, visibilidad, desbordamiento,
   altura, `transform` y `filter` de cada ancestro.
2. **Repara**: fuerza `opacity:1`, `visibility:visible`, `overflow:visible`,
   `clip-path:none`, `transform:none` y `filter:none` en toda la cadena. Y si el texto
   tiene el color del fondo, lo pasa a blanco dentro del formulario.
3. **Vuelve a medir** y pinta las dos medidas en un recuadro amarillo arriba.

Y el fondo del visor pasa a **magenta** en esa página: si la zona se vuelve magenta, la
página no pinta nada; si sigue oscura, la página pinta y el contenido falta por otra razón.

## 4. Por qué esto no es otra conjetura a ciegas

Porque el resultado es binario y **la propia captura lo dice**:

| Lo que veas | Qué significa |
|---|---|
| El formulario **aparece** | Era un ancestro oculto. Arreglado, y la cadena "DESPUÉS" lo confirma. |
| **Sigue negro**, pero el recuadro muestra la cadena con un ancestro raro (`op=0`, `ov=hidden`, `h=0`) | El culpable está identificado con nombre y apellidos, y la 1.0.9 va directa a él. |
| **Sigue negro** y la cadena sale toda limpia | No es un ancestro: es el texto del color del fondo o el motor. Y el propio recuadro lo dirá. |

En cualquiera de los tres casos salgo de esta con un dato, no con una hipótesis.

## 5. Aviso legal y seguridad

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify;
el riesgo lo asume la cuenta que use la app. Experimento privado.

El token clásico de GitHub usado para los push sigue vigente: **revócalo** en
GitHub → Settings → Developer settings → Tokens (classic).
