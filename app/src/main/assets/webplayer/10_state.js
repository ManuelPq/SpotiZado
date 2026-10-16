/*
 * 10_state.js — estado del reproductor + publicacion de cambios
 *
 * Modelo de datos: se lee el DOM del reproductor cada 2 s y se emite un evento
 * cuando cambia el "estado identificativo". La posicion se envia aparte y solo
 * si se ha movido mas de 4 s (evita saturar el puente).
 */
(function () {
  var S = {
    track: null, artist: '', album: '', duration: 0, position: 0,
    playing: false, repeat: 'false', shuffle: false, favorite: false,
    cover: null, adPlaying: false
  };
  window.SpotiState = S;

  var lastKey = null, lastPos = null;

  function text(sel) {
    var el = document.querySelector(sel);
    return el ? (el.textContent || '').trim() : null;
  }
  function firstText() {
    for (var i = 0; i < arguments.length; i++) {
      var t = text(arguments[i]);
      if (t) return t;
    }
    return null;
  }

  /** Relee el estado desde el DOM del reproductor. */
  window.spotiReadState = function () {
    S.track = firstText('a[data-testid=context-item-link]', '[data-testid=context-item-info-name]');
    S.artist = firstText('a[data-testid=context-item-info-artist]',
                         'a[data-testid=context-item-info-show]',
                         '[data-testid=context-item-info-artist]') || '';
    S.album = firstText('a[data-testid=context-item-info-album]') || '';

    var rr = document.querySelector('button[data-testid=control-button-repeat]');
    S.repeat = rr ? rr.getAttribute('aria-checked') : 'false';

    var sh = document.querySelector('button[data-testid=control-button-shuffle]');
    S.shuffle = !!(sh && sh.getAttribute('aria-checked') === 'true');

    var fav = document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
    S.favorite = !!(fav && fav.getAttribute('aria-checked') === 'true');

    var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
    if (rg) {
      S.duration = parseInt(rg.getAttribute('max'), 10) || 0;
      S.position = parseInt(rg.getAttribute('value'), 10) || 0;
    }

    var im = document.querySelector('img[data-testid=cover-art-image]');
    S.cover = im ? im.src : null;

    var pp = document.querySelector('button[data-testid=control-button-playpause]');
    if (pp) S.playing = pp.getAttribute('aria-label') !== 'Play';

    return S;
  };

  /** Emite el estado a Kotlin si algo relevante ha cambiado. */
  window.spotiPublish = function () {
    window.spotiReadState();
    var key = [S.track, S.artist, S.playing, S.repeat, S.shuffle, S.favorite, S.adPlaying].join('|');
    if (key !== lastKey) {
      lastKey = key;
      spotiNotify('state', {
        track: S.track, artist: S.artist, album: S.album,
        duration: S.duration, position: S.position, playing: S.playing,
        repeat: S.repeat, shuffle: S.shuffle, favorite: S.favorite,
        cover: S.cover, adPlaying: S.adPlaying
      });
    } else if (Math.abs(S.position - (lastPos || 0)) > 4000) {
      lastPos = S.position;
      spotiNotify('position', { position: S.position, duration: S.duration });
    }
  };

  var stateInterval = null;
  window.spotiStartStateLoop = function (ms) {
    if (stateInterval) clearInterval(stateInterval);
    stateInterval = setInterval(window.spotiPublish, ms || 2000);
    window.spotiPublish();
  };
  window.spotiStopStateLoop = function () {
    if (stateInterval) { clearInterval(stateInterval); stateInterval = null; }
  };
})();
