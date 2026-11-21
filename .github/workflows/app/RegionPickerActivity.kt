package com.robrun.esp

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View

/**
 * Full-screen transparent activity. User drags a rectangle over the game's
 * result display area. The Rect is broadcast back to OverlayService.
 */
class RegionPickerActivity : Activity() {

    companion object {
        const val EXTRA_RECT = "capture_rect"
        const val ACTION_REGION = "com.robrun.esp.REGION_PICKED"
    }

    private var startX = 0f
    private var startY = 0f
    private var currentRect = RectF()
    private var drawing = false

    private val fillPaint = Paint().apply {
        color = Color.argb(60, 88, 166, 255); style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        color = Color.argb(255, 88, 166, 255); style = Paint.Style.STROKE
        strokeWidth = 3f; pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 36f; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER; setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val overlay = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.argb(80, 0, 0, 0))
                if (drawing) {
                    canvas.drawRect(currentRect, fillPaint)
                    canvas.drawRect(currentRect, strokePaint)
                }
                canvas.drawText(
                    if (!drawing) "Arraste sobre a área de resultado"
                    else          "Solte para confirmar",
                    width / 2f,
                    if (!drawing) height / 2f else currentRect.top - 16f,
                    textPaint
                )
                invalidate()
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x; startY = event.y; drawing = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        currentRect.set(
                            minOf(startX, event.x), minOf(startY, event.y),
                            maxOf(startX, event.x), maxOf(startY, event.y)
                        )
                    }
                    MotionEvent.ACTION_UP -> {
                        // Confirm selection
                        val rect = android.graphics.Rect(
                            currentRect.left.toInt(), currentRect.top.toInt(),
                            currentRect.right.toInt(), currentRect.bottom.toInt()
                        )
                        sendBroadcast(Intent(ACTION_REGION).putExtra(EXTRA_RECT, rect))
                        finish()
                    }
                }
                return true
            }
        }

        setContentView(overlay)
    }
}
