package com.imparable.spoti.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Exportación e importación de ajustes a un fichero JSON.
 *
 * Se separa del modelo de dominio (`Settings`) a propósito: el fichero exportado
 * es un **contrato externo** que el usuario puede guardar, editar o compartir, y
 * lleva una etiqueta de versión para poder evolucionar sin romper los ficheros
 * ya exportados.
 */
@Serializable
data class SettingsExport(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: SettingsDto
) {
    companion object {
        const val FORMAT = "spoti-imparable.settings"
        const val VERSION = 1
    }
}

/** Copia serializable de [Settings]. Los enums se guardan por nombre. */
@Serializable
data class SettingsDto(
    val serviceEnabled: Boolean = true,
    val loggedIn: Boolean = false,
    val calibration: Boolean = false,
    val cleanMode: Boolean = false,
    val unhideFix: Boolean = true,
    val adBlockMode: String = "STRICT",
    val adGuardMode: String = "SILENT_AND_SKIP",
    val blockListAutoUpdate: Boolean = true,
    val guiMode: String = "MOBILE",
    val amoled: Boolean = true,
    val accentColor: String? = null,
    val autoCloseNowPlaying: Boolean = false,
    val batterySaver: Boolean = false,
    val swipeStop: Boolean = true,
    val headphonesAutoPlay: Boolean = false,
    val headphonesAutoStop: Boolean = false,
    val bluetoothAutoPlay: Boolean = false,
    val bluetoothAutoStop: Boolean = false,
    val autoShutMinutes: Int = 0,
    val sleepTimerMinutes: Int = 0
)

object SettingsCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun toDto(settings: Settings) = SettingsDto(
        serviceEnabled = settings.serviceEnabled,
        loggedIn = settings.loggedIn,
        calibration = settings.calibration,
        cleanMode = settings.cleanMode,
        unhideFix = settings.unhideFix,
        adBlockMode = settings.adBlockMode.name,
        adGuardMode = settings.adGuardMode.name,
        blockListAutoUpdate = settings.blockListAutoUpdate,
        guiMode = settings.guiMode.name,
        amoled = settings.amoled,
        accentColor = settings.accentColor,
        autoCloseNowPlaying = settings.autoCloseNowPlaying,
        batterySaver = settings.batterySaver,
        swipeStop = settings.swipeStop,
        headphonesAutoPlay = settings.headphonesAutoPlay,
        headphonesAutoStop = settings.headphonesAutoStop,
        bluetoothAutoPlay = settings.bluetoothAutoPlay,
        bluetoothAutoStop = settings.bluetoothAutoStop,
        autoShutMinutes = settings.autoShutMinutes,
        sleepTimerMinutes = settings.sleepTimerMinutes
    )

    /**
     * Convierte el DTO en ajustes, saneando los valores: un fichero editado a
     * mano no debe poder dejar la app en un estado inválido (p. ej. un modo de
     * bloqueo inexistente o un temporizador negativo).
     */
    fun toSettings(dto: SettingsDto) = Settings(
        serviceEnabled = dto.serviceEnabled,
        loggedIn = dto.loggedIn,
        calibration = dto.calibration,
        cleanMode = dto.cleanMode,
        unhideFix = dto.unhideFix,
        adBlockMode = dto.adBlockMode.toEnum(AdBlockMode.STRICT),
        adGuardMode = dto.adGuardMode.toEnum(AdGuardMode.SILENT_AND_SKIP),
        blockListAutoUpdate = dto.blockListAutoUpdate,
        guiMode = dto.guiMode.toEnum(GuiMode.MOBILE),
        amoled = dto.amoled,
        accentColor = dto.accentColor?.takeIf { it.matches(Regex("#[0-9a-fA-F]{6,8}")) },
        autoCloseNowPlaying = dto.autoCloseNowPlaying,
        batterySaver = dto.batterySaver,
        swipeStop = dto.swipeStop,
        headphonesAutoPlay = dto.headphonesAutoPlay,
        headphonesAutoStop = dto.headphonesAutoStop,
        bluetoothAutoPlay = dto.bluetoothAutoPlay,
        bluetoothAutoStop = dto.bluetoothAutoStop,
        autoShutMinutes = dto.autoShutMinutes.coerceIn(0, MAX_TIMER_MINUTES),
        sleepTimerMinutes = dto.sleepTimerMinutes.coerceIn(0, MAX_TIMER_MINUTES)
    )

    /** Serializa a JSON legible. */
    fun encode(settings: Settings): String =
        json.encodeToString(SettingsExport.serializer(), SettingsExport(settings = toDto(settings)))

    /**
     * Lee un JSON exportado. Devuelve también un aviso si el formato o la
     * versión no coinciden, para poder informar en lugar de fallar.
     */
    fun decode(text: String): DecodeResult {
        val export = runCatching {
            json.decodeFromString(SettingsExport.serializer(), text)
        }.getOrElse { e ->
            return DecodeResult.Failure("JSON inválido: ${e.message?.take(120) ?: "error de formato"}")
        }

        if (export.format != SettingsExport.FORMAT) {
            return DecodeResult.Failure("El fichero no es de SpotiZado (formato '${export.format}')")
        }
        if (export.version > SettingsExport.VERSION) {
            return DecodeResult.Failure(
                "El fichero es de una versión más nueva (v${export.version} > v${SettingsExport.VERSION})"
            )
        }
        return DecodeResult.Success(toSettings(export.settings), export.exportedAt)
    }

    private inline fun <reified T : Enum<T>> String.toEnum(fallback: T): T =
        runCatching { enumValueOf<T>(this) }.getOrNull() ?: fallback

    sealed interface DecodeResult {
        data class Success(val settings: Settings, val exportedAt: Long) : DecodeResult
        data class Failure(val reason: String) : DecodeResult
    }

    /** Límite de temporizadores, el mismo que el rango ofrecido en la interfaz. */
    const val MAX_TIMER_MINUTES = 180
}
