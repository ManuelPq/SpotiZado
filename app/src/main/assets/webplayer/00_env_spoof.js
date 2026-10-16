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
})();
