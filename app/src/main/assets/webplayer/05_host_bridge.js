/*
 * 05_host_bridge.js — puente Kotlin <-> JS (JSON-RPC 2.0)
 *
 * Dos direcciones:
 *   JS -> Kotlin : SpotiBridge.postMessage(json)   (WebMessageListener, origen restringido)
 *   Kotlin -> JS : window.SpotiHost.onCommand(json) (evaluateJavascript desde la app)
 *
 * Notas de diseño (mejora respecto a Spotifuck/Spotilol):
 *  - No se usa addJavascriptInterface/@JavascriptInterface: WebMessageListener
 *    solo acepta mensajes del origen declarado (open.spotify.com), así que el
 *    puente NO queda expuesto a cualquier página que cargue el WebView.
 *  - Las llamadas Kotlin->JS van firmadas con un token de sesión aleatorio que
 *    Kotlin entrega en el arranque; así una página de terceros no puede invocar
 *    comandos del host aunque descubra el nombre del objeto.
 */
(function () {
  var pending = {};
  var seq = 0;
  var sessionToken = null;

  function send(obj) {
    if (typeof SpotiBridge === 'undefined' || !SpotiBridge.postMessage) return false;
    SpotiBridge.postMessage(JSON.stringify(obj));
    return true;
  }

  /** Llamada RPC con promesa. */
  window.spotiCall = function (method, params, timeoutMs) {
    return new Promise(function (resolve, reject) {
      var id = 'rpc' + (++seq);
      pending[id] = { resolve: resolve, reject: reject };
      if (!send({ jsonrpc: '2.0', id: id, method: method, params: params || {} })) {
        delete pending[id];
        reject(new Error('puente no disponible'));
        return;
      }
      setTimeout(function () {
        if (pending[id]) { delete pending[id]; reject(new Error('timeout: ' + method)); }
      }, timeoutMs || 4000);
    });
  };

  /** Evento sin respuesta. */
  window.spotiNotify = function (event, payload) {
    send({ jsonrpc: '2.0', method: 'event.' + event, params: payload || {} });
  };

  window.SpotiHost = {
    /** Recibe la respuesta de una RPC iniciada desde el host. */
    onReply: function (json) {
      try {
        var m = JSON.parse(json);
        var p = pending[m.id];
        if (!p) return;
        delete pending[m.id];
        if (m.error) p.reject(new Error(m.error.message || 'error')); else p.resolve(m.result);
      } catch (e) { /* ignore */ }
    },

    /** Recibe un comando del host (app Android). */
    onCommand: function (json) {
      var msg;
      try { msg = JSON.parse(json); } catch (e) { return; }
      if (!sessionToken || msg.token !== sessionToken) return;   // comando no autenticado
      var fn = window.SpotiCommands && window.SpotiCommands[msg.method];
      var out = { id: msg.id };
      try {
        out.result = fn ? fn(msg.params || {}) : null;
        if (!fn) out.error = { message: 'comando desconocido: ' + msg.method };
      } catch (e) {
        out.error = { message: String(e && e.message ? e.message : e) };
      }
      send({ jsonrpc: '2.0', method: 'command.result', params: out });
    },

    /** Kotlin entrega aqui el token de sesion al inyectar. */
    setToken: function (t) { sessionToken = t; }
  };
})();
