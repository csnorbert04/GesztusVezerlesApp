package com.example.gesztusvezerlesapp


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: GestureRecognizerResult? = null

    // pontok stílusa
    private val pointPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    // vonalak stílusa
    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    // kéz csontváz 21 pont
    private val connections = listOf(
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), // Hüvelykujj
        Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), // Mutatóujj
        Pair(5, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), // Középső ujj
        Pair(9, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), // Gyűrűsujj
        Pair(13, 17), Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20) // Kisujj és tenyér
    )

    // új kéznél meghivj
    fun setResults(gestureResults: GestureRecognizerResult?) {
        results = gestureResults
        invalidate() // újrarajzolás
    }

    // actual rajzolás
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        results?.let { gestureResult ->
            // több kéz (nem lehet, mediapipe csak 1-et tud)
            for (landmarkList in gestureResult.landmarks()) {

                // összekötő vonalak
                for (connection in connections) {
                    val start = landmarkList[connection.first]
                    val end = landmarkList[connection.second]
                    // koordináta képernyő cucc szorzás
                    canvas.drawLine(
                        start.x() * width, start.y() * height,
                        end.x() * width, end.y() * height,
                        linePaint
                    )
                }

                // piros pöttyök
                for (landmark in landmarkList) {
                    val x = landmark.x() * width
                    val y = landmark.y() * height
                    canvas.drawCircle(x, y, 8f, pointPaint)
                }
            }
        }
    }
}