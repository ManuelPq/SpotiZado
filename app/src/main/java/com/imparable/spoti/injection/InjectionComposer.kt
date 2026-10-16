package com.imparable.spoti.injection

import android.content.Context
import android.util.Log
import com.imparable.spoti.data.AdGuardMode
import com.imparable.spoti.data.Settings
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Compone el JavaScript que se inyecta en el reproductor.
 *
 * Los scripts son ficheros de `assets/webplayer/` y se ensamblan **según los
 * ajustes activos**, de modo que el reproductor nunca recibe código de funciones
 * apagadas (a diferencia de Spotifuck, donde todo el JS iba en literales Java y
 * se ejecutaba siempre).
 */
class InjectionComposer(private val context: Context) {

    companion object {
        private const val TAG = "InjectionComposer"
        private const val DIR = "webplayer/"

        const val ENV_SPOOF = DIR + "00_env_spoof.js"
        const val HOST_BRIDGE = DIR + "05_host_bridge.js"
        const val STATE = DIR + "10_state.js"
        const val CONTROL = DIR + "20_control.js"
        const val SEARCH = DIR + "25_search.js"
        const val QUEUE = DIR + "26_queue.js"
        const val AD_GUARD = DIR + "30_adguard.js"
        const val LAYOUT = DIR + "40_layout.js"
        const val THEME = DIR + "50_theme.js"
        const val LOGIN = DIR + "60_login.js"
        const val PROBE = DIR + "90_probe.js"

        /** Ajustes vigentes (los actualiza SpotiApp desde DataStore). */
        @Volatile
        var settings: Settings = Settings()
    }

    private val cache = HashMap<String, String>(20)

    fun buildEnvSpoofScript(): String = load(ENV_SPOOF)

    fun buildLoginScript(): String = load(HOST_BRIDGE) + load(LOGIN)

    fun buildProbeScript(): String = load(PROBE)

    /** Script de arranque: puente, comandos y tema, antes de tener el reproductor. */
    fun buildBootScript(): String = buildString {
        append(load(HOST_BRIDGE))
        append(load(STATE))
        append(load(CONTROL))
        append(load(SEARCH))
        append(load(QUEUE))
        append(load(THEME))
        append(applyThemeCall())
    }

    /**
     * Controlador completo. El orden importa: el estado y los comandos deben
     * existir antes que el AdGuard y la búsqueda, que los reutilizan.
     */
    fun buildPlayerScript(): String = buildString {
        append(load(HOST_BRIDGE))
        append(load(STATE))
        append(load(CONTROL))
        append(load(SEARCH))
        append(load(QUEUE))

        // Capa 3: neutralizador de anuncios (solo si está activo).
        val guard = settings.adGuardMode
        if (guard != AdGuardMode.OFF && !com.imparable.spoti.adblock.AdBlocker.SAFE_MODE) {
            append(load(AD_GUARD))
            append(
                "window.SpotiAdGuardConfig={mode:${quote(modeName(guard))},"
                    + "intervalMs:700,maxAdMs:40000,recoverOnStuck:true};"
            )
            append("window.spotiStartAdGuard(window.SpotiAdGuardConfig);")
        }

        append(load(THEME))
        append(applyThemeCall())

        // El CSS que adapta el reproductor de ESCRITORIO a pantalla de movil no
        // tiene sentido si el usuario pide el reproductor movil: solo se inyecta
        // en modo escritorio.
        if (settings.guiMode == com.imparable.spoti.data.GuiMode.DESKTOP) {
            append(load(LAYOUT))
            append("window.spotiStartLayout(${settings.autoCloseNowPlaying});")
        }

        append("window.spotiStartStateLoop(${if (settings.batterySaver) 4000 else 2000});")
    }

    private fun applyThemeCall(): String {
        val accent = settings.accentColor?.let { quote(it) } ?: "null"
        return "window.spotiApplyTheme({amoled:${settings.amoled},accent:$accent});"
    }

    private fun modeName(mode: AdGuardMode): String = when (mode) {
        AdGuardMode.OFF -> "off"
        AdGuardMode.SILENT -> "silent"
        AdGuardMode.SKIP -> "skip"
        AdGuardMode.SILENT_AND_SKIP -> "silent_skip"
    }

    private fun quote(value: String) = org.json.JSONObject.quote(value)

    private fun load(asset: String): String {
        cache[asset]?.let { return it }
        val content = runCatching {
            context.assets.open(asset).use { readAll(it) }
        }.getOrElse {
            Log.e(TAG, "No se pudo leer $asset", it)
            ""
        }
        cache[asset] = content
        return content
    }

    private fun readAll(input: InputStream): String {
        val out = ByteArrayOutputStream(16 * 1024)
        val buffer = ByteArray(8192)
        while (true) {
            val n = try { input.read(buffer) } catch (e: IOException) { -1 }
            if (n <= 0) break
            out.write(buffer, 0, n)
        }
        return out.toString("UTF-8")
    }
}
