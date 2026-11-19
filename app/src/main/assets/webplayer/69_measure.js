/*
 * 69_measure.js — SOLO MIDE. Cero interferencia con la pagina.
 *
 * POR QUE SE LLEGA AQUI
 * ---------------------
 * v1.5.0, medido en el dispositivo:
 *
 *   hojas=7 reglas=22 inaccesibles=2 nodos=102
 *   bodyBg=rgb(18,18,18) bodyFg=rgb(255,255,255)
 *
 * Ese fondo y ese color de texto son de NUESTRO CSS de respaldo, que se inyecto
 * en la primera pasada (cuando la pagina aun tenia <20 nodos) y se quedo puesto.
 * Es decir: no hay prueba de que Spotify este aplicando su CSS, y aun asi no se
 * ve ni una caja de las que ese respaldo dibuja con borde y fondo.
 *
 * Antes de seguir culpando a Spotify hay que descartar al unico sospechoso que
 * queda y que nunca se ha quitado de en medio: NOSOTROS. Esta version no toca
 * nada. Ni estilos, ni clases, ni atributos, ni nodos existentes. Solo lee y
 * escribe su resultado en un panel propio (`position:fixed`, probado que pinta).
 *
 * Si con cero interferencia la pagina sigue negra, el problema no es nuestro y
 * la discusion se termina: hace falta chrome://inspect para ver el arbol real.
 */
(function () {
  if (window.__spotiMeasure) return;
  window.__spotiMeasure = 1;

  var PANEL = 'spoti-measure-panel';

  function el(o) {
    if (!o) return 'null';
    var t = (o.tagName || '?').toLowerCase();
    if (o.id) t += '#' + o.id;
    return t;
  }

  function medir() {
    var s = document.styleSheets || [];
    var reglas = 0, cruzadas = 0;
    for (var i = 0; i < s.length; i++) {
      try { reglas += (s[i].cssRules || []).length; } catch (e) { cruzadas++; }
    }

    var form = document.querySelector('form');
    var inp = document.querySelector('input[type=password]')
      || document.querySelector('input[type=email]')
      || document.querySelector('input');
    var r = inp ? inp.getBoundingClientRect() : null;

    var arriba = 'sin-campo';
    if (r && r.width > 2 && r.height > 2) {
      var cx = Math.round(Math.min(Math.max(r.left + r.width / 2, 1), window.innerWidth - 1));
      var cy = Math.round(Math.min(Math.max(r.top + r.height / 2, 1), window.innerHeight - 1));
      var top = document.elementFromPoint(cx, cy);
      arriba = el(top) + (top === inp ? ' =CAMPO' : ' TAPA');
    }

    var b = document.body;
    var cbs = b ? getComputedStyle(b) : null;

    return {
      hojas: s.length,
      reglas: reglas,
      cruzadas: cruzadas,
      nodos: document.querySelectorAll('*').length,
      form: form ? (getComputedStyle(form).display + '/' + getComputedStyle(form).opacity) : 'sin-form',
      caja: r ? (Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.width) + 'x' + Math.round(r.height)) : 'sin-caja',
      arriba: arriba,
      bodyBg: cbs ? cbs.backgroundColor : '-',
      bodyFg: cbs ? cbs.color : '-',
      vw: window.innerWidth,
      vh: window.innerHeight,
      docH: document.documentElement.scrollHeight,
      fonts: (document.fonts && document.fonts.status) || '?',
      url: location.href.replace(/^https?:\/\//, '').slice(0, 44)
    };
  }

  function pinta(m) {
    var p = document.getElementById(PANEL);
    if (!p) {
      p = document.createElement('div');
      p.id = PANEL;
      // ARRIBA, no abajo: el panel de la 1.6.0 salia recortado porque NUESTRA
      // propia tarjeta (Compose, capa nativa) ocupa el tercio inferior de la
      // pantalla y tapaba las lineas de abajo. El corte no era del WebView: era
      // de la app. Arriba no hay nada nativo encima.
      p.style.cssText = 'position:fixed;left:0;top:0;right:0;z-index:2147483000;'
        + 'font:9px/1.32 monospace;padding:3px 6px;white-space:pre-wrap;'
        + 'background:rgba(0,0,0,.94);color:#00ffb3;border-bottom:2px solid #00ffb3;'
        + 'max-height:58vh;overflow:auto';
      // Solo se ANADE un nodo propio. No se modifica ningun nodo de la pagina.
      document.documentElement.appendChild(p);
    }
    p.textContent = 'MEDIDA v1.6.0 (sin tocar la pagina)\n'
      + 'hojas=' + m.hojas + ' reglas=' + m.reglas + ' cruzadas=' + m.cruzadas
      + ' nodos=' + m.nodos + ' fonts=' + m.fonts + '\n'
      + 'form=' + m.form + ' caja=' + m.caja + '\n'
      + 'arriba=' + m.arriba + '\n'
      + 'bodyBg=' + m.bodyBg + ' bodyFg=' + m.bodyFg + '\n'
      + 'vw=' + m.vw + ' vh=' + m.vh + ' docH=' + m.docH + '\n'
      + m.url;
  }

  var n = 0;
  var t = setInterval(function () {
    var m = medir();
    pinta(m);
    if (++n > 8) clearInterval(t);
  }, 900);
})();
