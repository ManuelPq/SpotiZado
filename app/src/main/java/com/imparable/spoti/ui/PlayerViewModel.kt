package com.imparable.spoti.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imparable.spoti.SpotiApp
import com.imparable.spoti.adblock.BlockListUpdater
import com.imparable.spoti.bridge.BridgeProtocol
import com.imparable.spoti.data.AdBlockMode
import com.imparable.spoti.data.AdGuardMode
import com.imparable.spoti.data.BlockStats
import com.imparable.spoti.data.FetchState
import com.imparable.spoti.data.GuiMode
import com.imparable.spoti.data.PlayerHealth
import com.imparable.spoti.data.PlayerSnapshot
import com.imparable.spoti.data.QueueItem
import com.imparable.spoti.data.SearchResult
import com.imparable.spoti.data.Settings
import com.imparable.spoti.data.SettingsCodec
import com.imparable.spoti.media.PlayerHost
import com.imparable.spoti.timer.PlayerTimers
import com.imparable.spoti.web.PlayerWebViewClient
import com.imparable.spoti.web.PlayerWebViewFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado de la pantalla principal.
 *
 * Es el único sitio que habla con el reproductor y con los ajustes: la interfaz
 * es declarativa y no conoce ni el WebView ni DataStore.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = application as SpotiApp

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var healthJob: Job? = null

    // Se guardan las referencias para poder desregistrarlas: PlayerHost es un
    // objeto único de toda la app, así que un listener olvidado sobreviviría a
    // la pantalla y actualizaría un ViewModel muerto.
    private val onSnapshot: (PlayerSnapshot) -> Unit = { snap ->
        _ui.update { it.copy(snapshot = snap) }
        // Los temporizadores se reajustan con cada cambio de reproducción.
        PlayerTimers.sync(snap.playing)
    }
    private val onHealth: (PlayerHealth) -> Unit = { h -> _ui.update { it.copy(health = h) } }
    private val onAdEvent: (String) -> Unit = { phase -> _ui.update { it.copy(lastAdPhase = phase) } }
    private val onSearch: (String, Boolean, String?, List<SearchResult>) -> Unit =
        { query, ok, reason, results ->
            _ui.update {
                it.copy(
                    search = FetchState(
                        items = results,
                        loading = false,
                        reason = when {
                            !ok -> reason ?: "El reproductor no pudo completar la búsqueda"
                            results.isEmpty() -> "Sin resultados"
                            else -> null
                        },
                        query = query
                    )
                )
            }
        }
    private val onQueue: (Boolean, String?, List<QueueItem>) -> Unit = { ok, reason, items ->
        _ui.update {
            it.copy(
                queue = FetchState(
                    items = items,
                    loading = false,
                    reason = when {
                        !ok -> reason ?: "El reproductor no pudo leer la cola"
                        items.isEmpty() -> "La cola está vacía"
                        else -> null
                    }
                )
            )
        }
    }

    init {
        viewModelScope.launch {
            graph.settings.settings.collect { s -> _ui.update { it.copy(settings = s) } }
        }

        PlayerHost.addSnapshotListener(onSnapshot)
        PlayerHost.addHealthListener(onHealth)
        PlayerHost.addAdEventListener(onAdEvent)
        PlayerHost.addSearchListener(onSearch)
        PlayerHost.addQueueListener(onQueue)

        startHealthPolling()
        refreshStats()
    }

    override fun onCleared() {
        super.onCleared()
        healthJob?.cancel()
        PlayerHost.removeSnapshotListener(onSnapshot)
        PlayerHost.removeHealthListener(onHealth)
        PlayerHost.removeAdEventListener(onAdEvent)
        PlayerHost.removeSearchListener(onSearch)
        PlayerHost.removeQueueListener(onQueue)
    }

    /* ------------------------------------------------------------------ */
    /* Ajustes                                                             */
    /* ------------------------------------------------------------------ */

    fun setServiceEnabled(enabled: Boolean) = viewModelScope.launch {
        graph.settings.setServiceEnabled(enabled)
        if (enabled) graph.startPlaybackService() else graph.stopPlaybackService()
    }

    fun setAdBlockMode(mode: AdBlockMode) = viewModelScope.launch {
        graph.settings.setAdBlockMode(mode)
        refreshStats()
    }

    fun setAdGuardMode(mode: AdGuardMode) = viewModelScope.launch { graph.settings.setAdGuardMode(mode) }

    fun setCalibration(value: Boolean) = viewModelScope.launch { graph.settings.setCalibration(value) }

    fun setCleanMode(value: Boolean) = viewModelScope.launch { graph.settings.setCleanMode(value) }
    fun setGuiMode(mode: GuiMode) = viewModelScope.launch { graph.settings.setGuiMode(mode) }

    fun setShowNativePlayerBar(value: Boolean) =
        viewModelScope.launch { graph.settings.setShowNativePlayerBar(value) }
    fun setAmoled(value: Boolean) = viewModelScope.launch { graph.settings.setAmoled(value) }
    fun setAccent(value: String?) = viewModelScope.launch { graph.settings.setAccentColor(value) }
    fun setAutoCloseNowPlaying(value: Boolean) = viewModelScope.launch { graph.settings.setAutoCloseNowPlaying(value) }
    fun setBatterySaver(value: Boolean) = viewModelScope.launch { graph.settings.setBatterySaver(value) }
    fun setSwipeStop(value: Boolean) = viewModelScope.launch { graph.settings.setSwipeStop(value) }
    fun setBlockListAutoUpdate(value: Boolean) = viewModelScope.launch { graph.settings.setBlockListAutoUpdate(value) }
    fun setHeadphonesAutoPlay(v: Boolean) = viewModelScope.launch { graph.settings.setHeadphonesAutoPlay(v) }
    fun setHeadphonesAutoStop(v: Boolean) = viewModelScope.launch { graph.settings.setHeadphonesAutoStop(v) }
    fun setBluetoothAutoPlay(v: Boolean) = viewModelScope.launch { graph.settings.setBluetoothAutoPlay(v) }
    fun setBluetoothAutoStop(v: Boolean) = viewModelScope.launch { graph.settings.setBluetoothAutoStop(v) }

    /** Temporizador de reproducción (0 = apagado). Se aplica al momento. */
    fun setSleepTimer(minutes: Int) = viewModelScope.launch {
        graph.settings.setSleepTimerMinutes(minutes)
        PlayerTimers.sync(_ui.value.snapshot.playing)
    }

    /** Apagado automático por inactividad (0 = apagado). */
    fun setAutoShut(minutes: Int) = viewModelScope.launch {
        graph.settings.setAutoShutMinutes(minutes)
        PlayerTimers.sync(_ui.value.snapshot.playing)
    }

    /* ------------------------------------------------------------------ */
    /* Control del reproductor                                             */
    /* ------------------------------------------------------------------ */

    fun toggleFavorite() = PlayerHost.sendCommand(BridgeProtocol.Command.FAVORITE)
    fun toggleRepeat() = PlayerHost.sendCommand(BridgeProtocol.Command.REPEAT)
    fun toggleShuffle() = PlayerHost.sendCommand(BridgeProtocol.Command.SHUFFLE)
    fun next() = PlayerHost.sendCommand(BridgeProtocol.Command.NEXT)
    fun previous() = PlayerHost.sendCommand(BridgeProtocol.Command.PREVIOUS)
    fun togglePlayPause() = PlayerHost.sendCommand(BridgeProtocol.Command.TOGGLE)
    fun seekTo(positionMs: Long) =
        PlayerHost.sendCommand(BridgeProtocol.Command.SEEK_TO, """{"position":$positionMs}""")

    fun reloadPlayer() = PlayerHost.reload()

    /**
     * Abre la página de login con el formulario de email y contraseña a la
     * vista. Es la salida que faltaba cuando el aviso de «Falta iniciar sesión»
     * aparecía sin ninguna acción disponible.
     *
     * El perfil **móvil** es imprescindible aquí: con perfil de escritorio la
     * página se maqueta para ~980 px y el formulario queda fuera de la pantalla.
     */
    fun openLogin() {
        val view = PlayerHost.webView ?: return
        PlayerWebViewFactory.applyProfile(view, desktop = false)
        PlayerHost.loadUrl(PlayerWebViewClient.URL_LOGIN)
    }

    /**
     * Abre el login en el navegador del sistema.
     *
     * No sirve para iniciar sesión dentro de la app (las cookies viven en otro
     * sitio), pero es el diagnóstico más rápido: si en el navegador del teléfono
     * tampoco aparece el formulario, el problema es de red o de cuenta, no de la app.
     */
    fun openLoginInBrowser() {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    Uri.parse(PlayerWebViewClient.URL_LOGIN)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /* Búsqueda                                                            */
    /* ------------------------------------------------------------------ */

    /** Lanza la búsqueda; el resultado llega como evento del puente. */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _ui.update { it.copy(search = FetchState(query = "")) }
            return
        }
        _ui.update { it.copy(search = FetchState(loading = true, query = trimmed)) }

        val view = PlayerHost.webView
        val bridge = PlayerHost.bridge
        if (view == null || bridge == null) {
            _ui.update {
                it.copy(search = FetchState(query = trimmed, reason = "El reproductor no está cargado"))
            }
            return
        }
        if (!bridge.search(view, trimmed)) {
            _ui.update {
                it.copy(search = FetchState(query = trimmed, reason = "No se pudo contactar con el reproductor"))
            }
        }
    }

    fun clearSearch() {
        PlayerHost.sendCommand(BridgeProtocol.Command.CLEAR_SEARCH)
        _ui.update { it.copy(search = FetchState()) }
    }

    /** Reproduce un resultado: se delega en el reproductor (playFromUri). */
    fun playResult(result: SearchResult) {
        PlayerHost.sendCommand(
            BridgeProtocol.Command.PLAY_URI,
            """{"uri":${org.json.JSONObject.quote(result.uri)}}"""
        )
    }

    /* ------------------------------------------------------------------ */
    /* Cola                                                                */
    /* ------------------------------------------------------------------ */

    fun refreshQueue() {
        _ui.update { it.copy(queue = it.queue.copy(loading = true, reason = null)) }
        val view = PlayerHost.webView
        val bridge = PlayerHost.bridge
        if (view == null || bridge == null) {
            _ui.update { it.copy(queue = it.queue.copy(loading = false, reason = "El reproductor no está cargado")) }
            return
        }
        bridge.readQueue(view)
    }

    fun playQueueItem(item: QueueItem) {
        val view = PlayerHost.webView
        PlayerHost.bridge?.playQueueItem(view, item.index)
    }

    fun closeQueuePanel() {
        PlayerHost.sendCommand(BridgeProtocol.Command.CLOSE_QUEUE)
    }

    /* ------------------------------------------------------------------ */
    /* Exportar / importar ajustes                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Escribe los ajustes en el fichero elegido por el usuario. Se usa el
     * `contentResolver`, así que funciona con cualquier proveedor (Descargas,
     * Drive, USB…) sin pedir permisos de almacenamiento.
     */
    fun exportSettings(target: Uri) = viewModelScope.launch {
        val json = SettingsCodec.encode(_ui.value.settings)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(target)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
        }
        _ui.update { it.copy(settingsMessage = if (ok) "Ajustes exportados" else "No se pudo escribir el fichero") }
    }

    /**
     * Lee un fichero de ajustes y los aplica. Si el formato no es válido o es de
     * una versión más nueva, se informa **y no se toca nada**.
     */
    fun importSettings(source: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(source)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
        }
        if (text == null) {
            _ui.update { it.copy(settingsMessage = "No se pudo leer el fichero") }
            return@launch
        }
        when (val result = SettingsCodec.decode(text)) {
            is SettingsCodec.DecodeResult.Failure ->
                _ui.update { it.copy(settingsMessage = "Importación rechazada: ${result.reason}") }

            is SettingsCodec.DecodeResult.Success -> {
                graph.settings.applyAll(result.settings)
                PlayerTimers.sync(_ui.value.snapshot.playing)
                _ui.update { it.copy(settingsMessage = "Ajustes importados correctamente") }
            }
        }
    }

    fun clearSettingsMessage() = _ui.update { it.copy(settingsMessage = null) }

    /* ------------------------------------------------------------------ */
    /* Lista de bloqueo y diagnóstico                                      */
    /* ------------------------------------------------------------------ */

    fun updateBlockList() = viewModelScope.launch {
        _ui.update { it.copy(updatingList = true, listMessage = null) }
        val result = graph.blockListUpdater.update(graph.adBlocker)
        val message = when (result) {
            is BlockListUpdater.UpdateResult.Updated -> "Lista actualizada: ${result.rules} reglas"
            is BlockListUpdater.UpdateResult.Skipped -> "Sin cambios (${result.reason})"
            is BlockListUpdater.UpdateResult.Failed -> "Fallo: ${result.reason}"
        }
        _ui.update { it.copy(updatingList = false, listMessage = message) }
        refreshStats()
    }

    fun setBlockListUrl(url: String) = viewModelScope.launch { graph.blockListUpdater.sourceUrl = url }

    fun resetCounters() {
        graph.adBlocker.resetSession()
        refreshStats()
    }

    fun refreshStats() = viewModelScope.launch {
        val s = _ui.value.settings
        _ui.update {
            it.copy(
                stats = BlockStats(
                    blockedTotal = graph.adBlocker.blockedTotal,
                    blockedSinceStart = graph.adBlocker.blockedSinceStart,
                    adsNeutralized = it.health.neutralizedAds,
                    rulesLoaded = graph.adBlocker.ruleCount,
                    rulesVersion = graph.adBlocker.version,
                    mode = s.adBlockMode,
                    guardMode = s.adGuardMode
                )
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /* Sondeo de salud                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Cada 5 s se pregunta al reproductor por su estado y se refrescan las
     * cuentas atrás. Es el mecanismo que evita el fallo clásico de estos
     * wrappers (quedarse en negro sin saber por qué).
     */
    private fun startHealthPolling() {
        healthJob?.cancel()
        healthJob = viewModelScope.launch {
            while (true) {
                val view = PlayerHost.webView
                val bridge = PlayerHost.bridge
                if (view != null && bridge != null) {
                    bridge.probe(view) { h -> PlayerHost.publishHealth(h) }
                }
                refreshStats()
                _ui.update {
                    it.copy(
                        sleepRemainingMs = PlayerTimers.sleepRemainingMs(),
                        shutRemainingMs = PlayerTimers.shutRemainingMs()
                    )
                }
                delay(5_000)
            }
        }
    }

    data class UiState(
        val settings: Settings = Settings(),
        val snapshot: PlayerSnapshot = PlayerSnapshot(),
        val health: PlayerHealth = PlayerHealth(),
        val stats: BlockStats = BlockStats(),
        val search: FetchState<SearchResult> = FetchState(),
        val queue: FetchState<QueueItem> = FetchState(),
        val updatingList: Boolean = false,
        val listMessage: String? = null,
        val settingsMessage: String? = null,
        val lastAdPhase: String? = null,
        val sleepRemainingMs: Long = 0L,
        val shutRemainingMs: Long = 0L
    ) {
        val playerOperational: Boolean get() = health.playButtonPresent || health.ready
    }
}
