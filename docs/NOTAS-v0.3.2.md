# SpotiZado v0.3.2 — el ajuste que no hacia nada

## Lo que dicen tus dos respuestas

- **Cuenta gratuita**: el README del proyecto hermano SpotiDuck advierte de que
  «las cuentas gratuitas pueden experimentar errores al cargar la reproduccion en
  WebViews moviles». Encaja con `0:00` clavado y `Elementos de audio/video = 0`.
- **Suena en Chrome del telefono**: descarta cuenta, red y DRM. El problema esta en
  nuestro WebView, que es la unica variable distinta.

## El defecto encontrado

La app tiene en Ajustes «Modo del reproductor: Movil / Escritorio». **No hacia nada.**
En el codigo habia `desktop = true` y `desktopMode = true` fijos en tres sitios, asi
que el ajuste solo cambiaba el boton en pantalla.

Ahora manda el ajuste, y el valor por defecto es **Movil**: con cuenta gratuita el
reproductor web movil es el que Spotify disena para navegadores de telefono y es
donde el audio tiene alguna posibilidad.

En modo Movil: UA movil, sin spoof de 1920, sin CSS de escritorio. El reproductor
sera el de Spotify para movil, asi que **es normal que los selectores de control
(`play=`) den `no`**: eso es lo siguiente, primero va el audio.

## Que mirar al pulsar Datos

```
v0.3.2 | seguro=SI | drm=widevine OK | ready=… | play=… | audio=… | modo=mobile
layout=… vp=… dpr=…
medio=…
ua=…
```

| Dato | Que significa |
|---|---|
| **`medio=`** | `sin medio` = Spotify no ha creado el elemento de audio. `0.0s rs1 pausado` = existe pero no arranca. `1.3s rs4 sonando` = **suena** |
| **`ua=`** | Que navegador cree ser. Debe decir movil (`Android`, `Mobile`) |
| **`audio=`** | Cuantos elementos hay: 0 = no hay ninguno |
| **texto de la pagina** | Si Spotify muestra un error de reproduccion, aqui se lee |

Con `seguro=SI` el bloqueo sigue apagado: no puede ser el culpable.
