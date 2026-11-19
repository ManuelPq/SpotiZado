# SpotiZado v1.2.0 — notas de versión

**versionCode:** 29 · **versionName:** 1.2.0 · `com.imparable.spoti.debug` (actualiza encima)

---

## 1. Qué he leído, y qué dice el código que sí funciona

`Quackify` (github.com/23fpsz/Quackify) es la **continuación oficial de SpotiDuck**. Y ya no
es un envoltorio de WebView: tiene reproductor nativo (`krespot-*`, port de librespot),
interfaz Compose nativa, y usa el WebView **solo para el login**, para leer el `accessToken`
del `<script id="session">` y pasarle el relevo al reproductor nativo.

**Aviso legal, y es serio:** el `LICENSE` del repositorio dice GPL-3.0, pero **cada fichero
de código** lleva una cabecera que dice *«PROPRIETARY / SOURCE-AVAILABLE — copying,
modifying, distributing or integrating is strictly prohibited»*. Es una contradicción, y
ante ella la lectura prudente es la restrictiva: **se puede leer y auditar, no copiar**.
Así lo he hecho: he leído su configuración de WebView y he aplicado **valores de
configuración**, no su código.

## 2. Corrección de un error mío de la 1.1.1

En la 1.1.1 fijé un User-Agent de Chrome a mano para todas las páginas, creyendo que el UA
"legado" era la causa. **El cliente que funciona hace lo contrario**: su comentario literal
es *«init ua=native, desktop only on the player»* — UA nativo en el login, escritorio solo
al llegar al reproductor. Y usa el mismo UA nativo que nos salía a nosotros, el que yo
llamaba "legado".

**Mi teoría del UA era falsa.** Se revierte al UA nativo.

## 3. Cinco diferencias de configuración, ahora igualadas

| Ajuste | Lo nuestro (1.1.1) | El que funciona | Ahora |
|---|---|---|---|
| UA en login | Chrome fijado a mano | **Nativo del motor** | Nativo |
| `useWideViewPort` | Solo en escritorio | **Siempre activo** | Siempre |
| `loadWithOverviewMode` | Solo en escritorio | **Siempre activo** | Siempre |
| `setInitialScale` | `0` | **No se llama** | No se llama |
| Cookies de terceros | No se tocaban | **Aceptadas explícitamente** | Aceptadas |
| `builtInZoomControls` | `false` | **`true`** (controles ocultos) | `true` |

Y la URL de login vuelve a la forma sin prefijo de idioma:
`https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F`

## 4. Por qué estas cinco y no otras

Porque son **exactamente** las que difieren entre dos clientes que cargan la misma página en
el mismo teléfono: el suyo se ve, el nuestro no. Ya he descartado por medida el viewport
(`vw=384 sw=384 zoom=1.0`), la maquetación del formulario (`OK en vista`), la cadena de
ancestros, el `LAYER_TYPE_HARDWARE`, el pre-raster y el `allow_password`. Quedaba esto.

## 5. Qué probar

1. Instala encima y abre. **Sin tocar nada** — ni modo limpio, ni calibración.
2. ¿Aparece el formulario de login?
3. Si no aparece: **Diagnóstico (ⓘ) → Copiar informe** y pégalo en texto.

## 6. Si esto tampoco funciona

Entonces no queda hipótesis razonable dentro del WebView, y la conclusión es que el motor de
este teléfono no sirve esa página a nuestra app por alguna razón que solo se ve con el
depurador de Chrome conectado por USB (`chrome://inspect`). Ese es el siguiente paso real,
y no otra versión.

## 7. Aviso legal

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify;
el riesgo lo asume la cuenta que use la app. Proyecto privado, no distribuible.
