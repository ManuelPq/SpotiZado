package com.imparable.spoti.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.imparable.spoti.MainActivity
import com.imparable.spoti.R
import com.imparable.spoti.media.PlayerHost

/**
 * Receptor de enlaces de Spotify (`spotify:*` y `open.spotify.com/...`).
 *
 * A diferencia de `LinkHandler` de Spotifuck, aquí no se asume que el
 * reproductor esté listo: si no lo está, se avisa **y se abre la app principal**
 * (antes solo se mostraba un aviso y el enlace se perdía).
 */
class LinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data?.let { normalize(it) }
        val delivered = uri != null
        if (uri != null) {
            PlayerHost.sendCommand("playUri", """{"uri":${org.json.JSONObject.quote(uri)}}""")
        }

        if (!delivered) {
            Toast.makeText(this, R.string.link_not_ready, Toast.LENGTH_LONG).show()
        }

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
        overridePendingTransition(0, 0)
    }

    /**
     * Normaliza la URI a la forma `spotify:tipo:id` que entiende el reproductor:
     *  - `spotify:...` se acepta tal cual
     *  - `https://open.spotify.com/tipo/id` se convierte, descartando un posible
     *    prefijo de idioma (`intl-es`)
     */
    private fun normalize(data: Uri): String? {
        if (data.scheme == "spotify") return data.toString()
        if (data.scheme != "https" || data.host != "open.spotify.com") return null

        var segments = data.pathSegments
        if (segments.isNotEmpty() && segments.first().startsWith("intl-")) {
            segments = segments.drop(1)
        }
        if (segments.size < 2) return null
        return segments.joinToString(separator = ":", prefix = "spotify:")
    }
}
