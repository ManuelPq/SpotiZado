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

  /*
   * SIN PANEL. Medido en el dispositivo con la 1.7.0: el panel se veia como una
   * linea de 2 px sin texto — solo su borde. Dos paneles (este y el de medida)
   * anclados al mismo `top:0` se solapaban, y su contenido no llegaba a pintarse
   * porque en `document-start` el <body> aun no existe.
   *
   * Se deja de dibujar dentro de la pagina. Los datos se leen desde fuera con
   * `evaluateJavascript` (90_probe.js) y se muestran en la tarjeta NATIVA, que
   * sabemos que pinta siempre porque es Compose, no WebView. Ese canal no tiene
   * restriccion de origen ni depende del arbol de la pagina.
   */
})();
