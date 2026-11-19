/*
 * 70_console.js — captura los errores de la pagina y los ensena.
 *
 * POR QUE
 * -------
 * El USB no coopera (el dispositivo no aparece en chrome://inspect) y llevamos
 * cinco hipotesis descartadas por medicion. La unica que queda viva es «el
 * JavaScript de Spotify no se ejecuta»: 99 nodos no son una pagina de Next.js
 * hidratada, y las 2 hojas de accounts.scdn.co SI cargaron (cruzadas=2).
 *
 * Asi que en vez de preguntarle al escritorio, la pagina se lo cuenta a la app:
 * `window.onerror`, promesas rechazadas y violaciones de CSP. Eso da el mensaje
 * exacto, con fichero y linea, sin cable y sin adb.
 *
 * Este script se registra como DOCUMENT_START: corre ANTES que cualquier script
 * de Spotify, que es la unica forma de capturar los errores del arranque. Si se
 * inyectara al terminar la carga (onPageFinished), los primeros errores —que son
 * justo los que interesan— ya habrian pasado.
 *
 * NO MODIFICA LA PAGINA. Solo escucha y dibuja un panel propio.
 */
(function () {
  if (window.__spotiLog) return;

  var L = window.__spotiLog = {
    err: [], rej: [], csp: [], consola: [], inicio: Date.now(), panel: null
  };

  function corto(s) {
    s = String(s == null ? '' : s).replace(/\s+/g, ' ').trim();
    return s.length > 92 ? s.slice(0, 89) + '...' : s;
  }

  function push(arr, item) { if (arr.length < 12) arr.push(item); }

  window.addEventListener('error', function (e) {
    var t = e && e.target;
    if (t && t !== window && t.tagName) {
      // Error de CARGA de un recurso (script, css, img): el mas revelador.
      var src = (t.src || t.href || '?');
      push(L.err, 'CARGA ' + (t.tagName || '?').toLowerCase() + ' ' + corto(src.split('/').pop()));
      return;
    }
    push(L.err, 'JS ' + corto((e && e.message) || 'error') +
      ' @' + corto(((e && e.filename) || '').split('/').pop()) + ':' + ((e && e.lineno) || 0));
  }, true);

  window.addEventListener('unhandledrejection', function (e) {
    var r = e && e.reason;
    push(L.rej, 'PROM ' + corto(r && (r.message || r)));
  });

  document.addEventListener('securitypolicyviolation', function (e) {
    push(L.csp, 'CSP ' + corto(e.violatedDirective + ' ' + (e.blockedURI || '')));
  });

  // console.error tambien, que es donde Next.js grita.
  var ce = console.error;
  console.error = function () {
    try {
      push(L.consola, 'LOG ' + corto(Array.prototype.join.call(arguments, ' ')));
    } catch (x) {}
    return ce.apply(console, arguments);
  };

  /** Panel propio, arriba, fuera de la zona de la tarjeta nativa. */
  function panel() {
    if (L.panel && L.panel.isConnected) return L.panel;
    var p = document.createElement('div');
    p.id = 'spoti-log-panel';
    p.style.cssText = 'position:fixed;left:0;top:0;right:0;z-index:2147483000;'
      + 'font:9px/1.3 monospace;padding:3px 6px;white-space:pre-wrap;'
      + 'background:rgba(0,0,0,.95);color:#ffd400;border-bottom:2px solid #ffd400;'
      + 'max-height:62vh;overflow:auto;pointer-events:none';
    document.documentElement.appendChild(p);
    L.panel = p;
    return p;
  }

  function pintar() {
    if (!document.documentElement) return;
    var n = document.querySelectorAll('*').length;
    var hojas = (document.styleSheets || []).length;
    var res = 0;
    try { res = performance.getEntriesByType('resource').length; } catch (e) {}
    var hidratado = !!document.querySelector('#__next > * , [data-reactroot]');
    var nextData = !!window.__NEXT_DATA__;

    var lineas = [
      'LOG v1.7.0 nodos=' + n + ' hojas=' + hojas + ' recursos=' + res
        + ' next=' + (nextData ? 'si' : 'no') + ' hidr=' + (hidratado ? 'si' : 'no'),
      'err=' + L.err.length + ' rej=' + L.rej.length + ' csp=' + L.csp.length
        + ' consola=' + L.consola.length
    ];
    var todos = L.csp.concat(L.err, L.rej, L.consola).slice(0, 5);
    for (var i = 0; i < todos.length; i++) lineas.push((i + 1) + ' ' + todos[i]);
    if (!todos.length) lineas.push('sin errores capturados');
    panel().textContent = lineas.join('\n');
  }

  function arrancar() {
    pintar();
    var k = 0;
    var t = setInterval(function () { pintar(); if (++k > 12) clearInterval(t); }, 900);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', arrancar);
  } else {
    arrancar();
  }
})();
