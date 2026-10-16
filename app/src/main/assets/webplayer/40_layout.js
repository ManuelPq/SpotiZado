/*
 * 40_layout.js — adapta el reproductor de escritorio a pantalla de movil.
 * Solo se aplica si guiMode == "mobile"; si no, el WebView se muestra tal cual.
 * (La hoja de estilos va en 50_theme.js para poder desactivarla por separado.)
 */
(function () {
  var CSS = [
    'body{min-width:100%!important;min-height:100%!important}',
    '.contentSpacing{padding:0}',
    'div[data-testid=root]{--panel-gap:0!important}',
    '#main-view+div,#main-view+div>div{overflow:hidden!important;width:auto}',
    '#main-view+div>div>div>div:nth-child(2)>div{width:100vw!important}',
    'div[data-encore-id=banner],#global-nav-bar>div:first-of-type,' +
      '#global-nav-bar a[href="/download"],button[data-testid=fullscreen-mode-button],' +
      'div.main-view-container__mh-footer-container{display:none!important}',
    'div[data-testid=tracklist-row]{padding:0 10px 0 0;grid-gap:0}',
    'div[data-testid=tracklist-row] button:not([data-testid=add-to-playlist-button])' +
      '{transform:scale(1.3)!important;opacity:.6!important}',
    'main>section h1.encore-text-headline-large{font-size:22px!important}',
    'section[data-testid=track-page] h1{font-size:20px!important}',
    /* Barra inferior: ancho completo y controles grandes, como una app nativa. */
    'aside[data-testid=now-playing-bar]{min-width:100%!important;box-shadow:none}',
    'aside[data-testid=now-playing-bar]>div:first-child{margin-top:2px;flex-direction:column!important;height:auto!important}',
    'aside[data-testid=now-playing-bar]>div>div{width:100%!important}',
    'aside[data-testid=now-playing-bar]>div>div:last-child>div{min-height:32px;margin:5px 10px}',
    'aside[data-testid=now-playing-bar]>div>div:last-child button{transform:scale(1.15);margin:0 5px}',
    'div[data-testid=general-controls]{margin:15px 0 25px}',
    'div[data-testid=general-controls] button{transform:scale(1.4)!important;margin:0 8px!important}',
    'div[data-testid=now-playing-widget]{justify-content:center;overflow:hidden}',
    'div[data-testid=now-playing-widget]>div:first-child{display:none!important}',
    'div[data-testid=now-playing-widget]>div:nth-child(2){display:flex!important;overflow:hidden!important}',
    'div[data-testid=now-playing-widget]>div:nth-child(2) span{font-size:13px!important;height:20px!important;margin:0!important}',
    '[data-tippy-root]{overflow:hidden!important}',
    '[data-tippy-root],[data-tippy-root] *{transition:none!important;transform:none!important}',
    'form[role=search]{z-index:10;margin-left:48px;max-width:88%}',
    /* Boton propio para cerrar "Reproduciendo ahora" (lo inserta el JS). */
    '.npbtn{cursor:pointer;color:#b3b3b3;background:transparent;border:none;width:32px;height:32px;padding:8px}',
    '.npbtn.active{color:#1db954}'
  ].join(' ');

  function inject() {
    if (document.getElementById('spoti-layout')) return;
    var st = document.createElement('style');
    st.id = 'spoti-layout';
    st.textContent = CSS;
    document.head.appendChild(st);
  }

  function hook() {
    // Boton "cerrar Now Playing" junto al de letras.
    var ly = document.querySelector('button[data-testid=lyrics-button]:not(.npHooked)');
    if (ly && !document.querySelector('button.npbtn')) {
      ly.classList.add('npHooked');
      var b = document.createElement('button');
      b.className = 'npbtn';
      b.title = 'Cerrar la vista Reproduciendo ahora';
      b.innerHTML = '<svg viewBox="0 0 16 17" width="16" height="17"><rect x="1" y="0.75" width="14" ' +
        'height="15.5" rx="2" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M 6 5 L 6 ' +
        '5.916 L 9.693 8.5 L 6 11.08 L 6 12 L 11 8.5 L 6 5 z" stroke="currentColor" stroke-width="1.2"/></svg>';
      b.onclick = function () { window.SpotiCommands.closeNowPlaying(); };
      ly.parentNode.insertBefore(b, ly);
    }
    // Cerrar la vista automaticamente si la app lo pide.
    if (window.SpotiAutoCloseNowPlaying) {
      var panel = document.querySelector('#Desktop_PanelContainer_Id');
      if (panel && panel.parentNode && panel.parentNode.parentNode &&
          panel.parentNode.parentNode.ariaHidden === 'false') {
        window.SpotiCommands.closeNowPlaying();
      }
    }
  }

  var loop = null;
  window.spotiStartLayout = function (autoClose) {
    window.SpotiAutoCloseNowPlaying = !!autoClose;
    inject();
    if (loop) clearInterval(loop);
    loop = setInterval(hook, 3000);
    hook();
  };
  window.spotiStopLayout = function () {
    if (loop) { clearInterval(loop); loop = null; }
  };
})();
