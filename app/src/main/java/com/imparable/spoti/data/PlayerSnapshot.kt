package com.imparable.spoti.data

/**
 * Estado del reproductor tal como lo publica el JavaScript inyectado.
 * Todos los campos son opcionales porque el reproductor web cambia su DOM sin
 * aviso: el modelo nunca debe fallar por un campo ausente.
 */
data class PlayerSnapshot(
    val track: String? = null,
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    val position: Long = 0L,
    val playing: Boolean = false,
    val repeat: String = "false",
    val shuffle: Boolean = false,
    val favorite: Boolean = false,
    val cover: String? = null,
    val adPlaying: Boolean = false
) {
    val isEmpty: Boolean get() = track.isNullOrBlank()

    /** Los tres modos de repetición que expone el reproductor web. */
    val repeatIcon: RepeatMode
        get() = when (repeat) {
            "true" -> RepeatMode.ALL
            "mixed" -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }

    enum class RepeatMode { OFF, ALL, ONE }
}

/** Salud del reproductor, según la sonda JS (90_probe.js). */
data class PlayerHealth(
    val ready: Boolean = false,
    val playing: Boolean = false,
    val track: String? = null,
    val ad: Boolean = false,
    val neutralizedAds: Int = 0,
    val device: String? = null,
    val hasToken: Boolean = false,
    val playButtonPresent: Boolean = false,
    val mediaElements: Int = 0,
    val url: String? = null,
    /** Nº de campos en la página: 0 indica que no llegó a dibujarse. */
    val inputCount: Int = 0,
    /** ¿Hay campo de contraseña? Es el indicador fiable de «login visible». */
    val passwordField: Boolean = false,
    /** ¿Hay botón de envío/login? */
    val submitButton: Boolean = false,
    /** Longitud del texto visible: distingue página en blanco de página con aviso. */
    val bodyLength: Int = 0,
    /** ¿El campo de contraseña está dentro de la zona VISIBLE (no solo en el DOM)? */
    val passwordVisible: Boolean = false,
    /** Ancho del área de vista del documento. */
    val viewportWidth: Int = 0,
    /** Alto del área de vista del documento. */
    val viewportHeight: Int = 0,
    /** Ancho total del documento: si supera mucho el viewport, hay desbordamiento. */
    val scrollWidth: Int = 0,
    /** Escala aplicada al documento (screen.width / innerWidth). */
    val pageScale: Double = 0.0
)

/** Estadísticas del bloqueo, para el panel de diagnóstico. */
data class BlockStats(
    val blockedTotal: Long = 0L,
    val blockedSinceStart: Long = 0L,
    val adsNeutralized: Int = 0,
    val rulesLoaded: Int = 0,
    val rulesVersion: String = "—",
    val lastUpdate: Long = 0L,
    val mode: AdBlockMode = AdBlockMode.STRICT,
    val guardMode: AdGuardMode = AdGuardMode.SILENT_AND_SKIP
)
