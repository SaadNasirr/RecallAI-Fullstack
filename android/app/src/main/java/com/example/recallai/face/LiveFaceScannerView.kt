package com.example.recallai.face

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.recallai.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.SystemClock

private const val TAG = "LiveFaceScanner"

/** Normalized bounds in image space (0–1). */
data class FaceBoundsNorm(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun LiveFaceScannerView(
    modifier: Modifier = Modifier,
    embedder: FaceNetEmbedder,
    onFaceDetected: (faceCount: Int, vector: List<Float>, bounds: FaceBoundsNorm?) -> Unit,
    onCameraReady: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val embedExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            detector.close()
            cameraExecutor.shutdown()
            embedExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        var lastFaceProcessAt = 0L
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastFaceProcessAt < 500L) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            lastFaceProcessAt = now
                            try {
                                processFrame(
                                    imageProxy = imageProxy,
                                    detector = detector,
                                    embedder = embedder,
                                    embedExecutor = embedExecutor,
                                    mainExecutor = mainExecutor,
                                    onFaceDetected = onFaceDetected
                                )
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) Log.e(TAG, "analyzer error", e)
                                imageProxy.close()
                                mainExecutor.execute {
                                    onFaceDetected(0, emptyList(), null)
                                }
                            }
                        }
                        val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                        ContextCompat.getMainExecutor(ctx).execute { onCameraReady() }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "camera bind failed", e)
                        ContextCompat.getMainExecutor(ctx).execute { onCameraReady() }
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
    }
}

private fun processFrame(
    imageProxy: ImageProxy,
    detector: com.google.mlkit.vision.face.FaceDetector,
    embedder: FaceNetEmbedder,
    embedExecutor: ExecutorService,
    mainExecutor: java.util.concurrent.Executor,
    onFaceDetected: (faceCount: Int, vector: List<Float>, bounds: FaceBoundsNorm?) -> Unit
) {
    if (!embedder.isReady()) {
        mainExecutor.execute { onFaceDetected(0, emptyList(), null) }
        return
    }
    val mediaImage = imageProxy.image ?: run {
        mainExecutor.execute { onFaceDetected(0, emptyList(), null) }
        return
    }
    val rotation = imageProxy.imageInfo.rotationDegrees
    val image = InputImage.fromMediaImage(mediaImage, rotation)
    val iw = imageProxy.width.toFloat().coerceAtLeast(1f)
    val ih = imageProxy.height.toFloat().coerceAtLeast(1f)

    detector.process(image)
        .addOnSuccessListener { faces ->
            try {
                if (faces.isEmpty()) {
                    mainExecutor.execute { onFaceDetected(0, emptyList(), null) }
                    return@addOnSuccessListener
                }
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face == null || !FaceUsability.isFaceUsable(face)) {
                    mainExecutor.execute {
                        onFaceDetected(if (face == null) 0 else faces.size, emptyList(), null)
                    }
                    return@addOnSuccessListener
                }
                val bitmap = runCatching { imageProxy.toRotatedBitmap() }.getOrNull()
                if (bitmap == null) {
                    mainExecutor.execute { onFaceDetected(0, emptyList(), null) }
                    return@addOnSuccessListener
                }
                val b = face.boundingBox
                val bounds = FaceBoundsNorm(
                    left = (b.left / iw).coerceIn(0f, 1f),
                    top = (b.top / ih).coerceIn(0f, 1f),
                    right = (b.right / iw).coerceIn(0f, 1f),
                    bottom = (b.bottom / ih).coerceIn(0f, 1f)
                )
                embedExecutor.execute {
                    try {
                        val embedding = embedder.embed(bitmap, b)
                        val vec = embedding?.toList() ?: emptyList()
                        mainExecutor.execute {
                            onFaceDetected(faces.size, vec, bounds)
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "embed error", e)
                        mainExecutor.execute { onFaceDetected(0, emptyList(), null) }
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "face success handler error", e)
                mainExecutor.execute { onFaceDetected(0, emptyList(), null) }
            }
        }
        .addOnFailureListener { e ->
            if (BuildConfig.DEBUG) Log.e(TAG, "ml kit detect failed", e)
            mainExecutor.execute { onFaceDetected(0, emptyList(), null) }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun FloatArray.toList(): List<Float> = List(size) { this[it] }
