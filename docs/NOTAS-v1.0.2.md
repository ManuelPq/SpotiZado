# SpotiZado v1.0.2 — notas de versión

**APK:** SpotiZado-v1.0.2-debug.apk (23092128 bytes)
**versionCode:** 17 · **versionName:** 1.0.2
**applicationId:** com.imparable.spoti.debug (mismo paquete: actualiza encima)

---

## 1. Qué estaba mal (con la prueba, no con una sospecha)

Tu captura de las 12:26 enseña las dos cosas a la vez, en la misma pantalla:

- el cartel dice: **"Falta iniciar sesión · campos=0 · pwd=no"**
- y justo debajo, el texto que la app misma leyó de la página dice:
  **"Iniciaste sesión como 2k1979 · Vista general de la cuenta · Reproductor web · Cerrar Sesión"**

O sea: la sesión estaba abierta y la app creía que no. La causa está en `noticeFor()`, en `PlayerScreen.kt`: cualquier URL de `accounts.spotify.com` sin campo de contraseña mostraba ese cartel. Y estabas en `accounts.spotify.com/es/status`, la página de estado de tu cuenta, que no tiene formulario. Ese cartel ocupaba la mitad del visor.

Además se quedaba así para siempre: la detección de sesión solo se disparaba con `playButtonPresent` **y** una URL de `open.spotify.com`, condiciones que en la página de cuenta no se cumplen nunca. Por eso el aviso volvía en cada arranque.

## 2. Qué cambia

Dos piezas, un solo arreglo:

| Dónde | Qué hace ahora |
|---|---|
| `60_login.js` → `detectSession()` | Reconoce la sesión leyendo la propia página: texto "Iniciaste sesión como" / "Cerrar sesión", o el widget de cuenta del reproductor. Emite `session-detected`, que `PlayerBridge` ya sabía interpretar. |
| `PlayerScreen.kt` → `noticeFor()` | El aviso de login queda condicionado a `!state.settings.loggedIn`. Si ya hay sesión, no aparece. |

Nada más se toca. Ni el bloqueo (que sigue como en la 1.0.1), ni el viewport, ni el modo Escritorio.

## 3. Cómo probarlo

1. Instala encima y abre. El cartel de "Falta iniciar sesión" **no debe salir**.
2. En la página de cuenta, pulsa **"Reproductor web"**. Deberías llegar al reproductor sin que te pida nada.
3. Y ahí viene la prueba que no has podido hacer: ¿salen las **listas, canciones y menús**?

## 4. Lo que sigue SIN resolver: el espacio del visor

En las capturas de las 12:24 y 12:30 el reproductor sale **aplastado en una banda arriba** (cabecera, buscador y controles diminutos, uno al lado de otro) y el resto de la pantalla en negro. Tus flechas azules lo marcan: *PANTALLA*, *SCROLL*.

Eso es otra cosa y **no lo he tocado**, a propósito: no puedo verificar su causa desde aquí y no voy a cambiar a ciegas lo que aún no entiendo.

Hay un atajo que **no necesita recompilar nada**: en Ajustes, **Modo del reproductor → Móvil**. El reproductor móvil está diseñado para 384 px de ancho, así que no puede aplastarse en una banda. Prueba eso y dime si con Móvil aparecen las listas. Con esa respuesta ya sé si el culpable es el modo Escritorio o la capa de red, y hago la 1.0.3 con un solo cambio y las pilas puestas.

## 5. Sobre el bloqueo (recordatorio de la 1.0.1)

Está activo: 1.316 reglas en **Estricto** + neutralización **Silenciar + saltar**. Si la música no suena, prueba **Ajustes → Rastreadores** y luego **Apagado**, y dime en qué paso vuelve el sonido. Ese paso señala al culpable.

## 6. Aviso legal y seguridad

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify; el riesgo lo asume la cuenta que use la app. Experimento privado.

El token clásico de GitHub usado para los push sigue vigente: **revócalo** en GitHub → Settings → Developer settings → Tokens (classic).
