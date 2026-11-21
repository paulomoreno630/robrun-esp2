package com.robrun.esp

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.PixelCopy
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OverlayService : Service() {

    companion object {
        const val ACTION_START   = "START"
        const val ACTION_STOP    = "STOP"
        const val ACTION_PICK    = "PICK_REGION"
        const val EXTRA_CODE     = "result_code"
        const val EXTRA_DATA     = "result_data"
        const val CHANNEL_ID     = "robrun_esp"
        const val NOTIF_ID       = 1
        const val CAPTURE_INTERVAL_MS = 1500L
    }

    // ── Core objects ─────────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null

    private val engine = ProbabilityEngine()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val handler = Handler(Looper.getMainLooper())
    private var captureZone: Rect? = null   // screen region to OCR
    private var lastColorSeen: String? = null
    private var lastNumberSeen: Int? = null
    private var isCaptureRunning = false

    // ── Overlay view ─────────────────────────────────────────────────────
    private var overlayView: EspOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayX = 20
    private var overlayY = 100

    // ── Region picker broadcast ─────────────────────────────────────────
    private val regionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(RegionPickerActivity.EXTRA_RECT, Rect::class.java)
            else
                @Suppress("DEPRECATION") intent.getParcelableExtra(RegionPickerActivity.EXTRA_RECT)
            rect?.let {
                captureZone = it
                showToast("Zona capturada ✓")
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        registerReceiver(regionReceiver, IntentFilter(RegionPickerActivity.ACTION_REGION))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
                val code = intent.getIntExtra(EXTRA_CODE, -1)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
                if (code != -1 && data != null) {
                    mediaProjection = projectionManager.getMediaProjection(code, data)
                    setupCapture()
                    setupOverlay()
                    startCaptureLoop()
                }
            }
            ACTION_STOP -> stopSelf()
            ACTION_PICK -> {
                startActivity(
                    Intent(this, RegionPickerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isCaptureRunning = false
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        recognizer.close()
        overlayView?.let { windowManager.removeView(it) }
        unregisterReceiver(regionReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    // ── Screen capture setup ──────────────────────────────────────────
    private fun setupCapture() {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val dpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "RobRunCapture",
            w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
    }

    // ── Overlay setup ─────────────────────────────────────────────────
    private fun setupOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = overlayX; y = overlayY
        }

        overlayView = EspOverlayView(this).apply {
            onDrag = { dx, dy ->
                overlayX += dx; overlayY += dy
                overlayParams!!.x = overlayX; overlayParams!!.y = overlayY
                windowManager.updateViewLayout(this, overlayParams)
            }
            onMinToggle = {
                isMinimized = !isMinimized
                windowManager.updateViewLayout(this, overlayParams)
                invalidate()
            }
        }
        windowManager.addView(overlayView, overlayParams)
    }

    // ── Capture loop ──────────────────────────────────────────────────
    private fun startCaptureLoop() {
        isCaptureRunning = true
        handler.post(captureRunnable)
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!isCaptureRunning) return
            captureAndProcess()
            handler.postDelayed(this, CAPTURE_INTERVAL_MS)
        }
    }

    private fun captureAndProcess() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride   = planes[0].rowStride
            val rowPadding  = rowStride - pixelStride * image.width

            var bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to capture zone if set
            captureZone?.let { zone ->
                val safeLeft   = zone.left.coerceIn(0, bitmap.width - 1)
                val safeTop    = zone.top.coerceIn(0, bitmap.height - 1)
                val safeRight  = zone.right.coerceIn(safeLeft + 1, bitmap.width)
                val safeBottom = zone.bottom.coerceIn(safeTop + 1, bitmap.height)
                bitmap = Bitmap.createBitmap(bitmap, safeLeft, safeTop,
                    safeRight - safeLeft, safeBottom - safeTop)
            }

            processFrame(bitmap)
        } finally {
            image.close()
        }
    }

    private fun processFrame(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text
                if (raw.isBlank()) return@addOnSuccessListener

                val parsed = engine.parseOcrText(raw) ?: return@addOnSuccessListener

                // Only add if result changed from last seen
                val changed = parsed.color != lastColorSeen || parsed.number != lastNumberSeen
                if (changed) {
                    lastColorSeen  = parsed.color
                    lastNumberSeen = parsed.number
                    engine.addResult(parsed)
                    updateOverlay()
                }
            }
            .addOnFailureListener { /* silent — next frame */ }
    }

    // ── Update overlay UI ─────────────────────────────────────────────
    private fun updateOverlay() {
        handler.post {
            overlayView?.apply {
                probs      = engine.colorProbabilities()
                lastResult = engine.history.lastOrNull()
                streak     = engine.currentStreak()
                numStats   = engine.numberStats()
                invalidate()
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Rob Run ESP", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val pickIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_PICK },
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Rob Run ESP ativo")
            .setContentText("Lendo tela — toque para configurar")
            .addAction(android.R.drawable.ic_menu_crop, "Zona", pickIntent)
            .addAction(android.R.drawable.ic_delete, "Parar", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun showToast(msg: String) {
        handler.post { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show() }
    }
}
