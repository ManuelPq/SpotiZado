# Spot-Imparable — notas de compilación (v0.1.7 debug)

APK: `Spot-Imparable-v0.1.7-debug.apk` · `com.imparable.spoti.debug`
`versionCode 8` · `versionName 0.1.7` · `minSdk 26` · `targetSdk 36` · `compileSdk 36`

## 0. El login: RESUELTO (confirmado con el diagnóstico de la v0.1.5)

Datos recibidos:

```
campos=2   pwd=sí   visible=sí  (después visible=no)
sw=384     vw=384
```

- **`visible=sí`** → el formulario **se ve y se puede escribir**.
- **`sw == vw` (384 = 384)** → **sin desbordamiento**: el maquetado es correcto.
- **`visible=no` después** → el campo se sale de la vista al desplazarse o al
  cambiar de paso. No es un problema de tamaño.

La causa raíz del login era **`setSupportZoom(false)`**: con `useWideViewPort` y
`loadWithOverviewMode` activos, el WebView maqueta la página a **980 px** y sin zoom
no hay forma de encogerla para que quepa. Los campos existían en el DOM (por eso
`campos=2`) pero quedaban fuera de la pantalla. Se restauraron los tres ajustes de
Spotifuck: `setSupportZoom(true)`, `setSupportMultipleWindows(true)`,
`setJavaScriptCanOpenWindowsAutomatically(true)`.

### Mi error de calibración

La sonda contaba campos con `querySelectorAll`, que mide **«existe»**, no **«se ve»**.
Por eso di por bueno `campos=2 · pwd=sí` cuando el formulario seguía inutilizable.
Ahora mide visibilidad real: `getBoundingClientRect()` contra el viewport, más el
ancho del documento y la escala.

## 1. Novedad de la v0.1.7

`60_login.js` incorpora `ensurePasswordVisible()`: si el campo de contraseña existe
pero queda fuera de la vista, se trae al centro de la pantalla. Máximo 6 reintentos
(no es un bucle abierto) y avisa al host con `scrolled-to-password`.

Esto cubre el único síntoma que quedaba: `visible=no` con el formulario ya presente.

### Defecto detectado antes de entregar

La primera compilación de esta tanda añadió la **llamada** a `ensurePasswordVisible()`
pero no la **definición** (la comprobación de idempotencia buscaba el nombre, que ya
aparecía en la llamada). Habría lanzado `ReferenceError` y roto los ayudantes de
login. Se corrigió y se verificó antes de publicar: `node --check` pasa y hay
**2 ocurrencias** en el APK (llamada en la línea 39, definición en la 79). El APK
defectuoso no llegó a entregarse.

## 2. Qué mirar ahora

Línea de estado del aviso:

| Lo que veas | Significa |
|---|---|
| `visible=sí` | formulario a la vista: escribe email y contraseña |
| `visible=no` con `sw` ≈ `vw` | el campo no está en esta página (otro paso del flujo) |
| `visible=no` con `sw` ≫ `vw` | vuelve a haber desbordamiento: sería otra regresión |

Tras iniciar sesión (en **Diagnóstico**):

| Dato | Valor esperado |
|---|---|
| **Botón de play presente** | `sí` |
| **Reglas cargadas** | ~1316 |
| **Bloqueos (sesión)** | sube con el uso |
| **URL** | `open.spotify.com/…` |

El login de **Google no funciona** en WebView por decisión de Google
(`disallowed_useragent`). Hay que usar email + contraseña.

## 3. Sobre basarse en SpotiDuck

`23fpsz/SpotiDuck-Releases` es un repositorio **solo de binarios, sin código**, y su
README reconoce que **deriva de Spotifuck** (de *deviato*). Es la misma línea que el
APK original descompilado, así que la fuente con código es Spotifuck — y de ahí
salieron los ajustes que arreglaron el login. SpotiDuck se declara GPL-3.0 sin
publicar el código que esa licencia obliga a publicar.

## 4. Cadena de compilación

```bash
cd ~/workspace/imp/Spoti-Imparable
export ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk \
       GRADLE_USER_HOME=$HOME/.gradle JAVA_HOME=$HOME/jdk/jdk-21.0.12.1+1
~/gradle-dist/gradle-9.8.1/bin/gradle --no-daemon :app:assembleDebug
```

Gradle 9.8.1 · AGP 9.4.1 · Kotlin 2.4.21 · JDK Temurin 21.0.12.1 ·
SDK platform 36 + build-tools 36.0.0. El JDK del sistema no trae `javac`.

Deuda técnica en `gradle.properties` (desaparece en AGP 10): `android.newDsl=false`,
`android.builtInKotlin=false`, `android.experimental.disableCompileSdkChecks=true`.

## 5. Degradado / pendiente

- **Sin carátulas** (Coil 3.6 exige API 37, todavía sin publicar).
- **Firma de depuración**, no válida para publicar.
- **Sin probar en dispositivo real**: no puedo ver la pantalla; la sonda de
  visibilidad es la fuente de verdad.
- Los selectores del reproductor vienen de la reconstrucción de Spotifuck; si
  Spotify cambió el marcado, el diagnóstico lo dirá en lugar de quedarse en negro.

## 6. Aviso legal

Filtrar anuncios en un reproductor alternativo **infringe los Términos de Uso de
Spotify**. El riesgo lo asume la cuenta que use la app. Proyecto técnico.
