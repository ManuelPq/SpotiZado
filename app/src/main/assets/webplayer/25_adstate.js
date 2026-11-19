/*
 * 25_adstate.js — CAPTURA DE IDS DE AUDIO PUBLICITARIO
 * ============================================================================
 * POR QUE EXISTE
 *
 * El bloqueo por HOST no quita los anuncios: Spotify sirve el audio de anuncios
 * desde los mismos hosts CDN que la musica, asi que por URL no se distinguen, y
 * bloquear el host se lleva por delante la musica.
 *
 * El estado del reproductor SI los distingue: los anuncios llegan como pistas con
 * `content_type: 'AD'` dentro del `state_machine`, y cada una lleva sus `file_ids`.
 * Si conocemos esos IDs, el nativo puede interceptar ESA peticion concreta.
 *
 * COMO
 *
 * Se inyecta ANTES que los scripts de Spotify y envuelve `fetch` y `WebSocket`
 * para leer los payloads de estado. La respuesta original se devuelve intacta: no
 * se altera nada del reproductor, solo se observa. Ademas:
 *   - solo se parsea si el texto contiene `state_machine` (puerta barata);
 *   - tope de tamano por mensaje, para no reventar memoria;
 *   - cola acotada de IDs en el nativo.
 *
 * Idea de arquitectura observada en el APK de SpotiDuck (asset `ad_state_hook.js`),
 * reimplementada aqui desde cero.
 */
(function () {
  if (window.__spotiAdStateHook) return;
  window.__spotiAdStateHook = 1;

  var MAX_JSON = 8000000;
  var known = {};
  var order = [];
  var MAX_IDS = 32;
  var lastSent = '';
  var dirty = false;
  var decoder = null;
  var ID_RE = /^[a-zA-Z0-9_-]{8,128}$/;

  function remember(v) {
    if (typeof v !== 'string' || !ID_RE.test(v) || known[v]) return;
    known[v] = 1;
    order.push(v);
    dirty = true;
    while (order.length > MAX_IDS) delete known[order.shift()];
  }

  function flush() {
    if (!dirty) return;
    var ser;
    try { ser = JSON.stringify(order); } catch (e) { return; }
    dirty = false;
    if (ser === lastSent) return;
    lastSent = ser;
    try { spotiNotify('adids', { ids: order.slice() }); } catch (e) {}
  }

  /** ¿Es una pista de anuncio? Varias señales, ninguna dependiente de un selector. */
  function isAd(t) {
    if (!t) return false;
    var m = t.metadata || {};
    var c = [t.content_type, t.contentType, t.type, m.content_type, m.contentType,
             m.is_ad, m.isAd, t.is_ad, t.isAd];
    for (var i = 0; i < c.length; i++) {
      var v = c[i];
      if (v === true) return true;
      if (typeof v === 'string') {
        var u = v.toUpperCase();
        if (u === 'AD' || u === 'ADVERTISEMENT' || u === 'TRUE') return true;
      }
    }
    return false;
  }

  function harvest(man) {
    if (!man || typeof man !== 'object') return;
    var groups = [man.file_ids_mp3, man.file_ids_external, man.file_ids, man.alternatives];
    for (var g = 0; g < groups.length; g++) {
      var grp = groups[g];
      if (!grp || !grp.length) continue;
      for (var i = 0; i < grp.length; i++) {
        var e = grp[i];
        if (typeof e === 'string') remember(e);
        else if (e) { remember(e.file_id); remember(e.fileId); remember(e.id); }
      }
    }
  }

  function tracks(list) {
    if (!list || !list.length) return;
    for (var i = 0; i < list.length; i++) {
      var t = list[i];
      if (!isAd(t)) continue;
      harvest(t.manifest);
      remember(t.file_id);
      remember(t.fileId);
    }
  }

  function machine(sm) {
    if (!sm || typeof sm !== 'object') return;
    tracks(sm.tracks);
    tracks(sm.track_list);
    if (sm.queue && typeof sm.queue === 'object') tracks(sm.queue.tracks);
  }

  function payload(p) {
    if (!p || typeof p !== 'object') return;
    machine(p.state_machine);
    machine(p.stateMachine);
    if (p.payloads && p.payloads.length) {
      for (var i = 0; i < p.payloads.length; i++) {
        var e = p.payloads[i];
        if (e && typeof e === 'object') {
          machine(e.state_machine);
          machine(e.stateMachine);
        }
      }
    }
    flush();
  }

  function socketData(v) {
    if (typeof v === 'string') {
      if (v.indexOf('state_machine') === -1 && v.indexOf('stateMachine') === -1) return;
      if (v.length > MAX_JSON) return;
      try { payload(JSON.parse(v)); } catch (e) {}
      return;
    }
    if (typeof Blob === 'function' && v instanceof Blob) {
      if (v.size <= MAX_JSON) v.text().then(socketData).catch(function () {});
      return;
    }
    if (typeof ArrayBuffer === 'function' && v instanceof ArrayBuffer) {
      if (v.byteLength > MAX_JSON) return;
      try {
        if (!decoder) decoder = new TextDecoder();
        socketData(decoder.decode(v));
      } catch (e) {}
    }
  }

  // --- fetch: se observa la respuesta, se devuelve la original intacta ---
  var of = window.fetch;
  if (typeof of === 'function') {
    window.fetch = function (input) {
      var p = of.apply(this, arguments);
      try {
        var url = (typeof input === 'string') ? input : ((input && input.url) || '');
        if (url.indexOf('/state') !== -1 || url.indexOf('connect-state') !== -1) {
          Promise.resolve(p).then(function (res) {
            try { res.clone().json().then(payload).catch(function () {}); } catch (e) {}
          }).catch(function () {});
        }
      } catch (e) {}
      return p;
    };
  }

  // --- WebSocket: los deltas de estado llegan por el Dealer ---
  var NWS = window.WebSocket;
  if (typeof NWS === 'function' && !NWS.__spotiWrapped) {
    var W = function (url, protocols) {
      var s = protocols === undefined ? new NWS(url) : new NWS(url, protocols);
      s.addEventListener('message', function (ev) {
        try { socketData(ev.data); } catch (e) {}
      });
      return s;
    };
    W.prototype = NWS.prototype;
    W.CONNECTING = NWS.CONNECTING;
    W.OPEN = NWS.OPEN;
    W.CLOSING = NWS.CLOSING;
    W.CLOSED = NWS.CLOSED;
    W.__spotiWrapped = true;
    window.WebSocket = W;
  }
})();
