# SpotiZado v1.0.5 — notas de versión

**versionCode:** 20 · **versionName:** 1.0.5 · **applicationId:** com.imparable.spoti.debug (actualiza encima)

---

## 1. Lo que cambia de criterio, y por qué

El diagnóstico de la 1.0.4 deja tres números que descartan mi teoría anterior:

| Medida | Qué descarta |
|---|---|
| `vw=384 · sw=384` | **No hay desbordamiento.** El layout cabe en la pantalla. |
| `zoom=1.0 · layout=384` | La escala es correcta. |
| `campos=2 · pwd=sí` | El formulario **está** en la página. |

Y sin embargo la zona sale negra. Eso significa que el problema no era el viewport
—ni tampoco mi cambio de la 1.0.3, que la 1.0.4 ya revirtió sin efecto— sino
**el código que la app inyectaba en esa página**.

En Chrome del mismo teléfono esa misma página se ve bien. La única diferencia
entre Chrome y esta app era nuestro JavaScript. Así que la conclusión es obvia:
**fuera de ahí.**

## 2. Qué se ha quitado

`60_login.js` pasa a ser de **solo lectura**. Ya no:

- pulsa botones de la página,
- hace scroll,
- inserta elementos (el enlace "login clásico"),
- toca estilos ni el meta viewport.

Lo único que sigue haciendo es **leer** si la sesión ya está iniciada. Y una
excepción, con motivo: el botón de consentimiento de Facebook, porque esa pantalla
bloquea el flujo si no se pulsa.

Además:

| Cambio | Por qué |
|---|---|
| La sesión se detecta de forma **nativa** al llegar a `open.spotify.com` | Antes dependía de un script; si no se disparaba, la app volvía a pedir login aunque tuvieras sesión. |
| Las ventanas emergentes **automáticas** dejan de redirigirse al mismo WebView | Una emergente automática redirigida aquí navegaba la vista a una página vacía y la dejaba **en negro**. Solo se atienden las que abres tú con un toque. |

## 3. Qué probar

1. Instala encima. **Ajustes → Modo del reproductor → Móvil.**
2. Abre el reproductor. El formulario debe salir como sale en Chrome.
3. Si sale negro, pulsa **"Recargar"** una vez (ya está a mano en el aviso).

## 4. Si sigue negro

Entonces el problema no es nuestro JavaScript y hace falta medir otra cosa. Con
**"Datos"** pulsado, mándame estas tres líneas:

- `visible=`
- `vw=` y `sw=`
- `zoom=`

Y si puedes, una captura **con el aviso cerrado del todo** (la ✕), para ver la zona
de la web limpia, sin nada de la app encima.

## 5. Aviso legal y seguridad

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de
Spotify; el riesgo lo asume la cuenta que use la app. Experimento privado.

El token clásico de GitHub usado para los push sigue vigente: **revócalo** en
GitHub → Settings → Developer settings → Tokens (classic).
