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

        val bytesPerSample = 2
        val frameSize = channels * bytesPerSample
        val frameCount = data.size / frameSize
        val mono = ByteArray(frameCount * bytesPerSample)

        for (i in 0 until frameCount) {
            var sum = 0
            for (ch in 0 until channels) {
                val offset = i * frameSize + ch * bytesPerSample
                val sample = (data[offset].toInt() and 0xff) or (data[offset + 1].toInt() shl 8)
                sum += if (sample >= 0x8000) sample - 0x10000 else sample
            }
            val averaged = (sum / channels).toShort()
            mono[i * 2] = (averaged.toInt() and 0xff).toByte()
            mono[i * 2 + 1] = (averaged.toInt() ushr 8 and 0xff).toByte()
        }
        return mono
    }

    fun resampleIfNecessary(data: ByteArray, sourceRate: Int): ByteArray {
        if (sourceRate == TARGET_SAMPLE_RATE) return data

        Logger.i("AudioDecoder", "Resampling from $sourceRate to $TARGET_SAMPLE_RATE")

        val ratio = sourceRate.toDouble() / TARGET_SAMPLE_RATE
        val outputSamples = (data.size / 2 / ratio).toInt()
        val output = ByteArray(outputSamples * 2)

        for (i in 0 until outputSamples) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex

            val s0Offset = srcIndex * 2
            val s1Offset = (srcIndex + 1) * 2

            val s0 = if (s0Offset + 1 < data.size) {
                val raw = (data[s0Offset].toInt() and 0xff) or (data[s0Offset + 1].toInt() shl 8)
                if (raw >= 0x8000) raw - 0x10000 else raw
            } else 0

            val s1 = if (s1Offset + 1 < data.size) {
                val raw = (data[s1Offset].toInt() and 0xff) or (data[s1Offset + 1].toInt() shl 8)
                if (raw >= 0x8000) raw - 0x10000 else raw
            } else s0

            val interpolated = (s0 + frac * (s1 - s0)).toInt().toShort()
            output[i * 2] = (interpolated.toInt() and 0xff).toByte()
            output[i * 2 + 1] = (interpolated.toInt() ushr 8 and 0xff).toByte()
        }
        return output
    }

}
