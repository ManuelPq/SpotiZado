/*
 * 95_calib.js — CAPA DE CALIBRACION. Solo para diagnostico, apagada por defecto.
 *
 * Por que existe
 * --------------
 * Desde aqui no se puede ver la pantalla del telefono. Cada diagnostico costaba
 * una captura y una interpretacion, y encadenabamos hipotesis. Esto pinta sobre
 * la pagina una rejilla con coordenadas y un rotulo con las medidas REALES del
 * WebView, de modo que UNA captura conteste de golpe a las preguntas que hacen
 * falta. Ademas la app pinta el fondo del WebView en magenta cuando esta capa
 * esta activa: si la zona "negra" se vuelve magenta, el WebView NO esta pintando
 * nada de la pagina; si sigue oscura, la pagina SI pinta y el contenido esta en
 * otro sitio (o tapado).
 *
 * Que contesta el rotulo
 * ----------------------
 *   innerH : alto del area de la web. Si es diminuto, el WebView esta colapsado.
 *            Si es enorme, la pagina es un documento largo y hay que desplazarse.
 *   scrollH/scrollY : alto del documento y desplazamiento actual.
 *   form pwd left/top/w/h : donde esta el campo de contrasena. Si queda fuera de
 *            la vista, lo dice y dibuja una barra en el borde que corresponde.
 *
 * REGLA QUE CUMPLE
 * ----------------
 * Nada de lo que se inyecta puede estorbar a la pagina: la capa es
 * `pointer-events:none`, no toca el DOM de Spotify, no cambia estilos ni el
 * viewport. Solo AÑADE una capa por encima, y solo cuando el usuario lo pide.
 */
(function () {
  if (window.__spotiCalib) return;
  window.__spotiCalib = true;

  var OVER = 'spoti-calib-over';
  var READ = 'spoti-calib-read';
  var box = null;
  var readout = null;

  function style() {
    if (document.getElementById(OVER + '-css')) return;
    var st = document.createElement('style');
    st.id = OVER + '-css';
    st.textContent = [
      '#' + OVER + '{position:fixed;left:0;top:0;right:0;bottom:0;z-index:2147483646;',
      'pointer-events:none;background-image:',
      'linear-gradient(to right,rgba(255,0,0,.45) 1px,transparent 1px),',
      'linear-gradient(to bottom,rgba(0,140,255,.45) 1px,transparent 1px);',
      'background-size:100px 100px,100px 100px}',
      '#' + OVER + ' .m{position:absolute;font:10px/1 monospace;color:#ff0;',
      'background:rgba(0,0,0,.7);padding:1px 2px;white-space:nowrap}',
      '#' + OVER + ' .f{position:absolute;border:2px solid #0f0;',
      'background:rgba(0,255,0,.18);pointer-events:none}',
      '#' + OVER + ' .edge{position:absolute;left:0;right:0;height:6px;',
      'background:#ff0;pointer-events:none}',
      '#' + READ + '{position:fixed;left:0;top:0;right:0;z-index:2147483647;',
      'pointer-events:none;font:10px/1.4 monospace;color:#0f0;',
      'background:rgba(0,0,0,.88);padding:4px 6px;white-space:pre-wrap;',
      'border-bottom:1px solid #0f0}'
    ].join('');
    document.head.appendChild(st);
  }

  function bar(text, y) {
    var e = document.createElement('div');
    e.className = 'm';
    e.style.left = '0';
    e.style.top = y + 'px';
    e.textContent = text;
    box.appendChild(e);
  }

  function build() {
    if (document.getElementById(OVER)) return;
    style();
    box = document.createElement('div');
    box.id = OVER;
    document.documentElement.appendChild(box);
    readout = document.createElement('div');
    readout.id = READ;
    document.documentElement.appendChild(readout);

    for (var i = 100; i <= 3000; i += 100) {
      var h = document.createElement('div');
      h.className = 'm';
      h.style.left = i + 'px';
      h.style.top = '0';
      h.textContent = i + '';
      box.appendChild(h);
      bar('y' + i, i);
    }
  }

  function pwd() {
    return document.querySelector('input[type=password]') || null;
  }

  function markers() {
    if (!box) return;
    var olds = box.querySelectorAll('.f, .edge');
    for (var i = 0; i < olds.length; i++) olds[i].parentNode.removeChild(olds[i]);

    var p = pwd();
    if (!p) return;
    var r = p.getBoundingClientRect();
    var vh = window.innerHeight;
    var vw = window.innerWidth;

    var el = document.createElement('div');
    el.className = 'f';
    el.style.left = Math.max(0, Math.min(r.left, vw - 4)) + 'px';
    el.style.top = Math.max(0, Math.min(r.top, vh - 4)) + 'px';
    el.style.width = Math.max(4, Math.min(r.width, vw)) + 'px';
    el.style.height = Math.max(4, Math.min(r.height, vh)) + 'px';
    box.appendChild(el);

    if (r.bottom < 0 || r.top > vh) {
      var e2 = document.createElement('div');
      e2.className = 'edge';
      e2.style.top = (r.top > vh ? vh - 6 : 0) + 'px';
      box.appendChild(e2);
    }
  }

  function update() {
    build();
    var se = document.scrollingElement || document.documentElement;
    var r = pwd() ? pwd().getBoundingClientRect() : null;
    var dpr = window.devicePixelRatio || 0;
    var vv = window.visualViewport;
    var out = [];

    out.push('CALIBRACION   innerW=' + window.innerWidth + '  innerH=' + window.innerHeight
      + '   (' + Math.round(window.innerWidth * dpr) + 'x' + Math.round(window.innerHeight * dpr)
      + ' px fisicos)');
    out.push('doc: scrollH=' + (se ? se.scrollHeight : '?')
      + '  scrollY=' + (se ? Math.round(se.scrollTop) : 0)
      + '  dpr=' + dpr
      + '  zoom=' + (vv ? vv.scale : '-'));
    if (r) {
      var where = r.top < 0 ? '  << ARRIBA de la vista'
        : (r.top > window.innerHeight ? '  >> DEBAJO de la vista' : '  OK en vista');
      out.push('form pwd: left=' + Math.round(r.left) + ' top=' + Math.round(r.top)
        + ' w=' + Math.round(r.width) + ' h=' + Math.round(r.height) + where);
    } else {
      out.push('form pwd: NO existe en el DOM');
    }
    out.push('url=' + location.href.substring(0, 96));

    if (readout) readout.textContent = out.join('\n');
    markers();
  }

  update();

  var n = 0;
  var t = setInterval(function () {
    update();
    if (++n > 80) clearInterval(t);
  }, 500);

  window.addEventListener('resize', update);
  window.addEventListener('scroll', update, true);
  window.__spotiCalibUpdate = update;
})();
