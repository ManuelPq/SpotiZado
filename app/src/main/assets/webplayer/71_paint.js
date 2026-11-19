/*
 * 71_paint.js — por que un texto que existe no se pinta. MIDE Y REPARA.
 *
 * LO QUE YA ESTA DESCARTADO (medido en el dispositivo)
 * ---------------------------------------------------
 *   - El motor pinta glifos: la pagina de prueba (`openTextTest`) dibuja HOLA a
 *     44 px, un parrafo, una caja verde con texto, un borde rojo y un bloque azul.
 *   - La pagina de Spotify esta VIVA: `nodos=98 next=si hidr=si hojas=5 rec=27
 *     err=0`, y su texto completo esta en el DOM ("¡Hola de nuevo! Correo
 *     electronico Continuar...").
 *   - No hay errores JS. La unica violacion de CSP es `script-src wasm-eval`,
 *     que es del propio Spotify y no impide pintar.
 *
 * Queda una familia de causas, y todas son lo mismo: el subarbol EXISTE, tiene
 * texto, y el motor decide no dibujarlo. Los tres modos conocidos:
 *
 *   1. OPACIDAD ANIMADA CONGELADA. La pagina de login de Spotify entra con una
 *      animacion de opacidad. Si las animaciones no avanzan —escalas de animacion
 *      a 0 en Opciones de desarrollador, o el WebView considerandose no visible—
 *      el elemento se queda en su estado INICIAL: opacity 0. Para siempre.
 *   2. `content-visibility: hidden` o `auto` con contencion: el motor salta el
 *      pintado del subarbol para ahorrar (mide si, pinta no).
 *   3. `contain: paint/size/layout` con altura 0 en un ancestro: recorta a nada.
 *
 * QUE HACE
 * --------
 * Mide ANTES (opacidad de body/#__next, animaciones en curso, content-visibility
 * y contain en la cadena), aplica una reparacion que cubre las tres, y mide
 * DESPUES. El resultado queda en `window.__spotiPaint` y la sonda lo devuelve a
 * la app, que lo pinta en su tarjeta.
 *
 * La reparacion es minima y no borra contenido: nada de quitar nodos.
 */
(function () {
  if (window.__spotiPaint) return;

  var R = window.__spotiPaint = {
    antes: {}, despues: {}, fix: false, veredicto: 'sin ejecutar'
  };

  function cs(el, prop) {
    if (!el) return '-';
    try { return getComputedStyle(el).getPropertyValue(prop); } catch (e) { return '-'; }
  }

  function ancla() {
    return document.querySelector('#__next')
      || document.querySelector('main')
      || document.querySelector('form')
      || document.body;
  }

  /** Cuantas animaciones hay y en que estado: la pista del caso 1. */
  function anims(el) {
    try {
      if (!el || !el.getAnimations) return -1;
      var a = el.getAnimations({ subtree: true });
      var corriendo = 0;
      for (var i = 0; i < a.length; i++) {
        if (a[i].playState === 'running') corriendo++;
      }
      return a.length + '/' + corriendo;
    } catch (e) { return -2; }
  }

  /** Recorre la cadena buscando los tres modos de "no me pintes". */
  function cadena(el) {
    var out = [];
    var node = el, n = 0;
    while (node && node.nodeType === 1 && n < 14) {
      var op = cs(node, 'opacity');
      var cv = cs(node, 'content-visibility');
      var ct = cs(node, 'contain');
      if (parseFloat(op) < 1 || (cv && cv !== 'visible') || (ct && ct !== 'none' && ct !== 'normal')) {
        var t = (node.tagName || '?').toLowerCase();
        if (node.id) t += '#' + node.id;
        out.push(t + '(op=' + op + ' cv=' + cv + ' ct=' + ct + ')');
      }
      node = node.parentElement; n++;
    }
    return out;
  }

  function medir(destino) {
    var a = ancla();
    var b = document.body;
    destino.op = cs(b, 'opacity');
    destino.nextOp = cs(document.querySelector('#__next'), 'opacity');
    destino.animaciones = anims(b);
    destino.animNext = anims(document.querySelector('#__next'));
    destino.contentVis = cs(document.querySelector('#__next'), 'content-visibility');
    destino.contain = cs(document.querySelector('#__next'), 'contain');
    destino.nodos = document.querySelectorAll('*').length;
    destino.sospechosos = cadena(a).slice(0, 6).join(' ');
    // Quien esta arriba en el centro de la pantalla: si no es un ancestro del
    // contenido, hay algo tapando.
    try {
      var x = Math.round(window.innerWidth / 2);
      var y = Math.round(window.innerHeight / 2);
      var top = document.elementFromPoint(x, y);
      var t = top ? (top.tagName || '?').toLowerCase() + (top.id ? '#' + top.id : '') : 'null';
      destino.centro = t;
    } catch (e) { destino.centro = 'error'; }
  }

  /**
   * Reparacion. Cubre los tres modos:
   *  - animaciones a duracion ~0 con `fill-mode: forwards` → saltan a su estado
   *    FINAL, que en una entrada por opacidad es opacity 1;
   *  - `content-visibility: visible` y `contain: none` en todo el arbol;
   *  - opacidad forzada a 1 en los contenedores.
   */
  function reparar() {
    if (document.getElementById('spoti-paint-fix')) return false;
    var st = document.createElement('style');
    st.id = 'spoti-paint-fix';
    st.textContent = [
      '*,*::before,*::after{',
      'animation-duration:.001s !important;',
      'animation-delay:0s !important;',
      'animation-fill-mode:forwards !important;',
      'transition-duration:0s !important;',
      'content-visibility:visible !important;',
      'contain:none !important}',
      'html,body,#__next,main{opacity:1 !important}'
    ].join('');
    (document.head || document.documentElement).appendChild(st);
    return true;
  }

  function ciclo() {
    medir(R.antes);
    R.fix = reparar();
    setTimeout(function () {
      medir(R.despues);
      var antesMalo = parseFloat(R.antes.op) < 1
        || parseFloat(R.antes.nextOp) < 1
        || (R.antes.contentVis && R.antes.contentVis !== 'visible');
      var arreglado = parseFloat(R.despues.op) >= 1 && parseFloat(R.despues.nextOp) >= 1;
      R.veredicto = antesMalo
        ? (arreglado ? 'reparado: oculto por opacidad/animacion' : 'sigue oculto tras reparar')
        : 'no estaba oculto: buscar oclusion';
    }, 1200);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', ciclo);
  } else {
    ciclo();
  }
})();
