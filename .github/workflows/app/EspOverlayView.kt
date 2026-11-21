package com.robrun.esp

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class EspOverlayView(context: Context) : View(context) {

    // ── Paints ──────────────────────────────────────────────────────────────
    private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 3, 7, 18) }
    private val hdrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 13, 17, 23) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 33, 38, 45); style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 230, 237, 243); textSize = 28f
        typeface = Typeface.MONOSPACE
    }
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 125, 133, 144); textSize = 22f
        typeface = Typeface.MONOSPACE
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 88, 166, 255); textSize = 24f
        typeface = Typeface.MONOSPACE; isFakeBoldText = true
    }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 22, 27, 34) }
    private val dotPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val divPaint   = Paint().apply { color = Color.argb(100, 33, 38, 45) }

    // ── State ───────────────────────────────────────────────────────────────
    var probs: List<ColorProb>  = emptyList()
    var lastResult: RollResult? = null
    var streak: Streak?         = null
    var numStats: NumberStats?  = null
    var isMinimized: Boolean    = false

    // ── Drag ────────────────────────────────────────────────────────────────
    private var dragStartX = 0f
    private var dragStartY = 0f
    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null
    var onMinToggle: (() -> Unit)? = null

    private val cornerR = 14f
    private val w = 320  // dp-ish, in px we scale by density
    private val density get() = resources.displayMetrics.density

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val px = (w * density).roundToInt()
        val hPx = if (isMinimized) (48 * density).roundToInt()
                  else (220 * density).roundToInt()
        setMeasuredDimension(px, hPx)
    }

    override fun onDraw(canvas: Canvas) {
        val d = density
        val W = width.toFloat()
        val H = height.toFloat()
        val pad = 10 * d

        // Background
        val bgRect = RectF(0f, 0f, W, H)
        canvas.drawRoundRect(bgRect, cornerR, cornerR, bgPaint)
        canvas.drawRoundRect(bgRect, cornerR, cornerR, borderPaint)

        // Header bar
        val hdrRect = RectF(0f, 0f, W, 32 * d)
        canvas.drawRoundRect(hdrRect, cornerR, cornerR, hdrPaint)
        canvas.drawRect(0f, 16 * d, W, 32 * d, hdrPaint)

        // Title
        accentPaint.textSize = 22f * d / 3f
        canvas.drawText("ROB RUN", pad, 22 * d, accentPaint)
        mutedPaint.textSize = 18f * d / 3f
        canvas.drawText("ESP", pad + accentPaint.measureText("ROB RUN "), 22 * d, mutedPaint)

        // Min button
        textPaint.textSize = 24f * d / 3f
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(if (isMinimized) "▽" else "△", W - pad, 22 * d, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        if (isMinimized) {
            // Show only last result color dot on header
            lastResult?.let {
                dotPaint.color = ProbabilityEngine.COLOR_MAP[it.color] ?: Color.GRAY
                canvas.drawCircle(W / 2f, 16 * d, 8 * d, dotPaint)
            }
            return
        }

        var y = 38 * d

        // ── Last result ────────────────────────────────────────────────────
        lastResult?.let { r ->
            val hex = ProbabilityEngine.COLOR_MAP[r.color] ?: Color.GRAY
            dotPaint.color = hex
            canvas.drawCircle(pad + 7 * d, y + 7 * d, 7 * d, dotPaint)
            textPaint.color = hex
            textPaint.textSize = 24f * d / 3f
            canvas.drawText(r.color, pad + 20 * d, y + 13 * d, textPaint)
            r.number?.let { n ->
                mutedPaint.textSize = 20f * d / 3f
                canvas.drawText("· $n", pad + 20 * d + textPaint.measureText(r.color) + 4 * d, y + 13 * d, mutedPaint)
            }
            streak?.let { s ->
                if (s.count >= 2) {
                    mutedPaint.textSize = 18f * d / 3f
                    canvas.drawText("streak ${s.count}×", W - pad - mutedPaint.measureText("streak ${s.count}×"), y + 13 * d, mutedPaint)
                }
            }
        } ?: run {
            mutedPaint.textSize = 20f * d / 3f
            canvas.drawText("aguardando...", pad, y + 13 * d, mutedPaint)
        }
        y += 22 * d

        // Divider
        canvas.drawLine(pad, y, W - pad, y, divPaint)
        y += 8 * d

        // ── Probability bars ───────────────────────────────────────────────
        val barLeft  = pad + 70 * d
        val barRight = W - pad - 36 * d
        val barW     = barRight - barLeft
        val barH     = 8 * d

        for (cp in probs.take(3)) {
            dotPaint.color = cp.hex
            canvas.drawCircle(pad + 5 * d, y + 5 * d, 5 * d, dotPaint)

            mutedPaint.textSize = 18f * d / 3f
            mutedPaint.color = cp.hex
            canvas.drawText(cp.name.take(5), pad + 13 * d, y + 10 * d, mutedPaint)
            mutedPaint.color = Color.argb(255, 125, 133, 144)

            // Bar bg
            canvas.drawRoundRect(RectF(barLeft, y, barLeft + barW, y + barH), 4f, 4f, barBgPaint)
            // Bar fill
            barPaint.color = cp.hex
            barPaint.alpha = 200
            canvas.drawRoundRect(RectF(barLeft, y, barLeft + barW * cp.prob, y + barH), 4f, 4f, barPaint)

            // Pct
            textPaint.color = cp.hex
            textPaint.textSize = 20f * d / 3f
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${(cp.prob * 100).roundToInt()}%", W - pad, y + 10 * d, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.argb(255, 230, 237, 243)

            y += 18 * d
        }

        // Divider
        canvas.drawLine(pad, y, W - pad, y, divPaint)
        y += 8 * d

        // ── Number stats ───────────────────────────────────────────────────
        numStats?.let { ns ->
            mutedPaint.textSize = 18f * d / 3f
            val oddPct  = if (ns.total > 0) (ns.odds.toFloat()  / ns.total * 100).roundToInt() else 0
            val evenPct = if (ns.total > 0) (ns.evens.toFloat() / ns.total * 100).roundToInt() else 0

            textPaint.color = Color.argb(255, 230, 237, 243)
            textPaint.textSize = 20f * d / 3f
            canvas.drawText("ím:$oddPct%  par:$evenPct%", pad, y + 12 * d, textPaint)

            // Hot chips
            if (ns.hot.isNotEmpty()) {
                mutedPaint.color = Color.argb(255, 255, 71, 87)
                canvas.drawText("hot:" + ns.hot.take(3).joinToString(" "), W / 2f, y + 12 * d, mutedPaint)
                mutedPaint.color = Color.argb(255, 125, 133, 144)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val hdrH = 32 * density
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartX).roundToInt()
                val dy = (event.rawY - dragStartY).roundToInt()
                onDrag?.invoke(dx, dy)
                dragStartX = event.rawX
                dragStartY = event.rawY
                true
            }
            MotionEvent.ACTION_UP -> {
                // Tap on header = toggle minimize
                if (event.y < hdrH && Math.abs(event.rawX - dragStartX) < 10) {
                    onMinToggle?.invoke()
                }
                true
            }
            else -> false
        }
    }
}
