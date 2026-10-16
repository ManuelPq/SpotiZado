package com.imparable.spoti

import android.app.Application
import android.content.Intent
import com.imparable.spoti.adblock.AdBlocker
import com.imparable.spoti.adblock.BlockListUpdater
import com.imparable.spoti.data.Settings
import com.imparable.spoti.data.SettingsRepository
import com.imparable.spoti.injection.InjectionComposer
import com.imparable.spoti.media.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Application de SpotiZado: contenedor de dependencias y punto donde se
 * mantiene sincronizado el estado global (ajustes → motor de bloqueo, ajustes →
 * compositor de JavaScript).
 */
class SpotiApp : Application() {

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val adBlocker: AdBlocker by lazy { AdBlocker(this) }
    val blockListUpdater: BlockListUpdater by lazy { BlockListUpdater(this) }

    /** Ámbito de larga duración para trabajo de la app (no de una pantalla). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Última instantánea de los ajustes.
     *
     * Existe porque los temporizadores necesitan leer los ajustes **de forma
     * síncrona** desde un callback del `Handler`, donde no se puede esperar a un
     * `Flow`. Se actualiza en el mismo colector que el resto del estado.
     */
    @Volatile
    var settingsSnapshot: Settings = Settings()
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        appScope.launch {
            adBlocker.load()
            settings.settings.collectLatest { s ->
                settingsSnapshot = s
                adBlocker.mode = s.adBlockMode
                InjectionComposer.settings = s
                if (s.blockListAutoUpdate) {
                    runCatching { blockListUpdater.update(adBlocker) }
                }
            }
        }
    }

    /** Detiene el servicio de reproducción (lo usa el apagado automático). */
    fun stopPlaybackService() {
        runCatching { stopService(Intent(this, PlaybackService::class.java)) }
    }

    /** Inicia el servicio de reproducción en primer plano. */
    fun startPlaybackService() {
        val intent = Intent(this, PlaybackService::class.java)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    companion object {
        lateinit var instance: SpotiApp
            private set
    }
}
