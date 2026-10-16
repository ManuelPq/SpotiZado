package com.imparable.spoti

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.imparable.spoti.media.PlayerHost
import com.imparable.spoti.ui.PlayerScreen
import com.imparable.spoti.ui.PlayerViewModel
import com.imparable.spoti.ui.theme.SpotiImparableTheme
import com.imparable.spoti.web.PlayerWebViewFactory
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Pantalla principal: aloja el reproductor y la UI de control.
 *
 * El WebView se crea una sola vez y vive en [PlayerHost], de modo que sobrevive
 * a los cambios de configuración sin recargar el reproductor (recargar costaba
 * ~10 s y perdía la reproducción en Spotifuck).
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val graph = SpotiApp.instance
        requestNotificationPermissionIfNeeded()
        suggestBatteryOptimizationExemption()

        setContent {
            val vm: PlayerViewModel = viewModel()
            val state by vm.ui.collectAsState()

            SpotiImparableTheme(
                darkTheme = true,
                amoled = state.settings.amoled
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerScreen(
                        state = state,
                        viewModel = vm,
                        playerSlot = { PlayerWebViewSlot() }
                    )
                }
            }
        }
    }

    /* --- permisos y batería --- */

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Sin excepción de batería, Android mata el WebView en segundo plano y la
     * reproducción se corta: es la causa nº 1 de quejas en SpotiDuck.
     */
    private fun suggestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as? android.os.PowerManager ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:$packageName"))
            )
        }
    }
}

/**
 * Hueco del WebView. `AndroidView` mantiene la instancia: si el reproductor ya
 * existe en [PlayerHost], se reutiliza en lugar de recrearse.
 */
@Composable
private fun PlayerWebViewSlot() {
    val context = androidx.compose.ui.platform.LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val bridge = PlayerHost.bridge ?: buildBridge(ctx)
            val existing = PlayerHost.webView
            val view = existing ?: PlayerWebViewFactory.create(ctx, bridge, desktopMode = true)
            if (view.parent != null) {
                (view.parent as? ViewGroup)?.removeView(view)
            }
            PlayerHost.attachWebView(view, bridge)

            // La capa 1 del bloqueo se conecta aquí.
            (view.webViewClient as? com.imparable.spoti.web.PlayerWebViewClient)?.adBlocker =
                SpotiApp.instance.adBlocker

            if (existing == null) {
                PlayerWebViewFactory.injectBoot(view, bridge.composer)
                // El perfil (UA y escala) se fija ANTES de cargar: el User-Agent
                // viaja con la petición del documento, así que cambiarlo después
                // llega tarde y la página ya se ha maquetado con el equivocado.
                val target = if (bridge.isLoggedIn()) {
                    com.imparable.spoti.web.PlayerWebViewClient.URL_PLAYER
                } else {
                    com.imparable.spoti.web.PlayerWebViewClient.URL_LOGIN
                }
                PlayerWebViewFactory.applyProfile(
                    view,
                    desktop = target.startsWith(com.imparable.spoti.web.PlayerWebViewClient.PREFIX_PLAYER)
                )
                view.loadUrl(target)
            }
            view
        }
    )
}

/** Crea el puente si aún no existe (primer arranque de la app). */
private fun buildBridge(context: android.content.Context): com.imparable.spoti.bridge.PlayerBridge {
    val scope = MainScope()
    return com.imparable.spoti.bridge.PlayerBridge(
        context = context.applicationContext,
        scope = scope,
        onSnapshot = { snap -> PlayerHost.publishSnapshot(snap) },
        onAdEvent = { event -> PlayerHost.publishAdEvent(event) },
        onHealth = { h ->
            PlayerHost.publishHealth(h)

            // Detección de sesión reforzada. El heurístico del botón
            // `web-player-link` (heredado del APK original) puede no dispararse;
            // cuando la sonda ve la interfaz real del reproductor en
            // open.spotify.com, la sesión está iniciada y se persiste.
            val liveBridge = PlayerHost.bridge
            val liveView = PlayerHost.webView
            if (liveBridge != null && liveView != null && !liveBridge.isLoggedIn() &&
                h.playButtonPresent && h.url?.contains("open.spotify.com") == true
            ) {
                liveBridge.setLoggedIn(true)
                scope.launch { runCatching { SpotiApp.instance.settings.setLoggedIn(true) } }
                liveView.post { liveBridge.onPlayerPageReady(liveView) }
            }
        },
        onSearchResults = { query, ok, reason, results ->
            PlayerHost.publishSearch(query, ok, reason, results)
        },
        onQueue = { ok, reason, items -> PlayerHost.publishQueue(ok, reason, items) },
        onLoggedIn = {
            scope.launch { SpotiApp.instance.settings.setLoggedIn(true) }
        },
        onNeedsReload = { reason ->
            val view = PlayerHost.webView
            view?.post { view.reload() }
            PlayerHost.publishReload(reason)
        }
    )
}
