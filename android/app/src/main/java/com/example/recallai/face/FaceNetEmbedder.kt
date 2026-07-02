package com.example.recallai.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.recallai.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * MobileFaceNet TFLite — 112×112 RGB in, L2-normalized embedding out.
 */
@Singleton
class FaceNetEmbedder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lock = Any()
    private var interpreter: Interpreter? = null
    private var inputWidth: Int = 112
    private var inputHeight: Int = 112
    private var outputDim: Int = 192
    private var loadError: String? = null

    init {
        ensureLoaded()
    }

    fun isReady(): Boolean = interpreter != null

    fun loadErrorMessage(): String? = loadError

    private fun ensureLoaded() {
        if (interpreter != null || loadError != null) return
        synchronized(lock) {
            if (interpreter != null || loadError != null) return
            runCatching {
                val interp = createInterpreter()
                interpreter = interp
                inputWidth = interp.getInputTensor(0).shape()[2]
                inputHeight = interp.getInputTensor(0).shape()[1]
                outputDim = interp.getOutputTensor(0).shape().last()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "loaded model in=${inputWidth}x$inputHeight out=$outputDim")
                }
            }.onFailure { e ->
                loadError = e.message ?: "Face model failed to load"
                if (BuildConfig.DEBUG) Log.e(TAG, "model load failed", e)
            }
        }
    }

    val embeddingDimension: Int
        get() {
            ensureLoaded()
            return outputDim
        }

    fun embed(bitmap: Bitmap, faceBox: Rect): FloatArray? {
        if (!isReady()) return null
        val crop = FaceImageUtils.cropFace(bitmap, faceBox) ?: return null
        return try {
            embedAlignedFace(crop)
        } finally {
            if (crop !== bitmap) crop.recycle()
        }
    }

    fun embedAlignedFace(faceBitmap: Bitmap): FloatArray? {
        if (!isReady()) return null
        val interp = interpreter ?: return null
        return synchronized(lock) {
            val scaled = Bitmap.createScaledBitmap(faceBitmap, inputWidth, inputHeight, true)
            try {
                val inputBuffer = prepareInput(scaled, interp)
                val output = Array(1) { FloatArray(outputDim) }
                interp.run(inputBuffer, output)
                l2NormalizeInPlace(output[0])
                output[0]
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "embed failed", e)
                null
            } finally {
                if (scaled !== faceBitmap) scaled.recycle()
            }
        }
    }

    private fun prepareInput(bitmap: Bitmap, interp: Interpreter): ByteBuffer {
        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val inputTensor = interp.getInputTensor(0)
        val buffer = ByteBuffer.allocateDirect(inputTensor.numBytes()).order(ByteOrder.nativeOrder())
        when (inputTensor.dataType()) {
            DataType.FLOAT32 -> {
                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    buffer.putFloat((r - 127.5f) / 128f)
                    buffer.putFloat((g - 127.5f) / 128f)
                    buffer.putFloat((b - 127.5f) / 128f)
                }
            }
            DataType.UINT8 -> {
                for (pixel in pixels) {
                    buffer.put(((pixel shr 16) and 0xFF).toByte())
                    buffer.put(((pixel shr 8) and 0xFF).toByte())
                    buffer.put((pixel and 0xFF).toByte())
                }
            }
            else -> error("Unsupported input type ${inputTensor.dataType()}")
        }
        buffer.rewind()
        return buffer
    }

    private fun createInterpreter(): Interpreter {
        val options = Interpreter.Options().apply { setNumThreads(2) }
        return Interpreter(loadModelFile(), options)
    }

    private fun loadModelFile(): MappedByteBuffer {
        context.assets.openFd(MODEL_ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    private fun l2NormalizeInPlace(v: FloatArray) {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum).toFloat().coerceAtLeast(1e-6f)
        for (i in v.indices) v[i] /= norm
    }

    companion object {
        private const val TAG = "FaceNetEmbedder"
        const val MODEL_ASSET = "mobilefacenet.tflite"
        const val MIN_EMBEDDING_DIM = 64
    }
}
