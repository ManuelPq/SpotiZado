package com.imparable.spoti.bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Protocolo del puente Kotlin <-> JS (JSON-RPC 2.0).
 *
 * Contrato único y versionado: un cambio en el JavaScript no puede romper el
 * Kotlin en silencio, porque un mensaje que no encaja con el modelo se descarta
 * y se registra en lugar de lanzar.
 */
object BridgeProtocol {

    const val VERSION = 2
    const val JS_OBJECT = "SpotiBridge"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    /** Resultado de búsqueda tal como lo publica el JavaScript. */
    @Serializable
    data class SearchItem(
        val uri: String,
        val name: String,
        val subtitle: String = "",
        val image: String? = null,
        val kind: String = "unknown"
    )

    /** Elemento de cola tal como lo publica el JavaScript. */
    @Serializable
    data class QueueItemDto(
        val index: Int,
        val name: String,
        val subtitle: String = "",
        val image: String? = null,
        val uri: String? = null
    )

    /** Mensaje entrante (JS -> Kotlin). */
    @Serializable
    data class Incoming(
        @SerialName("jsonrpc") val version: String = "2.0",
        val id: String? = null,
        val method: String,
        val params: Params = Params(),
        val error: Error? = null
    ) {
        @Serializable
        data class Params(
            // estado del reproductor
            val track: String? = null,
            val artist: String? = null,
            val album: String? = null,
            val duration: Long = 0L,
            val position: Long = 0L,
            val playing: Boolean = false,
            val repeat: String = "false",
            val shuffle: Boolean = false,
            val favorite: Boolean = false,
            val cover: String? = null,
            @SerialName("adPlaying") val adPlaying: Boolean = false,

            // neutralización de anuncios
            val phase: String? = null,
            val mode: String? = null,
            val count: Int = 0,
            @SerialName("durationMs") val durationMs: Long = 0L,
            val skipped: Boolean = false,
            val ok: Boolean = true,

            // varios
            @SerialName("deviceId") val deviceId: String? = null,
            val reason: String? = null,

            // búsqueda
            val query: String = "",
            val results: List<SearchItem> = emptyList(),

            // cola
            val items: List<QueueItemDto> = emptyList(),
            val current: Int = -1
        )

        @Serializable
        data class Error(val code: Int = -32000, val message: String = "")
    }

    /** Eventos que el JavaScript puede enviar. */
    object Method {
        const val STATE = "event.state"
        const val POSITION = "event.position"
        const val AD_GUARD = "event.adguard"
        const val DEVICE = "event.device"
        const val RELOAD = "event.reload"
        const val LOGIN = "event.login"
        const val SEARCH_RESULTS = "event.searchResults"
        const val QUEUE = "event.queue"
    }

    /** Comandos que Kotlin puede enviar al JavaScript (ver 20/25/26_*.js). */
    object Command {
        const val PLAY = "play"
        const val PAUSE = "pause"
        const val TOGGLE = "toggle"
        const val NEXT = "next"
        const val PREVIOUS = "previous"
        const val REPEAT = "repeat"
        const val SHUFFLE = "shuffle"
        const val FAVORITE = "favorite"
        const val SEEK_TO = "seekTo"
        const val PLAY_URI = "playUri"
        const val READ_STATE = "readState"
        const val SEARCH = "search"
        const val CLEAR_SEARCH = "clearSearch"
        const val READ_QUEUE = "refreshQueue"
        const val PLAY_QUEUE_ITEM = "playQueueItem"
        const val CLOSE_QUEUE = "closeQueue"
    }

    /** Construye el JSON de un comando, con el token de sesión. */
    fun command(id: Long, method: String, paramsJson: String = "{}", token: String): String {
        val t = org.json.JSONObject.quote(token)
        val m = org.json.JSONObject.quote(method)
        return """{"id":"cmd$id","token":$t,"method":$m,"params":$paramsJson}"""
    }

    /** Respuesta a una RPC iniciada desde el JavaScript. */
    fun reply(id: String, resultJson: String): String =
        """{"id":${org.json.JSONObject.quote(id)},"result":$resultJson}"""

    /** Error de una RPC iniciada desde el JavaScript. */
    fun replyError(id: String, message: String): String =
        """{"id":${org.json.JSONObject.quote(id)},"error":{"message":${org.json.JSONObject.quote(message)}}}"""
}
