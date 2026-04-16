package com.example.gesztusvezerlesapp


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DemoActivity : AppCompatActivity(), GestureRecognizerHelper.GestureRecognizerListener {

    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private lateinit var backgroundExecutor: ExecutorService

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var gestureResultText: TextView

    // Új UI elemek a kilépéshez
    private lateinit var exitContainer: LinearLayout
    private lateinit var exitProgressBar: ProgressBar
    private var holdingStartTime = 0L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) startCamera() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)
        gestureResultText = findViewById(R.id.gestureResultText)

        // Kilépés jelzők
        exitContainer = findViewById(R.id.exitContainer)
        exitProgressBar = findViewById(R.id.exitProgressBar)

        backgroundExecutor = Executors.newSingleThreadExecutor()
        gestureRecognizerHelper = GestureRecognizerHelper(this, this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(backgroundExecutor) { imageProxy ->
                    gestureRecognizerHelper.recognizeLiveStream(imageProxy)
                } }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) { Log.e("DemoActivity", "Kamera hiba", exc) }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResults(result: GestureRecognizerResult) {
        runOnUiThread {
            // pontokat mindig rajzoljuk ki ha van kéz
            overlayView.setResults(result)

            val activeGesture = result.gestures().firstOrNull()?.firstOrNull()?.categoryName() ?: "None"
            val score = result.gestures().firstOrNull()?.firstOrNull()?.score() ?: 0f

            // VISSZA I_LOVE_YOU gesztus a kilépésre
            if (activeGesture == "ILoveYou") {
                if (holdingStartTime == 0L) holdingStartTime = System.currentTimeMillis()

                exitContainer.visibility = View.VISIBLE
                val timeHeld = System.currentTimeMillis() - holdingStartTime
                exitProgressBar.progress = ((timeHeld.toFloat() / 2000L) * 100).toInt()

                if (timeHeld >= 2000L) {
                    vibratePhone()
                    finish() // Vissza főmenü
                }

                gestureResultText.text = "VISSZA..."
            } else {
                holdingStartTime = 0L
                exitContainer.visibility = View.INVISIBLE

                // normál gesztusok kiírása
                if (score > 0.5f && activeGesture != "None") {
                    gestureResultText.text = "Gesztus: $activeGesture"
                } else {
                    gestureResultText.text = "Keresem a kezed..."
                }
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }

    private fun vibratePhone() {
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}