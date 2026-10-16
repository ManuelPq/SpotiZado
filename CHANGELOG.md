# Historial de versiones — SpotiZado

## 0.2.0 — 2026-10-16
Rebranding a **SpotiZado** + icono nuevo (verde #1ED860, adaptativo, 5 densidades).
Línea base declarada: se adopta el método de «una versión = un cambio, ningún cambio
sin medida». Punto de partida para portar el motor JS de Spotifuck.

## 0.1.7 — 2026-10-15
`ensurePasswordVisible()`: si el campo de contraseña queda fuera de la vista, se trae
al centro. Defecto detectado antes de entregar: la llamada se añadió sin la definición
(habría lanzado `ReferenceError`); corregido y verificado.

## 0.1.6 — 2026-10-15
Añadida la llamada de `ensurePasswordVisible()` (definición ausente; no entregado).

## 0.1.5 — 2026-10-15
**Causa raíz del login.** Faltaban tres ajustes del WebView que Spotifuck sí tenía:
`setSupportZoom(true)`, `setSupportMultipleWindows(true)`,
`setJavaScriptCanOpenWindowsAutomatically(true)`. Con `useWideViewPort` y
`loadWithOverviewMode`, la página se maquetaba a 980 px y sin zoom no había forma de
encogerla a los 384 px de la pantalla. Sonda de **visibilidad real**
(`getBoundingClientRect`) además de existencia en el DOM. Añadido `onCreateWindow`.

## 0.1.4 — 2026-10-15
El spoof de escritorio había quedado como código muerto al arreglar el login; la
recarga con perfil de escritorio comparaba contra un valor ya modificado y nunca se
disparaba. Se guarda `loadedWithDesktop` (con qué perfil se pidió esa carga).

## 0.1.3 — 2026-10-15
Perfil de página: el reproductor usa perfil de escritorio; login y demás, UA móvil con
escala automática. El aviso pasa a banda **fuera** de la caja del WebView. El perfil se
fija **antes** de cargar. Sonda con `campos`/`pwd`/`texto`.

## 0.1.2 — 2026-10-15
El spoof de escritorio se aplicaba en todas las páginas (forzaba `innerWidth=1920` en el
login). El bloqueo de red actuaba también en el login y cortaba el consentimiento GDPR
(`fundingchoicesmessages.google.com`). Lista blanca 24 → 26 reglas. User-Agent
construido desde el motor real del WebView.

## 0.1.1 — 2026-10-15
`URL_LOGIN` con `?allow_password=1`. El controlador se inyecta siempre en el
reproductor (antes dependía de un único botón). Aviso descartable con acción.

## 0.1.0 — 2026-10-15
Primera compilación. Cadena montada desde cero: Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21,
JDK Temurin 21, SDK 36.
