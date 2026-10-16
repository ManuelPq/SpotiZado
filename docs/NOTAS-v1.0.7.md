# SpotiZado v1.0.7 — notas de versión

**versionCode:** 22 · **versionName:** 1.0.7 · **applicationId:** com.imparable.spoti.debug (actualiza encima)

---

## 1. Lo que me dijo la capa de calibración

Funcionó, y descartó tres hipótesis de golpe. Tres lecturas reales de tu móvil:

| `innerW` | `innerH` | `scrollH` | `form pwd` |
|---|---|---|---|
| 384 | **703** px CSS (1977 físicos) | 703 | `left=24 top=274 w=336 h=52` **OK en vista** |
| 384 | **703** | 703 | `left=24 top=274 w=336 h=52` **OK en vista** |
| 384 | **255** px CSS (717 físicos) | 255 | `left=24 top=274 w=336 h=52` **>> DEBAJO de la vista** |

Lo que se deduce, sin interpretar:

- **`innerW=384`, `dpr=2.8125`, `zoom=1`** → la escala y el ancho son correctos. Fuera la
  teoría del desbordamiento, del viewport y del zoom. No era eso.
- **El campo está bien colocado**: `left=24 w=336` sobre 384 px de ancho → ocupa el ancho
  entero con 24 px de margen. Nadie está maquetando mal el formulario.
- **`innerH` es inestable**: entre 255 y 703 px CSS (91 dp y 250 dp). El visor **cambia de
  altura** según el estado de la app — y con 255 px el formulario queda fuera y el documento
  (`scrollH=255`) ni siquiera es desplazable para alcanzarlo.
- **Y el dato que decide**: mi rejilla y mi recuadro verde **sí se pintan** sobre la página,
  mientras el formulario de Spotify **no**. Mismo documento, misma ventana, mismas
  coordenadas.

## 2. Las dos conclusiones

**Primera: el contenido no está mal colocado, no se pinta.** Un elemento con
`left=24 top=274 w=336 h=52` y `OK en vista` está correctamente maquetado. Si no se ve, o no
se pinta o algo lo tapa. Eso deja de ser un problema de CSS.

**Segunda: quedan exactamente dos culpables** — el JavaScript que inyectamos, o el motor
WebView. Y ninguna captura puede separarlos, porque las dos cosas producen la misma imagen.

## 3. El experimento de control

**Ajustes → Diagnóstico → "Modo limpio (sin inyección)".**

Con eso activo la app **no inyecta nada**: ni el spoof de escritorio, ni el control, ni la
sonda, ni la calibración, ni las ayudas de login. El visor queda como un navegador sin tocar.

| Resultado | Conclusión |
|---|---|
| El login **se ve** en modo limpio | El culpable es nuestro JavaScript. Se arregla, y sé dónde mirar. |
| El login **sigue negro** | No es nuestro código. Es el motor WebView o la propia página, y hay que atacar por otro lado. |

Es una prueba de resultado binario: cualquiera de los dos me deja sin adivinar.

## 4. Además: la calibración ahora dice POR QUÉ

Si activas la calibración (con modo limpio **desactivado**), el rótulo informa ahora de dos
cosas nuevas:

- **`cadena:`** — el estilo **calculado** del campo de contraseña y de sus tres ancestros:
  `visibility`, `opacity`, `display`. Un `vis=hidden`, un `op=0` o un `disp=none` en
  cualquier nivel explica el misterio de una vez.
- **`fondo:`** — el color de fondo real de `html` y `body`, y cuántos caracteres de texto
  tiene la página.

## 5. Qué hacer, en orden

1. Instala encima.
2. **Ajustes → Diagnóstico → activa "Modo limpio".** Vuelve a abrir el reproductor.
3. Dime qué pasa: **¿se ve el formulario o sigue negro?** Es la pregunta más importante que
   te he hecho en toda la sesión, porque su respuesta parte el problema en dos.
4. Después, si quieres afinar: **desactiva modo limpio, activa calibración**, abre el
   reproductor y mándame las líneas `cadena:` y `fondo:` del rótulo.

## 6. Aviso legal y seguridad

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify;
el riesgo lo asume la cuenta que use la app. Experimento privado.

El token clásico de GitHub usado para los push sigue vigente: **revócalo** en
GitHub → Settings → Developer settings → Tokens (classic).
