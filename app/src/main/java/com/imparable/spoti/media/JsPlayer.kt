package com.imparable.spoti.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.imparable.spoti.data.PlayerSnapshot

/**
 * Reproductor Media3 que **no decodifica audio**: refleja el estado del
 * reproductor web y reenvía las órdenes al JavaScript.
 *
 * Es la forma correcta de integrar una fuente externa en Media3:
 * `SimpleBasePlayer` existe justamente para esto, y sustituye al
 * `MediaSessionCompat` + `PlaybackStateCompat` manual que usaban Spotifuck y
 * Spotilol (donde había que construir la notificación y el estado a mano).
 *
 * Contrapartida honesta: al no haber `ExoPlayer`, no hay posición interpolada ni
 * búsqueda real. La posición la publica el propio reproductor web cada 2 s, así
 * que la barra de la notificación avanza a saltos.
 */
@UnstableApi
class JsPlayer(
    private val commandSink: CommandSink
) : SimpleBasePlayer(Looper.getMainLooper()) {

    /** Vía por la que el reproductor envía órdenes al WebView. */
    interface CommandSink {
        fun send(method: String, paramsJson: String = "{}")
    }

    private var snapshot: PlayerSnapshot = PlayerSnapshot(track = null)

    /** Posición conocida, para no perderla entre publicaciones del JS. */
    private var knownPositionMs: Long = 0

    private val mediaItem: MediaItem
        get() {
            val s = snapshot
            return MediaItem.Builder()
                .setMediaId(s.track ?: "spoti-imparable")
                .setUri(s.cover)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.track)
                        .setArtist(s.artist.ifBlank { null })
                        .setAlbumTitle(s.album.ifBlank { null })
                        .setArtworkUri(s.cover?.let { android.net.Uri.parse(it) })
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }

    /**
     * Media3 no acepta un `MediaItem` suelto en el estado: la lista es de
     * `MediaItemData`, que es donde vive la duración. La posición la publica el
     * reproductor web cada 2 s, así que la barra avanza a saltos (limitación
     * honesta de no usar ExoPlayer).
     */
    private val mediaItemData: MediaItemData
        get() {
            val s = snapshot
            return MediaItemData.Builder("spoti-imparable")
                .setMediaItem(mediaItem)
                .setDurationUs(if (s.duration > 0L) s.duration * 1000L else C.TIME_UNSET)
                .setIsSeekable(true)
                .build()
        }

    /** Actualiza el estado desde el puente JS y notifica a Media3. */
    fun updateSnapshot(newSnapshot: PlayerSnapshot) {
        snapshot = newSnapshot
        knownPositionMs = newSnapshot.position
        invalidateState()
    }

    override fun getState(): State {
        val s = snapshot
        val playbackState =
            if (s.isEmpty && !s.playing) Player.STATE_IDLE else Player.STATE_READY
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAllCommands()
                    .build()
            )
            .setPlaylist(listOf(mediaItemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(knownPositionMs)
            .setPlayWhenReady(s.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setRepeatMode(
                when (s.repeatIcon) {
                    PlayerSnapshot.RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    PlayerSnapshot.RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    PlayerSnapshot.RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                }
            )
            .setShuffleModeEnabled(s.shuffle)
            .setIsLoading(s.isEmpty)
            .build()
    }

    /* --- órdenes del sistema -> JavaScript --- */

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        commandSink.send(if (playWhenReady) "play" else "pause")
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        knownPositionMs = positionMs
        commandSink.send("seekTo", """{"position":$positionMs}""")
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        commandSink.send("repeat")   // el reproductor web cicla off -> all -> one
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        commandSink.send("shuffle")
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        mediaItems.getOrNull(startIndex)?.mediaId?.let { uri ->
            commandSink.send("playUri", """{"uri":${org.json.JSONObject.quote(uri)}}""")
        }
        return Futures.immediateVoidFuture()
    }
}
