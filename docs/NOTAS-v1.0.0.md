# SpotiZado v1.0.0 — notas de versión

**APK:** `SpotiZado-v1.0.0-debug.apk` (22,1 MB · 23.091.668 bytes)
**versionCode:** 15 · **versionName:** 1.0.0
**applicationId:** `com.imparable.spoti.debug` (mismo paquete que 0.2.0 → 0.4.0: **actualiza encima**, no crea una segunda copia)
**Commit:** `3623465` (local = remoto, verificado)

---

## 1. Qué cambia respecto a 0.4.0

| Cambio | Efecto |
|---|---|
| **Vista de ESCRITORIO por defecto** (`guiMode = GuiMode.DESKTOP`) | Como SpotiDuck: al reproducir encadena la lista entera y el aleatorio. Antes arrancaba en Móvil, que se queda en la pista actual. |
| **Barra de reproducción nativa oculta por defecto** (`showNativePlayerBar = false`) | La barra propia ya no roba altura a la web. Hay interruptor en Ajustes para recuperarla. |
| **Guardas de recarga en `PlayerHost`** | Máx. **2 recargas cada 3 min**, con **8 s mínimo** entre ellas (`MAX_RELOADS=2`, `RELOAD_WINDOW_MS=180_000`, `MIN_RELOAD_GAP_MS=8_000`). |
| **`20_control.js`: sin recarga por anuncio** | Exige bloqueo **sostenido** (4 fallos y >15 s) y que no haya reproducción avanzando. |
| **Banner de arranque eliminado** | Verificado en el fuente commiteado: la llamada a `PlayerStrip(...)` no existe (solo queda la definición, línea 712) y no se dibuja. |
| **`27_connect.js`** | Descarta títulos que en realidad son el nombre del dispositivo. |

**Causa raíz del cuelgue con anuncios (corregida aquí):** `publishReload()` recargaba sin guardas; durante un anuncio `connect-state/v1/player/command/...` responde **404** → recarga → el anuncio reinicia → **bucle infinito**. Es el fallo que convertía "hay anuncio" en "la app se cuelga".

---

## 2. ⚠️ AVISO IMPORTANTE — el bloqueo de anuncios NO actúa en esta build

`AdBlocker.SAFE_MODE = true` (en `adblock/AdBlocker.kt:53`; corta en la línea 135 con `if (SAFE_MODE) return false`, y `injection/InjectionComposer.kt:77` no inyecta el AdGuard con ese valor).

Consecuencia, dicha sin adornos: **1.0.0 es una build para verificar reproducción, UI y el cuelgue de anuncios — no para bloquear**. El filtrado de red y la neutralización están desactivados desde 0.3.0 y este script no los cambió, a propósito: activarlos altera el comportamiento de red y hay que medirlo aparte (el propio README de SpotiDuck advierte que las listas agresivas bloquean endpoints y **las pistas dejan de sonar**). Va como **1.0.1**, un solo cambio.

---

## 3. Qué conviene comprobar en el móvil

1. **Vista de escritorio:** al terminar una pista, ¿encadena con la siguiente / respeta el aleatorio? (es el motivo del cambio)
2. **Anuncio:** ¿sigue sonando el anuncio sin colgarse la app? Con el bloqueo desactivado los anuncios **se oirán**; lo que debe haber desaparecido es el **cuelgue**.
3. **Login al 100 %:** la ventana de acceso debe entrar completa, sin zoom forzado.
4. **Sonido:** en modo Móvil ya reproducía; comprobar que en Escritorio también.
5. **Banner de arranque:** no debe aparecer ninguna banda.

---

## 4. Instalación

El `applicationId` no cambió → instalar encima de la versión anterior conserva sesión y ajustes. Si algo se comporta raro tras actualizar, desinstalar e instalar limpio descarta estados viejos.

---

## 5. Aviso legal (se repite a propósito)

Un reproductor alternativo de Spotify con bloqueo de anuncios **infringe los ToS de Spotify**; el riesgo lo asume la cuenta que use la app. Es un experimento privado, no un producto distribuible.

---

## 6. Seguridad — pendiente del usuario

El token clásico de GitHub usado para los push (`ghp_...`) **sigue siendo válido y hay que revocarlo** desde GitHub → Settings → Developer settings → Tokens (classic). No quedó guardado en `.git/config` (0 ocurrencias), pero revocado deja de servir.
