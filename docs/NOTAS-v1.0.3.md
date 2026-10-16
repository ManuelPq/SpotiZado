# SpotiZado v1.0.3 — notas de versión

**APK:** SpotiZado-v1.0.3-debug.apk (23092144 bytes)
**versionCode:** 18 · **versionName:** 1.0.3
**applicationId:** com.imparable.spoti.debug (mismo paquete: actualiza encima)

---

## 1. Tenías razón: habíamos retrocedido. Y sé exactamente por qué

Tu diagnóstico trae los números que lo demuestran:

| Medida (1.0.2) | Qué significa |
|---|---|
| Vista / documento = 1920×978 / 1920 | La página se maqueta a 1920 px de ancho |
| Escala de maquetación 1.0 · Escala VISUAL 1.0 | Y no se reduce nada al pintarla |
| Pantalla = 384 dp | En una ventana de 384 |

1920 px sin reducir dentro de 384 dp: solo cabe la esquina superior izquierda. De ahí el scroll en los dos sentidos, la zona negra y que no se viera la interfaz. En la captura solo asoma "brir Premium" porque el resto de la cabecera queda fuera, a la derecha.

Fueron **dos** causas, y una la puse yo:

1. `00_env_spoof.js` falseaba el tamaño a 1920×1080 y forzaba `meta viewport width=1920`. Ese forzado lo añadí en la 0.3.2 persiguiendo la incoherencia `innerWidth=1920 / scrollWidth=420`, y lo resolví en la dirección equivocada: en vez de quitar el 1920 falso, agrandé el layout hasta 1920. Eso es el retroceso.
2. `applyProfile` remataba con `setInitialScale(100)`, que fija el zoom a 1.0 y **anula el encaje automático** del WebView. Aunque el viewport fuera correcto, el 100 % impedía que se redujera para caber.

## 2. Qué cambia

| Dónde | Antes | Ahora |
|---|---|---|
| Tamaño que ve el JS | 1920×1080 falseado | **El real del teléfono** |
| `meta viewport` | `width=1920` | `width=device-width, initial-scale=1` |
| Escala inicial | 100 % (fija) | 0 = la decide el WebView, que conoce la vista |
| Modo por defecto | Escritorio | **Móvil** |

Es exactamente lo que propusiste: trabajar con el reproductor pensado para móvil, y dejar Escritorio como opción en Ajustes **respetando las medidas de pantalla** — que es lo que ahora garantiza `width=device-width`.

## 3. Escritorio: ojo con esto

Si ya tenías Escritorio guardado, **la 1.0.3 lo respeta** (tu preferencia está guardada en el teléfono y no la he tocado). Pero ya no se saldrá de la pantalla: ahora cabe. Aun así:

- **Móvil** = interfaz diseñada para 384 px. Legible y con todo a la vista. Es lo que recomiendo para lo que quieres hacer ahora.
- **Escritorio** = interfaz de escritorio metida en un móvil: cabe, pero se ve pequeña y hay que hacer zoom con los dedos (el zoom está activado).

Cambia en **Ajustes → Interfaz → Modo del reproductor**.

## 4. El login

El formulario **estaba funcionando**: el diagnóstico dice `campos=2 · pwd=sí · visible=sí · vw=384 · sw=384` — dos campos, contraseña presente y visible, sin desbordamiento. Lo que fallaba era que el aviso de la app ocupaba media pantalla y tapaba el formulario, que es alto.

Ahora el aviso ocupa tres líneas: título, explicación y una línea de sonda. Todo el detalle (URL, user-agent, layout, texto de la página) queda detrás de un botón **"Datos"**. La caja del reproductor recupera ese espacio y el formulario se ve entero.

## 5. Qué probar

1. **Ajustes → Modo del reproductor → Móvil** (si no lo está). Abre el reproductor.
2. ¿Aparece la interfaz de Spotify: listas, canciones, menús, carátulas? **No debe haber scroll horizontal.**
3. Login: el formulario debe verse entero, con el aviso en tres líneas debajo.
4. Si no suena: **Ajustes → Filtrado de red → Rastreadores**, luego **Apagado**. Dime en qué paso vuelve el sonido.

Si con Móvil tampoco sale la interfaz, el siguiente sospechoso es la capa de red, y ya sé qué medir.

## 6. Aviso legal y seguridad

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify; el riesgo lo asume la cuenta que use la app. Experimento privado.

El token clásico de GitHub usado para los push sigue vigente: **revócalo** en GitHub → Settings → Developer settings → Tokens (classic).
