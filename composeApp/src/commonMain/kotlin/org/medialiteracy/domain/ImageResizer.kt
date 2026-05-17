package org.medialiteracy.domain

/**
 * Abstraction for resizing images with aspect ratio preservation (letterboxing).
 */
interface ImageResizer {
    /**
     * Resizes the image to fit within the given dimensions using letterboxing.
     * @param imageBytes The source image bytes.
     * @param width The target width.
     * @param height The target height.
     * @return The resized image bytes (typically JPEG).
     */
    suspend fun letterbox(imageBytes: ByteArray, width: Int, height: Int): ByteArray
}
