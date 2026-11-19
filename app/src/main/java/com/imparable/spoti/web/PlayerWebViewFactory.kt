package com.imparable.spoti.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.imparable.spoti.BuildConfig
import com.imparable.spoti.bridge.PlayerBridge
import com.imparable.spoti.injection.InjectionComposer

/**
 * Crea y configura el WebView que aloja el reproductor.
 *
 * Diferencias con Spotifuck/Spotilol y por qué:
 *  - El puente se registra con **`WebViewCompat.addWebMessageListener`** y
 *    origen restringido a `https://open.spotify.com`, en lugar de
 *    `addJavascriptInterface`. Aquel expone el objeto a *cualquier* marco y
 *    página del WebView; este solo acepta mensajes del origen declarado, lo que
 *    cierra la vía de inyección más habitual en los wrappers de este tipo.
 *  - Se activa `MIXED_CONTENT_NEVER_ALLOW` y se desactiva el acceso a ficheros:
 *    el reproductor no los necesita y reducen la superficie de ataque.
 *  - El modo de UA (escritorio/móvil) es un ajuste, en lugar de estar fijado.
 */
object PlayerWebViewFactory {

    const val ALLOWED_ORIGIN = "https://open.spotify.com"
    private const val JS_OBJECT = "SpotiBridge"

    /**
     * User-Agent EXACTO del APK de SpotiDuck v1.2.7 (extraído de su dex).
     *
     * Son las cadenas "reducidas" de Chrome: `Android 10; K` en vez del modelo
     * real del teléfono. No es un detalle cosmético.
     *
     * Lo que medimos en el dispositivo con nuestra versión anterior:
     *   ua=Mozilla/5.0 (Linux; Android 16; 24117RN76E Build/BP2A.250605.031.A3;) ...
     * Eso es el UA LEGADO, con modelo y número de compilación. Ese formato lo
     * sirven navegadores antiguos, y Spotify puede responder con su página de
     * "navegador no compatible" — vacía, que es exactamente la pantalla en negro.
     *
     * Chrome lleva desde 2022 enviando el formato reducido por defecto. El motor
     * WebView de este teléfono devuelve el formato legado, así que hay que
     * fijarlo a mano. No se puede derivar del real: el real es el que falla.
     */
    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

    /** UA móvil: el mismo que usa SpotiDuck. */
    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"

    /** UA móvil: algunos flujos de login de terceros lo prefieren. */
    /**
     * Construye el UA a partir del **motor real** del WebView.
     *
     * Fijar "Chrome/140" a mano es un error: si el WebView instalado es más
     * antiguo, la página recibe una versión de Chrome que no le corresponde y
     * puede usar APIs que el motor no tiene. Aquí se parte del UA real y solo
     * se cambia lo justo: se quita la marca `wv` (que delata el WebView) y, en
     * modo escritorio, se sustituye el bloque de plataforma por uno de Windows.
     */
    /**
     * Devuelve el UA de SpotiDuck, **sin derivarlo** del motor.
     *
     * Antes se partía de `WebSettings.getDefaultUserAgent` y se limpiaba. El
     * problema es que el UA real de este dispositivo es el formato legado (con
     * `Build/...`), que es justo el que hay que evitar. Derivar del real era
     * heredar el fallo.
     */
    fun buildUserAgent(context: Context, desktop: Boolean): String =
        if (desktop) UA_DESKTOP else UA_MOBILE

    /**
     * Aplica el perfil de página correcto: **reproductor** o **página normal**
     * (login, consentimiento, redirecciones de OAuth).
     *
     * Es la diferencia que faltaba. El reproductor quiere un navegador de
     * escritorio (`innerWidth` de escritorio y selectores de escritorio), pero
     * aplicar eso mismo a la página de login la rompe: con UA de escritorio y
     * `setInitialScale(100)`, el WebView maqueta el login para ~980 px dentro de
     * una pantalla de ~400 px → el formulario cae fuera de la vista. La página
     * de login es responsive y está diseñada para el ancho real del teléfono, así
     * que ahí se usa UA móvil y **escala automática** (0 = la que decida la página).
     *
     * @param desktop `true` para el reproductor, `false` para todo lo demás.
     */
    fun applyProfile(webView: WebView, desktop: Boolean) {
        with(webView.settings) {
            // UA: **nativo** en login, escritorio solo al llegar al reproductor.
            //
            // Esto corrige un error mío de la 1.1.1. Allí fijé un UA de Chrome a
            // mano para todas las páginas. El cliente que SÍ funciona en este mismo
            // teléfono (Quackify, continuación de SpotiDuck) hace lo contrario:
            // usa `WebSettings.getDefaultUserAgent` en el login y cambia a
            // escritorio solo cuando ya está en open.spotify.com. Su comentario
            // literal: «init ua=native, desktop only on the player».
            //
            // Si el UA fijado no arregló nada, no era el problema. Se vuelve al
            // nativo, que es lo que usa el que funciona.
            userAgentString = if (desktop) UA_DESKTOP else nativeUserAgent(webView)

            // `useWideViewPort` y `loadWithOverviewMode` SIEMPRE activos, como en
            // el cliente que funciona. En 1.0.x los desactivábamos en el login
            // persiguiendo el fallo del 980 px; con el meta viewport ya forzado a
            // `device-width`, activarlos es lo correcto: dejan que el WebView
            // encaje la página en la vista sin imponer una escala fija.
            useWideViewPort = true
            loadWithOverviewMode = true

        }

        // `setInitialScale` NO se llama. Esa llamada no es neutra: resetea la
        // escala y deshace el encaje automático que acaban de habilitar
        // `useWideViewPort` y `loadWithOverviewMode`. El cliente que SÍ funciona
        // en este teléfono no la invoca en ningún momento.

        // CAPA DE PINTADO: la prueba que faltaba, y va en la direccion contraria.
        //
        // Medido en el dispositivo: con LAYER_TYPE_HARDWARE puesto para TODO,
        // el fondo del documento y los elementos con `position:fixed` y z-index
        // alto SI se pintan; el contenido de Spotify (texto, campos, imagenes)
        // NO, aunque su geometria es correcta y el HTML servido es plano.
        //
        // Que lo nuestro se pinte y lo suyo no descarta CSS, DOM y viewport: la
        // diferencia entre unos y otros es *el nivel de pintado*. `position:fixed`
        // con z-index alto crea capa de composicion propia; el contenido normal
        // va en la capa principal del documento. Si la principal no compone, el
        // resultado es exactamente lo que se ve: fondos y capas propias si, resto
        // no.
        //
        // Nadie habia probado lo contrario: renderizado SOFTWARE, que rasteriza
        // en CPU y no depende del compositor de GPU. Se aplica SOLO a las paginas
        // que no son el reproductor (login, consentimiento, redirecciones): ahi
        // no hay video ni DRM que perder, y el coste es irrelevante para un
        // formulario. El reproductor mantiene HARDWARE.
        // Fuera del reproductor no se fuerza NINGUNA capa: `LAYER_TYPE_NONE`, el
        // comportamiento por defecto, que es exactamente lo que hace Chrome. La
        // 1.4.0 probo SOFTWARE y la 1.0.10 HARDWARE; el testigo demostro que el
        // motor pinta bien (A, B y C se veian), asi que la capa no era el asunto
        // y lo correcto es dejar de tocarla.
        val layer = if (desktop) {
            android.view.View.LAYER_TYPE_HARDWARE
        } else {
            android.view.View.LAYER_TYPE_NONE
        }
        runCatching { webView.setLayerType(layer, null) }

        // Cookies de terceros: el login y el reproductor las necesitan (el flujo
        // de accounts.spotify.com deja cookies para open.spotify.com). El cliente
        // que funciona las acepta explícitamente y nosotros no las tocábamos.
        runCatching<Unit> {
            val cm = android.webkit.CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
        }
    }

    /** UA real del motor, sin derivar ni limpiar. */
    private fun nativeUserAgent(webView: WebView): String =
        runCatching { WebSettings.getDefaultUserAgent(webView.context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: UA_MOBILE

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, bridge: PlayerBridge, desktopMode: Boolean): WebView {
        val webView = WebView(context)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Las tres líneas siguientes son la diferencia con Spotifuck 1.6.4.1,
            // que es la app que SÍ funcionaba. Con `useWideViewPort=true` y
            // `loadWithOverviewMode=true` la página se maqueta con un ancho de
            // 980 px; `setSupportZoom(false)` **impide encogerla para que quepa**,
            // así que el contenido se sale de la pantalla y los campos quedan
            // fuera de la vista aunque existan en el DOM. De ahí el síntoma
            // «campos=2 pero no se ve nada».
            setSupportZoom(true)
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true

            // El cliente que funciona los deja ACTIVADOS (con los controles
            // ocultos). Desactivarlos impide el zoom por gesto, que es la única
            // forma de leer una página que no se ajusta sola.
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            // El reproductor necesita TLS moderno y no usa cookies de terceros.
            setGeolocationEnabled(false)
        }

        applyProfile(webView, desktopMode)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.setBackgroundColor(0xFF121212.toInt())
        webView.isScrollbarFadingEnabled = true

        /*
         * RENDERIZADO. Medido en el dispositivo: el fondo de la pagina
         * (`body` = rgb(18,18,18)) SI se pinta y mi rejilla inyectada tambien,
         * pero el TEXTO y los controles de la pagina NO. Con el mismo HTML en
         * Chrome se ve bien. Eso descarta el CSS y apunta a la composicion de
         * capas del WebView: la pagina crea capas (backdrop-filter, transform,
         * will-change) y el motor no las compone.
         *
         * Tres medidas, de la mas barata a la mas contundente:
         *   1. capa HARDWARE explicita para el WebView;
         *   2. `setOffscreenPreRaster`, que fuerza a rasterizar aunque la vista
         *      no se considere visible;
         *   3. desactivar el "wide viewport" solo en paginas no-reproductor, que
         *      es donde el escalado automatico peleaba con el CSS.
         */
        // La capa de pintado la decide `applyProfile` segun la pagina: SOFTWARE
        // fuera del reproductor, HARDWARE dentro. Fijarla aqui a HARDWARE para
        // todo era justo lo que impedia probar la alternativa.
        with(webView.settings) {
            runCatching { setOffscreenPreRaster(true) }
        }

        // Puente tipado y restringido por origen.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                JS_OBJECT,
                setOf(ALLOWED_ORIGIN),
                bridge
            )
        } else {
            // Fallback: este dispositivo no soporta WebMessageListener. El puente
            // lo detecta y avisa en el panel de diagnóstico, en vez de fallar en
            // silencio (que es lo que hacían los wrappers clásicos).
            bridge.markBridgeUnsupported()
        }

        webView.webViewClient = PlayerWebViewClient(bridge)
        webView.webChromeClient = PlayerWebViewClient.PlayerChromeClient()

        // Token de sesión: sin él, el JS ignora cualquier comando del host.
        webView.evaluateJavascript(
            "window.__spotiSessionToken=${quote(bridge.sessionToken)};",
            null
        )

        return webView
    }

    /** Inyecta el conjunto de scripts iniciales. */
    fun injectBoot(webView: WebView, composer: InjectionComposer) {
        webView.evaluateJavascript(composer.buildBootScript(), null)
    }

    private fun quote(value: String) = "\"" + value.replace("\"", "\\\"") + "\""
/**
     * Version real de **Android System WebView**.
     *
     * Es lo primero que hay que mirar cuando una pagina compleja no se pinta. El
     * README oficial de SpotiDuck documenta "pantalla en blanco" y su remedio es
     * actualizar el System WebView desde Play Store. Tenerlo a la vista evita
     * diagnosticar a ciegas.
     */
    fun webViewVersion(context: Context): String = runCatching {
        WebViewCompat.getCurrentWebViewPackage(context)?.let { pkg ->
            pkg.packageName + " " + (pkg.versionName ?: "?")
        }
    }.getOrNull() ?: "desconocida"
}
