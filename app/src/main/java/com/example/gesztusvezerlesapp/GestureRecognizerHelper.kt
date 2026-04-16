package com.example.gesztusvezerlesapp


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

class GestureRecognizerHelper(
    val context: Context,
    val listener: GestureRecognizerListener
) {
    private var gestureRecognizer: GestureRecognizer? = null

    init {
        //induláskor ai model betöltése task fileból
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("gesture_recognizer.task")
            .build()

        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM) // valós idejű kamerkakép
            .setResultListener { result, _ ->
                listener.onResults(result)
            }
            .setErrorListener { error ->
                listener.onError(error.message ?: "Hiba történt")
            }
            .build()

        try {
            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
        } catch (e: Exception) {
            listener.onError("Modell betöltési hiba: ${e.message}")
        }
    }

    // 30fps meghivja a kamerát
    fun recognizeLiveStream(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()

        // bitmappá alakit
        val bitmap = imageProxy.toBitmap()

        // előlapi kamera szóval tükrözünk preference kérdése
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // mediapipenak "küldés"
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        gestureRecognizer?.recognizeAsync(mpImage, frameTime)

        // aktuális frame bezárása hogy jöhessen a következő
        imageProxy.close()
    }

    // motor és felület kommunikálása
    interface GestureRecognizerListener {
        fun onError(error: String)
        fun onResults(result: GestureRecognizerResult)
    }
}