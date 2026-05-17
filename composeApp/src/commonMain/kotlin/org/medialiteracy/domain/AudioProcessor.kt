package org.medialiteracy.domain

/**
 * Common audio processing utilities.
 */
object AudioProcessor {
    private const val TARGET_SAMPLE_RATE = 16000

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
