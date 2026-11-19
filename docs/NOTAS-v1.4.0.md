# SpotiZado 1.4.0 — el testigo que dice donde se muere el pixel

versionCode 32 · versionName 1.4.0 · com.imparable.spoti.debug

## Lo que he comprobado en el servidor (sin el movil de por medio)

Con exactamente el User-Agent de este telefono, pedi a Spotify la pagina de login. Es HTML
plano y Next.js, renderizado en servidor:

    passwordless : 1 <input id="username">, 1 <form>, 17 <script>
    allow_password: 2 <input> (username + password), 1 <form>

Y cero de lo que podria hacer que un WebView no pinte: `content-visibility` 0, `contain` 0,
`@layer` 0, `color-mix` 0, `backdrop-filter` 0, `will-change` 0, `@container` 0.

Correccion de una lectura mia anterior: el `Error:` que vi en el `title` de la variante
`allow_password` **no es un error de Spotify**. Es el `<title>` de un SVG de icono dentro de
la propia pagina. La pagina esta bien.

Conclusion: no hay nada en el HTML ni en el CSS que explique la pantalla negra.

## La pista que nunca se siguio

| Elemento | Como se pinta | Resultado medido |
|---|---|---|
| Nuestro boton verde (`position:fixed`, z-index 2147483000) | **capa de composicion propia** | **se ve** |
| Contenido de Spotify (texto, campos, imagenes) | **capa principal del documento** | **no se ve** |

La diferencia entre los dos no es el CSS, ni el DOM, ni el viewport: es **el nivel de
pintado**. Cuando el compositor no compone la capa principal, se ve exactamente esto.

## Los cambios

1. **`LAYER_TYPE_SOFTWARE` en todas las paginas que no son el reproductor.** Rasteriza en
   CPU, sin compositor de GPU. Es la prueba que faltaba, y estaba tapada por una linea:
   `create()` fijaba `LAYER_TYPE_HARDWARE` para todo, y esa linea se ejecutaba **despues**
   de `applyProfile()`, pisando cualquier ajuste por pagina.
2. **`67_witness.js`**: tres franjas arriba, identicas salvo el nivel de pintado.
   - **A roja** — flujo normal → capa principal.
   - **B azul** — `absolute`, z-index 5 → capa principal apilada.
   - **C amarilla** — `fixed`, z-index 10 → capa de composicion propia.
3. `67_witness.js` neutraliza `content-visibility` y `contain` en la cadena del campo: los
   dos unicos modos de "ten caja y geometria, pero no me rasterices".

## Que hay que mirar

Abrir el login. Arriba salen las tres franjas:

- **A, B y C visibles** → el documento pinta bien. Entonces el fallo es oclusion o subarbol
  no pintado, y hay que dejar de adivinar: `chrome://inspect` por USB.
- **Solo C** (o C + B, sin A) → **confirmado**: la capa principal no compone. El renderizado
  software de esta version deberia hacer aparecer el formulario. Si aun asi no aparece, es
  driver, y la salida es otra.

Manda una captura con las franjas y, si ya sale el formulario, mejor.

## Sobre la barra superior de la app

La barra de SpotiZado (lupa, cola, informacion, engranaje) es **nuestra**, no de Spotify.
Cuando el reproductor cargue, dentro del WebView aparecera la interfaz web de Spotify con
**su propia** barra (busqueda, biblioteca, cuenta), asi que la nuestra queda duplicada.
Se puede quitar: lo unico que hace falta conservar es el acceso a **ajustes** (ahi viven el
bloqueo de anuncios, la calibracion y el informe de diagnostico). Lo dejo para cuando el
login funcione — ahora mismo esa barra es la unica forma de llegar a los ajustes y al
diagnostico, y quitarla me deja sin instrumentos.

Un reproductor alternativo con bloqueo de anuncios infringe los ToS de Spotify; el riesgo
lo asume la cuenta que lo use. Proyecto privado, no distribuible.
