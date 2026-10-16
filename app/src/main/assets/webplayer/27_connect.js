/*
 * 27_connect.js — CONTROL POR LA API DE CONNECT + ESTADO GENERICO
 * ============================================================================
 * POR QUE EXISTE ESTE FICHERO
 *
 * Todos los comandos de 20_control.js pulsan selectores del reproductor de
 * ESCRITORIO (`button[data-testid=control-button-playpause]`,
 * `div[data-testid=playback-progressbar]`, ...). Pero el modo que hace sonar la
 * musica es el MOVIL, y el reproductor web movil no tiene esos selectores: cada
 * orden devolvia `false` y el bucle de estado no leia nada. Resultado medido en
 * el dispositivo: la barra nativa mostraba «Sin reproduccion» y no respondia.
 *
 * La solucion no es inventada: es la que usaba Spotifuck (y su derivado
 * SpotiDuck, que funciona). Consiste en mandar las ordenes por la API de
 * Spotify Connect **al propio device_id del WebView**, en lugar de simular
 * clics. Eso resuelve ademas el problema de los «dos reproductores»: la orden
 * va dirigida a NUESTRO reproductor, no al de otro dispositivo de la cuenta.
 *
 * Los tres datos que hacen falta (Authorization, Client-Token y device_id) los
 * captura el interceptor de `fetch` de 20_control.js observando el trafico real
 * de Spotify: no hay que deducirlos del DOM.
 *
 * HONESTIDAD SOBRE LOS ENDPOINTS. Se usan los cuatro que estan confirmados en
 * el codigo de Spotifuck mas el de transferencia:
 *   resume, pause, next, skip_prev   (y /player/transfer para mover aqui)
 * `playFromUri` con endpoint `play` ya existia en 20_control.js y se reutiliza.
 * Para TODO lo demas (seek, repeat, shuffle, favorito) se mantiene el clic del
 * DOM y, si no hay selector, se informa del fallo en lugar de fingir exito:
 * inventar nombres de endpoint no arreglaria nada y ocultaria el problema.
 *
 * Cada comando intenta primero la API y, si no hay credenciales o la peticion
 * falla, cae al comportamiento anterior. Nada empeora: en modo escritorio se
 * sigue usando el clic, que es lo que funcionaba.
 */
(function () {
  var C = window.SpotiCommands = window.SpotiCommands || {};

  // Se guardan las versiones por DOM para poder caer a ellas.
  ['play', 'pause', 'toggle', 'next', 'previous', 'seekTo', 'repeat', 'shuffle', 'favorite']
    .forEach(function (n) {
      if (typeof C[n] === 'function' && !C['__dom_' + n]) C['__dom_' + n] = C[n];
    });

  var HOST = 'https://gew4-spclient.spotify.com/connect-state/v1/player/';

  /** ¿Hay con que hablar con la API? */
  function ready() {
    return !!(window.spotiDeviceId && window.spotiAuthToken);
  }

  function headers() {
    return {
      'Authorization': window.spotiAuthToken,
      'Client-Token': window.spotiClientToken || '',
      'Content-Type': 'application/json'
    };
  }

  /**
   * Manda un comando a NUESTRO reproductor.
   * @param endpoint 'resume' | 'pause' | 'next' | 'skip_prev'
   * @param onFail   se llama con el motivo si no se pudo enviar
   */
  function command(endpoint, onFail) {
    if (!ready()) { onFail && onFail('sin token o device_id'); return; }
    var dev = window.spotiDeviceId;
    var f = window.oriSpotiFetch || window.fetch;
    try {
      f(HOST + 'command/from/' + dev + '/to/' + dev, {
        method: 'POST',
        headers: headers(),
        body: JSON.stringify({
          command: {
            endpoint: endpoint,
            play_origin: {
              feature_identifier: 'web_player',
              feature_version: 'spotizado_' + Date.now(),
              referrer_identifier: 'web_player'
            },
            options: { license: 'tft', skip_to: {}, player_options_override: {} }
          }
        })
      }).then(function (r) {
        if (!r || !r.ok) onFail && onFail('http ' + (r ? r.status : '?'));
      }).catch(function (e) {
        onFail && onFail(String(e && e.message ? e.message : e));
      });
    } catch (e) {
      onFail && onFail(String(e));
    }
  }

  /** Comando con caida al DOM y aviso del resultado. */
  function commandOrDom(endpoint, domName) {
    var usedApi = ready();
    command(endpoint, function (why) {
      var dom = C['__dom_' + domName];
      var ok = typeof dom === 'function' ? dom() : false;
      spotiNotify('command', { endpoint: endpoint, via: 'dom', ok: !!ok, why: why });
    });
    if (usedApi) spotiNotify('command', { endpoint: endpoint, via: 'connect-api' });
    return usedApi;
  }

  C.play = function () { var b = commandOrDom('resume', 'play'); if (!b) C.__dom_play(); return true; };
  C.pause = function () { var b = commandOrDom('pause', 'pause'); if (!b) C.__dom_pause(); return true; };
  C.toggle = function () {
    // No hay endpoint de «alternar»: se decide con el estado conocido.
    var playing = !!(window.SpotiState && window.SpotiState.playing);
    return playing ? C.pause() : C.play();
  };
  C.next = function () { var b = commandOrDom('next', 'next'); if (!b) C.__dom_next(); return true; };
  C.previous = function () { var b = commandOrDom('skip_prev', 'previous'); if (!b) C.__dom_previous(); return true; };

  /**
   * Mueve la reproduccion a NUESTRO reproductor.
   *
   * Es el «truco» que el usuario describia: dentro de Spotify, decirle que el
   * reproductor es el WebView. Con esto dejan de estorbar los otros
   * reproductores de la misma cuenta.
   */
  C.transferHere = function () {
    if (!ready()) { spotiNotify('transfer', { ok: false, why: 'sin token o device_id' }); return false; }
    var dev = window.spotiDeviceId;
    var f = window.oriSpotiFetch || window.fetch;
    f(HOST + 'transfer/from/' + dev + '/to/' + dev, {
      method: 'POST', headers: headers(),
      // `transfer` necesita el contexto: se usa el ultimo que Spotify registro.
      body: JSON.stringify({
        command: {
          endpoint: 'transfer',
          context: window.spotiTransferContext || { uri: 'spotify:user:' , url: 'context://spotify:user:', metadata: {} },
          play_origin: {
            feature_identifier: 'web_player',
            feature_version: 'spotizado_' + Date.now(),
            referrer_identifier: 'web_player'
          },
          options: { license: 'tft', skip_to: {}, player_options_override: {} }
        }
      })
    }).then(function (r) {
      spotiNotify('transfer', { ok: !!(r && r.ok), status: r ? r.status : 0 });
    }).catch(function (e) {
      spotiNotify('transfer', { ok: false, why: String(e && e.message ? e.message : e) });
    });
    return true;
  };

  /** Guarda el contexto que usa Spotify para poder reutilizarlo en transfer. */
  function rememberContext(uri) {
    if (uri) window.spotiTransferContext = { uri: uri, url: 'context://' + uri, metadata: {} };
  }

  /* ----------------------------------------------------------------- */
  /* Estado: fuentes GENERICAS, sin depender del DOM de escritorio      */
  /* ----------------------------------------------------------------- */

  function meta() {
    var ms = navigator.mediaSession;
    return (ms && ms.metadata) ? ms.metadata : null;
  }

  function artworkSrc(m) {
    try {
      var a = m.artwork;
      if (!a || !a.length) return null;
      return a[a.length - 1].src || a[0].src || null;
    } catch (e) { return null; }
  }

  function ariaLabel() {
    var btns = document.querySelectorAll('button,[role=button]');
    for (var i = 0; i < btns.length; i++) {
      var l = (btns[i].getAttribute('aria-label') || '').toLowerCase();
      if (l.indexOf('reproduc') >= 0 || l.indexOf('paus') >= 0 || l === 'play' || l === 'pause') return l;
    }
    return null;
  }

  /**
   * Rellena los huecos que el DOM de escritorio no cubre.
   *
   * Fuentes, de mas fiable a mas generica:
   *   1) `navigator.mediaSession.metadata`: es lo que el navegador usa para los
   *      controles del sistema. Spotify la rellena siempre, en movil tambien.
   *   2) El elemento <audio> real: da posicion y duracion exactas.
   *   3) `document.title`: en el reproductor movil lleva la cancion.
   */
  function fillGaps(S) {
    var m = meta();
    if (!S.track && m && m.title) S.track = m.title;
    if (!S.artist && m && m.artist) S.artist = m.artist;
    if (!S.album && m && m.album) S.album = m.album;
    if (!S.cover) S.cover = artworkSrc(m);

    var a = document.querySelector('audio,video');
    if (a) {
      if (a.duration && isFinite(a.duration)) S.duration = Math.round(a.duration * 1000);
      if (a.currentTime) S.position = Math.round(a.currentTime * 1000);
      S.playing = !a.paused;
    }
    if (!S.playing && navigator.mediaSession && navigator.mediaSession.playbackState) {
      S.playing = navigator.mediaSession.playbackState === 'playing';
    }

    // Titulo del documento: el reproductor movil lo usa para la pista.
    if (!S.track && document.title) {
      var t = document.title.replace(/\s*[-–|]\s*(Spotify|Web Player)\s*$/i, '').trim();
      // El titulo puede llevar el NOMBRE DEL DISPOSITIVO («2k1979 | Spotify»),
      // que no es una pista: se descarta en lugar de mostrarlo como cancion.
      var esDispositivo = /(?:^|\|)\s*Spotify\s*$/i.test(t) ||
        /web player|mobile web|^[\w.-]+\s*\|\s*$/i.test(t);
      if (t && t.length > 1 && !esDispositivo) S.track = t;
    }

    // Barra de progreso generica (el movil no usa el data-testid de escritorio).
    if (!S.duration) {
      var rg = document.querySelector('input[type=range]');
      if (rg) {
        var max = parseInt(rg.getAttribute('max'), 10) || 0;
        var val = parseInt(rg.getAttribute('value'), 10) || 0;
        if (max > 0) { S.duration = max; S.position = val; }
      }
    }

    // Si ni el DOM ni el audio dicen nada, la etiqueta del boton decide.
    if (!S.playing) {
      var l = ariaLabel();
      if (l) S.playing = (l.indexOf('paus') >= 0 || l.indexOf('pausar') >= 0);
    }
    return S;
  }

  // Se envuelve el lector de estado: primero el original (escritorio) y, si
  // quedan huecos, se rellenan desde las fuentes genericas.
  var origRead = window.spotiReadState;
  if (typeof origRead === 'function') {
    window.spotiReadState = function () {
      var S = origRead.apply(null, arguments);
      try { if (S) fillGaps(S); } catch (e) { /* nunca romper el bucle */ }
      return S;
    };
  }

  /** Expuesto para la app y para el diagnostico. */
  window.SpotiConnect = {
    ready: ready,
    deviceId: function () { return window.spotiDeviceId || null; },
    rememberContext: rememberContext,
    fillGaps: function () { return window.spotiReadState && window.spotiReadState(); }
  };

  // El contexto que Spotify usa al reproducir se aprovecha para transfer.
  var origPlayUri = C.playUri;
  if (typeof origPlayUri === 'function') {
    C.playUri = function (p) { rememberContext(p && p.uri); return origPlayUri(p); };
  }
})();
