package com.imparable.spoti.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Modo de filtrado de red (capa 1 del bloqueo). */
enum class AdBlockMode {
    /** Sin filtrado. */
    OFF,

    /** Solo rastreadores y telemetría: nunca puede afectar a la reproducción. */
    TRACKERS,

    /** Añade los dominios publicitarios de Spotify. */
    STRICT
}

/** Modo de neutralización dentro del reproductor (capa 3 del bloqueo). */
enum class AdGuardMode { OFF, SILENT, SKIP, SILENT_AND_SKIP }

/** Modo de presentación del reproductor. */
enum class GuiMode { MOBILE, DESKTOP }

data class Settings(
    val serviceEnabled: Boolean = true,
    val loggedIn: Boolean = false,
    val adBlockMode: AdBlockMode = AdBlockMode.STRICT,
    val adGuardMode: AdGuardMode = AdGuardMode.SILENT_AND_SKIP,
    val blockListAutoUpdate: Boolean = true,
    /**
     * Vista del reproductor. Por defecto ESCRITORIO.
     *
     * Motivo medido: en la vista movil el reproductor web no encadena la lista —
     * al acabar una cancion hay que darle a reproducir otra vez. La de escritorio
     * si reproduce la lista entera y permite el aleatorio: comportarse como la
     * version de PC, que es lo que se pide.
     */
    val guiMode: GuiMode = GuiMode.DESKTOP,
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
    val sleepTimerMinutes: Int = 0,
    /**
     * Barra de reproduccion nativa (gris, bajo el reproductor).
     *
     * Apagada por defecto: no reflejaba bien el estado (llego a mostrar el nombre
     * del dispositivo en vez de la pista) y el reproductor web ya trae sus
     * propios controles. Se conserva el codigo para poder rehacerla.
     */
    val showNativePlayerBar: Boolean = false
)

/** Delegado de DataStore: de nivel de fichero, para que exista un único almacén. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("spoti_imparable")

/**
 * Ajustes persistidos con **DataStore**.
 *
 * Sustituye a las `SharedPreferences` de Spotifuck/Spotilol: los ajustes son un
 * `Flow`, así que la interfaz y el motor de bloqueo reaccionan a los cambios sin
 * listeners manuales.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val serviceEnabled = booleanPreferencesKey("service_enabled")
        val loggedIn = booleanPreferencesKey("logged_in")
        val adBlockMode = stringPreferencesKey("adblock_mode")
        val adGuardMode = stringPreferencesKey("adguard_mode")
        val blockListAutoUpdate = booleanPreferencesKey("blocklist_auto_update")
        val guiMode = stringPreferencesKey("gui_mode")
        val showNativePlayerBar = booleanPreferencesKey("show_native_player_bar")
        val amoled = booleanPreferencesKey("amoled")
        val accentColor = stringPreferencesKey("accent_color")
        val autoCloseNowPlaying = booleanPreferencesKey("auto_close_now_playing")
        val batterySaver = booleanPreferencesKey("battery_saver")
        val swipeStop = booleanPreferencesKey("swipe_stop")
        val headphonesAutoPlay = booleanPreferencesKey("hp_auto_play")
        val headphonesAutoStop = booleanPreferencesKey("hp_auto_stop")
        val bluetoothAutoPlay = booleanPreferencesKey("bt_auto_play")
        val bluetoothAutoStop = booleanPreferencesKey("bt_auto_stop")
        val autoShutMinutes = stringPreferencesKey("auto_shut_minutes")
        val sleepTimerMinutes = stringPreferencesKey("sleep_timer_minutes")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> read(p) }

    private fun read(p: Preferences) = Settings(
        serviceEnabled = p[Keys.serviceEnabled] ?: true,
        loggedIn = p[Keys.loggedIn] ?: false,
        adBlockMode = p[Keys.adBlockMode].toEnum(AdBlockMode.STRICT),
        adGuardMode = p[Keys.adGuardMode].toEnum(AdGuardMode.SILENT_AND_SKIP),
        blockListAutoUpdate = p[Keys.blockListAutoUpdate] ?: true,
        guiMode = p[Keys.guiMode].toEnum(GuiMode.DESKTOP),
        showNativePlayerBar = p[Keys.showNativePlayerBar] ?: false,
        amoled = p[Keys.amoled] ?: true,
        accentColor = p[Keys.accentColor],
        autoCloseNowPlaying = p[Keys.autoCloseNowPlaying] ?: false,
        batterySaver = p[Keys.batterySaver] ?: false,
        swipeStop = p[Keys.swipeStop] ?: true,
        headphonesAutoPlay = p[Keys.headphonesAutoPlay] ?: false,
        headphonesAutoStop = p[Keys.headphonesAutoStop] ?: false,
        bluetoothAutoPlay = p[Keys.bluetoothAutoPlay] ?: false,
        bluetoothAutoStop = p[Keys.bluetoothAutoStop] ?: false,
        autoShutMinutes = (p[Keys.autoShutMinutes] ?: "0").toIntOrNull() ?: 0,
        sleepTimerMinutes = (p[Keys.sleepTimerMinutes] ?: "0").toIntOrNull() ?: 0
    )

    suspend fun setServiceEnabled(value: Boolean) = edit { it[Keys.serviceEnabled] = value }
    suspend fun setLoggedIn(value: Boolean) = edit { it[Keys.loggedIn] = value }
    suspend fun setAdBlockMode(value: AdBlockMode) = edit { it[Keys.adBlockMode] = value.name }
    suspend fun setAdGuardMode(value: AdGuardMode) = edit { it[Keys.adGuardMode] = value.name }
    suspend fun setBlockListAutoUpdate(value: Boolean) = edit { it[Keys.blockListAutoUpdate] = value }
    suspend fun setGuiMode(value: GuiMode) = edit { it[Keys.guiMode] = value.name }
    suspend fun setShowNativePlayerBar(value: Boolean) = edit { it[Keys.showNativePlayerBar] = value }
    suspend fun setAmoled(value: Boolean) = edit { it[Keys.amoled] = value }
    suspend fun setAccentColor(value: String?) = edit {
        if (value == null) it.remove(Keys.accentColor) else it[Keys.accentColor] = value
    }
    suspend fun setAutoCloseNowPlaying(value: Boolean) = edit { it[Keys.autoCloseNowPlaying] = value }
    suspend fun setBatterySaver(value: Boolean) = edit { it[Keys.batterySaver] = value }
    suspend fun setSwipeStop(value: Boolean) = edit { it[Keys.swipeStop] = value }
    suspend fun setHeadphonesAutoPlay(value: Boolean) = edit { it[Keys.headphonesAutoPlay] = value }
    suspend fun setHeadphonesAutoStop(value: Boolean) = edit { it[Keys.headphonesAutoStop] = value }
    suspend fun setBluetoothAutoPlay(value: Boolean) = edit { it[Keys.bluetoothAutoPlay] = value }
    suspend fun setBluetoothAutoStop(value: Boolean) = edit { it[Keys.bluetoothAutoStop] = value }
    suspend fun setAutoShutMinutes(value: Int) = edit { it[Keys.autoShutMinutes] = value.toString() }
    suspend fun setSleepTimerMinutes(value: Int) = edit { it[Keys.sleepTimerMinutes] = value.toString() }

    /** Sobrescribe **todos** los ajustes (importación de un fichero). */
    suspend fun applyAll(settings: Settings) = edit { p ->
        p[Keys.serviceEnabled] = settings.serviceEnabled
        p[Keys.loggedIn] = settings.loggedIn
        p[Keys.adBlockMode] = settings.adBlockMode.name
        p[Keys.adGuardMode] = settings.adGuardMode.name
        p[Keys.blockListAutoUpdate] = settings.blockListAutoUpdate
        p[Keys.guiMode] = settings.guiMode.name
        p[Keys.showNativePlayerBar] = settings.showNativePlayerBar
        p[Keys.amoled] = settings.amoled
        settings.accentColor?.let { p[Keys.accentColor] = it } ?: p.remove(Keys.accentColor)
        p[Keys.autoCloseNowPlaying] = settings.autoCloseNowPlaying
        p[Keys.batterySaver] = settings.batterySaver
        p[Keys.swipeStop] = settings.swipeStop
        p[Keys.headphonesAutoPlay] = settings.headphonesAutoPlay
        p[Keys.headphonesAutoStop] = settings.headphonesAutoStop
        p[Keys.bluetoothAutoPlay] = settings.bluetoothAutoPlay
        p[Keys.bluetoothAutoStop] = settings.bluetoothAutoStop
        p[Keys.autoShutMinutes] = settings.autoShutMinutes.toString()
        p[Keys.sleepTimerMinutes] = settings.sleepTimerMinutes.toString()
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs -> block(prefs) }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
