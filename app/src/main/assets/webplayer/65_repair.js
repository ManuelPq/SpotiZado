/*
 * 65_repair.js — reparacion del formulario de login. EXPERIMENTAL, apagada por defecto.
 *
 * QUE SE MIDIO (v1.0.7, capa de calibracion, dispositivo real):
 *
 *   form pwd: left=24 top=274 w=336 h=52  OK en vista
 *   cadena: input(vis=visible op=1 disp=block) < div(...) < div(...) < form(...)
 *   fondo: html=rgba(0,0,0,0) body=rgb(18,18,18)   texto=240 car.
 *
 * O sea: la caja del formulario esta PERFECTA — colocada, en vista, y con estilos
 * calculados visibles/opacos. Y sin embargo no se pinta nada.
 *
 * Eso solo lo puede producir algo por ENCIMA de <form> en el arbol:
 *   - `opacity` no se hereda, asi que un ancestro con `opacity:0` deja al hijo con
 *     `op=1` en su computed y no se ve en la cadena medida (que llega hasta form);
 *   - `overflow:hidden` con altura 0 recorta el subarbol mientras el rect del campo
 *     sigue siendo correcto;
 *   - `clip-path`, `transform` o `filter` en un ancestro lo apartan o lo borran.
 * O una tercera: texto oscuro sobre fondo oscuro.
 *
 * Que hace este script
 * --------------------
 *   1. MIDE toda la cadena de ancestros hasta <html> (no solo 4 niveles), con
 *      opacity, overflow, altura, transform y filter de cada uno.
 *   2. REPARA: fuerza opacidad, visibilidad y desbordamiento en toda la cadena.
 *   3. Vuelve a MEDIR despues, y ensena las dos medidas en un recuadro en pantalla.
 *
 * Asi, con UNA captura, se sabe si el culpable era un ancestro oculto (el
 * formulario aparece y la cadena "despues" lo confirma) o si no lo era (el
 * formulario sigue sin pintarse y el recuadro dice cual era el estado real).
 * El recuadro se pinta con DOM propio, sin tocar el de Spotify.
 */
(function () {
  if (location.href.indexOf('accounts.spotify.com') < 0) return;

  var BOX = 'spoti-repair-box';

  function short(node) {
    var t = (node.tagName || '?').toLowerCase().split(':')[0];
    if (node.id) t += '#' + node.id;
    else if (node.className && typeof node.className === 'string' && node.className.length < 24) {
      t += '.' + node.className.trim().split(/\s+/)[0];
    }
    return t;
  }

  function describe(node, i) {
    var cs = getComputedStyle(node);
    var r = node.getBoundingClientRect();
    return i + ':' + short(node)
      + '(op=' + cs.opacity
      + ' vis=' + cs.visibility
      + ' ov=' + cs.overflow
      + ' h=' + Math.round(r.height)
      + ' tf=' + (cs.transform === 'none' ? '-' : 'S')
      + ' fl=' + (cs.filter === 'none' ? '-' : 'S') + ')';
  }

  function chain(el, max) {
    var out = [];
    var n = 0;
    var node = el;
    while (node && node.nodeType === 1 && n < max) {
      out.push(describe(node, n));
      node = node.parentElement;
      n++;
    }
    return out;
  }

  function paint(el, what) {
    var cs = getComputedStyle(el);
    return what + ' color=' + cs.color + ' bg=' + cs.backgroundColor
      + ' fs=' + cs.fontSize + ' op=' + cs.opacity;
  }

  function show(lines) {
    var b = document.getElementById(BOX);
    if (!b) {
      b = document.createElement('div');
      b.id = BOX;
      b.style.cssText = 'position:fixed;left:0;top:0;right:0;z-index:2147483647;'
        + 'font:9px/1.35 monospace;color:#ffe600;background:rgba(0,0,0,.92);'
        + 'padding:4px 6px;white-space:pre-wrap;pointer-events:none;'
        + 'border-bottom:1px solid #ffe600';
      document.documentElement.appendChild(b);
    }
    b.textContent = lines.join('\n');
  }

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

  var tries = 0;
  var timer = setInterval(function () {
    tries++;
    var pwd = document.querySelector('input[type=password]');
    if (!pwd) {
      if (tries > 20) clearInterval(timer);
      return;
    }
    clearInterval(timer);

    var form = pwd.form || pwd.closest('form') || pwd.parentElement;
    var head = document.querySelector('h1, h2, [role=heading], legend') || form;

    var before = ['REPARAR LOGIN (v1.0.8) — ANTES'];
    before.push(paint(pwd, 'pwd '));
    before.push(paint(form, 'form'));
    if (head && head !== form) before.push(paint(head, 'head'));
    before.push('cadena: ' + chain(pwd, 14).join(' '));
    show(before);

    // REPARACION: opacidad, visibilidad y recorte en TODA la cadena.
    var list = ancestors(form);
    var forced = 0;
    for (var i = 0; i < list.length; i++) {
      try {
        var st = list[i].style;
        if (!st) continue;
        st.setProperty('opacity', '1', 'important');
        st.setProperty('visibility', 'visible', 'important');
        st.setProperty('overflow', 'visible', 'important');
        st.setProperty('clip-path', 'none', 'important');
        st.setProperty('transform', 'none', 'important');
        st.setProperty('filter', 'none', 'important');
        forced++;
      } catch (e) { }
    }

    // Si el texto es del color del fondo, tambien se corrige: blanco sobre el
    // fondo oscuro de Spotify. Solo dentro del formulario.
    var fix = document.createElement('style');
    fix.textContent = 'form, form *{color:#fff !important}'
      + 'form input,form button{background-color:transparent !important}'
      + 'form input{border:1px solid #888 !important}';
    document.documentElement.appendChild(fix);

    setTimeout(function () {
      var after = ['REPARAR LOGIN (v1.0.8) — DESPUES (' + forced + ' nodos forzados)'];
      after.push(paint(pwd, 'pwd '));
      after.push(paint(form, 'form'));
      after.push('cadena: ' + chain(pwd, 14).join(' '));
      after.push('visibles en pantalla: ' + (pwd.getBoundingClientRect().height > 0 ? 'caja OK' : 'sin caja'));
      show(after);
      if (window.spotiNotify) spotiNotify('login', { phase: 'repair-applied' });
    }, 1600);
  }, 500);
})();
