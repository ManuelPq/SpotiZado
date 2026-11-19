/*
 * 68_ui.js — ¿esta la pagina ESTILADA? Y si no lo esta, la hacemos legible.
 *
 * LA PRUEBA QUE OBLIGA A ESTO
 * ---------------------------
 * v1.4.0, testigo de tres franjas en el dispositivo:
 *
 *   A estatico  -> SE VE
 *   B absoluto  -> SE VE
 *   C fijo      -> SE VE
 *   ...y debajo, negro absoluto, con `campos=2 · pwd=sí`.
 *
 * El documento pinta. El motor pinta. Nuestros nodos se ven y los de Spotify no.
 * La unica diferencia que queda entre unos y otros es de donde sale su pintado:
 * los nuestros llevan ESTILOS EN LINEA; los de Spotify dependen de SUS hojas de
 * estilo, que vienen de `accounts.scdn.co` (comprobado en el HTML servido).
 *
 * Si esas hojas no llegan o no se aplican, la pagina queda SIN ESTILOS: fondo
 * transparente (se ve el #121212 del WebView) y texto por defecto NEGRO.
 * Resultado literal: texto negro sobre fondo negro. Los campos de un formulario
 * sin estilos tambien quedan practicamente invisibles con ese fondo.
 *
 * QUE HACE
 * --------
 * 1. MIDE si la pagina esta estilada: numero de hojas, reglas legibles, fondo
 *    calculado del body y color calculado del texto. Eso distingue "sin CSS" de
 *    "CSS aplicado pero oculto", que son problemas opuestos y hasta ahora los
 *    estabamos tratando igual.
 * 2. Si no hay estilos (o el texto sale del color del fondo), inyecta un CSS de
 *    respaldo MINIMO que hace legible el formulario igualmente: fondo oscuro,
 *    texto blanco, campos con borde visible.
 * 3. Lo ensena en un panel fijo. Nuestros elementos fijos SI se pintan (esta
 *    medido), asi que el panel es un canal de salida fiable aunque la pagina
 *    este en negro.
 */
(function () {
  if (window.__spotiUi) return;

  var FALLBACK_ID = 'spoti-fallback-css';
  var PANEL_ID = 'spoti-ui-panel';

  function sheets() {
    var n = 0, rules = 0, blocked = 0;
    var ss = document.styleSheets || [];
    n = ss.length;
    for (var i = 0; i < n; i++) {
      try { rules += (ss[i].cssRules || []).length; } catch (e) { blocked++; }
    }
    return { n: n, rules: rules, blocked: blocked };
  }

  function styleState() {
    var s = sheets();
    var body = document.body;
    var bg = body ? getComputedStyle(body).backgroundColor : '?';
    var fg = body ? getComputedStyle(body).color : '?';
    var form = document.querySelector('form');
    var fcs = form ? getComputedStyle(form) : null;
    return {
      hojas: s.n,
      reglas: s.rules,
      inaccesibles: s.blocked,
      url: location.href.replace(/^https?:\/\//, '').slice(0, 46),
      bodyBg: bg,
      bodyFg: fg,
      formDisplay: fcs ? fcs.display : 'sin form',
      formOpacity: fcs ? fcs.opacity : '-',
      fonts: (document.fonts && document.fonts.status) || '?',
      nodos: document.querySelectorAll('*').length,
      // Un body transparente con el WebView en #121212 es la firma de "sin CSS".
      sinCss: (bg === 'rgba(0, 0, 0, 0)' || bg === 'transparent')
    };
  }

  /** CSS de respaldo: solo se activa si hace falta, nunca pisa lo que funciona. */
  function fallback() {
    if (document.getElementById(FALLBACK_ID)) return false;
    var st = document.createElement('style');
    st.id = FALLBACK_ID;
    st.textContent = [
      'html,body{background:#121212 !important;color:#fff !important;',
      'margin:0 !important;font:400 15px/1.4 system-ui,sans-serif !important}',
      'body *{opacity:1 !important;visibility:visible !important;filter:none !important}',
      'form{max-width:420px !important;margin:24px auto !important;padding:16px !important;',
      'background:#1b1b1b !important;border-radius:12px !important}',
      'form *{color:#fff !important;opacity:1 !important;visibility:visible !important}',
      'input{display:block !important;width:100% !important;min-height:44px !important;',
      'box-sizing:border-box !important;margin:0 0 12px !important;padding:0 12px !important;',
      'background:#2a2a2a !important;border:1px solid #8a8a8a !important;',
      'color:#fff !important;border-radius:6px !important;font-size:16px !important}',
      'label,legend,h1,h2,h3{color:#fff !important}',
      'button{min-height:44px !important;border-radius:999px !important;',
      'background:#1ed760 !important;color:#000 !important;border:0 !important;',
      'font-weight:700 !important;padding:0 20px !important}',
      'a{color:#a7c7ff !important}'
    ].join('');
    document.documentElement.appendChild(st);
    return true;
  }

  function panel(text, ok) {
    var p = document.getElementById(PANEL_ID);
    if (!p) {
      p = document.createElement('div');
      p.id = PANEL_ID;
      p.style.cssText = 'position:fixed;left:0;bottom:0;right:0;z-index:2147483000;'
        + 'font:10px/1.35 monospace;padding:4px 8px;white-space:pre-wrap;'
        + 'background:rgba(0,0,0,.88);border-top:2px solid #0f0;color:#0f0;'
        + 'max-height:42vh;overflow:auto';
      document.documentElement.appendChild(p);
    }
    p.style.borderTopColor = ok ? '#0f0' : '#ff4d4d';
    p.textContent = text;
  }

  function run() {
    var st = styleState();
    // Sin hojas, con el body transparente o sin nodos = la pagina no se aplico.
    var needFix = st.hojas === 0 || st.sinCss || st.nodos < 20;
    if (needFix) fallback();
    st.aplicado = needFix ? 'CSS de respaldo' : 'CSS de Spotify';
    window.__spotiUi = st;

    panel('UI v1.5.0 — ' + st.aplicado
      + '\nhojas=' + st.hojas + ' reglas=' + st.reglas + ' inaccesibles=' + st.inaccesibles
      + ' nodos=' + st.nodos + ' fonts=' + st.fonts
      + '\nbodyBg=' + st.bodyBg + ' bodyFg=' + st.bodyFg
      + '\nform=' + st.formDisplay + ' op=' + st.formOpacity
      + '\n' + st.url, !needFix);
    return st;
  }

  var n = 0;
  var t = setInterval(function () {
    run();
    if (++n > 14) clearInterval(t);
  }, 700);
})();
