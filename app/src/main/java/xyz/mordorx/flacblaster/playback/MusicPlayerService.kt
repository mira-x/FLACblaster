package xyz.mordorx.flacblaster.playback

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import xyz.mordorx.flacblaster.superutil.SuperService

@OptIn(UnstableApi::class)
class MusicPlayerService : SuperService() {
    var player: ExoPlayer? = null
    val downmixer = DownmixAudioProcessor()

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
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
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