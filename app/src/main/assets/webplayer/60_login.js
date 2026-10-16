/*
 * 60_login.js — ayudas de login (inyectadas en onPageFinished).
 *
 * HISTORIAL, y por que este fichero queda casi vacio:
 *
 *   - La version original insertaba un enlace "login clasico" con un selector
 *     unico y fragil, que dejaba de funcionar en cuanto Spotify tocaba el marcado.
 *   - Despues se anadieron mas ayudas: pulsar el boton de entrada por email,
 *     traer el campo de contrasena a la vista con scroll, y reescribir el meta
 *     viewport.
 *   - MEDIDO EN EL DISPOSITIVO (v1.0.4): el formulario SI esta en la pagina
 *     (campos=2, pwd=si) y el layout es correcto (vw=384, sw=384, zoom=1.0), es
 *     decir SIN desbordamiento, pero la zona de la web se ve NEGRA.
 *
 * Conclusion: las ayudas no arreglaban nada que estuviera roto, y ellas mismas
 * eran la unica diferencia entre esta app y un navegador. En Chrome del mismo
 * telefono esa misma pagina se ve bien.
 *
 * Por eso aqui ya NO se toca el DOM: ni se pulsa nada, ni se hace scroll, ni se
 * insertan elementos, ni se tocan estilos ni el viewport. Lo unico que se hace es
 * LEER si la sesion ya esta iniciada.
 *
 * La deteccion de sesion al terminar el login tampoco depende de esto: la app la
 * marca de forma nativa en cuanto la navegacion llega a open.spotify.com.
 */
(function () {
  var url = location.href;

  // Consentimiento GDPR de Facebook: caso aparte. Es una pantalla con un unico
  // boton que BLOQUEA el flujo si no se pulsa, y el usuario no puede hacerlo
  // porque el consentimiento se abre en un marco. Se sigue atendiendo, con la
  // unica accion imprescindible.
  if (url.indexOf('facebook.com/privacy/consent') >= 0) {
    var fb = document.querySelector('#facebook div[role=button]');
    if (fb) {
      fb.click();
      spotiNotify('login', { phase: 'facebook-consent' });
    }
    return;
  }

  if (url.indexOf('accounts.spotify.com') >= 0) {
    spotiNotify('login', { phase: 'login-page-ready' });
    detectSession();
  }

  /**
   * Marca la sesion como iniciada leyendo la propia pagina.
   *
   * SOLO LEE. El dato lo pone Spotify en el texto visible: "Iniciaste sesion como
   * <usuario>" y una accion "Cerrar sesion". Tambien vale el widget de cuenta del
   * reproductor. Se reintenta unas cuantas veces porque estas pantallas se dibujan
   * por partes; no es un bucle abierto.
   */
  function detectSession() {
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      var text = document.body ? document.body.innerText : '';
      if (document.querySelector('[data-testid=user-widget-link]') ||
          document.querySelector('[data-testid=user-widget-avatar]') ||
          /Iniciaste sesi\u00f3n como|Cerrar sesi\u00f3n|Iniciar sesi\u00f3n como/i.test(text)) {
        clearInterval(timer);
        spotiNotify('login', { phase: 'session-detected' });
        return;
      }
      if (tries > 8) clearInterval(timer);
    }, 900);
  }
})();
