package com.imparable.spoti.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import com.imparable.spoti.SpotiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Auriculares y Bluetooth.
 *
 * Unifica lo que en Spotifuck eran **dos** implementaciones paralelas (un
 * `BroadcastReceiver` para API &lt; 26 y un `AudioDeviceCallback` para API ≥ 26,
 * con listas de tipos de dispositivo duplicadas). Aquí hay una sola clase con un
 * único conjunto de reglas, y los ajustes se leen de DataStore en el momento.
 */
class MediaActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                applyHeadphoneRules(context, plugged = state == 1)
            }

            BluetoothDeviceMonitor.ACTION_BLUETOOTH_STATE -> {
                val state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1)
                applyBluetoothRules(context, connected = state == 2)
            }
        }
    }

    private fun applyHeadphoneRules(context: Context, plugged: Boolean) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val s = SpotiApp.instance.settings.settings.first()
            if (plugged && s.headphonesAutoPlay) PlayerHost.sendCommand("play")
            else if (!plugged && s.headphonesAutoStop) PlayerHost.sendCommand("pause")
        }
    }

    private fun applyBluetoothRules(context: Context, connected: Boolean) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val s = SpotiApp.instance.settings.settings.first()
            if (connected && s.bluetoothAutoPlay) PlayerHost.sendCommand("play")
            else if (!connected && s.bluetoothAutoStop) PlayerHost.sendCommand("pause")
        }
    }
}

/**
 * Monitor de dispositivos de audio para API 26+.
 *
 * Sustituye al `registerReceiver`/`unregisterReceiver` de auriculares, que en
 * Android 14+ ya no recibe el broadcast `ACTION_HEADSET_PLUG` de forma fiable.
 */
@UnstableApi
class BluetoothDeviceMonitor(context: Context) {

    companion object {
        const val ACTION_BLUETOOTH_STATE =
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"

        /** Tipos que cuentan como auriculares con cable. */
        private val WIRED = intArrayOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )

        /** Tipos inalámbricos. */
        private val WIRELESS = intArrayOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER
        )
    }

    private var callback: AudioDeviceCallback? = null

    // Nulo si el servicio no existe: se degrada en lugar de fallar al construir.
    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun start() {
        if (callback != null) return
        val manager = audioManager ?: return
        val scope = CoroutineScope(Dispatchers.Main)
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                scope.launch {
                    val s = SpotiApp.instance.settings.settings.first()
                    if (addedDevices.any { it.type in WIRED } && s.headphonesAutoPlay) {
                        PlayerHost.sendCommand("play")
                    } else if (addedDevices.any { it.type in WIRELESS } && s.bluetoothAutoPlay) {
                        PlayerHost.sendCommand("play")
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                scope.launch {
                    val s = SpotiApp.instance.settings.settings.first()
                    if (removedDevices.any { it.type in WIRED } && s.headphonesAutoStop) {
                        PlayerHost.sendCommand("pause")
                    } else if (removedDevices.any { it.type in WIRELESS } && s.bluetoothAutoStop) {
                        PlayerHost.sendCommand("pause")
                    }
                }
            }
        }
        manager.registerAudioDeviceCallback(cb, Handler(android.os.Looper.getMainLooper()))
        callback = cb
    }

    fun stop() {
        callback?.let { audioManager?.unregisterAudioDeviceCallback(it) }
        callback = null
    }

    private fun IntArray.contains(type: Int) = any { it == type }
}
