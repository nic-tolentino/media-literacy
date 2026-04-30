package org.medialiteracy.domain

/**
 * Utility for splitting raw audio into overlapping segments for multimodal analysis.
 * 
 * IMPORTANT: This utility assumes raw PCM data (16kHz, mono, 16-bit).
 * Encoded formats (MP3, WAV headers) must be normalized before chunking.
 * 
 * Optimized for LiteRT-LM's 30-second native limit.
 */
class AudioChunker(
    private val sampleRate: Int = 16000, // Standard for many speech models
    private val bytesPerSample: Int = 2, // 16-bit PCM
    private val channels: Int = 1
) {
    private val bytesPerSecond = sampleRate * bytesPerSample * channels
    
    /**
     * Splits a raw PCM [audioData] into segments of [chunkSizeSec] with [overlapSec].
     */
    fun chunk(
        audioData: ByteArray, 
        chunkSizeSec: Int = 25, 
        overlapSec: Int = 5
    ): List<AudioChunk> {
        if (audioData.isEmpty()) return emptyList()

        val chunkSizeBytes = chunkSizeSec * bytesPerSecond
        val overlapBytes = overlapSec * bytesPerSecond
        val stepBytes = chunkSizeBytes - overlapBytes
        
        val chunks = mutableListOf<AudioChunk>()
        var offset = 0
        
        while (offset < audioData.size) {
            val end = (offset + chunkSizeBytes).coerceAtMost(audioData.size)
            val chunkData = audioData.copyOfRange(offset, end)
            
            val startSec = offset / bytesPerSecond
            val endSec = end / bytesPerSecond
            val timestamp = formatTimestamp(startSec, endSec)
            
            chunks.add(AudioChunk(chunkData, timestamp))
            
            if (end == audioData.size) break
            offset += stepBytes
        }
        
        return chunks
    }

    private fun formatTimestamp(startSec: Int, endSec: Int): String {
        fun toMinSec(s: Int) = "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
        return "${toMinSec(startSec)}-${toMinSec(endSec)}"
    }
}

data class AudioChunk(
    val data: ByteArray,
    val timestamp: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioChunk) return false
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
