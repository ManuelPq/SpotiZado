/*
 * 00_env_spoof.js — fase onPageStarted
 * Hace que Spotify sirva el reproductor de ESCRITORIO dentro del WebView:
 * pantalla 1920x1080, plataforma Win32. Sin esto el reproductor movil no
 * expone los selectores que necesitan las capas de control y de anuncios.
 */
(function () {
  var g = function (obj, prop, value) { obj.__defineGetter__(prop, function () { return value; }); };
  // TAMANO: se declara 1920x1080, como en la 1.0.2.
  //
  // En la 1.0.3 lo quite como "limpieza" al arreglar el reproductor, y la pagina
  // de login se quedo EN NEGRO: el formulario seguia en la pagina (campos=2,
  // pwd=si) pero no se pintaba nada. Fue un cambio no medido, metido de propina
  // en una version cuyo objetivo era otro, y el precio fue perder lo que
  // funcionaba. Vuelve tal cual estaba.
  //
  // Lo que SI se queda fuera es el forzado del meta viewport a 1920: eso era lo
  // que sacaba la interfaz de la pantalla, y ahora se fija a `device-width`.
  g(window.screen, 'width', 1920);
  g(window.screen, 'height', 1080);
  g(window.screen, 'availWidth', 1920);
  g(window.screen, 'availHeight', 1040);
  g(window.screen, 'colorDepth', 24);
  g(window.screen, 'pixelDepth', 24);
  g(window, 'innerWidth', 1920);
  g(window, 'innerHeight', 978);
  g(window, 'outerWidth', 1920);
  g(window, 'outerHeight', 1040);
  g(window.navigator, 'vendor', 'Google Inc.');
  g(window.navigator, 'productSub', '20030107');
  g(window.navigator, 'platform', 'Win32');
  g(window.navigator, 'oscpu', 'null');
  g(window.navigator, 'maxTouchPoints', 0);

  /*
   * VIEWPORT: se fija al ANCHO REAL del dispositivo.
   *
   * Antes se forzaba `width=1920`. Medido en el dispositivo con la 1.0.2:
   *   Vista / documento = 1920x978 / 1920 · Escala de maquetacion 1.0 · VISUAL 1.0
   * O sea: una pagina de 1920 px CSS metida, sin reducir, en una ventana de
   * 384 dp. Solo cabia la esquina superior izquierda y hacia falta scroll en los
   * dos sentidos. La interfaz no se veia porque estaba fuera de la pantalla.
   *
   * `width=device-width` + `initial-scale=1` es lo que RESPETA las medidas de la
   * pantalla: el layout coincide con el ancho real y no hay desbordamiento. Es
   * ademas lo que necesita el CSS de 40_layout.js, que adapta la interfaz de
   * escritorio a ese ancho.
   */
  function spotiFixViewport() {
    var head = document.head || document.documentElement;
    if (!head) return;
    var want = 'width=device-width, initial-scale=1';
    var m = document.querySelector('meta[name=viewport]');
    if (m) {
      if (m.getAttribute('content') !== want) m.setAttribute('content', want);
    } else {
      m = document.createElement('meta');
      m.name = 'viewport';
      m.setAttribute('content', want);
      head.appendChild(m);
    }
  }
  spotiFixViewport();
  // En onPageStarted puede no existir <head> todavia: se reintenta un momento.
  var spotiVp = 0;
  var spotiVpT = setInterval(function () {
    spotiFixViewport();
    if (++spotiVp > 12) clearInterval(spotiVpT);
  }, 150);
})();
