/*
 * 30_adguard.js — NEUTRALIZADOR DE ANUNCIOS (la pieza diferencial)
 * ============================================================================
 * Por que neutralizar y no bloquear:
 *   En 2026 Spotify sirve audio y anuncios por la MISMA infraestructura de CDN,
 *   y su reproductor web detecta la manipulacion del reproductor (DOM/red)
 *   respondiendo con un bucle de congelacion. Por eso:
 *
 *     1) El bloqueo de RED (hosts) se queda en la capa Kotlin y NO toca los
 *        dominios de contenido -> elimina rastreadores y anuncios por dominio
 *        sin poder romper la reproduccion.
 *     2) Los anuncios que llegan por la CDN compartida NO se bloquean: se
 *        NEUTRALIZAN dentro del reproductor (silencio + salto de pista).
 *     3) Nunca se eliminan nodos del DOM de anuncios (eso es exactamente lo que
 *        delata a un bloqueador). Se dejan intactos y solo se neutraliza el
 *        audio y el avance.
 *
 * Modos (los combina la app):
 *   silent : silencia toda salida de audio mientras hay anuncio
 *   skip   : intenta saltar (boton next; si esta deshabilitado, seek al final)
 *   guard  : vigila que tras neutralizar la reproduccion se reanude; si no,
 *            reintenta y avisa al host (nunca deja el reproductor colgado)
 *
 * Deteccion del anuncio: varias senales independientes, sin depender de un
 * unico selector (los data-testid de Spotify cambian sin avisar).
 */
(function () {
  var CFG = window.SpotiAdGuardConfig || {};
  var SEL = {
    // senales de "hay anuncio"
    adSlot: [
      '[data-testid=ad-banner]',
      '[data-testid=ad-state]',
      '[data-encore-id=adSlot]',
      'aside[data-testid=advertisement]',
      'div[class*=AdSlot]',
      'div[class*=AdContainer]'
    ],
    // senales textuales (localizadas: es/en/pt)
    adText: /\b(anuncio|advertisement|advertising|publicidad|patrocinado|sponsored)\b/i,
    // controles del reproductor
    playPause: 'button[data-testid=control-button-playpause]',
    next: 'button[data-testid=control-button-skip-forward]',
    progress: 'div[data-testid=playback-progressbar] input[type=range]',
    trackLink: 'a[data-testid=context-item-link]'
  };

  var S = {
    inAd: false,
    since: 0,
    neutralized: 0,
    savedVolume: null,
    lastTrack: null,
    skipAttempts: 0,
    confirmTicks: 0
  };
  window.SpotiAdGuardState = S;

  /* ------------------------------------------------------------------ */
  /* Silenciado                                                          */
  /* ------------------------------------------------------------------ */

  /** Silencia TODAS las salidas: <audio>/<video> y cadenas de Web Audio. */
  function muteAll(mute) {
    try {
      var media = document.querySelectorAll('audio,video');
      for (var i = 0; i < media.length; i++) {
        if (mute) {
          if (media[i].__spotiPrevMuted === undefined) media[i].__spotiPrevMuted = media[i].muted;
          media[i].muted = true;
        } else if (media[i].__spotiPrevMuted !== undefined) {
          media[i].muted = media[i].__spotiPrevMuted;
          delete media[i].__spotiPrevMuted;
        }
      }
    } catch (e) { /* ignore */ }

    // Web Audio.
    //
    // OJO: aqui se SUSTITUIA window.AudioContext por una funcion propia. Eso es
    // invasivo y puede romper el arranque del audio de Spotify (el reproductor
    // comprueba el constructor y la cadena de prototipos). Se elimino: ahora
    // solo se envuelve `createGain` en una instancia YA existente, sin tocar el
    // constructor global.
    try {
      var ctx = window.__spotiAudioCtx || null;
      if (ctx && !ctx.__spotiGainWrapped) {
        var makeGain = ctx.createGain.bind(ctx);
        ctx.createGain = function () {
          var node = makeGain();
          window.__spotiGains = window.__spotiGains || [];
          window.__spotiGains.push(node);
          return node;
        };
        ctx.__spotiGainWrapped = true;
      }
      (window.__spotiGains || []).forEach(function (g) {
        if (mute) {
          if (g.__spotiPrevGain === undefined) g.__spotiPrevGain = g.gain.value;
          g.gain.value = 0;
        } else if (g.__spotiPrevGain !== undefined) {
          g.gain.value = g.__spotiPrevGain;
          delete g.__spotiPrevGain;
        }
      });
    } catch (e) { /* ignore */ }
  }

  /* ------------------------------------------------------------------ */
  /* Deteccion                                                           */
  /* ------------------------------------------------------------------ */

  function domSaysAd() {
    for (var i = 0; i < SEL.adSlot.length; i++) {
      var el = document.querySelector(SEL.adSlot[i]);
      if (el) {
        if (el.offsetParent !== null || el.getClientRects().length) return true;
      }
    }
    // Etiqueta textual cerca del reproductor (sin recorrer todo el documento).
    var aside = document.querySelector('aside[data-testid=now-playing-bar], [data-testid=now-playing-widget]');
    if (aside && SEL.adText.test(aside.textContent || '')) return true;
    return false;
  }

  /**
   * Pista sin texto.
   *
   * ESTE ERA EL FALLO. `trackMissing()` se usaba como senal para ABRIR el estado
   * de anuncio, y devuelve true siempre que el enlace de pista esta vacio — algo
   * que ocurre de forma NORMAL mientras una cancion carga o al cambiar de pista.
   * Resultado: un falso anuncio -> `muteAll(true)` -> musica muda.
   *
   * Ahora esta senal NO silencia por si sola: solo cuenta como indicio cuando ya
   * se esta dentro de un anuncio confirmado.
   */
  function trackMissing() {
    var link = document.querySelector(SEL.trackLink);
    if (!link) return false;
    var t = (link.textContent || '').trim();
    return t.length === 0;
  }

  /** Ticks consecutivos con senal de anuncio en el DOM (evita transitorios). */
  var AD_CONFIRM_TICKS = 2;

  /* ------------------------------------------------------------------ */
  /* Neutralizacion                                                      */
  /* ------------------------------------------------------------------ */

  function trySkip() {
    if (CFG.mode === 'silent') return false;
    var next = document.querySelector(SEL.next);
    if (next && !next.disabled) {
      next.click();
      S.skipAttempts++;
      return true;
    }
    // Sin salto disponible (anuncio no saltables): empujar al final del medio.
    var rg = document.querySelector(SEL.progress);
    if (rg) {
      var max = parseInt(rg.getAttribute('max'), 10) || 0;
      rg.value = Math.max(1, max - 1);
      rg.dispatchEvent(new Event('change', { bubbles: true }));
      S.skipAttempts++;
      return true;
    }
    return false;
  }

  function enterAd() {
    S.inAd = true;
    S.since = Date.now();
    S.skipAttempts = 0;
    // El silencio solo se aplica en modo silenciar; en modo saltar se intenta
    // saltar sin tocar el audio (asi un fallo de deteccion no deja la musica muda).
    if (CFG.mode === 'silent' || CFG.mode === 'both') muteAll(true);
    trySkip();
    window.SpotiState && (window.SpotiState.adPlaying = true);
    spotiNotify('adguard', {
      phase: 'start', mode: CFG.mode, skipped: S.skipAttempts > 0, at: S.since
    });
  }

  function leaveAd(ok) {
    S.inAd = false;
    S.neutralized++;
    muteAll(false);                       // restaurar siempre el audio
    window.SpotiState && (window.SpotiState.adPlaying = false);
    spotiNotify('adguard', {
      phase: 'end', mode: CFG.mode, count: S.neutralized,
      durationMs: Date.now() - S.since, ok: ok
    });
  }

  /**
   * Guardian: tras neutralizar, comprueba que el audio vuelve y que la pista
   * avanza. Si el reproductor se queda atascado (el fallo clasico del
   * anti-adblock de Spotify), reintenta y, si no lo consigue, pide recargar.
   */
  function guard() {
    if (!S.inAd) return;
    var track = document.querySelector(SEL.trackLink);
    var name = track ? (track.textContent || '').trim() : '';
    if (name && name !== S.lastTrack) {   // la pista cambio: anuncio superado
      S.lastTrack = name;
      leaveAd(true);
      return;
    }
    // Watchdog: si llevamos un rato "en anuncio" pero el DOM ya no muestra
    // ninguna senal de anuncio, fue un falso positivo. Se sale y se restaura el
    // audio SIEMPRE. Esta es la red de seguridad contra "musica muda para siempre".
    if (Date.now() - S.since > 8000 && !domSaysAd()) {
      leaveAd(true);
      spotiNotify('adguard', { phase: 'false-positive-cleared' });
      return;
    }
    if (Date.now() - S.since > (CFG.maxAdMs || 40000)) {
      leaveAd(false);
      spotiNotify('adguard', { phase: 'stuck' });
      if (CFG.recoverOnStuck !== false) window.location.reload();
      return;
    }
    if (S.skipAttempts < 3) trySkip();
    // Reaplicar el silencio: el reproductor puede crear un <audio> nuevo.
    if (CFG.mode === 'silent' || CFG.mode === 'both') muteAll(true);
  }

  /* ------------------------------------------------------------------ */
  /* Bucle                                                               */
  /* ------------------------------------------------------------------ */

  var tickInterval = null;
  window.spotiStartAdGuard = function (config) {
    if (config) CFG = config;
    window.SpotiAdGuardConfig = CFG;
    if (tickInterval) clearInterval(tickInterval);
    tickInterval = setInterval(function () {
      if (!S.inAd) {
        // Solo la senal del DOM confirma un anuncio, y debe repetirse en varios
        // ticks. La ausencia de texto en la pista NO basta: es lo que provocaba
        // los falsos positivos que dejaban la musica muda.
        if (domSaysAd()) {
          S.confirmTicks++;
          if (S.confirmTicks >= AD_CONFIRM_TICKS) {
            S.confirmTicks = 0;
            enterAd();
          }
        } else {
          S.confirmTicks = 0;
        }
      } else {
        guard();
      }
    }, CFG.intervalMs || 700);
  };
  window.spotiStopAdGuard = function () {
    if (tickInterval) { clearInterval(tickInterval); tickInterval = null; }
    muteAll(false);
    S.inAd = false;
  };
})();
