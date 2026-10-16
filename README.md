# SpotiZado

Cliente de Spotify para Android basado en el reproductor web, con bloqueo de anuncios,
controles de sistema y widget. Derivado del trabajo de **deviato** (Spotifuck).

> **Aviso legal.** Un reproductor alternativo que filtra anuncios **infringe los
> Términos de Uso de Spotify**. El riesgo de sanción lo asume la cuenta que use la app.
> Proyecto técnico y de aprendizaje, no un producto para distribuir.

## Estado

| Pieza | Estado |
|---|---|
| Login | **Resuelto** — verificado en dispositivo (`visible=sí`, `sw == vw` = 384) |
| Reproducción | **Sin verificar** — siguiente hito |
| Bloqueo de anuncios | Capa 1 (red) implementada, sin verificar con la app sonando |
| Widget, temporizadores, exportar/importar | Sin verificar |

## Estructura que se busca

```
SpotiZado/
├── README.md
├── CHANGELOG.md
├── docs/
│   ├── ANALISIS-ESTRATEGICO.md     ← qué falla y por qué; plan por fases
│   └── NOTAS-BUILD.md              ← cadena de compilación
├── app/                            ← código Android (Kotlin + Compose)
├── gradle/libs.versions.toml
└── releases/                       ← un APK por versión
```

## Compilar

```bash
export ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk \
       GRADLE_USER_HOME=$HOME/.gradle JAVA_HOME=$HOME/jdk/jdk-21.0.12.1+1
~/gradle-dist/gradle-9.8.1/bin/gradle --no-daemon :app:assembleDebug
```

Salida: `app/build/outputs/apk/debug/app-debug.apk`

| Pieza | Versión |
|---|---|
| Gradle | 9.8.1 |
| Android Gradle plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| JDK | Temurin 21 (el del sistema no trae `javac`) |
| SDK Android | platform 36 + build-tools 36.0.0 |

**Deuda técnica** (`gradle.properties`, desaparece en AGP 10):
`android.newDsl=false`, `android.builtInKotlin=false`,
`android.experimental.disableCompileSdkChecks=true` (AndroidX/Compose 2026 exige API 37,
todavía sin publicar).

## Credenciales

- `applicationId`: `com.imparable.spoti` — se mantiene para que el APK **actualice** la
  app instalada en lugar de crear una segunda copia.
- El login de **Google no funciona** dentro de WebView: es una restricción de Google
  (`disallowed_useragent`). Hay que usar email + contraseña.
- La app **no guarda credenciales**: usa la sesión que el propio reproductor web abre.

## Método de trabajo

1. **Una versión = un cambio.** Dos cosas que tocar, dos versiones.
2. **Ningún cambio sin medida.** Cada arreglo entra con la cifra que lo verifica.
3. **No se reescribe lo que funciona.** Para apartarse de la solución probada de
   Spotifuck hay que citar el fallo observado que lo justifica.
