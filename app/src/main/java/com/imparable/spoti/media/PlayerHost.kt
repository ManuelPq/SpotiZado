package com.imparable.spoti.media

import android.util.Log
import android.webkit.WebView
import com.imparable.spoti.bridge.PlayerBridge
import com.imparable.spoti.data.PlayerSnapshot
import com.imparable.spoti.data.QueueItem
import com.imparable.spoti.data.SearchResult
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Punto único donde vive el WebView del reproductor.
 *
 * Necesario porque el WebView sobrevive a la actividad (el servicio lo mantiene
 * vivo en segundo plano) y Media3 necesita alcanzarlo desde otro proceso lógico.
 * Se guardan referencias débiles para no filtrar la actividad.
 */
object PlayerHost {

    private const val TAG = "PlayerHost"

    private var webViewRef: WeakReference<WebView> = WeakReference(null)
    private var bridgeRef: WeakReference<PlayerBridge> = WeakReference(null)
    private var playerRef: WeakReference<JsPlayer> = WeakReference(null)

    private val snapshotListeners = CopyOnWriteArrayList<(PlayerSnapshot) -> Unit>()
    private val healthListeners = CopyOnWriteArrayList<(com.imparable.spoti.data.PlayerHealth) -> Unit>()
    private val adListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val reloadListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val searchListeners =
        CopyOnWriteArrayList<(String, Boolean, String?, List<SearchResult>) -> Unit>()
    private val queueListeners =
        CopyOnWriteArrayList<(Boolean, String?, List<QueueItem>) -> Unit>()

    @Volatile
    var serviceRunning: Boolean = false
        private set

    val webView: WebView? get() = webViewRef.get()
    val bridge: PlayerBridge? get() = bridgeRef.get()
    val player: JsPlayer? get() = playerRef.get()

    fun attachWebView(webView: WebView, bridge: PlayerBridge) {
        webViewRef = WeakReference(webView)
        bridgeRef = WeakReference(bridge)
    }

    fun detachWebView() {
        webViewRef = WeakReference(null)
        bridgeRef = WeakReference(null)
    }

    fun attachPlayer(player: JsPlayer) {
        playerRef = WeakReference(player)
    }

    fun detachPlayer() {
        playerRef = WeakReference(null)
    }

    fun onServiceStarted(running: Boolean) {
        serviceRunning = running
    }

    /** Envía una orden al reproductor web. Si no hay WebView, no hace nada. */
    fun sendCommand(method: String, paramsJson: String = "{}") {
        val view = webViewRef.get()
        val bridge = bridgeRef.get()
        if (view == null || bridge == null) {
            Log.d(TAG, "comando '$method' ignorado: reproductor no disponible")
            return
        }
        bridge.sendCommand(view, method, paramsJson)
    }

    /** Publica un estado nuevo desde el puente. */
    fun publishSnapshot(snapshot: PlayerSnapshot) {
        playerRef.get()?.updateSnapshot(snapshot)
        snapshotListeners.forEach { it(snapshot) }
    }

    fun publishHealth(health: com.imparable.spoti.data.PlayerHealth) {
        healthListeners.forEach { it(health) }
    }

    /** Publica un evento de la capa de neutralización de anuncios. */
    fun publishAdEvent(event: com.imparable.spoti.bridge.PlayerBridge.AdEvent) {
        adListeners.forEach { it(event.phase) }
    }

    /** Resultado de una búsqueda lanzada desde la app. */
    fun publishSearch(query: String, ok: Boolean, reason: String?, results: List<SearchResult>) {
        searchListeners.forEach { it(query, ok, reason, results) }
    }

    /** Cola leída del reproductor. */
    fun publishQueue(ok: Boolean, reason: String?, items: List<QueueItem>) {
        queueListeners.forEach { it(ok, reason, items) }
    }

    /** El reproductor necesita recargarse (sesión bloqueada o atasco). */
    fun publishReload(reason: String) {
        reloadListeners.forEach { it(reason) }
        reload()
    }

    /** Recarga la página del reproductor. */
    fun reload() {
        val view = webViewRef.get() ?: return
        view.post { view.reload() }
    }

    /** Navega a una URL concreta (p. ej. volver al login con el formulario). */
    fun loadUrl(url: String) {
        val view = webViewRef.get() ?: return
        view.post { view.loadUrl(url) }
    }

    fun addSnapshotListener(listener: (PlayerSnapshot) -> Unit) { snapshotListeners.add(listener) }
    fun removeSnapshotListener(listener: (PlayerSnapshot) -> Unit) { snapshotListeners.remove(listener) }
    fun addHealthListener(listener: (com.imparable.spoti.data.PlayerHealth) -> Unit) { healthListeners.add(listener) }
    fun removeHealthListener(listener: (com.imparable.spoti.data.PlayerHealth) -> Unit) { healthListeners.remove(listener) }
    fun addAdEventListener(listener: (String) -> Unit) { adListeners.add(listener) }
    fun removeAdEventListener(listener: (String) -> Unit) { adListeners.remove(listener) }
    fun addReloadListener(listener: (String) -> Unit) { reloadListeners.add(listener) }
    fun removeReloadListener(listener: (String) -> Unit) { reloadListeners.remove(listener) }
    fun addSearchListener(listener: (String, Boolean, String?, List<SearchResult>) -> Unit) {
        searchListeners.add(listener)
    }
    fun removeSearchListener(listener: (String, Boolean, String?, List<SearchResult>) -> Unit) {
        searchListeners.remove(listener)
    }
    fun addQueueListener(listener: (Boolean, String?, List<QueueItem>) -> Unit) {
        queueListeners.add(listener)
    }
    fun removeQueueListener(listener: (Boolean, String?, List<QueueItem>) -> Unit) {
        queueListeners.remove(listener)
    }
}
