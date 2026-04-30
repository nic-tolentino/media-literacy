package org.medialiteracy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioChunkerTest {
    private val chunker = AudioChunker(sampleRate = 16000, bytesPerSample = 2, channels = 1)
    private val bytesPerSecond = 16000 * 2 * 1
    
    @Test
    fun testEmptyData() {
        val chunks = chunker.chunk(ByteArray(0))
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun testSmallFile() {
        // 10 seconds of audio
        val data = ByteArray(10 * bytesPerSecond)
        val chunks = chunker.chunk(data, chunkSizeSec = 25, overlapSec = 5)
        
        assertEquals(1, chunks.size)
        assertEquals("00:00-00:10", chunks[0].timestamp)
    }

    @Test
    fun testOverlapLogic() {
        // 40 seconds of audio
        // Chunk 1: 0-25s
        // Step: 25 - 5 = 20s
        // Chunk 2: 20-40s (end capped at 40s)
        val data = ByteArray(40 * bytesPerSecond)
        val chunks = chunker.chunk(data, chunkSizeSec = 25, overlapSec = 5)
        
        assertEquals(2, chunks.size)
        assertEquals("00:00-00:25", chunks[0].timestamp)
        assertEquals("00:20-00:40", chunks[1].timestamp)
    }

    @Test
    fun testMultiChunkLogic() {
        // 65 seconds of audio
        // Chunk 1: 0-25s
        // Chunk 2: 20-45s
        // Chunk 3: 40-65s
        val data = ByteArray(65 * bytesPerSecond)
        val chunks = chunker.chunk(data, chunkSizeSec = 25, overlapSec = 5)
        
        assertEquals(3, chunks.size)
        assertEquals("00:00-00:25", chunks[0].timestamp)
        assertEquals("00:20-00:45", chunks[1].timestamp)
        assertEquals("00:40-01:05", chunks[2].timestamp)
    }
}
