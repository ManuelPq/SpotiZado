# SpotiZado v0.3.0 — build de diagnóstico

> **Esta versión está pensada para MEDIR, no para usar todos los días.** Trae el
> bloqueo desactivado a propósito (ver punto 3). Dice `seguro=SI` en la banda de
> datos, para que no haya duda de qué está corriendo.

---

## 1. Lo que he entendido de tus capturas

Antes de tocar nada, esto es lo que dicen tus ocho capturas, en frío:

| Captura | Lectura |
|---|---|
| `BInadKND` | `campos=0 · vw=0 · sw=0` → «Iniciando el reproductor…»: ahí **no había página cargada** |
| `ACscPLex`, `EiZ1mHPi` | `campos=0 · pwd=no · vw=384 · sw=384`, URL `accounts.spotify.com/es/status?flow_ct…` → estamos en la **página de estado del flujo OAuth**, no en el login |
| `KpARIRAW`, `pjULT2DM`, `PJIMGRVQ` | `campos=2 · pwd=sí`, pero `visible=sí` en unos y **`visible=no` en otros** con los mismos valores: la visibilidad **oscila** |
| `RLqIvh8o` | El consentimiento GDPR se dibuja y ocupa ~60-65 % del alto |
| `KptjPclE` | El reproductor de escritorio **sí carga** («Viento de Cara», barra de reproducción estilo escritorio) |

Dos conclusiones que cambian el enfoque:

**a) No es un problema de login.** El login se dibuja (`campos=2 · pwd=sí`). El fallo
está en **cuánto sitio ocupa la página en pantalla** y en que **el audio no sale**.

**b) La tarjeta de aviso se estaba comiendo la pantalla.** En tus capturas ocupa
entre el **35 % y el 40 % del alto**, más la cabecera y la barra de reproducción.
Con eso, al reproductor le queda menos de la mitad. Por eso «la pantalla no usa el
100 %»: literalmente, nuestra interfaz le robaba el sitio.

## 2. Los tres cambios

### a) La tarjeta de aviso pasa a **banda de una línea**

Ocupa una sola línea plegada y solo se abre si pulsas **Datos**. Devuelve al
reproductor el 35-40 % de la pantalla que se estaba perdiendo.

### b) El viewport del login, a comportamiento móvil normal

`useWideViewPort` era `true` **siempre**, incluido el login. En una página móvil eso
activa el modo panorámico (ancho de 980 px) y, con el zoom automático, reduce el
formulario a una franja diminuta. Ahora es `true` **solo en el reproductor**; en el
login vale `false`, que es el comportamiento normal del WebView: la página usa el
ancho del dispositivo tal cual, sin escalas impuestas.

### c) **Modo seguro**: bloqueo apagado para aislar el audio

El filtrado de red y la neutralización quedan **desactivados por completo**
(`SAFE_MODE = true`). No es una precaución teórica: el README del proyecto hermano
**SpotiDuck** advierte literalmente de que *«cargar listas de filtros agresivas puede
bloquear endpoints esenciales del servidor, impidiendo que suenen las pistas»*. La
nuestra va en modo **STRICT con 1.316 reglas**.

Ese mismo README avisa también de dos cosas que encajan con lo que ves:

> «Las cuentas gratuitas pueden experimentar errores al cargar la reproducción en
> WebViews móviles.»
> «La reproducción de medios segura requiere soporte DRM a nivel de dispositivo.»

Así que hay **dos sospechosos con nombre** y hasta ahora estaban los dos activos a la
vez. Con el modo seguro el experimento es limpio: la app queda como un contenedor
puro.

## 3. Qué necesito: pulsa **Datos** y mándame la captura

En esa banda salen, en dos líneas:

```
v0.3.0 | seguro=SI | drm=… | ready=… | play=… | ads=…/…
campos=… pwd=… vis=… vw=… sw=… zoom=…
```

Las tres que deciden:

| Dato | Qué significa |
|---|---|
| **`drm`** | `widevine OK` = el audio puede sonar. **`SIN widevine` = el audio no sonará nunca** por mucho que arreglemos código: es del dispositivo |
| **`zoom`** | Debe ser `1`. Si es menor, la página sigue reduciéndose |
| **`play`** | `si` = el reproductor montó. `no` = no hay reproductor que controlar |

Con el bloqueo apagado, si **ahora sí suena**, el culpable era nuestra capa 1 y la
arreglo (lista blanca más fina, o modo rastreadores por defecto). Si **sigue sin
sonar** con `drm=SIN widevine`, el problema es DRM y te lo diré claro: ese camino no
tiene arreglo por software.

## 4. Lo que no he tocado

«No hay interactividad en los menús» sigue **sin diagnóstico confirmado**. No te voy a
inventar una causa. Con `play` y `drm` en la mano se separa: si el audio muere por DRM,
Spotify deja la interfaz a medias y encaja con lo que ves; si el audio arranca y solo
fallan los clics, es nuestra capa de eventos y la ataco aparte.

## 5. Reglas que me he impuesto (y que esta versión respeta)

1. **Una versión, un cambio.** Aquí van tres, pero son ejes independientes y
   verificables por separado: pantalla, viewport y audio.
2. **Ningún cambio sin medida.** Cada uno entra con el dato que lo comprueba.
3. **No reescribir lo que funciona.** El modo seguro existe precisamente para
   comprobar si lo que estorba es lo nuestro.

## 6. Aviso legal

Filtrar anuncios en un reproductor alternativo **infringe los Términos de Uso de
Spotify**. El riesgo lo asume la cuenta que use la app. Proyecto técnico.
