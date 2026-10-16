package com.imparable.spoti.bridge

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.imparable.spoti.data.PlayerHealth
import com.imparable.spoti.data.PlayerSnapshot
import com.imparable.spoti.data.QueueItem
import com.imparable.spoti.data.SearchResult
import com.imparable.spoti.injection.InjectionComposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Puente Kotlin <-> JavaScript. **Única** vía por la que el reproductor puede
 * cambiar el estado de la app, lo que hace el sistema auditable.
 *
 * Escucha mensajes del origen permitido (`https://open.spotify.com`) y empuja
 * comandos al reproductor como eventos (`event.state`, `event.searchResults`,
 * `event.queue`…), de modo que ninguna operación bloquea al llamante.
 */
class PlayerBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSnapshot: (PlayerSnapshot) -> Unit,
    private val onAdEvent: (AdEvent) -> Unit,
    private val onHealth: (PlayerHealth) -> Unit,
    private val onSearchResults: (query: String, ok: Boolean, reason: String?, results: List<SearchResult>) -> Unit,
    private val onQueue: (ok: Boolean, reason: String?, items: List<QueueItem>) -> Unit,
    private val onLoggedIn: () -> Unit,
    private val onNeedsReload: (String) -> Unit
) : WebViewCompat.WebMessageListener {

    companion object {
        private const val TAG = "PlayerBridge"
    }

    /** Token que autentica los comandos Kotlin -> JS. */
    val sessionToken: String = UUID.randomUUID().toString()

    val composer = InjectionComposer(context)

    private val commandSeq = AtomicLong(0)
    private var replyProxy: JavaScriptReplyProxy? = null

    @Volatile private var loggedIn = false
    @Volatile private var bridgeUnsupported = false
    @Volatile private var lastSnapshot: PlayerSnapshot? = null

    /**
     * Evita inyectar el controlador dos veces en la misma página: el bucle de
     * estado se arrancaría por duplicado y publicaría cada cambio dos veces.
     * Se reinicia al empezar una navegación nueva.
     */
    @Volatile private var playerScriptInjected = false

    fun isLoggedIn(): Boolean = loggedIn
    fun setLoggedIn(value: Boolean) { loggedIn = value }

    /**
     * La navegacion ha llegado al reproductor: el login ha terminado, sin
     * depender de ningun script de la pagina. Lo unico que hace es persistir el
     * dato para que la app no vuelva a pedir credenciales en el proximo arranque.
     */
    fun onLoggedInFromNavigation() {
        loggedIn = true
        onLoggedIn()
    }
    fun markBridgeUnsupported() { bridgeUnsupported = true; Log.e(TAG, "WebMessageListener no soportado") }
    val isBridgeUnsupported: Boolean get() = bridgeUnsupported

    /* ------------------------------------------------------------------ */
    /* JS -> Kotlin                                                        */
    /* ------------------------------------------------------------------ */

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        this.replyProxy = replyProxy
        val raw = message.data ?: return
        if (!isMainFrame) return

        val incoming = runCatching {
            BridgeProtocol.json.decodeFromString(BridgeProtocol.Incoming.serializer(), raw)
        }.getOrElse {
            Log.w(TAG, "Mensaje descartado (no encaja con el protocolo): ${raw.take(180)}")
            return
        }

        when (incoming.method) {
            BridgeProtocol.Method.STATE -> handleState(incoming)
            BridgeProtocol.Method.POSITION -> handlePosition(incoming)
            BridgeProtocol.Method.AD_GUARD -> handleAdGuard(incoming)
            BridgeProtocol.Method.SEARCH_RESULTS -> handleSearch(incoming)
            BridgeProtocol.Method.QUEUE -> handleQueue(incoming)
            BridgeProtocol.Method.RELOAD -> onNeedsReload(incoming.params.reason ?: "playback-stalled")
            BridgeProtocol.Method.LOGIN -> handleLogin(incoming)
            BridgeProtocol.Method.DEVICE -> Log.d(TAG, "device_id=${incoming.params.deviceId}")
            else -> Log.d(TAG, "método no manejado: ${incoming.method}")
        }
    }

    private fun handleState(msg: BridgeProtocol.Incoming) {
        val p = msg.params
        val snapshot = PlayerSnapshot(
            track = p.track,
            artist = p.artist.orEmpty(),
            album = p.album.orEmpty(),
            duration = p.duration,
            position = p.position,
            playing = p.playing,
            repeat = p.repeat,
            shuffle = p.shuffle,
            favorite = p.favorite,
            cover = p.cover,
            adPlaying = p.adPlaying
        )
        lastSnapshot = snapshot
        onSnapshot(snapshot)
    }

    private fun handlePosition(msg: BridgeProtocol.Incoming) {
        val last = lastSnapshot ?: return
        val updated = last.copy(position = msg.params.position, duration = msg.params.duration)
        lastSnapshot = updated
        onSnapshot(updated)
    }

    private fun handleAdGuard(msg: BridgeProtocol.Incoming) {
        val p = msg.params
        onAdEvent(
            AdEvent(
                phase = p.phase ?: "unknown",
                mode = p.mode ?: "unknown",
                skipped = p.skipped,
                ok = p.ok,
                durationMs = p.durationMs,
                count = p.count
            )
        )
    }

    private fun handleSearch(msg: BridgeProtocol.Incoming) {
        val p = msg.params
        val results = p.results.map {
            SearchResult(
                uri = it.uri,
                name = it.name,
                subtitle = it.subtitle,
                image = it.image,
                kind = it.kind
            )
        }
        onSearchResults(p.query, p.ok, p.reason, results)
    }

    private fun handleQueue(msg: BridgeProtocol.Incoming) {
        val p = msg.params
        val items = p.items.map {
            QueueItem(
                index = it.index,
                name = it.name,
                subtitle = it.subtitle,
                image = it.image,
                uri = it.uri
            )
        }
        onQueue(p.ok, p.reason, items)
    }

    private fun handleLogin(msg: BridgeProtocol.Incoming) {
        when (msg.params.phase) {
            "session-detected" -> { loggedIn = true; onLoggedIn() }
            else -> Log.i(TAG, "ayuda de login aplicada: ${msg.params.phase}")
        }
    }

    /* ------------------------------------------------------------------ */
    /* Kotlin -> JS                                                        */
    /* ------------------------------------------------------------------ */

    /** Envía un comando al reproductor. */
    fun sendCommand(view: WebView?, method: String, paramsJson: String = "{}"): Boolean {
        if (view == null) return false
        val payload = BridgeProtocol.command(commandSeq.incrementAndGet(), method, paramsJson, sessionToken)
        val quoted = JSONObject.quote(payload)
        view.post { view.evaluateJavascript("window.SpotiHost.onCommand($quoted);", null) }
        return true
    }

    /** Busca en el reproductor; el resultado llega por [onSearchResults]. */
    fun search(view: WebView?, query: String): Boolean =
        sendCommand(view, BridgeProtocol.Command.SEARCH, """{"query":${JSONObject.quote(query)}}""")

    /** Pide la cola; el resultado llega por [onQueue]. */
    fun readQueue(view: WebView?): Boolean =
        sendCommand(view, BridgeProtocol.Command.READ_QUEUE)

    fun playQueueItem(view: WebView?, index: Int): Boolean =
        sendCommand(view, BridgeProtocol.Command.PLAY_QUEUE_ITEM, """{"index":$index}""")

    fun playUri(view: WebView?, uri: String): Boolean =
        sendCommand(view, BridgeProtocol.Command.PLAY_URI, """{"uri":${JSONObject.quote(uri)}}""")

    /** Consulta la salud del reproductor (90_probe.js). */
    fun probe(view: WebView?, onResult: (PlayerHealth) -> Unit) {
        if (view == null) return
        view.post {
            view.evaluateJavascript(composer.buildProbeScript()) { raw ->
                parseHealth(raw)?.let(onResult)
            }
        }
    }

    private fun parseHealth(raw: String?): PlayerHealth? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val unescaped = org.json.JSONTokener(raw).nextValue() as? String ?: return null
            val o = JSONObject(unescaped)
            PlayerHealth(
                ready = o.optBoolean("ready"),
                playing = o.optBoolean("playing"),
                track = o.optString("track", null),
                ad = o.optBoolean("ad"),
                neutralizedAds = o.optInt("neutralized"),
                device = o.optString("device", null),
                hasToken = o.optBoolean("hasToken"),
                playButtonPresent = o.optBoolean("playBtn"),
                mediaElements = o.optInt("media"),
                url = o.optString("url", null),
                inputCount = o.optInt("inputs"),
                passwordField = o.optBoolean("pwd"),
                submitButton = o.optBoolean("emailBtn"),
                bodyLength = o.optInt("bodyLen"),
                passwordVisible = o.optBoolean("pwdVisible"),
                viewportWidth = o.optInt("vw"),
                viewportHeight = o.optInt("vh"),
                scrollWidth = o.optInt("scrollW"),
                pageScale = o.optDouble("scale", 0.0),
                visualScale = o.optDouble("visual", 1.0),
                drmStatus = o.optString("drm", null),
                layoutWidth = o.optInt("clientW"),
                metaViewport = o.optString("metaVp", null),
                devicePixelRatio = o.optDouble("dpr", 0.0),
                pageText = o.optString("bodyText", null),
                userAgent = o.optString("ua", null),
                mediaState = o.optString("audio", null)
            )
        }.getOrElse {
            Log.w(TAG, "sonda ilegible", it)
            null
        }
    }

    fun onPageStarted(url: String) {
        playerScriptInjected = false
        Log.d(TAG, "onPageStarted: $url")
    }

    /** Inyecta el controlador completo (composición según ajustes). */
    fun onPlayerPageReady(view: WebView) {
        if (playerScriptInjected) return
        playerScriptInjected = true
        val script = composer.buildPlayerScript()
        view.evaluateJavascript(script, null)
        Log.i(TAG, "Controlador inyectado (${script.length} bytes)")
    }

    fun requestLoginDetection(view: WebView) {
        view.evaluateJavascript(composer.buildLoginScript(), null)
    }

    fun onMainFrameError(code: Int, description: String) {
        Log.w(TAG, "Error en el marco principal: $code $description")
    }

    /** Cachea la portada para evitar descargas repetidas. */
    fun cacheCover(coverUrl: String?) {
        if (coverUrl.isNullOrBlank()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "covers").apply { mkdirs() }
                val target = File(dir, coverUrl.hashCode().toString())
                if (target.exists()) return@runCatching
                java.net.URL(coverUrl).openStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    data class AdEvent(
        val phase: String,
        val mode: String,
        val skipped: Boolean,
        val ok: Boolean,
        val durationMs: Long,
        val count: Int
    )
}
