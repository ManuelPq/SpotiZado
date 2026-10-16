/*
 * 65_unhide.js — hacen visible lo que Spotify deja oculto. ARREGLO PERMANENTE.
 *
 * LA PRUEBA
 * ---------
 * Medido en el dispositivo (capa de calibracion, v1.0.7) en la pagina de login:
 *
 *   form pwd: left=24 top=274 w=336 h=52  OK en vista
 *   cadena: input(vis=visible op=1 disp=block) < div < div < form
 *   fondo:  html=rgba(0,0,0,0) body=rgb(18,18,18)   texto=240 car.
 *
 * La caja del formulario estaba PERFECTA y no se pintaba nada. La cadena medida
 * llegaba solo hasta <form>: el culpable estaba MAS ARRIBA, fuera de la medida.
 * Y en la practica: al forzar opacidad/visibilidad/desbordamiento en toda la
 * cadena, EL FORMULARIO APARECE. Eso confirma el diagnostico.
 *
 * QUE HACE
 * --------
 * Recorre la cadena de ancestros desde el elemento de interes hasta <html> y
 * fuerza los valores que pueden ocultar un subarbol. Se aplica con dos juegos de
 * propiedades, porque las dos paginas no admiten las mismas:
 *
 *   LOGIN  — juego completo, el que se probo y funciona. La pagina es simple.
 *            opacity, visibility, overflow, clip-path, transform, filter.
 *            Ademas pasa el texto a blanco si saliera del color del fondo.
 *
 *   PLAYER — juego conservador. En el reproductor, `overflow:hidden` y `transform`
 *            son parte del diseño (paneles y scroll), y forzarlos podria romperlo.
 *            Solo se toca lo que de verdad oculta: opacity < 1, visibility != visible,
 *            clip-path distinto de none, y `overflow:hidden` en un elemento de
 *            ALTURA 0 (recorte de subarbol, que es invisible desde el hijo).
 *
 * Si la calibracion esta activa, se ensena la medida ANTES y DESPUES en pantalla.
 * Si no, trabaja en silencio: esto ya no es un experimento.
 */
(function () {
  var CALIB = !!window.__spotiCalibOn;
  var BOX = 'spoti-unhide-box';

  function short(node) {
    var t = (node.tagName || '?').toLowerCase().split(':')[0];
    if (node.id) t += '#' + node.id;
    return t;
  }

  function brief(node, i) {
    var cs = getComputedStyle(node);
    var r = node.getBoundingClientRect();
    return i + ':' + short(node)
      + '(op=' + cs.opacity
      + ' vis=' + cs.visibility
      + ' ov=' + cs.overflow
      + ' h=' + Math.round(r.height)
      + ' cp=' + (cs.clipPath === 'none' ? '-' : 'S')
      + ' tf=' + (cs.transform === 'none' ? '-' : 'S')
      + ' fl=' + (cs.filter === 'none' ? '-' : 'S') + ')';
  }

  function chain(el, max) {
    var out = [];
    var node = el;
    var n = 0;
    while (node && node.nodeType === 1 && n < max) {
      out.push(brief(node, n));
      node = node.parentElement;
      n++;
    }
    return out;
  }

  function show(lines) {
    if (!CALIB) return;
    var b = document.getElementById(BOX);
    if (!b) {
      b = document.createElement('div');
      b.id = BOX;
      b.style.cssText = 'position:fixed;left:0;top:0;right:0;z-index:2147483647;'
        + 'font:9px/1.35 monospace;color:#ffe600;background:rgba(0,0,0,.92);'
        + 'padding:4px 6px;white-space:pre-wrap;pointer-events:none';
      document.documentElement.appendChild(b);
    }
    b.textContent = lines.join('\n');
  }

  /** Toda la cadena de ancestros, elemento incluido, hasta <html>. */
  function ancestors(el) {
    var out = [];
    var node = el;
    var n = 0;
    while (node && node.nodeType === 1 && n < 60) {
      out.push(node);
      node = node.parentElement;
      n++;
    }
    return out;
  }

  /**
   * Fuerza los valores que ocultan un subarbol.
   *
   * @param el      elemento de partida (su cadena se recorre entera)
   * @param full    true = juego completo (login); false = conservador (reproductor)
   */
  function unhide(el, full) {
    if (!el) return 0;
    var list = ancestors(el);
    var before = CALIB ? chain(el, 16) : null;
    var touched = 0;

    for (var i = 0; i < list.length; i++) {
      var node = list[i];
      var st = node.style;
      if (!st) continue;
      var cs = getComputedStyle(node);
      var did = false;

      // Opacidad y visibilidad: nunca se heredan, asi que hay que mirarlas en
      // cada nivel. Son los dos sospechosos principales.
      if (parseFloat(cs.opacity) < 1) { st.setProperty('opacity', '1', 'important'); did = true; }
      if (cs.visibility && cs.visibility !== 'visible') {
        st.setProperty('visibility', 'visible', 'important'); did = true;
      }
      if (cs.clipPath && cs.clipPath !== 'none') {
        st.setProperty('clip-path', 'none', 'important'); did = true;
      }

      if (full) {
        if (cs.overflow && cs.overflow.indexOf('hidden') >= 0) {
          st.setProperty('overflow', 'visible', 'important'); did = true;
        }
        if (cs.transform && cs.transform !== 'none') {
          st.setProperty('transform', 'none', 'important'); did = true;
        }
        if (cs.filter && cs.filter !== 'none') {
          st.setProperty('filter', 'none', 'important'); did = true;
        }
      } else {
        // Conservador: solo el recorte de subarbol, y solo si el elemento no
        // tiene altura (con altura, `overflow:hidden` es scroll legitimo).
        if (cs.overflow && cs.overflow.indexOf('hidden') >= 0 && node.clientHeight === 0
            && node.clientWidth > 0) {
          st.setProperty('overflow', 'visible', 'important');
          st.setProperty('height', 'auto', 'important');
          did = true;
        }
      }

      if (did) touched++;
    }

    if (CALIB) {
      setTimeout(function () {
        show(['OCULTOS REPARADOS v1.0.9 — ' + touched + ' nodos forzados'
          , 'cadena DESPUES: ' + (before ? 'antes ' + before.length + ' niveles' : '')
          , chain(el, 16).join(' ')].slice(0, 4));
      }, 1500);
    }
    return touched;
  }

  /** Espera a que exista el elemento y aplica la reparacion. */
  function whenReady(find, full, tries) {
    var n = 0;
    var timer = setInterval(function () {
      n++;
      var el = find();
      if (el) {
        clearInterval(timer);
        unhide(el, full);
        // Las paginas de Spotify se redibujan: se reaplica sin abrir bucles.
        var again = 0;
        var t2 = setInterval(function () {
          unhide(el, full);
          if (++again > 6) clearInterval(t2);
        }, 1200);
        return;
      }
      if (n > (tries || 20)) clearInterval(timer);
    }, 400);
  }

  var url = location.href;

  if (url.indexOf('accounts.spotify.com') >= 0) {
    // LOGIN: juego completo, el que esta probado. Ademas, si el texto saliera del
    // color del fondo, se pasa a blanco (el fondo de Spotify es #121212).
    whenReady(function () {
      var p = document.querySelector('input[type=password]');
      return p ? (p.form || p.parentElement) : null;
    }, true);

    var style = document.createElement('style');
    style.textContent = 'form, form *{color:#fff !important}'
      + 'form input{background-color:rgba(255,255,255,.06) !important;'
      + 'border:1px solid #888 !important}';
    document.documentElement.appendChild(style);
    return;
  }

  if (url.indexOf('open.spotify.com') >= 0) {
    // REPRODUCTOR: juego conservador. El contenedor del reproductor es
    // `#main-view`; si no existe todavia, se usa la raiz de la app.
    whenReady(function () {
      return document.querySelector('#main-view')
        || document.querySelector('div[data-testid=root]')
        || document.querySelector('#Desktop_LeftSidebar_Id');
    }, false, 30);
  }
})();
