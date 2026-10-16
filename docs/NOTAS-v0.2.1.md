# SpotiZado v0.2.1 — qué se ha arreglado y qué necesito de ti

## 1. Lo que revelan tus capturas (gracias, son exactas)

| Captura | Lo que demuestra |
|---|---|
| `KofuBCgB` | **El consentimiento GDPR se dibuja** (ACEPTO / RECHAZAR TODO). La corrección de la lista blanca funcionó |
| `UvNiGsLL`, `iuEQ5M6F` | **El reproductor carga** — «Viento de Cara / Supersubmarina», `0:00 / 4:17`, controles. No es un fallo de login |
| `qxtk8wm2` | `v0.2.0 · campos=2 · pwd=sí · visible=sí · vw=384 · sw=384` |

## 2. Causa raíz del formulario minúsculo: `loadWithOverviewMode`

**Era `loadWithOverviewMode = true` en la página de login.**

Con esa opción el WebView **reduce la página ENTERA** para encajarla en la pantalla. La página de login es larga, así que la reduce muchísimo: el formulario quedaba a ~5 % de la pantalla, con scroll forzado.

Detalle importante: `vw=384` y `sw=384` **eran correctos** — no había desbordamiento. El maquetado estaba bien; lo que estaba mal era la **escala visual**. Por eso ninguna de mis medidas anteriores podía detectarlo: medían el viewport de maquetación, no el zoom aplicado.

**Corregido:** `loadWithOverviewMode` ahora es `true` solo en el reproductor (donde interesa para el diseño de escritorio) y **`false` en el login**, que así usa el ancho real. Igual que `setInitialScale`.

**Y añadida la medida que faltaba:** `visualViewport.scale`. Si vuelve a pasar algo así, saldrá como número.

## 3. Defecto propio encontrado en el bloqueo de anuncios (música muda)

Buscando por qué no suena, revisé nuestra propia capa de neutralización y encontré **un fallo que podía dejar la música muda de forma permanente**:

```js
var suspicious = trackMissing() || domSaysAd();
```

`trackMissing()` devuelve «hay anuncio» cuando el texto de la pista está **vacío** — algo que ocurre de forma **normal** mientras una canción carga o al cambiar de pista. Resultado: **falso anuncio → `muteAll(true)` → música muda**.

Y había un segundo problema: para poder silenciar, el script **sustituía `window.AudioContext`** por una función propia. Eso es invasivo y puede romper el arranque del audio de Spotify, que comprueba el constructor.

**Corregido:**

| Antes | Ahora |
|---|---|
| `trackMissing()` por sí solo abría el estado de anuncio | **Solo la señal del DOM** confirma un anuncio, y debe repetirse **2 ticks** (~1,4 s) |
| Silenciaba en cualquier modo | Silencia **solo** en modo silenciar; en modo saltar no toca el audio |
| Sustituía `window.AudioContext` | **No se sustituye nada global**; solo se envuelve `createGain` en una instancia existente |
| Un falso positivo podía durar hasta 40 s | **Watchdog a 8 s**: si ya no hay señal de anuncio, sale del estado y **restaura el audio siempre** |

Añadido el evento `adguard: false-positive-cleared` para verlo en Diagnóstico.

## 4. Lo que necesito de ti (dos números)

He añadido a la sonda una comprobación de **Widevine**. Es importante: **el reproductor web de Spotify descifra el audio con DRM (Widevine/EME)**. Si tu dispositivo no lo soporta, la música **nunca sonará** por mucho que todo lo demás esté bien — y ninguna corrección de código lo arreglaría.

Instala `SpotiZado-v0.2.1-debug.apk`, entra y abre **Diagnóstico**. Mándame:

1. **«Widevine (DRM)»** → debe decir `widevine OK`. Si dice `SIN widevine`, ese es el problema y es del dispositivo.
2. **«Escala VISUAL»** → debe salir `1`. Si sale menos de 1, el zoom sigue mal.
3. Si puedes, también **«Anuncios neutralizados»**: si sube mientras suena música normal, el detector sigue dando falsos positivos.

## 5. Sobre «no hay interactividad en los menús»

Esto aún no lo tengo resuelto y no quiero inventar una causa. Dos hipótesis que la próxima medición descartará:

- Si el reproductor arranca pero **el audio falla por DRM**, Spotify deja la interfaz a medias: es coherente con «no responde».
- Si el audio **sí** arranca y solo fallan los clics, entonces el problema está en nuestra capa de eventos y lo ataco por separado.

El dato de Widevine separa las dos. Por eso lo pido antes de tocar nada más.

## 6. Aviso legal

Filtrar anuncios en un reproductor alternativo **infringe los Términos de Uso de Spotify**. El riesgo lo asume la cuenta que use la app. Proyecto técnico.
