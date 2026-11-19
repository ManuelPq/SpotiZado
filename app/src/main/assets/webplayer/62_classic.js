/*
 * 62_classic.js — acceso con email y contraseña en la pagina de login.
 *
 * EL PROBLEMA
 *
 * Spotify ya no sirve el formulario de email por defecto: la pagina de login es
 * "sin contrasena" (telefono / Google / enlace magico). Medido en el dispositivo
 * con la 1.2.0: `campos=2` paso a `pwd=no`, o sea que el campo de contrasena
 * desaparecio del DOM en cuanto la configuracion del WebView cambio.
 *
 * COMO SE RESUELVE
 *
 * El cliente que funciona en este mismo telefono inserta un enlace cuyo destino
 * es la MISMA pagina con `?allow_password=1`. El matiz importa: el parametro NO
 * va en la carga inicial (ahi hace que Spotify rechace el intento), sino en una
 * navegacion posterior, disparada por el usuario.
 *
 * Aqui se hace lo mismo, pero con un boton FIJO en pantalla en lugar de depender
 * de encontrar un ancla concreta en el marcado: los selectores de Spotify cambian
 * sin avisar, y un boton propio funciona igual aunque la pagina se rediseñe.
 * Si ya hay un campo de contrasena, no se inyecta nada.
 */
(function () {
  if (window.__spotiClassic) return;
  window.__spotiClassic = 1;

  var ID = 'spoti-classic-login';

  /** URL de la misma pagina con allow_password, sin duplicar el parametro. */
  function classicHref() {
    try {
      var base = location.origin + location.pathname;
      var q = location.search.replace(/^\?/, '')
        .replace(/(^|&)allow_password=1/g, '')
        .replace(/^&|&$/g, '');
      return base + '?' + (q ? q + '&' : '') + 'allow_password=1';
    } catch (e) {
      return '?allow_password=1';
    }
  }

  function hasPassword() {
    return !!document.querySelector('input[type=password]');
  }

  function build() {
    if (document.getElementById(ID) || hasPassword()) return false;
    if (location.pathname.indexOf('login') === -1) return false;

    var a = document.createElement('a');
    a.id = ID;
    a.href = classicHref();
    a.textContent = 'Entrar con email y contraseña';
    a.style.cssText = [
      'position:fixed', 'left:12px', 'right:12px', 'bottom:14px', 'z-index:2147483000',
      'display:block', 'padding:13px 16px', 'box-sizing:border-box',
      'text-align:center', 'text-decoration:none',
      'font:600 14px/1.2 -apple-system,Roboto,sans-serif',
      'color:#0a1a10', 'background:#1ed760', 'border-radius:999px',
      'box-shadow:0 6px 18px rgba(0,0,0,.45)'
    ].join(';');
    a.addEventListener('click', function () {
      try { spotiNotify('login', { phase: 'classic-login-tap' }); } catch (e) {}
    });
    document.documentElement.appendChild(a);
    try { spotiNotify('login', { phase: 'classic-button-added' }); } catch (e) {}
    return true;
  }

  // La pagina se redibuja: se reintenta un momento, sin bucle abierto.
  var n = 0;
  build();
  var t = setInterval(function () {
    build();
    if (++n > 20) clearInterval(t);
  }, 700);
})();
