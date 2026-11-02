# SpotiZado v1.0.10 — notas de versión

**versionCode:** 25 · **versionName:** 1.0.10 · **applicationId:** com.imparable.spoti.debug (actualiza encima)

---

## 1. Un dato nuevo que descarta el CSS (y descarta mi trabajo de esta tarde)

De tus capturas con la calibración salen tres hechos que juntos cambian el diagnóstico:

| Medida | Qué implica |
|---|---|
| `body = rgb(18,18,18)` | El **fondo de la página sí se pinta**. Es casi negro: por eso parece "negro vacío". |
| Mi rejilla y mi recuadro verde se pintan | El WebView **compone capas** que inyecto yo. |
| El texto y los controles de Spotify **no** se pintan | Lo que falta es el contenido de la página. |
| El mismo HTML en **Chrome** se ve bien | El CSS de Spotify está correcto. |

Conclusión: **no es maquetación, ni el viewport, ni el zoom**. Es la **composición de
capas del WebView** en tu dispositivo. Las tres versiones que dediqué al CSS apuntaban
al sitio equivocado, y lo digo sin rodeos.

## 2. Qué hace la 1.0.10

Tres medidas de renderizado, de la más barata a la más contundente:

1. **Capa HARDWARE explícita** para el WebView (`LAYER_TYPE_HARDWARE`).
2. **`setOffscreenPreRaster(true)`**: fuerza el rasterizado aunque la vista no se considere
   visible.
3. Se deja de pelear con el escalado automático en las páginas que no son el reproductor.

Y una **regresión mía revertida**: en la 1.0.5 limité las ventanas emergentes a las que abre
el usuario. Si Spotify abre un paso del login en una ventana sin gesto, al rechazarla el
flujo se queda esperando y la página no avanza. Vuelven a atenderse todas.

## 3. Lo que de verdad necesito saber ahora

En **Diagnóstico** hay una fila nueva: **System WebView**. Mándame su valor.

Esto no es curiosidad. El README oficial de SpotiDuck documenta el síntoma "pantalla en
blanco" y su remedio es **actualizar Android System WebView desde Play Store**. Si tu
versión está atrasada, ninguna versión de esta app lo va a arreglar: el motor es del sistema.

Cómo se actualiza: Play Store → busca **Android System WebView** → Actualizar. También
se actualiza vía Chrome (`Chrome → Ajustes → Acerca de`). Después, reinicia el teléfono.

## 4. Si el WebView ya está al día

Entonces el problema es de composición en ese motor concreto y hay una salida honesta: el
reproductor de SpotiDuck, cuyo interfaz dices que es el ideal, funciona en tu móvil. Ahí la
app nuestra no aporta nada todavía, y sería mejor no hacerte seguir instalando versiones.

## 5. Aviso legal

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify;
el riesgo lo asume la cuenta que use la app. Experimento privado.
