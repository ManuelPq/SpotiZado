# SpotiZado v0.4.0 — el control, por la API de Connect

## Lo que confirma tu informe

Buenas noticias primero: **ya suena** y, una vez logueado, **abre al 100 %**. Los dos
problemas anteriores mas gordos estan resueltos (perfil movil + viewport coherente).

## Por que la barra gris no funciona (causa raiz)

Todos los comandos de `20_control.js` pulsaban selectores del reproductor de
**escritorio**: `button[data-testid=control-button-playpause]`,
`div[data-testid=playback-progressbar]`, etc. Pero el modo que hace sonar la musica es
el **movil**, y el reproductor web movil **no tiene esos selectores**. Consecuencia:
cada orden devolvia `false` y el bucle de estado no leia nada. De ahi, exactamente, los
dos sintomas que describes: la barra muestra «Sin reproduccion» y no responde.

## La solucion: tu pista

Lo que cuentas de Spotifuck — poner la web en Chrome y **decirle a Spotify que el
reproductor era el WebView** — es el enfoque correcto, y es el que usa la API de
**Spotify Connect**: las ordenes se mandan al propio `device_id` del WebView en lugar
de simular clics.

Lo mejor: ya lo teniamos a medias. Nuestro interceptor de `fetch` **ya capturaba**
`spotiAuthToken`, `spotiClientToken` y `spotiDeviceId`, y `playFromUri` ya usaba el
endpoint `connect-state`. Solo faltaba usarlo para el resto de comandos.

## Que trae la 0.4.0

- `resume`, `pause`, `next`, `skip_prev` y (nuevo) **transferir la reproduccion aqui**.
- Si no hay credenciales o la peticion falla, **cae al clic anterior**: en modo
  escritorio todo sigue como estaba.
- Estado leido tambien de **fuentes genericas**, sin depender del DOM de escritorio:
  `navigator.mediaSession.metadata` (pista, artista, album, caratula), el elemento
  `<audio>` real (posicion y duracion exactas), `document.title` y el `aria-label`.
  Esto es lo que deberia llenar por fin la barra gris.

## Honestidad sobre los endpoints

`resume`, `pause`, `next` y `skip_prev` son los que aparecen en el codigo de Spotifuck
que tenemos descompilado, mas el de transferencia. `play` ya lo usaba nuestro
`playFromUri`. Para **seek, repeat, shuffle y favorito** no hay endpoint confirmado en
esa fuente, asi que se quedan en el clic del DOM y el fallo se informa en Diagnostico.
Preferi eso a inventar nombres: un endpoint inventado no arreglaria nada y taparia el
problema real.

## Que mirar

Al pulsar **Datos** y en **Diagnostico**:

| Dato | Que esperar |
|---|---|
| `medio=` | `Xs rs4 sonando` en vez de `sin medio` |
| `device_id` | debe aparecer (ya aparecia) |
| Evento `command` | `via=connect-api` si la orden sale por la API; `via=dom` si cae al clic |
| **Pista en la barra gris** | debe mostrar titulo y artista, y responder a los botones |

Si la barra sigue vacia pero `medio` dice `sonando`, el problema estara en el lector de
estado y lo ataco con ese dato, no a ciegas.

## Aviso legal

Filtrar anuncios en un reproductor alternativo **infringe los Terminos de Uso de
Spotify**. El riesgo lo asume la cuenta que use la app.
