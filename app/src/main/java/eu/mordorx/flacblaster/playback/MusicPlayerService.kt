package eu.mordorx.flacblaster.playback

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import eu.mordorx.flacblaster.fs.DatabaseSingleton
import eu.mordorx.flacblaster.fs.FileEntity
import eu.mordorx.flacblaster.superutil.SuperService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

@OptIn(UnstableApi::class)
class MusicPlayerService : SuperService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: ExoPlayer? = null
    private val downmixer = DownmixAudioProcessor()

    // For each file, store a flow that stores at which millisecond the song is being played back
    var filesPlaybackState = HashMap<String, MutableStateFlow<Float>>()

    fun play(f: FileEntity) {
        if (player == null) return;
        serviceScope.launch {
            val db = DatabaseSingleton.get(this@MusicPlayerService)
            db.fileEntityDao().setSelection(f.path)
        }
        player!!.setMediaItem(MediaItem.fromUri(f.getUri()))
        player!!.prepare()
        player!!.play()
    }

    fun isPlaying(): Boolean = player?.isPlaying ?: false
    /** Resume playback, if applicable */
    fun play() = player?.play()
    /** Pause playback, if applicable */
    fun pause() = player?.pause()

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

        player?.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),  /* handleAudioFocus= */
            true
        )

        player?.addListener(object : Player.Listener {
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
    }

}