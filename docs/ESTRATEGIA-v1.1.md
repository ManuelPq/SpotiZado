# Estrategia v1.1 — coger lo mejor de Spotifuck y SpotiDuck para eliminar anuncios

> Documento de decisión. Reenfoca el proyecto al objetivo real: **quitar los anuncios**.
> Todo lo demás (widgets, temporizadores, exportar ajustes) es secundario hasta que esto
> funcione de principio a fin.

---

## 1. Lo primero: qué se puede reutilizar y qué no

Esto no es burocracia, es lo que decide qué trabajo es posible y cuál no.

| Proyecto | Qué hay | Licencia / estado | ¿Se puede reutilizar código? |
|---|---|---|---|
| **Spotifuck** (deviato) | APK publicado; sin repositorio con fuentes | Sin licencia = **todos los derechos reservados** | **No.** Solo se puede estudiar la *técnica* y reimplementarla. |
| **SpotiDuck** (23fpsz) | Repositorio de **solo binarios**: APK, capturas y notas de versión | Código fuente **no publicado** | **No.** No hay código que copiar; el README es la única fuente de información. |
| **Spotilol** | APK | Todos los derechos reservados | **No.** |

Consecuencia práctica, y ya está aplicada en el proyecto: **nada de código copiado**. Lo que
se aprovecha es el *conocimiento* — qué hace falta, en qué orden y con qué límites — y se
reimplementa aquí. El motor JS de este repo es propio, y la lista de bloqueo se importó como
*datos* (una lista de hosts no es código), con su origen citado en la cabecera del fichero.

**Lo que dice el propio README de SpotiDuck** (fuente pública, y es la mejor guía de errores
que hay escrita):

- Cuentas **gratuitas** pueden fallar al cargar en WebViews móviles.
- **Listas de filtros agresivas bloquean endpoints esenciales y las pistas dejan de sonar.**
- El DRM seguro es **a nivel de dispositivo**: ROMs sin certificar no arrancan el audio.
- Hay que mantener **Android System WebView actualizado**, también por el maquetado.
- Los logins de Google/Facebook se bloquean dentro de navegadores embebidos.

Las cuatro primeras ya nos han mordido. Están asumidas como restricciones del entorno.

---

## 2. Qué aporta cada uno (y qué copiamos de cada idea)

### De Spotifuck: el andamiaje

| Idea | Por qué importa | Estado aquí |
|---|---|---|
| WebView con **UA de escritorio** + JS inyectado | Es lo que hace que Spotify sirva el reproductor de PC, que sí expone controles | Hecho — pero hoy **desactivado**: el modo activo es Móvil, y eso rompió el control por selectores |
| **Controles por selectores del DOM** | Base de todo: play, next, progreso | Hecho (`20_control.js`) — solo válido en modo Escritorio |
| Settings de zoom y compatibilidad | Es lo que permite entrar en el login | Hecho, y hoy resuelto por otra vía (`65_unhide.js`) |
| Servicio en primer plano | Sin él, Android mata el audio al bloquear pantalla | Hecho |

### De SpotiDuck: la lista y las advertencias

| Idea | Por qué importa | Estado aquí |
|---|---|---|
| **Listas de bloqueo por host** (~1.316 reglas) | Quita rastreadores y endpoints de anuncios sin tocar el audio | Importadas: `base.txt` (1.305) + `spotify_ads.txt` (11) |
| **Lista blanca con prioridad absoluta** | La defensa contra "bloqueo agresivo rompe la reproducción" | Hecho: `allow.txt`, 26 hosts críticos |
| Modo de compatibilidad / escalado de UA | Alternativa cuando el login falla | Sustituido por un arreglo medido (`65_unhide`) |

### Lo nuestro, que no está en ninguno de los dos

1. **Control por la API de Connect apuntando a nuestro propio `device_id`** (`27_connect.js`).
   Es la diferencia de fondo: en modo Móvil **no existen** los selectores de escritorio, y
   sin esto todos los comandos devuelven `false`. Sin capturar el tráfico real no hay forma
   de deducir `Authorization`, `Client-Token` ni `device_id`.
2. **Neutralizar en vez de borrar** (`30_adguard.js`): no se elimina ningún nodo de anuncio,
   porque eso es exactamente lo que delata a un bloqueador. Se silencia y se salta.
3. **Arreglo de elementos ocultos** (`65_unhide.js`): medido hoy en el dispositivo. El
   formulario estaba con la caja perfecta (`left=24 w=336`, `OK en vista`) y no se pintaba
   porque un ancestro ocultaba el subárbol. Forzar la cadena lo resuelve.
4. **Instrumentos de medida** (`95_calib.js`, modo limpio, sonda): lo que permitió pasar de
   adivinar a saber. Esto no lo tiene ninguno de los dos.

---

## 3. El problema real del bloqueo, dicho sin adornos

El bloqueo de red **no quita los anuncios**: Spotify sirve audio y anuncios por la misma
CDN. Bloquear los hosts de anuncios sirve para rastreo, pero si el anuncio llega igualmente
por la CDN compartida, la reproducción se queda muda o en bucle. Y borrar nodos del DOM es
la señal que usa el reproductor para congelarse.

Así que la única vía que puede funcionar de verdad es **detectar el anuncio y neutralizarlo**:
silencio + salto. Y para eso hace falta **una señal fiable de "esto es un anuncio"**.

Hoy esa señal es frágil: `30_adguard.js` busca selectores `data-testid=...` y texto
localizado. Los `data-testid` cambian sin avisar, y el reproductor móvil no los tiene.

**La señal buena está en el estado de Connect**, que ya estamos capturando para los
controles. Pero no voy a inventar qué campo marca el anuncio: hay que **medirlo durante un
anuncio real**.

---

## 4. Plan, una versión = un cambio

### v1.1.0 — Grabadora de huella de anuncio (siguiente)

Objetivo: capturar, durante un anuncio real, el JSON completo de estado de Connect y las
señales del DOM, y guardarlo en el panel de diagnóstico (exportable a texto).

Por qué primero esto: sin saber qué campo distingue un anuncio de una pista, cualquier
detección que escriba es una conjetura más. Es el mismo criterio que funcionó hoy con la
calibración.

Criterio de éxito: una captura de un anuncio con su JSON, y otra de música normal — y la
diferencia entre ambas, visible a simple vista.

### v1.1.1 — Detección por API, no por selectores

Con la huella de la v1.1.0, detectar el anuncio desde el estado de Connect (que funciona en
Móvil y en Escritorio) y neutralizarlo: silencio inmediato + avance. Los selectores quedan
solo como respaldo.

### v1.1.2 — Verificación de la neutralización

Comprobar que tras saltar un anuncio la música **vuelve sola** y que no se entra en bucle.
Es el fallo que costó la 1.0.0: recarga → anuncio reinicia → recarga sin fin.

### v1.1.3 — Decidir el modo por defecto

Escritorio (encadena la lista entera, tiene selectores) frente a Móvil (donde el audio
funciona). Hoy el modo está resuelto a mano; esto lo deja decidido con datos.

### Después, y solo después

Widget, temporizadores, exportar/importar ajustes. Están implementados y sin verificar; no
aportan nada mientras no haya reproducción sin anuncios.

---

## 5. Reglas de trabajo (las que me he saltado alguna vez)

1. **Una versión, un cambio.** En la 1.0.3 cambié el tamaño declarado "de limpieza" mientras
   arreglaba otra cosa, y el login se quedó en negro. Costó una versión entera.
2. **Ningún cambio sin medida.** La calibración existe por esto.
3. **No reescribir lo que funciona.** Para apartarse de Spotifuck hay que citar el fallo
   observado, no la preferencia.
4. **Decir lo que no se sabe.** "No verificado" es un estado válido; "debería funcionar" no.
5. **Los instrumentos se quedan.** `95_calib.js`, el modo limpio y la sonda no son código de
   depuración temporal: son lo que permite diagnosticar a distancia.

---

## 6. Aviso que no se puede quitar

Un reproductor alternativo de Spotify con bloqueo de anuncios **infringe los Términos de Uso**
de Spotify. El riesgo lo asume la cuenta que use la app. Es un proyecto técnico y privado, no
un producto distribuible.
