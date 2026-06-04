package ch.alpmann.flacblaster.playback

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import ch.alpmann.flacblaster.fs.DatabaseSingleton
import ch.alpmann.flacblaster.fs.FileEntity
import ch.alpmann.flacblaster.superutil.SuperService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.fixedRateTimer

@OptIn(UnstableApi::class)
class MusicPlayerService : SuperService() {
    companion object {
        private const val NOTIFICATION_ID = 1337
        private const val NOTIFICATION_CHANNEL_ID = "ch.alpmann.flacblaster.playback"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /// Note: ExoPlayer may only be accessed from the Main Thread, i.e. not serviceScope
    private var player: ExoPlayer? = null
    private val downmixer = DownmixAudioProcessor()
    // MediaSession exposes the player to the system (lock screen, Bluetooth, Android Auto).
    private var mediaSession: MediaSession? = null
    // PlayerNotificationManager builds and updates the media notification automatically.
    private var notificationManager: PlayerNotificationManager? = null

    fun play(f: FileEntity) {
        if (player == null) return;
        serviceScope.launch {
            val db = DatabaseSingleton.get(this@MusicPlayerService)
            db.fileEntityDao().setSelection(f.path)
        }
        player!!.setMediaItem(MediaItem.fromUri(f.getUri()))
        // TODO: Re-add podcast check
        //if (f.isPodcast) {
            player!!.seekTo(f.lastResumeMs)
        //}
        player!!.prepare()
        player!!.play()
    }

    fun isPlaying(): Boolean = player?.isPlaying ?: false
    /** Resume playback, if applicable */
    fun play() = player?.play()
    /** Pause playback, if applicable */
    fun pause() = player?.pause()
    /** The ExoPlayer may only be accessed from the main thread. Use this to access it via Main Thread when inside a serviceScope */
    private suspend fun accessPlayer(lambda: suspend CoroutineScope.() -> Unit) = withContext(Dispatchers.Main, lambda)
    /** This runs the lambda in a thread/scope where DB access is permitted */
    private suspend fun accessDatabase(lambda: suspend CoroutineScope.() -> Unit) = withContext(Dispatchers.IO, lambda)

    override fun onDestroy() {
        notificationManager?.setPlayer(null)
        mediaSession?.release()
        player?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("MusicPlayerService", "onCreate!!!!!!!!!!!!!!!!!1")

        // This boilerplate is necessary in order to allow use of our custom AudioProcessor
        val renderersFactory: DefaultRenderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<AudioProcessor>(downmixer))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .build()

        player!!.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),  /* handleAudioFocus= */
            true
        )

        fixedRateTimer("save podcast resume time", daemon = true, period = 5000L) {
            serviceScope.launch {
                accessPlayer {
                    if (player == null) return@accessPlayer
                    if (!player!!.isPlaying) return@accessPlayer
                    val pos = player!!.currentPosition
                    accessDatabase {
                        val db = DatabaseSingleton.get(this@MusicPlayerService).fileEntityDao()
                        val sel = db.getSelection() ?: return@accessDatabase

                        // TODO: Remove hack. We assume that all media longer than 10min is a podcast
                        //if (!sel.isPodcast) return@accessDatabase
                        if (sel.durationMs < 10*60*1000) return@accessDatabase

                        sel.lastResumeMs = pos
                        db.upsert(sel)
                    }
                }
            }
        }

        player!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("MusicPlayerService", "Player got signal: $playbackState")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("MusicPlayerService", "Player is playing: $isPlaying")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicPlayerService", error.toString())
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                Log.d("MusicPlayerService", "onPositionDiscontinuity")
            }
        })

        // MediaSession connects the player to the Android media system (lock screen, Bluetooth, etc.)
        mediaSession = MediaSession.Builder(this, player!!).build()

        // Notification channel is required on Android 8+. Creating it repeatedly is a no-op.
        NotificationChannel(NOTIFICATION_CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }

        // PlayerNotificationManager builds the media notification and keeps it in sync with the
        // player state. Next/previous are forwarded to the Player, which currently does nothing.
        notificationManager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, NOTIFICATION_CHANNEL_ID)
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                @SuppressLint("ForegroundServiceType")
                override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                    Log.d("MusicPlayerService", "Notification posted, ongoing=$ongoing")
                    if (ongoing) startForeground(notificationId, notification)
                    else stopForeground(false)
                }
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    Log.d("MusicPlayerService", "Notification cancelled, dismissedByUser=$dismissedByUser")
                    stopForeground(true)
                }
            })
            .build()
            .also { it.setPlayer(player) }

        // Select last selected song
        serviceScope.launch {
            DatabaseSingleton.get(this@MusicPlayerService).fileEntityDao().getSelection()?.let {
                accessPlayer {
                    player?.setMediaItem(MediaItem.fromUri(it.getUri()))
                    // TODO: Re-add podcast check
                    //if (it.isPodcast) {
                        player?.seekTo(it.lastResumeMs)
                    //}
                    player?.prepare()
                }
            }
        }
    }

}