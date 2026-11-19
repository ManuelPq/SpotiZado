/*
 * 67_witness.js — testigo de pintado: donde se muere el pixel.
 *
 * POR QUE EXISTE
 * --------------
 * Estado medido en el dispositivo con la 1.3.0: el formulario esta en el DOM,
 * con geometria valida dentro de la vista (`visible=sí`), la pagina servida es
 * HTML normal (un <form>, un <input id=username>, cero CSS exotico comprobado
 * en el servidor) y aun asi la zona del WebView esta negra.
 *
 * Lo unico que SI se pinta es lo que inyectamos con `position:fixed` y
 * `z-index:2147483000`. Eso no es un detalle: `position:fixed` con z-index alto
 * obliga al motor a crear una CAPA DE COMPOSICION propia. Si esa capa se pinta
 * y el documento normal no, el fallo no esta en el CSS ni en el DOM: esta en la
 * composicion de la capa principal del WebView.
 *
 * QUE HACE
 * --------
 * Pinta tres franjas identicas salvo en UNA cosa cada una, para aislar el nivel
 * de pintado que falla:
 *
 *   A — flujo normal (sin position, sin z-index)  → capa principal
 *   B — position:absolute, z-index:5              → capa principal, pero apilada
 *   C — position:fixed, z-index:10                → capa de composicion propia
 *
 * Si solo se ve C, la capa principal no compone. Si se ven A, B y C, el
 * documento pinta y el problema es otra cosa (oclusion o subarbol no pintado).
 * Con la calibracion activa, ademas escribe el resumen en pantalla.
 */
(function () {
  if (window.__spotiWitness) return;
  window.__spotiWitness = 1;

  function band(kind, label, color, css) {
    var d = document.createElement('div');
    d.textContent = label;
    d.style.cssText = 'box-sizing:border-box;width:100%;height:26px;padding:2px 6px;'
      + 'font:600 11px/22px -apple-system,Roboto,sans-serif;color:#000;'
      + 'background:' + color + ';' + css;
    return d;
  }

  // A: flujo normal. Se inserta como PRIMER hijo de <body>.
  var A = band('A', 'A estatico', '#ff2d2d', 'position:static;');

  // B: absoluto, apilado.
  var B = band('B', 'B absoluto', '#2d7dff', 'position:absolute;left:0;top:26px;z-index:5;');

  // C: fijo, capa propia.
  var C = band('C', 'C fijo (capa)', '#ffd400', 'position:fixed;left:0;top:52px;z-index:10;');

  function place() {
    var body = document.body;
    if (!body) return false;
    if (A.isConnected && B.isConnected && C.isConnected) return true;
    try {
      if (!A.isConnected) body.insertBefore(A, body.firstChild);
      if (!B.isConnected) body.insertBefore(B, body.firstChild);
      if (!C.isConnected) body.appendChild(C);
      return true;
    } catch (e) { return false; }
  }

  /**
   * Neutraliza los dos modos de "no pintes el subarbol aunque si midas".
   * Ninguno estaba cubierto por 65_unhide.js, y los dos encajan con el sintoma:
   * el elemento tiene caja y geometria, pero su contenido no se rasteriza.
   */
  function neutro(el) {
    var node = el, n = 0, tocados = 0;
    while (node && node.nodeType === 1 && n < 40) {
      var cs = getComputedStyle(node);
      var st = node.style;
      if (cs.contentVisibility && cs.contentVisibility !== 'visible') {
        st.setProperty('content-visibility', 'visible', 'important'); tocados++;
      }
      if (cs.contain && cs.contain !== 'none' && cs.contain !== 'normal') {
        st.setProperty('contain', 'none', 'important'); tocados++;
      }
      node = node.parentElement; n++;
    }
    return tocados;
  }

  function run() {
    place();
    var f = document.querySelector('input[type=password]')
      || document.querySelector('input#username')
      || document.querySelector('input');
    if (f) neutro(f);
    return { A: !!A.isConnected, B: !!B.isConnected, C: !!C.isConnected };
  }

  var n = 0;
  var t = setInterval(function () {
    run();
    if (++n > 20) clearInterval(t);
  }, 600);

  var BENCH = 'AVISO TESTIGO v1.4.0';
  window.__spotiWitnessReport = function () {
    return BENCH + ' A=' + (!!A.isConnected) + ' B=' + (!!B.isConnected) + ' C=' + (!!C.isConnected);
  };
})();
