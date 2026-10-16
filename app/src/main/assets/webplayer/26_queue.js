/*
 * 26_queue.js — COLA de reproduccion.
 *
 * El reproductor web ya mantiene una cola (sus propias canciones siguientes);
 * lo que hace este bloque es leerla y permitir saltar a un elemento concreto,
 * sin duplicar la cola en la app (que se desincronizaria en cuanto el usuario
 * tocara algo en la web).
 *
 * Rastreo generico por filas: se buscan contenedores de tipo lista dentro del
 * panel de cola, y si no, se usa el panel lateral derecho visible.
 */
(function () {
  var ITEM_SELECTORS = [
    '[data-testid=queue-song-item]',
    '[data-testid=queue-track-item]',
    '[data-testid=playlist-tracklist] div[role=row]',
    'div[role=row]'
  ];
  var pending = null;

  function queuePanel() {
    return document.querySelector('[data-testid=queue-panel]')
      || document.querySelector('aside [data-testid=right-sidebar]')
      || document.querySelector('aside[aria-label]')
      || null;
  }

  function isQueueOpen() {
    var btn = document.querySelector('button[data-testid=control-button-queue]');
    return !!(btn && btn.getAttribute('aria-checked') === 'true');
  }

  function textOf(el, maxLen) {
    var t = (el.textContent || '').replace(/\s+/g, ' ').trim();
    return t.length > (maxLen || 160) ? t.slice(0, maxLen || 160) : t;
  }

  function collect() {
    var panel = queuePanel();
    if (!panel) return [];

    var rows = [];
    for (var s = 0; s < ITEM_SELECTORS.length && rows.length === 0; s++) {
      var found = panel.querySelectorAll(ITEM_SELECTORS[s]);
      // `div[role=row]` aparece por todas partes: solo se acepta si hay varios.
      if (found.length >= 2 || (s < 2 && found.length > 0)) rows = found;
    }
    if (!rows.length) return [];

    var items = [];
    for (var i = 0; i < rows.length && items.length < 60; i++) {
      var row = rows[i];
      var name = textOf(row, 140);
      if (!name || name.length < 2) continue;

      var link = row.querySelector('a[href]');
      var img = row.querySelector('img');
      var uri = null;
      if (link) {
        var parts = (link.getAttribute('href') || '').replace(/^\//, '').split('/').filter(Boolean);
        if (parts.length >= 2) uri = 'spotify:' + parts.slice(0, 2).join(':');
      }
      items.push({
        index: items.length,
        name: name,
        subtitle: '',
        image: img ? img.src : null,
        uri: uri
      });
    }
    return items;
  }

  function publish(ok, reason, items) {
    spotiNotify('queue', { ok: ok, reason: reason || null, items: items || [], current: 0 });
  }

  /** Abre el panel de cola si hace falta y publica `event.queue`. */
  window.spotiReadQueue = function () {
    if (pending) clearInterval(pending);

    var btn = document.querySelector('button[data-testid=control-button-queue]');
    if (btn && !isQueueOpen()) {
      btn.click();
    } else if (!btn) {
      publish(false, 'no se encontro el boton de cola en el reproductor', []);
      return;
    }

    var started = Date.now();
    pending = setInterval(function () {
      var items = collect();
      if (items.length > 0) {
        clearInterval(pending); pending = null;
        publish(true, null, items);
        return;
      }
      if (Date.now() - started > 5000) {
        clearInterval(pending); pending = null;
        publish(false, 'la cola esta vacia o su estructura ha cambiado', []);
      }
    }, 400);
  };

  /**
   * Reproduce el elemento `index` de la cola visible: se pulsa su boton de
   * reproduccion y, si no existe, la propia fila (Spotify reproduce al hacer
   * clic en la fila de la cola).
   */
  window.spotiPlayQueueItem = function (index) {
    var panel = queuePanel();
    if (!panel) return false;
    var rows = panel.querySelectorAll('[data-testid=queue-song-item], [data-testid=queue-track-item], div[role=row]');
    var row = rows[index];
    if (!row) return false;
    var playBtn = row.querySelector('button[data-testid=play-button], button[aria-label*="ay"], button[aria-label*="Play"]');
    if (playBtn) { playBtn.click(); return true; }
    row.click();
    return true;
  };

  window.spotiCloseQueue = function () {
    if (pending) { clearInterval(pending); pending = null; }
    var btn = document.querySelector('button[data-testid=control-button-queue]');
    if (btn && isQueueOpen()) { btn.click(); return true; }
    return false;
  };
})();
