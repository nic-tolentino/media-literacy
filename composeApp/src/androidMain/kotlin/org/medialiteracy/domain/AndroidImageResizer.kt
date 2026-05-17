package org.medialiteracy.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AndroidImageResizer : ImageResizer {
    override suspend fun letterbox(imageBytes: ByteArray, width: Int, height: Int): ByteArray = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inMutable = true }
        val source = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options) ?: return@withContext imageBytes
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)
        
        val srcW = source.width
        val srcH = source.height
        
        val ratio = Math.min(width.toFloat() / srcW, height.toFloat() / srcH)
        val targetW = (srcW * ratio).toInt()
        val targetH = (srcH * ratio).toInt()
        
        val left = (width - targetW) / 2
        val top = (height - targetH) / 2
        
        val srcRect = Rect(0, 0, srcW, srcH)
        val dstRect = Rect(left, top, left + targetW, top + targetH)
        
        canvas.drawBitmap(source, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
        
        val stream = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.toByteArray()
    }
}
