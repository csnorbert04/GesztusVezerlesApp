package com.example.gesztusvezerlesapp


import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
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

class MediaActivity : AppCompatActivity(), GestureRecognizerHelper.GestureRecognizerListener {

    private lateinit var gestureHelper: GestureRecognizerHelper
    private lateinit var backgroundExecutor: ExecutorService

    // UI Elemek
    private lateinit var viewFinder: PreviewView
    private lateinit var tvMediaGesture: TextView
    private lateinit var mediaProgressBar: ProgressBar
    private lateinit var tvTrackName: TextView
    private lateinit var tvPlayerStatus: TextView
    private lateinit var tvPlayPauseIcon: TextView

    // Média adatok
    private val playlist = listOf(
        "1. Szám: AC/DC - Thunderstruck",
        "2. Szám: Queen - Bohemian Rhapsody",
        "3. Szám: The Weeknd - Blinding Lights",
        "4. Szám: Hans Zimmer - Time"
    )
    private var currentTrackIndex = 0
    private var isPlaying = false

    // Töltőcsík változók
    private var holdingGesture: String = "None"
    private var holdingStartTime = 0L
    private val REQUIRED_HOLD_TIME = 1500L // 1.5 másodperc a normál média akciókhoz
    private var lastActionTime = 0L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) startCamera() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media)

        viewFinder = findViewById(R.id.viewFinderMedia)
        tvMediaGesture = findViewById(R.id.tvMediaGesture)
        mediaProgressBar = findViewById(R.id.mediaProgressBar)
        tvTrackName = findViewById(R.id.tvTrackName)
        tvPlayerStatus = findViewById(R.id.tvPlayerStatus)
        tvPlayPauseIcon = findViewById(R.id.tvPlayPauseIcon)

        backgroundExecutor = Executors.newSingleThreadExecutor()
        gestureHelper = GestureRecognizerHelper(this, this)

        updatePlayerUI()

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
                .also { it.setAnalyzer(backgroundExecutor) { imageProxy -> gestureHelper.recognizeLiveStream(imageProxy) } }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) { Log.e("MediaActivity", "Kamera hiba", exc) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun resetHoldState() {
        holdingGesture = "None"
        holdingStartTime = 0L
        mediaProgressBar.progress = 0
        tvMediaGesture.setTextColor(Color.WHITE)
        // Visszaállítjuk zöldre az alapállapotot
        mediaProgressBar.progressTintList = ColorStateList.valueOf(Color.GREEN)
    }

    override fun onResults(result: GestureRecognizerResult) {
        runOnUiThread {
            if (result.gestures().isNotEmpty() && result.gestures()[0].isNotEmpty()) {
                val activeGesture = result.gestures()[0][0].categoryName()
                val score = result.gestures()[0][0].score()

                if (score > 0.5f && activeGesture != "None") {

                    // kilépés figyelése
                    if (activeGesture == "ILoveYou") {
                        tvMediaGesture.text = "VISSZA A MENÜBE"
                        tvMediaGesture.setTextColor(Color.RED)
                        // piros csík zöld helyett
                        mediaProgressBar.progressTintList = ColorStateList.valueOf(Color.RED)

                        if (holdingGesture == "ILoveYou") {
                            val timeHeld = System.currentTimeMillis() - holdingStartTime
                            val progressPercent = ((timeHeld.toFloat() / 2000L) * 100).toInt() // Itt fix 2 másodperc kell
                            mediaProgressBar.progress = progressPercent

                            if (timeHeld >= 2000L) {
                                vibratePhone()
                                finish() // vissza főmenübe
                            }
                        } else {
                            holdingGesture = "ILoveYou"
                            holdingStartTime = System.currentTimeMillis()
                            mediaProgressBar.progress = 0
                        }
                        return@runOnUiThread // kilépéskor a zenelejátszó kódja már ne fusson le
                    }


                    // normál gesztusfigyelés
                    tvMediaGesture.text = "Látott: $activeGesture"
                    // Biztosítjuk, hogy zöld legyen a csík
                    mediaProgressBar.progressTintList = ColorStateList.valueOf(Color.GREEN)

                    if (System.currentTimeMillis() - lastActionTime < 2000) {
                        resetHoldState()
                        return@runOnUiThread
                    }

                    val isValidGesture = activeGesture in listOf("Victory", "Thumb_Up", "Thumb_Down")

                    if (isValidGesture) {
                        tvMediaGesture.setTextColor(Color.GREEN)

                        if (holdingGesture == activeGesture) {
                            val timeHeld = System.currentTimeMillis() - holdingStartTime
                            val progressPercent = ((timeHeld.toFloat() / REQUIRED_HOLD_TIME) * 100).toInt()
                            mediaProgressBar.progress = progressPercent

                            if (timeHeld >= REQUIRED_HOLD_TIME) {
                                vibratePhone()
                                executeMediaAction(activeGesture)
                                resetHoldState()
                                lastActionTime = System.currentTimeMillis()
                            }
                        } else {
                            holdingGesture = activeGesture
                            holdingStartTime = System.currentTimeMillis()
                            mediaProgressBar.progress = 0
                        }
                    } else {
                        resetHoldState()
                    }
                } else {
                    tvMediaGesture.text = "Látott: Keresem..."
                    resetHoldState()
                }
            } else {
                tvMediaGesture.text = "Látott: Keresem..."
                resetHoldState()
            }
        }
    }

    private fun executeMediaAction(gesture: String) {
        when (gesture) {
            "Victory" -> {
                isPlaying = !isPlaying
                if (isPlaying) Toast.makeText(this, "▶️ Lejátszás elindítva", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this, "⏸️ Megállítva", Toast.LENGTH_SHORT).show()
            }
            "Thumb_Down" -> {
                currentTrackIndex = (currentTrackIndex + 1) % playlist.size
                Toast.makeText(this, "⏭️ Következő szám", Toast.LENGTH_SHORT).show()
            }
            "Thumb_Up" -> {
                currentTrackIndex = if (currentTrackIndex - 1 < 0) playlist.size - 1 else currentTrackIndex - 1
                Toast.makeText(this, "⏮️ Előző szám", Toast.LENGTH_SHORT).show()
            }
        }
        updatePlayerUI()
    }

    private fun updatePlayerUI() {
        tvTrackName.text = playlist[currentTrackIndex]
        if (isPlaying) {
            tvPlayerStatus.text = "Lejátszás folyamatban..."
            tvPlayerStatus.setTextColor(Color.GREEN)
            tvPlayPauseIcon.text = "⏸️"
        } else {
            tvPlayerStatus.text = "Szüneteltetve"
            tvPlayerStatus.setTextColor(Color.parseColor("#FF5555"))
            tvPlayPauseIcon.text = "▶️"
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