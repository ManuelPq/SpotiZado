package com.imparable.spoti.timer

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.imparable.spoti.SpotiApp
import com.imparable.spoti.media.PlayerHost
import kotlinx.coroutines.launch

/**
 * Temporizador de reproducción y apagado automático por inactividad.
 *
 * **Semántica** (la misma que en el Spotifuck original, aquí explícita):
 *  - *Temporizador de reproducción*: cuenta mientras **hay reproducción**.
 *    Al vencer, pausa. Si el usuario pausa antes, se cancela.
 *  - *Apagado automático*: cuenta mientras **no hay reproducción** (es decir,
 *    inactividad). Al vencer, detiene el servicio y limpia el WebView.
 *
 * Se implementa con `Handler` (como el original), que en el hilo principal es
 * lo apropiado para una tarea de este tipo: es barato, se rearma en cada cambio
 * de estado y no necesita `AlarmManager` (que despertaría el dispositivo).
 *
 * La cuenta se lleva por *marca de tiempo* en lugar de por contador de mensajes:
 * si el sistema retrasa el `Runnable`, el tiempo restante sigue siendo correcto.
 */
object PlayerTimers {

    private const val TAG = "PlayerTimers"

    private val handler = Handler(Looper.getMainLooper())

    private var sleepTask: Runnable? = null
    private var shutTask: Runnable? = null

    @Volatile
    var sleepDeadline: Long = 0L
        private set

    @Volatile
    var shutDeadline: Long = 0L
        private set

    /** Milisegundos restantes del temporizador de reproducción (0 = inactivo). */
    fun sleepRemainingMs(): Long = remaining(sleepDeadline)

    /** Milisegundos restantes del apagado automático (0 = inactivo). */
    fun shutRemainingMs(): Long = remaining(shutDeadline)

    private fun remaining(deadline: Long): Long =
        if (deadline <= 0L) 0L else (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    /**
     * Reajusta los temporizadores según el estado actual. Se llama en cada
     * cambio de estado de reproducción; es idempotente.
     */
    fun sync(playing: Boolean) {
        val settings = SpotiApp.instance.settingsSnapshot
        val serviceUp = PlayerHost.serviceRunning

        syncSleep(playing, settings.sleepTimerMinutes, serviceUp)
        syncShut(playing, settings.autoShutMinutes, serviceUp)
    }

    private fun syncSleep(playing: Boolean, minutes: Int, serviceUp: Boolean) {
        val shouldRun = serviceUp && playing && minutes > 0
        if (!shouldRun) {
            cancelSleep()
            return
        }
        if (sleepTask != null) return   // ya está en marcha: no se reinicia

        val delay = minutes * 60_000L
        sleepDeadline = SystemClock.elapsedRealtime() + delay
        val task = Runnable {
            sleepTask = null
            sleepDeadline = 0L
            Log.i(TAG, "Temporizador de reproducción vencido: pausando")
            onSleepFired()
        }
        sleepTask = task
        handler.postDelayed(task, delay)
    }

    private fun syncShut(playing: Boolean, minutes: Int, serviceUp: Boolean) {
        // Cuenta la inactividad: solo mientras NO se reproduce.
        val shouldRun = serviceUp && !playing && minutes > 0
        if (!shouldRun) {
            cancelShut()
            return
        }
        if (shutTask != null) return

        val delay = minutes * 60_000L
        shutDeadline = SystemClock.elapsedRealtime() + delay
        val task = Runnable {
            shutTask = null
            shutDeadline = 0L
            Log.i(TAG, "Apagado automático vencido: deteniendo el servicio")
            onShutFired()
        }
        shutTask = task
        handler.postDelayed(task, delay)
    }

    /** Cancela ambos temporizadores (al detener el servicio, por ejemplo). */
    fun cancelAll() {
        cancelSleep()
        cancelShut()
    }

    private fun cancelSleep() {
        sleepTask?.let { handler.removeCallbacks(it) }
        sleepTask = null
        sleepDeadline = 0L
    }

    private fun cancelShut() {
        shutTask?.let { handler.removeCallbacks(it) }
        shutTask = null
        shutDeadline = 0L
    }

    /* ------------------------------------------------------------------ */

    private fun onSleepFired() {
        PlayerHost.sendCommand("pause")
        // El temporizador es de un solo uso: al vencer se desactiva, como en el
        // Spotifuck original, para que no vuelva a dispararse sin querer.
        SpotiApp.instance.appScope.launch {
            runCatching { SpotiApp.instance.settings.setSleepTimerMinutes(0) }
        }
    }

    private fun onShutFired() {
        PlayerHost.sendCommand("pause")
        SpotiApp.instance.appScope.launch {
            runCatching { SpotiApp.instance.settings.setAutoShutMinutes(0) }
        }
        SpotiApp.instance.stopPlaybackService()
        PlayerHost.onServiceStarted(false)
        cancelAll()
    }
}
