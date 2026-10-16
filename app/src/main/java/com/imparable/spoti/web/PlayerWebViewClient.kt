package com.imparable.spoti.web

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.imparable.spoti.adblock.AdBlocker
import com.imparable.spoti.bridge.PlayerBridge
import java.io.ByteArrayInputStream

/**
 * Cliente del WebView: inyecta los scripts por fase de carga y aplica la capa 1
 * del bloqueo (filtrado de red por host) mediante `shouldInterceptRequest`.
 *
 * Nota: en el APK original de Spotifuck este método no se pudo descompilar, así
 * que su lógica exacta se desconoce. Aquí se implementa desde cero, con una
 * diferencia importante: **no se corta la petición con una respuesta vacía**
 * (lo que provoca errores de reproducción difíciles de diagnosticar), sino que
 * el bloqueo se registra y se devuelve la respuesta vacía solo para hosts que
 * no son de contenido (garantizado por la lista blanca de [AdBlocker]).
 */
class PlayerWebViewClient(
    private val bridge: PlayerBridge
) : WebViewClient() {

    companion object {
        private const val TAG = "PlayerWebViewClient"
        const val URL_PLAYER = "https://open.spotify.com/"

        /**
         * Página de login **con el formulario de email y contraseña ya visible**.
         *
         * `allow_password=1` es imprescindible: sin ese parámetro Spotify ofrece
         * solo enlaces mágicos y proveedores externos, y Google bloquea el login
         * dentro de un WebView (`disallowed_useragent`). El APK original cargaba
         * la URL sin el parámetro y luego intentaba **inyectar un enlace** con un
         * selector único y frágil (`section>div>div>div>div>a:first-child`): si Spotify
         * cambiaba el marcado, el usuario se quedaba sin forma de entrar.
         */
        const val URL_LOGIN = "https://accounts.spotify.com/login?allow_password=1"

        const val PREFIX_PLAYER = "https://open.spotify.com"
        private const val FACEBOOK_CONSENT = "https://www.facebook.com/privacy/consent/gdp/"
    }

    @Volatile
    var adBlocker: AdBlocker? = null

    /**
     * URL del documento que está cargado ahora mismo.
     *
     * Hace falta para decidir **en qué páginas** se aplica cada capa: el spoof de
     * escritorio y el bloqueo de red solo tienen sentido en el reproductor.
     */
    @Volatile
    private var currentUrl: String = ""

    /** ¿Estamos dentro del reproductor (y no en el login u otra página)? */
    private val onPlayerPage: Boolean get() = currentUrl.startsWith(PREFIX_PLAYER)

    /**
     * Perfil aplicado al WebView. El reproductor necesita perfil de escritorio y
     * la página de login, perfil móvil; cambiar de uno a otro **después** de que
     * la página se haya cargado no basta (ya se envió el UA y ya se maquetó), así
     * que al volver al reproductor se recarga una vez con el perfil correcto.
     */
    @Volatile
    private var profileIsDesktop: Boolean? = null

    /**
     * Perfil con el que **se pidió** el documento que se está cargando ahora.
     *
     * Es distinto de [profileIsDesktop] y la distinción importa: `onPageStarted`
     * cambia el perfil antes de que termine la carga, así que comparar contra el
     * perfil «actual» en `onPageFinished` siempre daba «ya está bien» y la
     * recarga con perfil de escritorio **nunca se disparaba**.
     */
    @Volatile
    private var loadedWithDesktop: Boolean = false

    /**
     * Perfil que pide el usuario para el reproductor.
     *
     * DEFECTO CORREGIDO: el ajuste «Modo del reproductor: Movil/Escritorio» solo
     * se usaba en la interfaz. Aqui estaba `desktop = true` fijo, asi que elegir
     * «Movil» no cambiaba nada. Ahora manda el ajuste.
     *
     * Con cuenta gratuita el modo Movil es el experimento que toca: el
     * reproductor web movil es el que Spotify disena para navegadores de
     * telefono, y es donde el audio tiene alguna posibilidad de arrancar.
     */
    private val desktopWanted: Boolean
        get() = (
            com.imparable.spoti.injection.InjectionComposer.settings.guiMode ==
                com.imparable.spoti.data.GuiMode.DESKTOP
            )

    /** Cambia el perfil solo si hace falta y deja constancia. */
    private fun switchProfile(view: WebView, desktop: Boolean) {
        if (profileIsDesktop == desktop) return
        PlayerWebViewFactory.applyProfile(view, desktop)
        profileIsDesktop = desktop
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentUrl = url
        bridge.onPageStarted(url)

        val isPlayer = url.startsWith(PREFIX_PLAYER)
        // Lo que importa es con qué perfil se pidió ESTA carga, no con cuál se
        // quedará el WebView a partir de ahora.
        loadedWithDesktop = profileIsDesktop == true
        switchProfile(view, desktop = isPlayer && desktopWanted)

        // Spoof de escritorio: solo en el reproductor. Es el script que hace que
        // Spotify sirva el reproductor de ESCRITORIO dentro del WebView, que es
        // el que expone los selectores que usan el control y el AdGuard.
        // En el login se aplica lo contrario (perfil móvil) porque esa página es
        // responsive y necesita el ancho real del teléfono.
        if (isPlayer && desktopWanted) {
            view.evaluateJavascript(bridge.composer.buildEnvSpoofScript(), null)
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        currentUrl = url

        // CAPA DE CALIBRACION (herramienta de diagnostico, apagada por defecto).
        //
        // Se inyecta en TODAS las paginas, login incluido: el objetivo es medir, y
        // la pagina que hay que medir es justo esa. El fondo del visor pasa a
        // magenta: si la zona que se veia "negra" se vuelve magenta, el WebView no
        // esta pintando nada de la pagina; si sigue oscura, la pagina si pinta y el
        // contenido esta en otro sitio o tapado. Es la prueba que separa las dos
        // posibilidades de un vistazo.
        if (com.imparable.spoti.injection.InjectionComposer.settings.calibration &&
            !com.imparable.spoti.injection.InjectionComposer.settings.cleanMode
        ) {
            view.setBackgroundColor(0xFFFF00FF.toInt())
            view.evaluateJavascript(bridge.composer.buildCalibScript(), null)
        } else {
            view.setBackgroundColor(0xFF121212.toInt())
        }

        when {
            // Consentimiento GDPR de Facebook: ayuda puntual y se acaba.
            url.startsWith(FACEBOOK_CONSENT) ->
                view.evaluateJavascript(bridge.composer.buildLoginScript(), null)

            // Login: se añaden los ayudantes (enlace clásico y detección de sesión).
            url.contains("accounts.spotify.com") || url.contains("/login") -> {
                // Refuerzo: la página de login se cargó con perfil móvil, y el
                // formulario ya está en el DOM. El spoof de escritorio NO se aplica
                // aquí (rompería el maquetado), solo la ayuda de login.
                view.evaluateJavascript(bridge.composer.buildLoginScript(), null)

                // Arreglo de elementos ocultos (login). Medido: el formulario
                // estaba con la caja perfecta y no se pintaba por un ancestro.
                if (com.imparable.spoti.injection.InjectionComposer.settings.unhideFix) {
                    view.evaluateJavascript(bridge.composer.buildUnhideScript(), null)
                }
                // El fondo magenta es SOLO de diagnostico: sin calibracion, el
                // visor vuelve a su color normal.
                if (com.imparable.spoti.injection.InjectionComposer.settings.calibration) {
                    view.setBackgroundColor(0xFFFF00FF.toInt())
                } else {
                    view.setBackgroundColor(0xFF121212.toInt())
                }
            }

            // Reproductor: si venimos de una página con perfil móvil (p. ej. tras
            // iniciar sesión), esta carga trajo el reproductor MÓVIL. Se recarga
            // una vez con perfil de escritorio, que es el que expone los selectores
            // que usan el control y el AdGuard.
            // Solo se recarga si el usuario QUIERE escritorio. Sin este
            // `desktopWanted` habria bucle infinito: en modo movil el perfil
            // nunca es «escritorio» y la recarga se dispararia sin fin.
            url.startsWith(PREFIX_PLAYER) && desktopWanted && !loadedWithDesktop -> {
                switchProfile(view, desktop = true)
                view.post { view.reload() }
            }

            // Reproductor con el perfil correcto: se inyecta el controlador
            // SIEMPRE, esté o no marcada la sesión. Antes dependía de un heurístico
            // (el botón `web-player-link`) y, si no aparecía, no se inyectaba nada:
            // sin controles, sin AdGuard y sin pista alguna de por qué.
            url.startsWith(PREFIX_PLAYER) -> {
                // Deteccion NATIVA de sesion, sin depender de ningun script: si la
                // navegacion ha llegado al reproductor, el login ha terminado. Antes
                // esto solo se deducia desde JS, y si el JS no se disparaba la app
                // seguia creyendo que no habia sesion y volvia a pedir login.
                if (!bridge.isLoggedIn()) {
                    bridge.setLoggedIn(true)
                    bridge.onLoggedInFromNavigation()
                }
                bridge.onPlayerPageReady(view)

                // EL MISMO ARREGLO EN EL REPRODUCTOR.
                //
                // Las capturas del reproductor ensenaban la cabecera dibujada y
                // TODO lo de abajo en negro: el mismo sintoma que el login, que ya
                // esta identificado como un ancestro que oculta el subarbol. Aqui va
                // el juego conservador (ver 65_unhide.js): no toca `overflow` ni
                // `transform`, que en el reproductor son parte del diseno.
                if (com.imparable.spoti.injection.InjectionComposer.settings.unhideFix) {
                    view.evaluateJavascript(bridge.composer.buildUnhideScript(), null)
                }
            }

            else -> bridge.requestLoginDetection(view)
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val blocker = adBlocker ?: return null

        // El bloqueo de red actúa **solo** en el reproductor.
        //
        // En el login no hay anuncios que bloquear y sí mucho que romper: la
        // pantalla de consentimiento GDPR (en la UE es obligatoria y se sirve
        // desde `fundingchoicesmessages.google.com`, que estaba en la lista de
        // bloqueo) y el propio formulario de acceso. Si esa pantalla no carga,
        // el formulario nunca aparece: el síntoma exacto que se reportó.
        if (!onPlayerPage) return null

        val uri: Uri = request.url
        if (!blocker.shouldBlock(uri.host)) return null

        Log.d(TAG, "Bloqueado: ${uri.host}${uri.path}")
        // 204 sin cuerpo: no se propaga un error de red, simplemente no hay nada
        // que entregar. La lista blanca garantiza que el host no es de contenido.
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "Blocked by SpotiZado",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        // Solo se reportan fallos del documento principal: los recursos
        // bloqueados por la lista generan "errores" que no son problemas.
        if (request.isForMainFrame) {
            bridge.onMainFrameError(error.errorCode, error.description?.toString() ?: "")
        }
    }

    /** Concede permisos de medios que pide el reproductor (p. ej. EME/vídeo). */
    class PlayerChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        /**
         * Ventanas emergentes.
         *
         * Spotify abre algunas en el login y en flujos de OAuth. Con
         * `setSupportMultipleWindows(true)` el WebView **exige** que alguien
         * atienda `onCreateWindow`; si nadie lo hace, la ventana no se abre y el
         * flujo se queda colgado sin error visible. Aquí se redirige la emergente
         * al mismo WebView, que es lo que necesita un reproductor de una sola vista.
         */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message
        ): Boolean {
            // Solo se atienden las ventanas que abre el USUARIO con un toque.
            //
            // `javaScriptCanOpenWindowsAutomatically` deja que la pagina abra
            // ventanas sin gesto alguno. Redirigirlas a este mismo WebView navega
            // la vista a esa ventana, y si es una pagina vacia o bloqueada la deja
            // EN NEGRO. Un reproductor de una sola vista no necesita emergentes
            // automaticas: las que importan (OAuth) son siempre por gesto.
            if (!isUserGesture) return false

            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            transport.webView = view
            resultMsg.sendToTarget()
            return true
        }

        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
            if (msg.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                Log.w(TAG, "JS: ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
            }
            return true
        }
    }
}
