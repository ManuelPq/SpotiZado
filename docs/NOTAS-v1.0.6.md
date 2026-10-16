# SpotiZado v1.0.6 — notas de versión

**versionCode:** 21 · **versionName:** 1.0.6 · **applicationId:** com.imparable.spoti.debug (actualiza encima)

---

## 1. Tu idea, hecha herramienta

Propusiste pintar algo de fondo que me ayudara a ver la pantalla. Es exactamente lo que
faltaba: hasta ahora yo interpretaba capturas a ciegas y encadenaba hipótesis. Ahora hay
un instrumento.

**Ajustes → Diagnóstico → "Capa de calibración".** Apagada por defecto. Al activarla, sobre
la página se pinta:

- una **rejilla** cada 100 px con sus coordenadas en los bordes;
- un **rótulo fijo** arriba con los números reales del visor;
- un **recuadro verde** marcando el campo de contraseña;
- y el **fondo del visor en magenta**.

Cambia el ajuste y vuelve a abrir el reproductor (o pulsa "Recargar").

## 2. Cómo se lee (esto es lo importante)

El fondo magenta es la prueba que decide todo de un vistazo:

| Lo que ves | Qué significa |
|---|---|
| La zona oscura se vuelve **magenta** | El WebView **no está pintando nada** de la página. El problema es del motor o de la superficie, no del contenido. |
| La zona sigue **oscura** | La página **sí pinta**. El contenido está ahí, pero en otro sitio o tapado. |

Y el rótulo da los números que antes me faltaban:

| Dato | Qué descarta |
|---|---|
| `innerH` | Si es diminuto (20, 60…), el visor está **colapsado**: tiene altura de casi nada. Si es grande (1500+), la página es un documento largo. |
| `scrollH` / `scrollY` | Si `scrollH` es mucho mayor que `innerH`, la página es larga y puede estar desplazada. `scrollY` dice dónde. |
| `form pwd … OK en vista / << ARRIBA / >> DEBAJO` | Dónde está el campo de contraseña **respecto a lo que se ve**. Si está fuera, el recuadro verde aparece como barra amarilla en el borde. |

## 3. Lo que necesito

Dos capturas, con el aviso de la app **cerrado del todo** (la ✕) para que se vea la zona limpia:

1. **Con la calibración activada** en el login: se verá la rejilla y el rótulo.
2. Y si puedes, el **rótulo legible** (es texto pequeño; si sale borroso, mándame los valores
   escritos a mano: `innerW`, `innerH`, `scrollH`, `scrollY` y la línea de `form pwd`).

Con la rejilla y el magenta ya no adivino: sé si el visor pinta, y si pinta, dónde está el
contenido.

## 4. Incluye el arreglo de la 1.0.5 (no te la entregué suelta)

Para no hacerte instalar dos APKs, la 1.0.6 trae también lo de la 1.0.5:

- Fuera **todo** el código de la app en la página de login: `60_login.js` pasa a solo lectura.
  Ya no pulsa botones, no hace scroll, no inserta elementos ni toca el viewport.
- La sesión se detecta **de forma nativa** al llegar a `open.spotify.com`.
- Las ventanas emergentes **automáticas** dejan de redirigirse al mismo WebView (una
  emergente vacía redirigida ahí dejaba la vista en negro).

## 5. Aviso legal y seguridad

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify;
el riesgo lo asume la cuenta que use la app. Experimento privado.

El token clásico de GitHub usado para los push sigue vigente: **revócalo** en
GitHub → Settings → Developer settings → Tokens (classic).
