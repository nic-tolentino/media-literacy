package org.medialiteracy.domain

/**
 * Abstraction for extracting text from images.
 * This allows using platform-specific OCR (like ML Kit) or falling back to multimodal LLM analysis.
 */
interface ImageTranscriber {
    /**
     * Transcribes text from the given image data.
     * @return The extracted text, or null if transcription failed or no text was found.
     */
    suspend fun transcribe(imageBytes: ByteArray): String?

    /**
     * Closes the transcriber and releases any associated resources.
     */
    fun close()
}
