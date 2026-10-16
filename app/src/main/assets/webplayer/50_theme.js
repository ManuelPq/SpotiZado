/*
 * 50_theme.js — tema AMOLED y color de acento.
 *
 * AMOLED: fuerza a negro puro los fondos del reproductor (ahorra bateria en
 * pantallas OLED). El color de acento se toma de la app (Material You / manual)
 * y se inyecta como variable CSS, de modo que el reproductor hereda la paleta
 * del sistema en lugar de usar el verde de Spotify.
 */
(function () {
  var AMOLED = [
    '.encore-dark-theme{--background-base:#000;--background-highlight:#000;' +
      '--background-elevated-base:#000;--background-elevated-highlight:#000;' +
      '--background-elevated-press:#000;--background-tinted-base:#000}',
    'aside[data-testid=now-playing-bar]{background:#000!important;box-shadow:none;border-top:1px solid #666}'
  ].join(' ');

  window.spotiApplyTheme = function (cfg) {
    cfg = cfg || {};
    var old = document.getElementById('spoti-theme');
    if (old) old.remove();
    if (!cfg.amoled && !cfg.accent) return;

    var css = '';
    if (cfg.amoled) css += AMOLED + '\n';
    if (cfg.accent) {
      // Sobrescribe las variables de acento de Encore (el sistema de diseño de
      // Spotify) con el color elegido por el usuario.
      css += ':root,.encore-dark-theme{' +
        '--background-highlight:' + cfg.accent + ';' +
        '--essential-bright-accent:' + cfg.accent + ';' +
        '--text-bright-accent:' + cfg.accent + ';' +
        '--decorative-subdued:' + cfg.accent + '99}';
    }
    var st = document.createElement('style');
    st.id = 'spoti-theme';
    st.textContent = css;
    document.head.appendChild(st);
  };
})();
