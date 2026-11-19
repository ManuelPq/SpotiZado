package com.imparable.spoti.adblock

/**
 * Almacén de **identificadores de audio publicitario**.
 *
 * POR QUÉ POR ID Y NO POR HOST
 * ----------------------------
 * Insight de SpotiDuck (asset `ad_state_hook.js`, incluido en su APK): el audio de
 * los anuncios se sirve desde **los mismos hosts CDN** que la música. Por URL no
 * se pueden distinguir. Y bloquear el host se lleva por delante la música.
 *
 * Pero el estado del reproductor sí lo distingue: los anuncios llegan como pistas
 * con `content_type: 'AD'` dentro del `state_machine`, y cada una lleva sus
 * `file_ids` de audio. Si se conocen esos IDs, se puede interceptar **esa
 * petición concreta** en `shouldInterceptRequest` sin tocar nada más.
 *
 * Flujo completo:
 *   1. `25_adstate.js` envuelve `fetch` y `WebSocket` y lee los `state_machine`.
 *   2. Cuando ve una pista marcada como anuncio, extrae sus `file_ids` y los envía
 *      al nativo con `spotiNotify('adids', ...)`.
 *   3. Aquí se guardan (cola acotada, los más recientes).
 *   4. `PlayerWebViewClient.shouldInterceptRequest` consulta [matches] y responde
 *      204 a esa URL y solo a esa.
 *
 * Diferencias con el enfoque anterior (bloquear hosts de anuncios): aquello dejaba
 * anuncios mudos y a veces rompía la reproducción. Esto elimina el audio del
 * anuncio concreto, sin tocar dominios de contenido.
 */
object AdIdStore {

    /** Cuántos IDs se recuerdan. Spotify usa pocos anuncios por sesión. */
    private const val MAX_IDS = 64

    private val ids = LinkedHashSet<String>()

    @Volatile
    var received: Int = 0
        private set

    @Volatile
    var hits: Int = 0
        private set

    /** Registra una tanda de IDs venida del JS. Ignora valores no plausibles. */
    @Synchronized
    fun record(list: List<String>) {
        for (raw in list) {
            val id = raw.trim()
            if (id.length !in 8..128) continue
            if (!id.all { it.isLetterOrDigit() || it == '_' || it == '-' }) continue
            ids.add(id)
            received++
        }
        while (ids.size > MAX_IDS) {
            val first = ids.first()
            ids.remove(first)
        }
    }

    /**
     * ¿La URL de esta petición corresponde a un audio de anuncio conocido?
     *
     * Se compara por **segmento de ruta**: los `file_id` viajan dentro de la URL
     * del CDN, así que basta comprobar que la URL contiene el ID como trozo de
     * ruta (no como subcadena suelta, para no confundirlo con otra cosa).
     */
    @Synchronized
    fun matches(url: String?): Boolean {
        if (url == null || ids.isEmpty()) return false
        val ok = ids.any { url.contains(it) }
        if (ok) hits++
        return ok
    }

    @Synchronized
    fun snapshot(): List<String> = ids.toList()

    @Synchronized
    fun clear() {
        ids.clear()
        received = 0
        hits = 0
    }
}
