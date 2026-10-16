package com.imparable.spoti.adblock

import android.content.Context
import android.util.Log
import com.imparable.spoti.data.AdBlockMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Capa 1 del bloqueo: filtrado de red por host.
 *
 * Diseño (aprendido de los tres proyectos):
 *  - **Nunca** se bloquean dominios de contenido: `AllowList` tiene prioridad
 *    absoluta sobre cualquier regla, incluidas las descargadas. Es la defensa
 *    contra el fallo que SpotiDuck documenta («una blocklist agresiva bloquea
 *    endpoints esenciales y las pistas no suenan»).
 *  - Los dominios publicitarios de Spotify se activan **solo** en modo STRICT y
 *    van en su propia lista, para que el modo TRACKERS sea 100 % seguro.
 *  - Emparejamiento por host exacto y por sufijo de dominio, con un `HashSet`
 *    (O(1)) en lugar de un recorrido lineal: en un WebView que lanza decenas de
 *    peticiones por segundo, la diferencia se nota.
 *  - Contador de bloqueos por hilo: el panel de diagnóstico muestra lo que hace.
 */
class AdBlocker(private val context: Context) {

    companion object {
        private const val TAG = "AdBlocker"
        const val ASSET_BASE = "blocklist/base.txt"
        const val ASSET_SPOTIFY_ADS = "blocklist/spotify_ads.txt"
        const val ASSET_ALLOW = "blocklist/allow.txt"

        /**
         * Mínimo de reglas que debe traer una lista remota para ser aceptada.
         * Una lista más corta suele indicar descarga corrupta o manipulada.
         */
        const val MIN_REMOTE_RULES = 100
    }

    private val exactRules = HashSet<String>(4096)
    private val suffixRules = HashSet<String>(2048)
    private val allowExact = HashSet<String>(128)
    private val allowSuffix = HashSet<String>(128)

    private val counter = AtomicLong(0)
    private val sessionCounter = AtomicLong(0)

    @Volatile
    var mode: AdBlockMode = AdBlockMode.STRICT
        set(value) {
            field = value
            rebuildActiveRules()
        }

    @Volatile
    var version: String = "—"
        private set

    @Volatile
    private var baseRules: List<String> = emptyList()
    private var spotifyAdRules: List<String> = emptyList()

    val blockedTotal: Long get() = counter.get()
    val blockedSinceStart: Long get() = sessionCounter.get()
    val ruleCount: Int get() = exactRules.size + suffixRules.size

    /** Carga las listas incluidas en los assets. Idempotente. */
    suspend fun load() = withContext(Dispatchers.IO) {
        if (baseRules.isNotEmpty()) return@withContext

        allowExact.clear()
        allowSuffix.clear()
        readAsset(ASSET_ALLOW).forEach { host ->
            if (host.count { it == '.' } <= 2) allowExact.add(host) else allowSuffix.add(host)
        }

        baseRules = readAsset(ASSET_BASE)
        // La lista de anuncios de Spotify contiene los endpoints publicitarios;
        // se excluye del modo TRACKERS para que ese modo nunca toque a Spotify.
        spotifyAdRules = readAsset(ASSET_SPOTIFY_ADS)
        version = "assets(${baseRules.size + spotifyAdRules.size})"
        rebuildActiveRules()
        Log.i(TAG, "Listas cargadas: base=${baseRules.size} spotifyAds=${spotifyAdRules.size} allow=${allowExact.size + allowSuffix.size}")
    }

    /**
     * Sustituye la lista base por una descargada en tiempo de ejecución
     * (idea tomada del workflow de CI de SpotiDuck, pero en la app: así el
     * bloqueo se actualiza sin publicar un APK nuevo).
     *
     * @return número de reglas aceptadas, o -1 si la lista se rechazó.
     */
    suspend fun replaceBaseRules(hosts: List<String>, versionTag: String): Int =
        withContext(Dispatchers.Default) {
            // Validación: una lista demasiado pequeña o con dominios críticos es
            // sospechosa (descarga corrupta o lista maliciosa) y se rechaza.
            val clean = hosts
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && HOST_REGEX.matches(it) }
                .distinct()

            if (clean.size < MIN_REMOTE_RULES) {
                Log.w(TAG, "Lista remota rechazada: solo ${clean.size} reglas válidas")
                return@withContext -1
            }
            val conflicting = clean.filter { isAllowed(it) }
            if (conflicting.isNotEmpty()) {
                Log.w(TAG, "Lista remota contiene ${conflicting.size} dominios críticos: se descartan")
            }

            baseRules = clean.filterNot { isAllowed(it) }
            version = versionTag
            rebuildActiveRules()
            baseRules.size
        }

    /** ¿Se debe bloquear este host? */
    fun shouldBlock(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        if (mode == AdBlockMode.OFF) return false

        val h = host.lowercase()
        if (mode == AdBlockMode.TRACKERS && isSpotifyAdHost(h)) return false
        if (isAllowed(h)) return false

        val blocked = match(h)
        if (blocked) {
            counter.incrementAndGet()
            sessionCounter.incrementAndGet()
        }
        return blocked
    }

    fun resetSession() = sessionCounter.set(0)

    /* ------------------------------------------------------------------ */

    private fun rebuildActiveRules() {
        exactRules.clear()
        suffixRules.clear()
        val active = when (mode) {
            AdBlockMode.OFF -> emptyList()
            AdBlockMode.TRACKERS -> baseRules
            AdBlockMode.STRICT -> baseRules + spotifyAdRules
        }
        active.forEach { host ->
            if (host.count { it == '.' } <= 2) exactRules.add(host) else suffixRules.add(host)
        }
    }

    /**
     * Empareja por host exacto o por sufijo de subdominio (`a.b.cdn.com`
     * coincide con la regla `cdn.com`), que es como funcionan las listas de
     * hosts. No se usan comodines ni expresiones regulares: son lentas y aquí
     * no aportan nada.
     */
    private fun match(host: String): Boolean {
        if (exactRules.contains(host)) return true
        var idx = host.indexOf('.')
        while (idx >= 0) {
            if (exactRules.contains(host.substring(idx + 1))) return true
            idx = host.indexOf('.', idx + 1)
        }
        // Sufijos de más de dos etiquetas se comprueban aparte.
        var i = 0
        while (i < host.length) {
            if (suffixRules.contains(host.substring(i))) return true
            val next = host.indexOf('.', i)
            if (next < 0) break
            i = next + 1
        }
        return false
    }

    private fun isAllowed(host: String): Boolean {
        if (allowExact.contains(host)) return true
        var idx = host.indexOf('.')
        while (idx >= 0) {
            if (allowExact.contains(host.substring(idx + 1))) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }

    private fun isSpotifyAdHost(host: String): Boolean =
        host.endsWith(".spotify.com") || host.endsWith(".scdn.co")

    private fun readAsset(name: String): List<String> = runCatching {
        context.assets.open(name).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.lowercase() }
                .toList()
        }
    }.getOrElse {
        Log.e(TAG, "No se pudo leer $name", it)
        emptyList()
    }

    private val HOST_REGEX = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")
}
