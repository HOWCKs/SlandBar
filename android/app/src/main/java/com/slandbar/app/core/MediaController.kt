package com.slandbar.app.core

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estado de mídia observado pelo widget de música do painel. */
data class MediaState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val albumArtUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Controlador de mídia global: encontra as sessões de mídia ativas do sistema
 * (Spotify, YouTube Music, players locais…) e permite controlá-las a partir
 * do painel flutuante — sem precisar abrir o app do player.
 */
class MediaController(private val context: Context) {

    private val manager = context.getSystemService(MediaSessionManager::class.java)

    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state

    private var current: android.media.session.MediaController? = null
    private var listening = false

    private val callback = object : android.media.session.MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publish()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        rescan()
    }

    fun start() {
        if (listening) return
        listening = true
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, null)
        } catch (_: SecurityException) {
            // Sem acesso a sessões novas; o rescan periódico cobre isso.
        }
        rescan()
    }

    fun stop() {
        if (!listening) return
        listening = false
        try {
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        current?.unregisterCallback(callback)
        current = null
        _state.value = MediaState()
    }

    fun rescan() {
        val sessions = try {
            manager.getActiveSessions(null)
        } catch (_: SecurityException) {
            emptyList()
        }
        val controller = sessions.firstOrNull()
        if (controller === current) {
            publish()
            return
        }
        current?.unregisterCallback(callback)
        current = controller
        controller?.registerCallback(callback)
        publish()
    }

    fun playPause() {
        val c = current ?: return
        if (_state.value.playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = current?.transportControls?.skipToNext()
    fun previous() = current?.transportControls?.skipToPrevious()
    fun seekTo(ms: Long) = current?.transportControls?.seekTo(ms)

    private fun publish() {
        val c = current ?: run {
            _state.value = MediaState()
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        _state.value = MediaState(
            active = true,
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            albumArtUri = md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            positionMs = ps?.position ?: 0L,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        )
    }
}
