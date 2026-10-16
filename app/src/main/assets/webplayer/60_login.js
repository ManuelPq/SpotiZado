/*
 * 60_login.js — ayudas de login (inyectadas en onPageFinished por la app).
 *   - consentimiento GDPR de Facebook: aceptar para no bloquear el flujo
 *   - formulario clasico de email + contrasena (?allow_password=1). Spotify
 *     ofrece por defecto solo enlaces magicos y proveedores externos; Google
 *     ademas bloquea el login dentro de un WebView ("disallowed_useragent").
 *   - deteccion de sesion ya iniciada
 *
 * El original insertaba el enlace con un unico selector
 * (`section>div>div>div>div>a:first-child`), que deja de funcionar en cuanto
 * Spotify toca el marcado: si no encontraba ese ancla, el usuario se quedaba
 * sin ninguna forma de entrar. Aqui se prueban varios contenedores y, como
 * ultimo recurso, se usa una barra fija que no puede fallar.
 *
 * La app ya carga la pagina de login con `allow_password=1`, asi que en el caso
 * normal este enlace no hace falta: es la red de seguridad.
 */
(function () {
  var url = location.href;

  if (url.indexOf('facebook.com/privacy/consent') >= 0) {
    var fb = document.querySelector('#facebook div[role=button]');
    if (fb) { fb.click(); spotiNotify('login', { phase: 'facebook-consent' }); }
    return;
  }

  if (url.indexOf('accounts.spotify.com') >= 0) {
    // Solo se añade si la URL no trae ya el parametro y no hay un formulario
    // de contrasena en la pagina.
    var hasPasswordField = !!document.querySelector('input[type=password]');
    if (url.indexOf('allow_password') < 0 && !hasPasswordField && !document.querySelector('a.spotiClassic')) {
      addClassicLink();
    }
    // Si Spotify muestra un boton de "continuar con email" en vez del
    // formulario, se pulsa. Es el mismo objetivo por otra via.
    clickEmailEntryWhenIdle(hasPasswordField);
    // Y si el campo de contrasena existe pero se ha quedado fuera de la vista
    // (al cambiar de paso o al desplazarse la pagina), se trae a la vista.
    ensurePasswordVisible();
    spotiNotify('login', { phase: 'login-page-ready' });
  }

  var webPlayerBtn = document.querySelector('button[data-testid=web-player-link]');
  if (webPlayerBtn) {
    webPlayerBtn.click();
    spotiNotify('login', { phase: 'session-detected' });
  }

  /**
   * Busca un boton de entrada por email y lo pulsa una sola vez. Se reintenta
   * un par de veces porque el login de Spotify se dibuja por partes.
   */
  function clickEmailEntryWhenIdle(alreadyHasPassword) {
    if (alreadyHasPassword) return;
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      if (document.querySelector('input[type=password]') || tries > 4) {
        clearInterval(timer);
        return;
      }
      var entry = findEmailEntry();
      if (entry) {
        entry.click();
        spotiNotify('login', { phase: 'email-entry-clicked' });
        clearInterval(timer);
      }
    }, 900);
  }

  /**
   * Trae el campo de contrasena a la vista si se ha quedado fuera.
   *
   * El diagnostico reporto `campos=2 pwd=si` con `visible=si` al principio y
   * `visible=no` despues, SIN desbordamiento (`sw == vw`): el formulario existe
   * y cabe en el ancho, pero se sale de la vista al desplazarse o al cambiar de
   * paso. Se reintenta unas cuantas veces, no en bucle abierto.
   */
  function ensurePasswordVisible() {
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      var p = document.querySelector('input[type=password]');
      if (p) {
        var r = p.getBoundingClientRect();
        var inView = r.width >= 4 && r.height >= 4 &&
          r.top < window.innerHeight && r.bottom > 0 &&
          r.left < window.innerWidth && r.right > 0;
        if (!inView) {
          try { p.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
          catch (e) { p.scrollIntoView(); }
          spotiNotify('login', { phase: 'scrolled-to-password' });
        } else {
          clearInterval(timer);
          return;
        }
      }
      if (tries > 6) clearInterval(timer);
    }, 1200);
  }

  function findEmailEntry() {
    var nodes = document.querySelectorAll('button, a, [role=button]');
    var re = /(email|correo|e-?mail)/i;
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      var label = (n.innerText || n.textContent || '').trim();
      if (!label || label.length > 40) continue;
      if (re.test(label) && !/google|facebook|apple|telefono|phone/i.test(label)) return n;
    }
    return null;
  }

  function addClassicLink() {
    var link = document.createElement('a');
    link.className = 'spotiClassic';
    link.textContent = 'Email + contrase\u00f1a (login clasico)';
    link.style.cssText =
      'display:block;padding:12px;margin:12px 0;color:#fff;font-weight:700;' +
      'text-decoration:none;border:1px solid #ddd;background:#2a2a72;' +
      'border-radius:30px;text-align:center';
    link.href = '?allow_password=1';

    // Del contenedor mas especifico al mas generico.
    var hosts = ['section>div>div>div>div>a:first-child', 'form', 'main section', 'body'];
    for (var i = 0; i < hosts.length; i++) {
      var host = document.querySelector(hosts[i]);
      if (!host) continue;

      if (i === hosts.length - 1) {
        // Ultimo recurso: barra fija abajo, visible y sin tapar el formulario.
        link.style.cssText += ';position:fixed;left:12px;right:12px;bottom:12px;z-index:2147483647';
      }
      host.parentNode.insertBefore(link, host);
      spotiNotify('login', { phase: 'classic-link-added' });
      return;
    }
  }
})();
