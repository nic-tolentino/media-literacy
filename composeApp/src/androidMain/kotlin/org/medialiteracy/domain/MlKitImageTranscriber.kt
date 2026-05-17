package org.medialiteracy.domain

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of ImageTranscriber using ML Kit Text Recognition.
 */
class MlKitImageTranscriber : ImageTranscriber {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun transcribe(imageBytes: ByteArray): String? {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            
            // Format result: sort by top-to-bottom, left-to-right
            // ML Kit's result.text already does a reasonable job of basic layout preservation
            // but we ensure it's not empty.
            val text = result.text
            return if (text.isNotBlank()) text else null
        } catch (e: Exception) {
            Logger.e("MlKitImageTranscriber", "OCR failed: ${e.message}")
            return null
        }
    }

    override fun close() {
        recognizer.close()
    }
}
