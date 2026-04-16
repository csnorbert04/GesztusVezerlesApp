package com.example.gesztusvezerlesapp


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class PopupActivity : AppCompatActivity(), GestureRecognizerHelper.GestureRecognizerListener {

    private lateinit var gestureHelper: GestureRecognizerHelper
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var tvCurrentGesture: TextView
    private lateinit var gestureProgressBar: ProgressBar

    private var currentAlertDialog: AlertDialog? = null
    private var currentDialogState = 0
    private var lastActionTime = 0L

    private var holdingGesture: String = "None"
    private var holdingStartTime = 0L
    private val REQUIRED_HOLD_TIME = 2000L // 2 másodperc kell az elfogadáshoz

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) startCamera() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)

        viewFinder = findViewById(R.id.viewFinderPopup)
        tvCurrentGesture = findViewById(R.id.tvCurrentGesture)
        gestureProgressBar = findViewById(R.id.gestureProgressBar)
        val btnOpenDialog = findViewById<Button>(R.id.btnOpenDialog)

        backgroundExecutor = Executors.newSingleThreadExecutor()
        gestureHelper = GestureRecognizerHelper(this, this)

        btnOpenDialog.setOnClickListener {
            if (currentDialogState == 0) showDialog1()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showDialog1() {
        currentDialogState = 1
        currentAlertDialog = AlertDialog.Builder(this)
            .setTitle("1. Kérdés (Yes / No)")
            .setMessage("Készen állsz a folytatásra?\n\n👍 = Igen (Thumb Up)\n👎 = Nem (Thumb Down)")
            .setCancelable(false)
            .show()
    }

    private fun showDialog2() {
        currentDialogState = 2
        currentAlertDialog = AlertDialog.Builder(this)
            .setTitle("2. Kérdés (Több válasz)")
            .setMessage("Melyik programozási nyelvet használjuk most?\n\n1️⃣ (Mutatóujj) =  Java\n2️⃣ (Hüvelyk+Mutató) =  Kotlin\n3️⃣ (Hüvelyk+Mutató+Középső) =  C++")
            .setCancelable(false)
            .show()
    }

    private fun showDialog3() {
        currentDialogState = 3
        currentAlertDialog = AlertDialog.Builder(this)
            .setTitle("Információ")
            .setMessage("Sikeresen végigmentél a gesztusvezérelt menün!\n\n✊ = Ablak bezárása (Closed Fist)")
            .setCancelable(false)
            .show()
    }

    private fun closeCurrentAndShowNext(nextState: Int) {
        currentAlertDialog?.dismiss()
        lastActionTime = System.currentTimeMillis()
        resetHoldState() // csík reset

        when (nextState) {
            2 -> showDialog2()
            3 -> showDialog3()
            0 -> currentDialogState = 0
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
            } catch (exc: Exception) { Log.e("PopupActivity", "Kamera hiba", exc) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectCustomFingerCount(landmarks: List<NormalizedLandmark>): String {
        val isIndexUp = landmarks[8].y() < landmarks[6].y()
        val isMiddleUp = landmarks[12].y() < landmarks[10].y()
        val isRingUp = landmarks[16].y() < landmarks[14].y()
        val isPinkyUp = landmarks[20].y() < landmarks[18].y()

        val thumbTipDistX = abs(landmarks[4].x() - landmarks[9].x())
        val thumbBaseDistX = abs(landmarks[2].x() - landmarks[9].x())
        val isThumbOut = thumbTipDistX > thumbBaseDistX

        if (isThumbOut && !isIndexUp && !isMiddleUp && !isRingUp && !isPinkyUp) return "1"
        if (isThumbOut && isIndexUp && !isMiddleUp && !isRingUp && !isPinkyUp) return "2"
        if (isThumbOut && isIndexUp && isMiddleUp && !isRingUp && !isPinkyUp) return "3"

        return "Unknown"
    }

    // csík reset
    private fun resetHoldState() {
        holdingGesture = "None"
        holdingStartTime = 0L
        gestureProgressBar.progress = 0
        tvCurrentGesture.setTextColor(Color.WHITE)
        gestureProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(Color.GREEN)
    }

    override fun onResults(result: GestureRecognizerResult) {
        runOnUiThread {
            if (result.gestures().isNotEmpty() && result.gestures()[0].isNotEmpty()) {
                val recognizedAiGesture = result.gestures()[0][0].categoryName()
                val score = result.gestures()[0][0].score()
                val landmarks = result.landmarks().firstOrNull()

                // gesztusérzékelés
                var activeGesture = "None"
                var displayGestureName = "Nincs"

                if (score > 0.5f && recognizedAiGesture != "None") {
                    activeGesture = recognizedAiGesture
                    displayGestureName = recognizedAiGesture
                }

                if (landmarks != null) {
                    val customNum = detectCustomFingerCount(landmarks)
                    if (customNum != "Unknown") {
                        activeGesture = customNum
                        displayGestureName = "Szám: $customNum"
                    }
                }

                tvCurrentGesture.text = "Látott: $displayGestureName"
                // főmenübe vissza gesztus
                if (activeGesture == "ILoveYou") {
                    tvCurrentGesture.text = "VISSZA A MENÜBE"
                    tvCurrentGesture.setTextColor(Color.RED)
                    gestureProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(Color.RED)

                    if (holdingGesture == "ILoveYou") {
                        val timeHeld = System.currentTimeMillis() - holdingStartTime
                        val progressPercent = ((timeHeld.toFloat() / 2000L) * 100).toInt()
                        gestureProgressBar.progress = progressPercent

                        if (timeHeld >= 2000L) {
                            vibratePhone()
                            finish() // vissza a főmenübe
                        }
                    } else {
                        holdingGesture = "ILoveYou"
                        holdingStartTime = System.currentTimeMillis()
                        gestureProgressBar.progress = 0
                    }
                    return@runOnUiThread // teljes kilépés
                }

                if (currentDialogState == 0 || System.currentTimeMillis() - lastActionTime < 1500) {
                    resetHoldState()
                    return@runOnUiThread
                }

                // érvényes e a gesztus az aktuális popup ablakhoz
                var isValidForDialog = false
                when (currentDialogState) {
                    1 -> if (activeGesture == "Thumb_Up" || activeGesture == "Thumb_Down") isValidForDialog = true
                    2 -> if (activeGesture == "Pointing_Up" || activeGesture == "2" || activeGesture == "3") isValidForDialog = true
                    3 -> if (activeGesture == "Closed_Fist") isValidForDialog = true
                }

                // csík
                if (isValidForDialog) {
                    tvCurrentGesture.setTextColor(Color.GREEN)

                    if (holdingGesture == activeGesture) {
                        val timeHeld = System.currentTimeMillis() - holdingStartTime

                        val progressPercent = ((timeHeld.toFloat() / REQUIRED_HOLD_TIME) * 100).toInt()
                        gestureProgressBar.progress = progressPercent

                        // 2mp requied time held
                        if (timeHeld >= REQUIRED_HOLD_TIME) {
                            executeDialogAction(activeGesture)
                        }
                    } else {
                        holdingGesture = activeGesture
                        holdingStartTime = System.currentTimeMillis()
                        gestureProgressBar.progress = 0
                    }
                } else {
                    // ha érvényes gesztus eltünik reset csík
                    resetHoldState()
                }

            } else {
                tvCurrentGesture.text = "Látott: Keresem..."
                resetHoldState()
            }
        }
    }

    // ha csík betölt 100%
    private fun executeDialogAction(gesture: String) {
        vibratePhone()
        when (currentDialogState) {
            1 -> {
                if (gesture == "Thumb_Up") {
                    Toast.makeText(this, "IGEN kiválasztva!", Toast.LENGTH_SHORT).show()
                    closeCurrentAndShowNext(2)
                } else if (gesture == "Thumb_Down") {
                    Toast.makeText(this, "NEM kiválasztva!", Toast.LENGTH_SHORT).show()
                    closeCurrentAndShowNext(2)
                }
            }
            2 -> {
                if (gesture == "Pointing_Up") {
                    Toast.makeText(this, "1-es opció kiválasztva!", Toast.LENGTH_SHORT).show()
                    closeCurrentAndShowNext(3)
                } else if (gesture == "2") {
                    Toast.makeText(this, "2-es (Kotlin) kiválasztva!", Toast.LENGTH_SHORT).show()
                    closeCurrentAndShowNext(3)
                } else if (gesture == "3") {
                    Toast.makeText(this, "3-as opció kiválasztva!", Toast.LENGTH_SHORT).show()
                    closeCurrentAndShowNext(3)
                }
            }
            3 -> {
                if (gesture == "Closed_Fist") {
                    Toast.makeText(this, "Ablak bezárva, teszt vége!", Toast.LENGTH_SHORT).show()
                    closeCurrentAndShowNext(0)
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
        currentAlertDialog?.dismiss()
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