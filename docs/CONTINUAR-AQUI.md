# Continuar en una conversación nueva

Documento de traspaso. Si retomas esto en otro chat, **empieza por aquí**: contiene el
estado real, lo que está verificado y —lo más valioso— las trampas en las que ya hemos
caído, para no repetirlas.

---

## 1. Estado, en una línea

App Android (Kotlin + Compose) que envuelve el reproductor web de Spotify. **El login se
resuelve con `65_unhide.js`**, pero en el dispositivo el contenido de las páginas de Spotify
**no se pinta** (fondo sí, texto y controles no) mientras que el mismo HTML en Chrome sí.
Última versión: **1.0.11** (`versionCode 26`).

| Pieza | Estado |
|---|---|
| Compilación | Funciona. Receta en `docs/NOTAS-BUILD.md` |
| Login | **Resuelto**: la reparación de la cadena de ancestros hace aparecer el formulario |
| WebView (login y reproductor) | **No pinta** el contenido de Spotify. Causa sin confirmar |
| Reproducción | Suena en modo Móvil |
| Bloqueo de anuncios | Listas cargadas (1.316 reglas + 26 en lista blanca). Neutralización **sin verificar** |
| Widget, temporizadores, exportar | Implementados, sin verificar |

---

## 2. Lo siguiente, exactamente

1. Instalar **1.0.11**.
2. **Diagnóstico (ⓘ) → "Copiar informe"** y pegar el texto en el chat. Nada de capturas: el
   informe trae versión de la app, **versión de Android System WebView**, modelo, API, URL,
   DRM, `viewport`/`documento`/`zoom`, campos del login y estado del bloqueo.
3. Leer la línea `webview=`. **Si el System WebView está atrasado, ninguna versión de la app
   lo arregla**: el motor es del sistema. El README de SpotiDuck documenta ese síntoma y su
   remedio es actualizarlo desde Play Store.
4. Según eso: atacar el motor, o dar por cerrada la vía WebView y quedarse con SpotiDuck.

---

## 3. Trampas ya pagadas (no repetirlas)

Cada una costó al menos una versión. Todas se detectaron midiendo, no razonando.

| Lo que se intentó | Por qué falló |
|---|---|
| Culpar al **viewport / zoom / desbordamiento** | Medido: `vw=384 sw=384 zoom=1.0`. El layout era correcto. Tres versiones al sitio equivocado |
| Forzar `meta viewport width=1920` (v0.3.2) | Resolvió la incoherencia en la dirección equivocada: agrandó el layout hasta 1920 y sacó la interfaz de la pantalla |
| Quitar el tamaño declarado 1920×1080 "de limpieza" (v1.0.3) | Dejó el login **en negro**. Un cambio no medido dentro de una versión con otro objetivo |
| Creer que el formulario "no estaba" | Estaba: `campos=2 pwd=sí`. Lo que fallaba era que no se pintaba |
| Medir la cadena de estilos solo hasta `<form>` | El culpable estaba **más arriba**: `opacity` no se hereda, así que el hijo dice `op=1`. Hay que ir hasta `<html>` |
| Limitar las ventanas emergentes a las de gesto (v1.0.5) | Si Spotify abre un paso del login sin gesto, al rechazarla el flujo se queda esperando. Revertido en 1.0.10 |
| Interpretar capturas a ciegas | Cada foto responde a una pregunta. De ahí `95_calib.js`, el modo limpio y el informe en texto |

**Reglas que salieron de ahí**: una versión = un cambio · ningún cambio sin medida · no
reescribir lo que funciona · decir "no verificado" en vez de "debería funcionar".

---

## 4. Cómo se compila

```bash
cd ~/workspace/imp/SpotiZado
export ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk \
       GRADLE_USER_HOME=$HOME/.gradle JAVA_HOME=$HOME/jdk/jdk-21.0.12.1+1
~/gradle-dist/gradle-9.8.1/bin/gradle --no-daemon :app:assembleDebug
```

**El toolchain vive en el home del sandbox y puede no sobrevivir a una conversación nueva.**
Si no está, hay que reinstalarlo; la receta completa (versiones, flags, deuda técnica con
API 37) está en `docs/NOTAS-BUILD.md`. Esto es lo primero que hay que comprobar al retomar.

---

## 5. Dónde está todo

| Qué | Dónde |
|---|---|
| Código, docs y APKs | Repositorio **`ManuelPq/SpotiZado`** (público) — es la fuente de verdad |
| Plan de bloqueo de anuncios | `docs/ESTRATEGIA-v1.1.md` |
| Receta de compilación | `docs/NOTAS-BUILD.md` |
| Historial y decisiones | `CHANGELOG.md` |
| Instrumentos de medida | `95_calib.js` (rejilla), modo limpio, `90_probe.js`, informe en texto |
| APKs | `releases/` |

---

## 6. Objetivo del proyecto

Quitar los anuncios combinando lo mejor de **Spotifuck** (andamiaje del reproductor web) y
**SpotiDuck** (listas de bloqueo y sus advertencias), reimplementado desde cero.

**Nada de código copiado**: Spotifuck no tiene licencia (todos los derechos reservados) y
SpotiDuck solo publica binarios, sin fuentes. Se aprovecha el conocimiento; las listas de
bloqueo se importan como **datos**, con su origen citado.

Aviso que no se puede quitar: un reproductor alternativo con bloqueo **infringe los ToS de
Spotify**; el riesgo lo asume la cuenta que lo use. Proyecto privado, no distribuible.
