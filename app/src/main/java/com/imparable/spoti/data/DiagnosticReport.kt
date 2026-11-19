package com.imparable.spoti.data

import com.imparable.spoti.BuildConfig
import com.imparable.spoti.injection.InjectionComposer

/**
 * Informe de diagnóstico en TEXTO.
 *
 * Por qué existe: diagnosticar a distancia con capturas de pantalla tiene dos
 * problemas, y el segundo es el que duele.
 *
 *   1. Cada captura es una sola pregunta: hay que pedir otra para el siguiente
 *      dato. Con este informe, una sola vez trae TODO.
 *   2. Las imágenes están limitadas por conversación. El texto, no.
 *
 * Además el texto se puede citar, comparar y buscar. Una captura hay que
 * interpretarla; un informe se lee.
 *
 * El formato es deliberadamente plano y corto: una línea por dato, `clave=valor`,
 * pensado para pegarse en un chat sin que nada se corte.
 */
object DiagnosticReport {

    fun build(state: com.imparable.spoti.ui.PlayerViewModel.UiState, webViewVersion: String): String {
        val h = state.health
        val s = state.settings
        val snap = state.snapshot
        val stats = state.stats

        return buildString {
            appendLine("=== SpotiZado · informe de diagnóstico ===")
            appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("webview=$webViewVersion")
            appendLine("android=${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("dispositivo=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            appendLine("--- sesión y ajustes ---")
            appendLine("sesion=${if (s.loggedIn) "iniciada" else "no"}  servicio=${if (s.serviceEnabled) "on" else "off"}")
            appendLine("modo=${s.guiMode.name}  adblock=${s.adBlockMode.name}/${s.adGuardMode.name}")
            appendLine("barraNativa=${s.showNativePlayerBar}  unhide=${s.unhideFix}  calib=${s.calibration}  limpio=${s.cleanMode}")
            appendLine()
            appendLine("--- página ---")
            appendLine("url=${h.url ?: "-"}")
            appendLine("drm=${h.drmStatus ?: "-"}  listo=${h.ready}  play=${h.playButtonPresent}")
            appendLine("audioVideo=${h.mediaElements}  textoVisible=${h.bodyLength} car")
            appendLine("viewport=${h.viewportWidth}x${h.viewportHeight}  documento=${h.scrollWidth}")
            appendLine("escalaMaq=${h.pageScale}  escalaVisual=${h.visualScale}  dpr=${h.devicePixelRatio}")
            appendLine("layout=${h.layoutWidth}  metaViewport=${h.metaViewport ?: "-"}")
            appendLine()
            appendLine("--- login ---")
            appendLine("campos=${h.inputCount}  pwd=${h.passwordField}  visible=${h.passwordVisible}  boton=${h.submitButton}")
            appendLine()
            appendLine("--- por que no se dibuja ---")
            appendLine("nodos=${h.nodeCount}  next=${h.hasNextData}  hidratado=${h.hydrated}")
            appendLine("hojas=${h.styleSheets}  recursos=${h.resources}")
            appendLine("erroresJS=${h.jsErrors}")
            appendLine("op=${h.bodyOpacity ?: "-"}  nextOp=${h.nextOpacity ?: "-"}  anim=${h.animations ?: "-"}  cv=${h.contentVisibility ?: "-"}")
            appendLine("centro=${h.centerElement ?: "-"}  veredicto=${h.paintVerdict ?: "-"}")
            appendLine("sospechosos=${h.paintSuspects ?: "-"}")
            appendLine("js=${h.jsErrorText ?: "-"}")
            appendLine("csp=${h.cspText ?: "-"}")
            appendLine("rechazo=${h.rejectionText ?: "-"}")
            appendLine("consola=${h.consoleText ?: "-"}")
            appendLine("textoPagina=${(h.pageText ?: "").take(160)}")
            appendLine()
            appendLine("--- reproducción ---")
            appendLine("pista=${snap.track ?: "-"}")
            appendLine("artista=${snap.artist.ifBlank { "-" }}")
            appendLine("sonando=${snap.playing}  posicion=${snap.position}ms")
            appendLine("deviceId=${h.device ?: "-"}")
            appendLine()
            appendLine("--- bloqueo ---")
            appendLine("reglas=${stats.rulesLoaded}  version=${stats.rulesVersion}")
            appendLine("bloqueos=${stats.blockedSinceStart}/${stats.blockedTotal}  anunciosNeutralizados=${stats.adsNeutralized}")
            appendLine("ultimoEventoAnuncio=${state.lastAdPhase ?: "-"}")
            appendLine()
            appendLine("--- arranque del informe ---")
            append("cfg=${InjectionComposer.settings.guiMode.name}")
        }
    }

    /** Línea corta para el resumen copiable de un toque (Telegram/WhatsApp). */
    fun short(state: com.imparable.spoti.ui.PlayerViewModel.UiState, webViewVersion: String): String {
        val h = state.health
        return "v${BuildConfig.VERSION_NAME} wv=${webViewVersion.take(28)} " +
            "vw=${h.viewportWidth} sw=${h.scrollWidth} zoom=${h.visualScale} " +
            "campos=${h.inputCount} pwd=${if (h.passwordField) "si" else "no"} " +
            "vis=${if (h.passwordVisible) "si" else "no"} " +
            "modo=${state.settings.guiMode.name} " +
            "n=${h.nodeCount} nx=${if (h.hasNextData) "si" else "no"} hd=${if (h.hydrated) "si" else "no"} " +
            "err=${h.jsErrors} " +
            "url=${(h.url ?: "-").take(44)}"
    }
}
