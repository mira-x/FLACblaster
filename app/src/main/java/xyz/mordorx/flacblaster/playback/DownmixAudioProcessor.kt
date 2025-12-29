package xyz.mordorx.flacblaster.playback

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** This can be used to either:
 * - Bypass audio (data coming in goes out unchanged)
 * - Modify audio by overriding handleBuffer
 *
 * @author LoliBall (<a href="https://github.com/WhichWho">...</a>) on 2023-01-17
 */
@OptIn(UnstableApi::class)
class DownmixAudioProcessor : BaseAudioProcessor() {
    var outputBuffer = EMPTY_BUFFER
    /** Whether we are currently receiving data that we know how to downmix, i.e. 16-bit PCM stereo */
    var supported = false
    /** Whether the user has currently enabled stereo */
    var stereo = true

    private fun prepareOutputBuffer(inputBuffer: ByteBuffer) {
        if (outputBuffer.capacity() < inputBuffer.remaining()) {
            outputBuffer = ByteBuffer.allocateDirect(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        supported = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2);
        Log.d("DownmixAudioProcessor", "Audio format: " + inputAudioFormat + " supported: " + supported);
        if (supported) {
            return inputAudioFormat;
        } else {
            return super.onConfigure(inputAudioFormat);
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            return
        }
        prepareOutputBuffer(inputBuffer)

        if (supported && stereo) {
            while (inputBuffer.remaining() >= 4) {
                val l = inputBuffer.getShort()
                val r = inputBuffer.getShort()
                val avg = (l + r).div(2).toShort()
                outputBuffer.putShort(avg)
                outputBuffer.putShort(avg)
            }
            outputBuffer.flip()
            replaceOutputBuffer(remaining).put(outputBuffer).flip()
        } else {
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
        }
    }
}
