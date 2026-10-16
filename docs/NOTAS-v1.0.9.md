# SpotiZado v1.0.9 — notas de versión

**versionCode:** 24 · **versionName:** 1.0.9 · **applicationId:** com.imparable.spoti.debug (actualiza encima)

---

## 1. El login funciona

Verificado en tu dispositivo: al forzar la visibilidad de la cadena de ancestros, el
formulario aparece. **El culpable era un ancestro del árbol que ocultaba el subárbol** — y
estaba fuera del alcance de lo que yo medía, porque mi cadena llegaba solo hasta `<form>`.

Eso encaja con los dos mecanismos que había señalado, y explica por qué ninguna de mis
versiones anteriores podía arreglarlo:

- `opacity` **no se hereda**: un ancestro con `opacity:0` deja al hijo diciendo `op=1` en su
  computed. Por eso la cadena medida salía impecable.
- O un `overflow:hidden` con altura 0, que recorta el subárbol mientras el
  `getBoundingClientRect()` del campo sigue devolviendo su caja correcta.

Las dos cosas producen exactamente lo que medimos: **caja perfecta, nada pintado.**

## 2. Qué cambia en la 1.0.9

| Cambio | Detalle |
|---|---|
| El arreglo pasa a **permanente y automático** | `65_unhide.js` sustituye al script experimental. Ya no hay que activar nada. |
| El ajuste queda **activado por defecto** | Se puede apagar desde Ajustes como salida de emergencia. |
| **El mismo arreglo se aplica al reproductor** | Con un juego de propiedades conservador. Ver abajo. |
| El fondo magenta vuelve a ser **solo de diagnóstico** | Solo aparece con la calibración activada. |

### Por qué el reproductor lleva un juego distinto

En el **login** se fuerza el juego completo (`opacity`, `visibility`, `overflow`,
`clip-path`, `transform`, `filter`): la página es simple y está probado que funciona.

En el **reproductor** no se puede hacer lo mismo: ahí `overflow:hidden` y `transform` son
parte del diseño (paneles y scroll), y forzarlos rompería la interfaz. Así que solo se toca
lo que de verdad oculta:

- `opacity` menor que 1 → 1
- `visibility` distinta de visible → visible
- `clip-path` definido → ninguno
- `overflow:hidden` **solo** en elementos de altura 0 (recorte de subárbol; con altura es
  scroll legítimo)

## 3. Por qué esto puede arreglar también el reproductor

Las capturas del reproductor enseñaban **la cabecera dibujada y todo lo de abajo en negro**:
"solo asoma «brir Premium»". Es el mismo síntoma que tenía el login — contenido a medias,
con una parte fuera de la vista. Si la causa es la misma (un ancestro que oculta el
subárbol), este arreglo lo resuelve.

No lo puedo prometer: en el reproductor el juego es conservador y puede que el culpable sea
justo una de las propiedades que no me atrevo a forzar ahí (`transform`). Pero es la
primera vez que ataco el reproductor con la causa **confirmada** en lugar de con una teoría.

## 4. Qué probar

1. Instala encima. **El login debe salir solo, sin activar nada.**
2. Entra y llega al reproductor: **¿aparece la interfaz de Spotify?**
3. Si el reproductor sigue negro: activa **Ajustes → "Capa de calibración"** (junto con el
   arreglo, que ya viene activado) y mándame el rótulo. Ahora el arreglo también informa de
   **cuántos nodos ha forzado** y de cómo queda la cadena después.

## 5. Aviso legal y seguridad

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify;
el riesgo lo asume la cuenta que use la app. Experimento privado.

El token clásico de GitHub usado para los push sigue vigente: **revócalo** en
GitHub → Settings → Developer settings → Tokens (classic).
