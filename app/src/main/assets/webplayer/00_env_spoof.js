/*
 * 00_env_spoof.js — fase onPageStarted
 * Hace que Spotify sirva el reproductor de ESCRITORIO dentro del WebView:
 * pantalla 1920x1080, plataforma Win32. Sin esto el reproductor movil no
 * expone los selectores que necesitan las capas de control y de anuncios.
 */
(function () {
  var g = function (obj, prop, value) { obj.__defineGetter__(prop, function () { return value; }); };
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
   * COHERENCIA DE VIEWPORT — el arreglo del 100 % de pantalla.
   *
   * Medido en el dispositivo: `Vista / documento = 1920x978 / 420`.
   *   - `window.innerWidth` = 1920  → lo pone este spoof.
   *   - `document.scrollWidth` = 420 → el ancho REAL del layout.
   *
   * Esa contradiccion es el fallo: el JS de Spotify cree que hay 1920 px y por
   * eso sirve su interfaz de ESCRITORIO, que necesita ese ancho; pero el CSS se
   * maqueta a 420 px (por el `meta viewport` de Spotify, que manda
   * `width=device-width`). La UI de escritorio se sale de la pantalla y solo se
   * ve el trozo que cae dentro de esos 420 px: de ahi el area negra y la
   * sensacion de que «no se usa el 100 %».
   *
   * Solucion: forzar el layout viewport a 1920 para que coincida con lo que
   * declara el spoof. El WebView entonces escala 1920 -> 420 y todo cuadra:
   * layout de escritorio, visible entero y coherente con el JS.
   */
  function spotiFixViewport() {
    var head = document.head || document.documentElement;
    if (!head) return;
    var m = document.querySelector('meta[name=viewport]');
    if (m) {
      if (m.getAttribute('content') !== 'width=1920') m.setAttribute('content', 'width=1920');
    } else {
      m = document.createElement('meta');
      m.name = 'viewport';
      m.setAttribute('content', 'width=1920');
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
