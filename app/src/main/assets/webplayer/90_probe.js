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
  vw: window.innerWidth,
  vh: window.innerHeight,
  scrollW: (document.documentElement && document.documentElement.scrollWidth) || 0,
  scale: (window.innerWidth > 0)
    ? Math.round((window.screen.width / window.innerWidth) * 100) / 100
    : 0
})
