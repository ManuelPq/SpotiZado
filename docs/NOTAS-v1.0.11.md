# SpotiZado v1.0.11 — notas de versión

**versionCode:** 26 · **versionName:** 1.0.11

---

## 1. Se acabaron las capturas

Diagnosticar con fotos tenía dos problemas, y el segundo lo acabas de encontrar tú:

1. Cada captura responde a **una** pregunta, así que hacen falta muchas.
2. Las imágenes están **limitadas** por conversación. El texto no.

Así que la app ahora escribe el diagnóstico.

## 2. Diagnóstico → "Copiar informe"

Una pulsación copia al portapapeles **todo** lo que hace falta, en texto plano:

```
=== SpotiZado · informe de diagnóstico ===
app=1.0.11 (26)
webview=com.google.android.webview 141.0.7390.122
android=16 (API 36)
dispositivo=samsung SM-S911B

--- sesión y ajustes ---
sesion=iniciada  servicio=on
modo=MOBILE  adblock=STRICT/SILENT_AND_SKIP
barraNativa=false  unhide=true  calib=false  limpio=false

--- página ---
url=https://accounts.spotify.com/es/login?allow_password=1
drm=widevine OK  listo=true  play=true
audioVideo=0  textoVisible=240 car
viewport=384x703  documento=384
escalaMaq=1.0  escalaVisual=1.0  dpr=2.8125
layout=384  metaViewport=width=device-width, initial-scale=1

--- login ---
campos=2  pwd=true  visible=true  boton=false
textoPagina=Iniciar sesión con contraseña Correo electrónico…

--- reproducción ---
pista=-  artista=-  sonando=false  posicion=0ms
deviceId=031ecc1a338c5fde4f87d3762afdb67

--- bloqueo ---
reglas=1316  version=assets(1316)
bloqueos=7/7  anunciosNeutralizados=0
ultimoEventoAnuncio=-
```

Con eso sé —sin una sola foto— qué motor tienes, si el formulario está en el DOM,
dónde queda, cuánto mide el visor, si el DRM está bien y si el bloqueo está actuando.

## 3. Dos botones más

| Botón | Para qué |
|---|---|
| **Copiar informe** | Portapapeles: completo, se pega en el chat tal cual. |
| **Compartir** | Envía el informe por cualquier app (correo, Telegram, notas). |
| **Copiar resumen (1 línea)** | Versión mínima, para cuando solo hace falta lo esencial: `v1.0.11 wv=… vw=384 sw=384 zoom=1.0 campos=2 pwd=si vis=si modo=MOBILE url=…` |

## 4. Qué hacer

1. Instala la 1.0.11 encima.
2. Abre **Diagnóstico** (icono ⓘ) → **Copiar informe**.
3. Pega el texto en el chat. **Sin capturas.**

Si prefieres una sola línea: **Copiar resumen**.

## 5. Aviso legal

Un reproductor alternativo de Spotify con bloqueo de anuncios infringe los ToS de Spotify;
el riesgo lo asume la cuenta que use la app. Experimento privado.
