package com.example.recallai.face

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProcessingUtil
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min

private const val FACE_PADDING_RATIO = 0.22f

object FaceImageUtils {

    fun cropFace(bitmap: Bitmap, box: Rect, paddingRatio: Float = FACE_PADDING_RATIO): Bitmap? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val padX = (box.width() * paddingRatio).toInt()
        val padY = (box.height() * paddingRatio).toInt()
        val left = max(0, box.left - padX)
        val top = max(0, box.top - padY)
        val right = min(bitmap.width, box.right + padX)
        val bottom = min(bitmap.height, box.bottom + padY)
        val w = right - left
        val h = bottom - top
        if (w < 32 || h < 32) return null
        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }
}

/**
 * Bitmap in the same orientation ML Kit uses ([InputImage] rotation applied).
 * Must be called while [ImageProxy] is still open.
 */
fun ImageProxy.toRotatedBitmap(): Bitmap? {
    val raw = runCatching { ImageProcessingUtil.convertYUVToBitmap(this) }.getOrNull() ?: return null
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (rotated !== raw) raw.recycle()
    return rotated
}
