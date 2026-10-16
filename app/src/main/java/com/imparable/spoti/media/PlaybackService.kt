package com.imparable.spoti.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.imparable.spoti.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Servicio de reproducción sobre **Media3**.
 *
 * Reemplaza al servicio con `MediaSessionCompat` + `MediaBrowserServiceCompat`
 * de Spotifuck (y al equivalente de Spotilol). Lo que se gana:
 *  - La notificación la construye Media3: botones, carátula, pantalla de bloqueo
 *    y Android Auto salen del mismo estado, así que no pueden desincronizarse.
 *  - `onGetSession` sirve a Android Auto y al widget sin código adicional.
 *  - Sin `MediaBrowserCompat` ni `MediaBrowserServiceCompat` (en desuso).
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var player: JsPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        val graph = com.imparable.spoti.SpotiApp.instance
        val jsPlayer = JsPlayer(object : JsPlayer.CommandSink {
            override fun send(method: String, paramsJson: String) {
                // El WebView vive en PlayerHost (lo crea la actividad o el propio
                // servicio al arrancar en segundo plano).
                PlayerHost.sendCommand(method, paramsJson)
            }
        })
        player = jsPlayer
        PlayerHost.attachPlayer(jsPlayer)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builtSession = MediaSession.Builder(this, jsPlayer)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: androidx.media3.session.SessionCommand,
                    args: android.os.Bundle
                ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_TOGGLE_FAVORITE -> PlayerHost.sendCommand("favorite")
                        ACTION_TOGGLE_REPEAT -> PlayerHost.sendCommand("repeat")
                    }
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        androidx.media3.session.SessionResult(RESULT_SUCCESS)
                    )
                }
            })
            .build()

        session = builtSession

        // Mantener la fachada del servicio en sincronía con los ajustes.
        scope.launch {
            val settings = graph.settings.settings.first()
            PlayerHost.onServiceStarted(settings.serviceEnabled)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Al cerrar la app desde recientes: si el usuario ha activado «detener al
     * deslizar», se para el servicio; si no, se mantiene la reproducción.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val graph = com.imparable.spoti.SpotiApp.instance
        scope.launch {
            val settings = graph.settings.settings.first()
            if (settings.swipeStop) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run {
            player?.release()
            release()
        }
        session = null
        player = null
        PlayerHost.detachPlayer()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "com.imparable.spoti.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_REPEAT = "com.imparable.spoti.TOGGLE_REPEAT"
        private const val RESULT_SUCCESS = 0
    }
}
