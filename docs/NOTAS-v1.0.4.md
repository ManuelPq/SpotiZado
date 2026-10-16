# SpotiZado v1.0.4 — notas de versión

**versionCode:** 19 · **versionName:** 1.0.4 · **applicationId:** com.imparable.spoti.debug (actualiza encima)

---

## 1. El fallo de la 1.0.3 era mío, y era un cambio que no venía a cuento

En la 1.0.3 arreglé el desbordamiento del reproductor y, **de propina**, quité el
tamaño declarado (1920x1080) pensando que era "limpieza". No lo era: era un cambio
no medido dentro de una versión cuyo objetivo era otro.

Resultado, en tus capturas: `campos=2 · pwd=sí` — **el formulario está en la página** —
pero la zona de la web sale negra y no se ve nada.

Vuelve tal cual estaba en la 1.0.2, que es donde el formulario se veía.
**El forzado del meta viewport a 1920 NO vuelve**: eso sí era el fallo del
desbordamiento, y sigue fijado a `device-width`.

Regla que me salto yo mismo y que no vuelvo a saltarme: un cambio = una versión.

## 2. Qué cambia en la 1.0.4

| Cambio | Por qué |
|---|---|
| Tamaño declarado (1920x1080) restaurado | Es lo único que cambió entre el login que se veía (1.0.2) y el negro (1.0.3). Se revierte. |
| El formulario se coloca en pantalla **en el acto** | Se elimina el scroll suave: dejaba el WebView a medio camino y se veía una zona vacía. Ahora se calcula la posición a un tercio desde arriba, sin animación. |
| La línea de sonda ya no se corta | Pasa a dos líneas e incluye el zoom. En una sola, `visible` y `vw/sw` quedaban tapados por el botón "Datos" — justo los datos que hacen falta. |
| **"Recargar"** siempre a mano | Si la página se queda en blanco, tienes salida sin cerrar la app. |

## 3. El login: qué deberías ver

**Ajustes → Interfaz → Modo del reproductor → Móvil** (la 1.0.3 ya lo pone por defecto,
pero tu preferencia guardada es Escritorio y se respeta: cámbialo a mano).

Luego abre el reproductor. El formulario de email y contraseña debe aparecer en la
mitad superior, **sin scroll horizontal** y sin zonas negras.

## 4. Si vuelve a salir en negro: dos números y lo tengo

Toca **"Datos"** en el aviso. Ahora la línea se lee entera. Me hacen falta:

- `visible=` (sí o no)
- `vw=` y `sw=` (los dos)

Con eso sé si el problema es de colocación o de ancho, y no adivino.

## 5. Aviso legal y seguridad

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de
Spotify; el riesgo lo asume la cuenta que use la app. Experimento privado.

El token clásico de GitHub usado para los push sigue vigente: **revócalo** en
GitHub → Settings → Developer settings → Tokens (classic).
