package com.example.gesztusvezerlesapp


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
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

class MainActivity : AppCompatActivity(), GestureRecognizerHelper.GestureRecognizerListener {

    private lateinit var gestureHelper: GestureRecognizerHelper
    private lateinit var exitContainer: LinearLayout
    private lateinit var exitProgressBar: ProgressBar

    private var holdingStartTime = 0L
    private var backgroundExecutor: ExecutorService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exitContainer = findViewById(R.id.exitContainer)
        exitProgressBar = findViewById(R.id.exitProgressBar)

        gestureHelper = GestureRecognizerHelper(this, this)

        // gombok
        findViewById<Button>(R.id.btnPopup).setOnClickListener { startActivity(Intent(this, PopupActivity::class.java)) }
        findViewById<Button>(R.id.btnMedia).setOnClickListener { startActivity(Intent(this, MediaActivity::class.java)) }
        findViewById<Button>(R.id.btnDemo).setOnClickListener { startActivity(Intent(this, DemoActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        holdingStartTime = 0L
        exitContainer.visibility = View.INVISIBLE

        if (backgroundExecutor == null || backgroundExecutor!!.isShutdown) {
            backgroundExecutor = Executors.newSingleThreadExecutor()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startHiddenCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        backgroundExecutor?.shutdown()
    }

    private fun startHiddenCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val viewFinderMain = findViewById<PreviewView>(R.id.viewFinderMain)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinderMain.surfaceProvider) }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(backgroundExecutor!!) { imageProxy ->
                        gestureHelper.recognizeLiveStream(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResults(result: GestureRecognizerResult) {
        runOnUiThread {
            val gesture = result.gestures().firstOrNull()?.firstOrNull()?.categoryName() ?: "None"

            if (gesture == "ILoveYou") {
                if (holdingStartTime == 0L) holdingStartTime = System.currentTimeMillis()

                exitContainer.visibility = View.VISIBLE
                val timeHeld = System.currentTimeMillis() - holdingStartTime
                exitProgressBar.progress = ((timeHeld.toFloat() / 2000L) * 100).toInt()

                if (timeHeld >= 2000L) {
                    vibratePhone()
                    finishAffinity() // TELJES KILÉPÉS
                }
            } else {
                holdingStartTime = 0L
                exitContainer.visibility = View.INVISIBLE
            }
        }
    }

    override fun onError(error: String) {}

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