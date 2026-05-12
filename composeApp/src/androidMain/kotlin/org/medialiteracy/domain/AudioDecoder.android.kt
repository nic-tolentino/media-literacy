package org.medialiteracy.domain

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Utility to decode various audio formats (MP3, AAC, M4A, etc.) 
 * into raw 16kHz 16-bit Mono PCM for the Gemma engine.
 */
object AudioDecoder {

    private const val TARGET_SAMPLE_RATE = 16000
    private const val TIMEOUT_US = 10000L

    suspend fun decodeToPcm(context: Context, audioBytes: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_decode_source")
        try {
            FileOutputStream(tempFile).use { it.write(audioBytes) }
            decodeFileToPcm(tempFile.absolutePath)
        } catch (e: Exception) {
            Logger.e("AudioDecoder", "Decoding failed: ${e.message}")
            null
        } finally {
            tempFile.delete()
        }
    }

    private fun decodeFileToPcm(path: String): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        
        try {
            extractor.setDataSource(path)
            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) return null
            
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            
            val outputStream = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            
            val sourceSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                
                val outputBufferIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outputBufferIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isOutputEOS = true
                    }
                    
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                    val data = ByteArray(info.size)
                    outputBuffer.get(data)
                    outputBuffer.clear()
                    
                    // Convert to Mono if needed and then write to stream
                    val pcmData = processAudioData(data, sourceChannels)
                    outputStream.write(pcmData)
                    
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Log format change if needed
                }
            }
            
            val decodedBytes = outputStream.toByteArray()
            return resampleIfNecessary(decodedBytes, sourceSampleRate)
            
        } catch (e: Exception) {
            Logger.e("AudioDecoder", "Internal decode error: ${e.message}")
            return null
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return -1
    }

    private fun processAudioData(data: ByteArray, channels: Int): ByteArray {
        if (channels == 1) return data
        
        // Stereo to Mono: take first channel samples (16-bit)
        val monoSize = data.size / channels
        val mono = ByteArray(monoSize)
        for (i in 0 until monoSize step 2) {
            if (i + 1 < data.size && i + 1 < mono.size) {
                mono[i] = data[i * channels]
                mono[i + 1] = data[i * channels + 1]
            }
        }
        return mono
    }

    fun resampleIfNecessary(data: ByteArray, sourceRate: Int): ByteArray {
        if (sourceRate == TARGET_SAMPLE_RATE) return data
        
        Logger.i("AudioDecoder", "Resampling from $sourceRate to $TARGET_SAMPLE_RATE")
        
        val ratio = sourceRate.toDouble() / TARGET_SAMPLE_RATE
        val outputSize = (data.size / 2 / ratio).toInt() * 2
        val output = ByteArray(outputSize)
        
        for (i in 0 until outputSize step 2) {
            val sourceIndex = (i / 2 * ratio).toInt() * 2
            if (sourceIndex + 1 < data.size) {
                output[i] = data[sourceIndex]
                output[i + 1] = data[sourceIndex + 1]
            }
        }
        return output
    }

    /**
     * Wraps raw PCM data in a standard WAV header.
     * miniaudio (used by LiteRT-LM) needs this header to know the format.
     */
    fun wrapInWav(pcmData: ByteArray): ByteArray {
        val header = ByteArray(44)
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize
        val byteRate = TARGET_SAMPLE_RATE * 2 // 16000 * 2 (16-bit mono)

        // RIFF header
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalSize and 0xff).toByte()
        header[5] = (totalSize shr 8 and 0xff).toByte()
        header[6] = (totalSize shr 16 and 0xff).toByte()
        header[7] = (totalSize shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Subchunk1Size
        header[20] = 1; header[21] = 0 // AudioFormat (PCM)
        header[22] = 1; header[23] = 0 // NumChannels (Mono)
        header[24] = (TARGET_SAMPLE_RATE and 0xff).toByte()
        header[25] = (TARGET_SAMPLE_RATE shr 8 and 0xff).toByte()
        header[26] = (TARGET_SAMPLE_RATE shr 16 and 0xff).toByte()
        header[27] = (TARGET_SAMPLE_RATE shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = 2; header[33] = 0 // BlockAlign
        header[34] = 16; header[35] = 0 // BitsPerSample
        
        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (dataSize and 0xff).toByte()
        header[41] = (dataSize shr 8 and 0xff).toByte()
        header[42] = (dataSize shr 16 and 0xff).toByte()
        header[43] = (dataSize shr 24 and 0xff).toByte()

        return header + pcmData
    }
}
