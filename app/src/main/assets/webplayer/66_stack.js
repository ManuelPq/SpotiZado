/*
 * 66_stack.js — quien tapa el contenido de Spotify. MEDIDO, no supuesto.
 *
 * LA PRUEBA QUE LO PIDE
 * ---------------------
 * v1.2.1, captura del dispositivo con el login clasico cargado:
 *
 *   v1.2.1 · campos=2 · pwd=sí · visible=sí · vw=384 · sw=384 · zoom=1.0
 *
 * Y la pantalla sigue NEGRA. El formulario esta en el DOM, ocupa la zona visible
 * (por eso `visible=sí`) y aun asi no se ve ni un pixel suyo. Mientras tanto el
 * boton que inyectamos nosotros —`position:fixed`, `z-index:2147483000`— SI se
 * pinta. Eso descarta el DOM, el viewport, el motor de pintado y el WebView: hay
 * algo OPACO POR ENCIMA del contenido. Nuestro boton se ve porque va por delante.
 *
 * QUE HACE
 * --------
 * 1. MIDE. En el centro del campo de email/password pregunta
 *    `document.elementsFromPoint()`, que devuelve la pila de elementos bajo ese
 *    punto, de arriba abajo. Todo lo que aparezca por encima del campo es un
 *    candidato a taparlo.
 * 2. BUSCA CAPAS. Recorre tambien los elementos `position:fixed`/`absolute` que
 *    cubren casi toda la ventana con fondo opaco: cortinas de carga, overlays de
 *    consentimiento, `backdrop-filter` a pantalla completa.
 * 3. REPARA. Quita las capas que tapan (las saca del arbol con `display:none`) y
 *    vuelve a medir para comprobar que ahora el campo es el primero de la pila.
 * 4. INFORMA. Deja el resultado en `window.__spotiStack` y, si la calibracion
 *    esta activa, lo escribe en pantalla.
 *
 * Nada de esto toca el formulario ni sus valores: solo retira lo que se le pone
 * delante.
 */
(function () {
  if (window.__spotiStackDone) return;
  window.__spotiStackDone = 1;

  var CALIB = !!window.__spotiCalibOn;
  var BOX = 'spoti-stack-box';
  var own = 'spoti-classic-login';

  var report = { fase: 'inicio', punto: null, pila: [], capas: [], quitadas: 0, veredicto: '' };

  function desc(el) {
    if (!el || el.nodeType !== 1) return '?';
    var t = (el.tagName || '?').toLowerCase();
    if (el.id) t += '#' + el.id;
    var c = (typeof el.className === 'string' ? el.className : '').trim();
    if (c) t += '.' + c.split(/\s+/).slice(0, 2).join('.');
    return t;
  }

  function bg(cs) {
    var b = cs.backgroundColor || '';
    return b.replace(/\s+/g, '');
  }

  /** Un fondo cuenta como opaco si es rgb() sin canal alfa o con alfa >= .85 */
  function opaque(color) {
    if (!color || color === 'transparent') return false;
    var m = color.match(/rgba?\(([^)]+)\)/);
    if (!m) return false;
    var p = m[1].split(',').map(function (x) { return parseFloat(x); });
    if (p.length < 3) return false;
    var a = p.length >= 4 ? p[3] : 1;
    return a >= 0.85;
  }

  function viewportCover(el) {
    var r = el.getBoundingClientRect();
    var W = window.innerWidth, H = window.innerHeight;
    if (W <= 0 || H <= 0) return 0;
    var w = Math.max(0, Math.min(r.right, W) - Math.max(r.left, 0));
    var h = Math.max(0, Math.min(r.bottom, H) - Math.max(r.top, 0));
    return (w * h) / (W * H);
  }

  function isOurs(el) { return !!(el && (el.id === own || el.id === BOX)); }

  /** El campo del formulario, sea clasico (password) o passwordless (email). */
  function field() {
    return document.querySelector('input[type=password]')
      || document.querySelector('input[type=email]')
      || document.querySelector('input[autocomplete*=username]')
      || document.querySelector('input[name=username]')
      || document.querySelector('input');
  }

  function center(el) {
    var r = el.getBoundingClientRect();
    return {
      x: Math.round(Math.min(Math.max(r.left + r.width / 2, 1), window.innerWidth - 1)),
      y: Math.round(Math.min(Math.max(r.top + r.height / 2, 1), window.innerHeight - 1))
    };
  }

  /**
   * Capas a pantalla completa que no son el formulario.
   * Mirado en todo el documento, no solo bajo el campo: hay cortinas que no
   * cubren el punto exacto pero si el resto de la pantalla.
   */
  function scanLayers() {
    var out = [];
    var all = document.querySelectorAll('body *');
    var max = Math.min(all.length, 4000);
    for (var i = 0; i < max; i++) {
      var el = all[i];
      if (isOurs(el)) continue;
      var cs = getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden') continue;
      if (cs.position !== 'fixed' && cs.position !== 'absolute') continue;
      var cover = viewportCover(el);
      if (cover < 0.85) continue;
      if (!opaque(bg(cs))) continue;
      var zi = parseInt(cs.zIndex, 10);
      out.push({ el: el, name: desc(el), z: isNaN(zi) ? 0 : zi, cover: Math.round(cover * 100), bg: bg(cs) });
    }
    // De arriba abajo: primero lo que mas alto esta.
    out.sort(function (a, b) { return b.z - a.z; });
    return out;
  }

  /** ¿Este elemento contiene el formulario? Entonces no es una cortina, es su marco. */
  function wrapsField(el, f) {
    return !!(el && f && el.contains && el.contains(f));
  }

  function hide(el, why) {
    if (!el || isOurs(el)) return false;
    try {
      el.setAttribute('data-spoti-hidden', why || 'stack');
      el.style.setProperty('display', 'none', 'important');
      return true;
    } catch (e) { return false; }
  }

  function measure(punto, f) {
    var stack = [];
    try {
      var list = document.elementsFromPoint(punto.x, punto.y) || [];
      for (var i = 0; i < list.length && i < 8; i++) {
        var el = list[i];
        var cs = getComputedStyle(el);
        stack.push(desc(el) + '[' + (el === f ? 'CAMPO' : bg(cs)) + ']');
      }
      // ¿Por encima del campo, cuantos elementos hay? Eso es lo que lo tapa.
    } catch (e) {
      stack.push('error: ' + e.message);
    }
    return stack;
  }

  function paint() {
    if (!CALIB) return;
    var b = document.getElementById(BOX);
    if (!b) {
      b = document.createElement('div');
      b.id = BOX;
      b.style.cssText = 'position:fixed;left:0;bottom:0;right:0;z-index:2147483647;'
        + 'font:9px/1.3 monospace;color:#00ffb3;background:rgba(0,0,0,.92);'
        + 'padding:3px 6px;white-space:pre-wrap;pointer-events:none';
      document.documentElement.appendChild(b);
    }
    b.textContent = [
      'CAPAS v1.3.0 — ' + report.veredicto,
      'punto=' + JSON.stringify(report.punto),
      'pila=' + report.pila.join(' > '),
      'capas=' + (report.capas.length ? report.capas.join(' ') : 'ninguna'),
      'quitadas=' + report.quitadas
    ].join('\n');
  }

  function run() {
    var f = field();
    if (!f) { report.veredicto = 'sin campo'; return false; }

    var punto = center(f);
    report.punto = punto;
    report.pila = measure(punto, f);

    // 1) Lo que este por encima del campo bajo su propio centro.
    var quitadas = 0;
    try {
      var list = document.elementsFromPoint(punto.x, punto.y) || [];
      for (var i = 0; i < list.length; i++) {
        var el = list[i];
        if (el === f || isOurs(el)) break;          // hemos llegado al campo
        if (wrapsField(el, f)) continue;            // es su marco, no una cortina
        if (el === document.documentElement || el === document.body) continue;
        var cs = getComputedStyle(el);
        // Solo se retira si de verdad puede tapar: area grande o fondo opaco.
        if (viewportCover(el) > 0.5 || opaque(bg(cs))) {
          if (hide(el, 'encima-del-campo')) quitadas++;
        }
      }
    } catch (e) { /* sin elementsFromPoint no se repara */ }

    // 2) Cortinas a pantalla completa, aunque no cubran el centro exacto.
    var capas = scanLayers();
    report.capas = capas.map(function (c) { return c.name + '(z' + c.z + ' cov' + c.cover + ' ' + c.bg + ')'; });
    for (var k = 0; k < capas.length; k++) {
      if (wrapsField(capas[k].el, f)) continue;
      if (hide(capas[k].el, 'cortina')) quitadas++;
    }

    report.quitadas = quitadas;
    if (quitadas === 0) {
      report.veredicto = 'sin capa: nada tapa el campo';
      report.pila = measure(punto, field() || f);
    } else {
      // 3) Volver a medir: ahora el campo deberia ser el primero de la pila.
      var f2 = field() || f;
      var p2 = center(f2);
      var after = measure(p2, f2);
      var top = (document.elementsFromPoint(p2.x, p2.y) || [])[0];
      report.veredicto = (top === f2)
        ? 'campo al frente (' + quitadas + ' capas fuera)'
        : 'campo aun tapado por ' + desc(top);
      report.pila = after;
    }
    return true;
  }

  var intentos = 0;
  var timer = setInterval(function () {
    intentos++;
    if (run()) { paint(); }
    // Las paginas de Spotify se redibujan: se re-mide unas cuantas veces.
    if (intentos > 0 && intentos % 2 === 0) run();
    if (intentos >= 12) { clearInterval(timer); paint(); }
  }, 700);
})();
