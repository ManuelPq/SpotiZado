# SpotiZado — Análisis estratégico y plan

Fecha: 2026-10-16 · Repositorio: `ManuelPq/SpotiZado`

---

## 1. Resumen en una frase

**El problema técnico está resuelto (el login ya funciona). El problema de método
no:** hemos construido 4.182 líneas de Kotlin y 1.119 de JavaScript alrededor de una
base que **nunca había reproducido una sola canción**, y cada fallo lo hemos
arreglado adivinando en lugar de midiendo.

---

## 2. Qué nos ha estado fallando

### 2.1. Lo construimos a ciegas

Ninguna de las dos partes de este bucle puede **ver** el WebView. Siete versiones
persiguiendo un formulario de login invisible. La raíz no era falta de código: era
falta de **medición**.

Prueba concreta: mi sonda contaba campos con `querySelectorAll` — es decir, medía
*«existe en el DOM»*, no *«está en pantalla»*. Por eso te di por bueno
`campos=2 · pwd=sí` mientras tú veías una pantalla vacía. Tenías razón desde el
primer mensaje y yo te devolví dos versiones más antes de admitirlo.

**Regla que sale de aquí:** ninguna capa nueva se construye antes de tener su
medida. La sonda que ahora reporta *existe vs. visible*, ancho de vista vs. ancho de
documento y escala debía haber existido en la v0.1.0.

### 2.2. Nos desviamos de una base que funcionaba

**Spotifuck 1.6.4.1 funciona.** Su código está descompilado en nuestro propio
disco desde el primer día. Y sin embargo reescribimos su configuración del WebView
«a nuestra manera» y la rompimos con `setSupportZoom(false)`: con `useWideViewPort`
y `loadWithOverviewMode` activos, la página se maqueta a **980 px** y sin zoom no
hay forma de encogerla a los 384 px reales de la pantalla.

Las tres líneas que faltaban estaban en un fichero que ya teníamos. **Casi todos los
problemas del login han sido autolesiones por apartarnos de una configuración
probada.**

### 2.3. Invertimos el orden del trabajo

Construido antes de haber reproducido nada:

| Construido | Estado real |
|---|---|
| Bloqueo en 3 capas | Capa 1 (red) es la única con lógica sólida; capa 3 (JS) sin verificar |
| Sesión Media3 + notificación | Sin verificar (nunca ha sonado una canción) |
| Widget con Glance | Sin verificar |
| UI Compose, búsqueda, cola | Selectores heredados de 2025, pueden estar caducados |
| Temporizadores, exportar/importar | Sin verificar |

Cuando el login falló, **podía ser cualquiera de ocho subsistemas**. Esa es la
factura de construir arriba antes que abajo.

### 2.4. Nuestro JavaScript reinventa lo que Spotifuck ya resolvió

Tenemos 1.119 líneas propias de JS. Spotifuck tiene un motor de reproductor
**completo y probado en campo** de 558 líneas que apenas usamos.

---

## 3. Qué reutilizamos sin inventar nada

### Nivel 1 — Probado, se porta tal cual

Del script inyectado de Spotifuck (`injected-webplayer.js`, 558 líneas):

| Pieza | Qué resuelve | Por qué copiarla |
|---|---|---|
| **Interceptor de `fetch()`** | Captura `spotAuthToken`, `spotCliToken` y `spotDevId` de las peticiones reales de Spotify | Es la forma fiable de obtener los tokens: no hay que deducirlos del DOM |
| **`playFromUri()`** | Reproducir por URI usando `connect-state/v1/player/command` | Endpoint documentado y en uso |
| **`fetchAllLibrary()` + `parseLibrary()`** | Biblioteca completa por `pathfinder/v2/query` (playlists, álbumes, artistas, podcasts) | API real, no scraping |
| **`actPlayPause / actSkipBack / actSkipForward / actRepeat / actAddToFav / actSeek`** | Todos los controles de transporte | Selectores ya resueltos |
| **`updMedia()` + bucle de 2 s** | Publica pista, artista, cover, duración, posición | Modelo de estado completo |
| **`firstFuck()` + `trigUnlock()`** | Recuperación cuando el reproductor se queda clavado | **Oro puro:** es el antídoto contra el «bucle de congelación» que temíamos |
| **`addCSSJSHack()`** | Adapta el reproductor de escritorio a pantalla de móvil | Sin esto el reproductor de escritorio no es usable en el teléfono |
| **Helpers de login** | `?allow_password=1` + enlace clásico | Ya lo replicamos con mejores reservas |
| **Estilo AMOLED** | Tema negro | Trivial, ya hecho |

Y de su configuración nativa: **los ajustes del WebView** (ya restaurados en v0.1.5)
y el enfoque de sesión de medios.

### Nivel 2 — Reutilizamos la idea; nuestra implementación es mejor

- **Lista blanca con prioridad absoluta**: nuestro `AdBlocker` con `allow.txt` es más
  defendible que lo que había.
- **Actualización de lista en caliente** con ETag, validación (mínimo de reglas) y
  *rollback*: resuelve exactamente el fallo que SpotiDuck documenta («una blocklist
  agresiva bloquea endpoints y las pistas no suenan»).

### Nivel 3 — Se mantiene solo si supera verificación

UI Compose, Media3, Glance, temporizadores, exportar/importar ajustes, búsqueda y
cola. **Nada de esto se toca hasta que suene una canción.**

### Descartado con motivo

- **SpotiDuck**: repositorio **solo de binarios, sin código**, y su propio README
  reconoce que deriva de Spotifuck. Se declara GPL-3.0 sin publicar el código que esa
  licencia obliga a publicar → no sirve como fuente.
- **Spotilol**: *all rights reserved* → no se copia.

---

## 4. Qué aportamos nosotros de verdad

Siendo estrictos, porque el resto es reconstrucción:

1. **Lista blanca con prioridad absoluta** + **actualización en caliente con validación
   y rollback**. Es una protección que ninguno de los tres proyectos de referencia tiene.
2. **Diagnóstico medible**: *existe vs. visible*, ancho de vista vs. documento, escala,
   campos, texto visible. Convierte «no funciona» en un número accionable.
3. **Login robusto con cuatro reservas encadenadas**: parámetro en la URL, enlace
   clásico con cascada de contenedores, pulsación automática del botón de email, y
   traída del campo a la vista.
4. **Pila moderna**: Compose, Media3, Glance, `minSdk 26`, `targetSdk 36`, AGP 9.
5. **Documentación honesta de lo degradado** en lugar de silencio.

Lo que **no** podemos reclamar todavía como aportación: el «bloqueo en 3 capas». Solo
la capa 1 tiene lógica verificada; decir «infalible» sería vender humo.

---

## 5. El plan: reconstruir el orden

### Fase 0 — Línea base que reproduce *(esta versión)*
Portar el motor JS de Spotifuck como núcleo del reproductor.
**Criterio de salida: suena una canción, funcionan los controles, aparece la notificación.**
Si no se cumple, no se pasa de fase.

### Fase 1 — Bloqueo de anuncios, una capa cada vez
Primero capa 1 (red) con la lista blanca, midiendo bloqueos. Después la neutralización
JS, comprobando que **no** congela el reproductor. **Un criterio numérico por capa.**

### Fase 2 — Nuestros extras, de uno en uno
Widget, temporizadores, exportar/importar, búsqueda, cola. Cada uno verificado antes
de añadir el siguiente.

### Fase 3 — Repositorio y versiones
Estructura del repo, README, historial de versiones con su APK.

### Reglas de trabajo (las tres que evitan repetir esto)

1. **Una versión = un cambio.** Si hay que tocar dos cosas, son dos versiones.
2. **Ningún cambio sin medida.** Cada arreglo entra con la cifra que lo verifica.
3. **No se reescribe lo que funciona.** Si Spotifuck lo resolvió, se porta; para
   apartarse de su solución hay que citar el fallo observado que lo justifica.

---

## 6. Estado actual

| | |
|---|---|
| Login | **Resuelto** (`visible=sí`, `sw == vw` = 384) |
| Reproducción | **Sin verificar** |
| API interna | `com.imparable.spoti` (se mantiene para actualizar sin desinstalar) |
| Nombre visible | **SpotiZado** |
| Icono | Nuevo, verde `#1ED860`, adaptativo + 5 densidades |
| Cadena | Gradle 9.8.1 · AGP 9.4.1 · Kotlin 2.4.21 · JDK 21 · SDK 36 |

**Deuda técnica declarada:** `android.newDsl=false`, `android.builtInKotlin=false`,
`android.experimental.disableCompileSdkChecks=true` (AndroidX/Compose 2026 exige API 37,
todavía sin publicar). Los tres desaparecen en AGP 10.

---

## 7. Aviso legal

Un reproductor alternativo que filtra anuncios **infringe los Términos de Uso de
Spotify**. El riesgo de sanción lo asume la cuenta que use la app. Proyecto técnico.
