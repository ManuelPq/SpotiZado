/*
 * 20_control.js — acciones invocables desde Android (notificacion, widget,
 * auriculares, Android Auto, deep-links) y hook de red para capturar tokens.
 *
 * El hook de window.fetch no bloquea nada por si mismo: solo OBSERVA las
 * peticiones de Spotify para obtener Authorization / Client-Token / device_id,
 * que son necesarios para (a) saltar de pista y (b) resolver deep-links con
 * connect-state. El bloqueo de red vive en la capa Kotlin (AdBlocker).
 */
(function () {
  var oriFetch = window.fetch;
  window.oriSpotiFetch = oriFetch;

  window.fetch = function () {
    var args = Array.prototype.slice.call(arguments);
    var url = String(args[0] && args[0].url ? args[0].url : args[0]);
    var opts = args[1] || {};
    var method = String(opts.method || 'GET').toUpperCase();
    var headers = opts.headers || {};

    try {
      if (method === 'POST' && url.indexOf('/track-playback/v1/devices') >= 0 && opts.body) {
        var dev = (JSON.parse(opts.body).device || {});
        if (dev.device_id && dev.device_id !== window.spotiDeviceId) {
          window.spotiDeviceId = dev.device_id;
          spotiNotify('device', { deviceId: dev.device_id });
        }
      }
      var cs = url.match(/\/connect-state\/v1\/player\/(?:command|transfer)\/from\/([^/]+)\/to\/([^/]+)/);
      if (cs && cs[2] && cs[2] !== window.spotiDeviceId) {
        window.spotiDeviceId = cs[2];
        spotiNotify('device', { deviceId: cs[2] });
      }
      var cli = headers['Client-Token'] || headers['client-token'];
      if (cli && cli !== window.spotiClientToken) window.spotiClientToken = cli;
      var auth = headers.Authorization || headers.authorization;
      if (auth && auth.indexOf('Bearer ') === 0) window.spotiAuthToken = auth;
    } catch (e) { /* la observacion nunca debe romper la peticion */ }

    return oriFetch.apply(window, args).then(function (resp) {
      // Sesion "bloqueada" por el servidor.
      //
      // ANTES se pedia recargar al PRIMER 404, y eso pasa justo mientras suena un
      // anuncio: la app recargaba en mitad del anuncio, este volvia a empezar,
      // volvia el 404 y la pagina quedaba en bucle. Ese era el cuelgue.
      //
      // Ahora se exige bloqueo SOSTENIDO (4 fallos y >15 s) y que no haya
      // reproduccion avanzando. Si es un anuncio, no se toca nada.
      if (resp.status === 404 && url.indexOf('connect-state') >= 0 && url.indexOf('/command/from/') >= 0) {
        var now = Date.now();
        if (!window.spotiLockState) window.spotiLockState = { count: 0, first: now };
        window.spotiLockState.count++;
        var enAnuncio = !!(window.SpotiAdGuardState && window.SpotiAdGuardState.inAd);
        var media = document.querySelector('audio,video');
        var avanzando = !!(media && !media.paused && media.currentTime > 0);
        if (!enAnuncio && !avanzando && window.spotiLockState.count >= 4 &&
            now - window.spotiLockState.first > 15000) {
          window.spotiLockState = { count: 0, first: now };
          spotiNotify('reload', { reason: 'player-locked' });
        }
      } else if (resp.status >= 200 && resp.status < 300) {
        window.spotiLockState = null;
      }
      return resp;
    });
  };

  function click(sel) {
    var el = document.querySelector(sel);
    if (el) { el.click(); return true; }
    return false;
  }

  /** Reproduce un contexto (spotify:album:..., spotify:playlist:...). */
  function playFromUri(uri) {
    if (!uri || !window.spotiDeviceId || !window.spotiAuthToken) return false;
    var type = (uri.match(/^spotify:([^:]+)/) || [])[1] || 'your_library';
    if (type === 'user') type = 'your_library';
    return oriFetch('https://gew4-spclient.spotify.com/connect-state/v1/player/command/from/'
      + window.spotiDeviceId + '/to/' + window.spotiDeviceId, {
      method: 'POST',
      headers: {
        'Authorization': window.spotiAuthToken,
        'Client-Token': window.spotiClientToken || '',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        command: {
          context: { uri: uri, url: 'context://' + uri, metadata: {} },
          play_origin: {
            feature_identifier: type,
            feature_version: 'web-player_' + Date.now(),
            referrer_identifier: 'your_library'
          },
          options: { license: 'tft', skip_to: {}, player_options_override: {} },
          endpoint: 'play'
        }
      })
    }).then(function () { return true; }).catch(function () { return false; });
  }

  /** Se desplaza al final del medio actual (usado por el AdGuard). */
  function seekToEnd() {
    var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
    if (!rg) return false;
    var max = parseInt(rg.getAttribute('max'), 10) || 0;
    rg.value = Math.max(1, max - 1);
    rg.dispatchEvent(new Event('input', { bubbles: true }));
    rg.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  }

  window.SpotiCommands = {
    play: function () { return click('button[data-testid=control-button-playpause]'); },
    pause: function () { return click('button[data-testid=control-button-playpause]'); },
    toggle: function () { return click('button[data-testid=control-button-playpause]'); },
    next: function () { return click('button[data-testid=control-button-skip-forward]'); },
    previous: function () { return click('button[data-testid=control-button-skip-back]'); },
    repeat: function () { return click('button[data-testid=control-button-repeat]'); },
    shuffle: function () { return click('button[data-testid=control-button-shuffle]'); },
    favorite: function () { return click('div[data-testid=now-playing-widget]>div:last-child>button'); },
    seekTo: function (p) {
      var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
      if (!rg) return false;
      rg.value = p.position;
      rg.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    },
    seekToEnd: seekToEnd,
    playUri: function (p) { return playFromUri(p.uri); },
    readState: function () { return window.spotiReadState(); },
    closeNowPlaying: function () {
      var npBtn = document.querySelector('button.npbtn');
      if (npBtn) { npBtn.click(); return true; }
      return false;
    }
  };
})();
