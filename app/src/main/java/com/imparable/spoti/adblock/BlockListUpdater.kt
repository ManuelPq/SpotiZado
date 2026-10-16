package com.imparable.spoti.adblock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Actualización en tiempo de ejecución de la blocklist.
 *
 * El repo de SpotiDuck **actualiza su lista por CI** (workflow
 * `update-adblock-hosts.yml`) y luego la sirve dentro del APK: eso obliga a
 * publicar una versión nueva para cada cambio. Aquí se descarga en la app, con
 * caché en disco, control de versión (ETag) y **rollback**: si la lista nueva
 * no valida o el recuento cae, se conserva la anterior.
 *
 * La URL es configurable y **no** apunta a ningún tercero por defecto: el
 * usuario decide su fuente. Sin URL configurada, no se descarga nada.
 */
class BlockListUpdater(private val context: Context) {

    companion object {
        private const val TAG = "BlockListUpdater"
        private const val PREF_URL = "blocklist_url"
        private const val PREF_ETAG = "blocklist_etag"
        private const val PREF_VERSION = "blocklist_version"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val prefs = context.getSharedPreferences("blocklist", Context.MODE_PRIVATE)

    var sourceUrl: String?
        get() = prefs.getString(PREF_URL, null)
        set(value) = prefs.edit().putString(PREF_URL, value?.trim().orEmpty()).apply()

    val lastVersion: String?
        get() = prefs.getString(PREF_VERSION, null)

    /**
     * Descarga y aplica la lista remota si ha cambiado.
     *
     * @return resultado legible para el panel de diagnóstico.
     */
    suspend fun update(blocker: AdBlocker): UpdateResult = withContext(Dispatchers.IO) {
        val url = sourceUrl
        if (url.isNullOrBlank()) return@withContext UpdateResult.Skipped("sin URL configurada")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SpotiZado/${userAgentTag()}")
            .apply { prefs.getString(PREF_ETAG, null)?.let { header("If-None-Match", it) } }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> UpdateResult.Skipped("sin cambios")

                    !response.isSuccessful ->
                        UpdateResult.Failed("HTTP ${response.code}")

                    else -> {
                        val body = response.body?.string().orEmpty()
                        val hosts = parse(body)
                        val accepted = blocker.replaceBaseRules(hosts, "remota(${hosts.size})")
                        if (accepted < 0) {
                            UpdateResult.Failed("lista rechazada por validación (${hosts.size} entradas)")
                        } else {
                            prefs.edit()
                                .putString(PREF_ETAG, response.header("ETag"))
                                .putString(PREF_VERSION, "remota($accepted)")
                                .apply()
                            UpdateResult.Updated(accepted)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al actualizar la lista", e)
            UpdateResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * Acepta tres formatos, porque las listas públicas no son homogéneas:
     *   1. `0.0.0.0 dominio`      (formato hosts, el del fichero aportado)
     *   2. `127.0.0.1 dominio`    (formato hosts alternativo)
     *   3. `dominio`              (una lista de dominios a secas)
     * Las líneas `||dominio^` de formato AdBlock se normalizan al dominio.
     */
    private fun parse(body: String): List<String> {
        val out = ArrayList<String>(4096)
        body.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return@forEach

            when {
                line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1") ->
                    line.split(Regex("\\s+")).getOrNull(1)?.let { out.add(it) }

                line.startsWith("||") ->
                    out.add(line.removePrefix("||").substringBefore('^'))

                else -> out.add(line.substringBefore(' '))
            }
        }
        return out
    }

    // `versionName` es `String?` en el SDK moderno, así que se cae a "dev".
    private fun userAgentTag(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "dev"

    sealed interface UpdateResult {
        data class Updated(val rules: Int) : UpdateResult
        data class Skipped(val reason: String) : UpdateResult
        data class Failed(val reason: String) : UpdateResult
    }
}
