/*
 * 25_search.js — BUSQUEDA dentro del reproductor web.
 *
 * Como funciona: se escribe en el propio buscador de Spotify (disparando el
 * evento que espera React, porque asignar .value a secas NO lo activa) y luego
 * se rastrean los resultados del DOM.
 *
 * Decision de diseño: el rastreo es GENERICO (enlaces con href /track|album|
 * artist|playlist|episode|show) en lugar de depender de un `data-testid`
 * concreto, porque Spotify los cambia sin aviso. Si no encuentra nada, lo
 * informa con un motivo en lugar de devolver una lista vacia en silencio.
 */
(function () {
  var RESULT_HREF = /^\/(track|album|artist|playlist|episode|show|collection)\//;
  var pending = null;

  function setInputValue(input, value) {
    var proto = window.HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  function kindOf(href) {
    var m = href.match(RESULT_HREF);
    return m ? m[1] : 'unknown';
  }

  function toUri(href) {
    var parts = href.replace(/^\//, '').split('/').filter(Boolean);
    if (parts[0] && parts[0].indexOf('intl-') === 0) parts = parts.slice(1);   // /intl-es/track/...
    return parts.length >= 2 ? 'spotify:' + parts.slice(0, 2).join(':') : null;
  }

  /** Rastrea los resultados visibles. Devuelve [] si el DOM aun no los tiene. */
  function collect() {
    var scope = document.querySelector('[data-testid=search-page]')
      || document.querySelector('main')
      || document;
    var anchors = scope.querySelectorAll('a[href]');
    var seen = {};
    var items = [];

    for (var i = 0; i < anchors.length && items.length < 40; i++) {
      var a = anchors[i];
      var href = a.getAttribute('href') || '';
      if (!RESULT_HREF.test(href)) continue;

      var uri = toUri(href);
      if (!uri || seen[uri]) continue;

      // Nombre y subtitulo: se toman de los spans del propio enlace.
      var spans = a.querySelectorAll('span');
      var texts = [];
      for (var s = 0; s < spans.length; s++) {
        var t = (spans[s].textContent || '').trim();
        if (t && t.length < 200) texts.push(t);
      }
      var name = texts.length ? texts[0] : (a.textContent || '').trim().slice(0, 120);
      if (!name) continue;
      var subtitle = texts.slice(1, 3).join(' · ');

      var img = a.querySelector('img');
      seen[uri] = true;
      items.push({
        uri: uri,
        name: name,
        subtitle: subtitle,
        image: img ? img.src : null,
        kind: kindOf(href)
      });
    }
    return items;
  }

  function publish(query, ok, reason, items) {
    spotiNotify('searchResults', {
      query: query, ok: ok, reason: reason || null, results: items || []
    });
  }

  /**
   * Busca. Publica `event.searchResults` cuando termina (exito o fallo), por lo
   * que Kotlin no tiene que esperar de forma sincrona.
   */
  window.spotiSearch = function (query) {
    if (!query || query.trim().length === 0) {
      publish(query, false, 'consulta vacia', []);
      return;
    }
    query = query.trim();

    var input = document.querySelector('input[data-testid=search-input]')
      || document.querySelector('input[role=searchbox]')
      || document.querySelector('form[role=search] input');

    if (!input) {
      publish(query, false, 'no se encontro el cuadro de busqueda', []);
      return;
    }

    try {
      input.focus();
      setInputValue(input, query);
    } catch (e) {
      publish(query, false, 'no se pudo escribir en el buscador: ' + e.message, []);
      return;
    }

    // El buscador de Spotify hace la peticion y pinta resultados de forma
    // asincrona: se sondea hasta 8 s antes de darse por vencido.
    if (pending) clearInterval(pending);
    var started = Date.now();
    var firstRound = null;

    pending = setInterval(function () {
      var items = collect();

      // En la primera pasada que haya algo, se espera un poco mas para que
      // terminen de renderizarse todas las tarjetas (mejora la lista final).
      if (items.length > 0 && !firstRound) firstRound = Date.now();

      if (items.length > 0 && firstRound && Date.now() - firstRound > 1200) {
        clearInterval(pending); pending = null;
        publish(query, true, null, items);
        return;
      }
      if (Date.now() - started > 8000) {
        clearInterval(pending); pending = null;
        if (items.length > 0) publish(query, true, null, items);
        else publish(query, false, 'sin resultados tras 8 s (puede haber cambiado el DOM)', []);
      }
    }, 400);
  };

  window.spotiClearSearch = function () {
    if (pending) { clearInterval(pending); pending = null; }
    var input = document.querySelector('input[data-testid=search-input]');
    if (input) { setInputValue(input, ''); }
    var close = document.querySelector('button[data-testid=search-clear-button]');
    if (close) close.click();
    return true;
  };
})();
