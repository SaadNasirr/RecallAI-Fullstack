package com.example.recallai.care.pairing

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * CPU fallback when ML Kit fails to read on-screen QRs (dense payloads, OEM camera quirks).
 * Uses the Y plane of [ImageFormat.YUV_420_888] only.
 */
internal object CareQrZxingDecoder {

    fun tryDecodeQr(imageProxy: ImageProxy): String? {
        if (imageProxy.format != ImageFormat.YUV_420_888) return null
        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) return null
        val yPlane = imageProxy.planes.getOrNull(0) ?: return null
        val rowStride = yPlane.rowStride
        val buffer = yPlane.buffer.duplicate()
        val total = rowStride * height
        if (buffer.remaining() < total) return null
        val yBytes = ByteArray(total)
        buffer.rewind()
        buffer.get(yBytes, 0, total)

        val source = PlanarYUVLuminanceSource(
            yBytes,
            rowStride,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        val reader = MultiFormatReader().apply { setHints(hints) }
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
