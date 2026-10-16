package com.imparable.spoti.data

/**
 * Un resultado de búsqueda obtenido del propio reproductor web.
 *
 * No se consulta ninguna API de Spotify desde la app: la búsqueda la ejecuta el
 * reproductor (con su propia sesión) y la app solo muestra y elige. Así la app
 * nunca necesita credenciales ni tokens propios.
 */
data class SearchResult(
    val uri: String,
    val name: String,
    val subtitle: String = "",
    val image: String? = null,
    val kind: String = "unknown"
) {
    /** Etiqueta legible del tipo de resultado. */
    val kindLabel: String
        get() = when (kind) {
            "track" -> "Canción"
            "album" -> "Álbum"
            "artist" -> "Artista"
            "playlist" -> "Lista"
            "episode" -> "Episodio"
            "show" -> "Podcast"
            "collection" -> "Colección"
            else -> "Elemento"
        }
}

/** Un elemento de la cola de reproducción del reproductor. */
data class QueueItem(
    val index: Int,
    val name: String,
    val subtitle: String = "",
    val image: String? = null,
    val uri: String? = null
)

/** Resultado de una operación de búsqueda o lectura de cola. */
data class FetchState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val reason: String? = null,
    val query: String = ""
) {
    val isEmptyResult: Boolean get() = !loading && items.isEmpty() && reason == null
}
