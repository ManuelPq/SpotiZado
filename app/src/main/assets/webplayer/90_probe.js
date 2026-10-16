/*
 * 90_probe.js — sonda de salud del reproductor.
 * La app la ejecuta periodicamente; devuelve JSON con lo que necesita saber
 * para decidir si el reproductor esta realmente operativo o hay que recargar.
 * Debe ser una EXPRESION que devuelva string (evaluateJavascript).
 */
JSON.stringify({
  ready: !!window.SpotiCommands,
  playing: !!(window.SpotiState && window.SpotiState.playing),
  track: (window.SpotiState && window.SpotiState.track) || null,
  ad: !!(window.SpotiAdGuardState && window.SpotiAdGuardState.inAd),
  neutralized: (window.SpotiAdGuardState && window.SpotiAdGuardState.neutralized) || 0,
  device: window.spotiDeviceId || null,
  hasToken: !!window.spotiAuthToken,
  playBtn: !!document.querySelector('button[data-testid=control-button-playpause]'),
  media: document.querySelectorAll('audio,video').length,
  url: location.href,
  // Diagnostico del login. `inputs`/`pwd` solo dicen si los campos EXISTEN en el
  // DOM; `pwdVisible` dice si ademas estan dentro de la zona visible, que es lo
  // que de verdad importa (un formulario puede existir y estar fuera de pantalla).
  inputs: document.querySelectorAll('input').length,
  pwd: !!document.querySelector('input[type=password]'),
  emailBtn: !!document.querySelector('[data-testid=login-button], button[type=submit]'),
  bodyLen: (document.body && document.body.innerText) ? document.body.innerText.length : 0,
  pwdVisible: (function () {
    var p = document.querySelector('input[type=password]');
    if (!p) return false;
    var r = p.getBoundingClientRect();
    if (r.width < 4 || r.height < 4) return false;
    return r.top < window.innerHeight && r.bottom > 0 &&
           r.left < window.innerWidth && r.right > 0;
  })(),
  // Ancho del LAYOUT (sin spoof). Debe acabar siendo 1920 como innerWidth.
  clientW: (document.documentElement && document.documentElement.clientWidth) || 0,
  // Que dice el meta viewport: si es 'width=1920', el arreglo funciono.
  metaVp: (function () {
    var m = document.querySelector('meta[name=viewport]');
    return m ? (m.getAttribute('content') || '') : 'sin meta';
  })(),
  dpr: window.devicePixelRatio || 1,
  // Lo primero que dice la pagina: si Spotify muestra un error de reproduccion
  // ('Algo salio mal', 'Can\'t play this right now'), aqui se vera.
  bodyText: ((document.body && document.body.innerText) || '').replace(/\s+/g, ' ').slice(0, 180),
  vw: window.innerWidth,
  vh: window.innerHeight,
  scrollW: (document.documentElement && document.documentElement.scrollWidth) || 0,
  scale: (window.innerWidth > 0)
    ? Math.round((window.screen.width / window.innerWidth) * 100) / 100
    : 0,
  // Escala VISUAL: es la que decide si el contenido se ve minúsculo. El
  // viewport de maquetación puede ser correcto mientras el zoom visual es 0.3.
  visual: (window.visualViewport && window.visualViewport.scale)
    ? Math.round(window.visualViewport.scale * 100) / 100
    : 1,
  // DRM. El reproductor web de Spotify descifra el audio con Widevine (EME).
  // Si el dispositivo no lo soporta, la música NUNCA sonará por mucho que el
  // resto esté bien. Se comprueba una vez y se guarda.
  drm: (function () {
    if (window.spotiDrm !== undefined) return window.spotiDrm;
    if (!navigator.requestMediaKeySystemAccess) {
      window.spotiDrm = 'no soportado';
      return window.spotiDrm;
    }
    window.spotiDrm = 'comprobando';
    try {
      navigator.requestMediaKeySystemAccess('com.widevine.alpha', [{
        initDataTypes: ['cenc'],
        audioCapabilities: [{
          contentType: 'audio/mp4; codecs="mp4a.40.2"'
        }]
      }]).then(function () {
        window.spotiDrm = 'widevine OK';
      }).catch(function () {
        window.spotiDrm = 'SIN widevine';
      });
    } catch (e) {
      window.spotiDrm = 'error: ' + e.message;
    }
    return window.spotiDrm;
  })()
})
