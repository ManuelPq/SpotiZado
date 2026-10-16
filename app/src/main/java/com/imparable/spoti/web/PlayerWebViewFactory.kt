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

    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    /** UA móvil: algunos flujos de login de terceros lo prefieren. */
    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

    /**
     * Construye el UA a partir del **motor real** del WebView.
     *
     * Fijar "Chrome/140" a mano es un error: si el WebView instalado es más
     * antiguo, la página recibe una versión de Chrome que no le corresponde y
     * puede usar APIs que el motor no tiene. Aquí se parte del UA real y solo
     * se cambia lo justo: se quita la marca `wv` (que delata el WebView) y, en
     * modo escritorio, se sustituye el bloque de plataforma por uno de Windows.
     */
    fun buildUserAgent(context: Context, desktop: Boolean): String {
        val real = runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return if (desktop) UA_DESKTOP else UA_MOBILE

        val withoutWv = real.replace(" wv)", ")").replace("; wv", "")
        if (!desktop) return withoutWv

        var ua = withoutWv
            .replace(Regex("\\(Linux; Android[^)]*\\)"), "(Windows NT 10.0; Win64; x64)")
            .replace("Version/4.0 ", "")
            .replace(" Mobile Safari", " Safari")
        if (!ua.contains("Chrome/")) ua += " Chrome/140.0.0.0"
        return ua
    }

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
            userAgentString = buildUserAgent(webView.context, desktop)
            useWideViewPort = true
            loadWithOverviewMode = true
            // Sin esto, la página de login se dibuja a 980 px y no se ve el formulario.
            // `loadWithOverviewMode` solo en el reproductor.
            //
            // Es la causa de que el formulario de login quedara minusculo: con
            // overview activo el WebView reduce la pagina ENTERA (que es alta)
            // para encajarla en la pantalla. Medido en el dispositivo:
            // vw=384, sw=384 (sin desbordamiento) y el formulario a ~5 % de la
            // pantalla, con scroll. El viewport de maquetacion era correcto, asi
            // que ninguna medida anterior podia detectarlo: lo que estaba mal era
            // la ESCALA VISUAL. Se anade esa medida a la sonda.
            loadWithOverviewMode = desktop
            webView.setInitialScale(if (desktop) 100 else 0)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, bridge: PlayerBridge, desktopMode: Boolean): WebView {
        val webView = WebView(context)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true

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

            builtInZoomControls = false
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
}
