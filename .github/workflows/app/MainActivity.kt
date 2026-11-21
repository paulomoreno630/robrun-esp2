package com.robrun.esp

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {

    companion object {
        const val REQ_OVERLAY    = 100
        const val REQ_PROJECTION = 101
    }

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // ── Simple programmatic UI ─────────────────────────────────────
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#030712"))
        }

        fun tv(text: String, size: Float = 14f, color: Int = 0xFFE6EDF3.toInt()) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        fun btn(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setBackgroundColor(android.graphics.Color.parseColor("#161b22"))
            setTextColor(android.graphics.Color.parseColor("#58a6ff"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            setOnClickListener { action() }
        }

        root.addView(tv("ROB RUN ESP", 22f, 0xFF58A6FF.toInt()))
        root.addView(Space(this).apply { minimumHeight = 8 })
        root.addView(tv("Overlay automático sobre o jogo", 12f, 0xFF7D8590.toInt()))
        root.addView(Space(this).apply { minimumHeight = 32 })

        statusTv = tv("Status: aguardando permissões", 12f, 0xFFf0883e.toInt())
        root.addView(statusTv)
        root.addView(Space(this).apply { minimumHeight = 24 })

        root.addView(btn("1. Permitir overlay (janela flutuante)") { requestOverlayPermission() })
        root.addView(Space(this).apply { minimumHeight = 12 })
        root.addView(btn("2. Iniciar ESP (captura de tela)") { requestProjection() })
        root.addView(Space(this).apply { minimumHeight = 12 })
        root.addView(btn("3. Definir zona de captura") { pickRegion() })
        root.addView(Space(this).apply { minimumHeight = 12 })
        root.addView(btn("Parar ESP") { stopService(Intent(this@MainActivity, OverlayService::class.java)) })

        root.addView(Space(this).apply { minimumHeight = 32 })
        root.addView(tv("""
Como usar:
 1. Toque em "Permitir overlay"
 2. Toque em "Iniciar ESP" → aceite captura
 3. Abra o Rob Run
 4. Volte aqui → "Definir zona"
    (arraste sobre a área onde aparece
     a cor/número do resultado)
 5. Pronto — ESP lê sozinho
        """.trimIndent(), 11f, 0xFF7D8590.toInt()))

        setContentView(root)
        checkStatus()
    }

    private fun checkStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        statusTv.text = when {
            !hasOverlay -> "Status: precisa de permissão de overlay"
            else        -> "Status: pronto — toque em Iniciar ESP"
        }
        statusTv.setTextColor(
            if (hasOverlay) 0xFF3fb950.toInt() else 0xFFf0883e.toInt()
        )
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
        } else {
            Toast.makeText(this, "Overlay já permitido ✓", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestProjection() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permita o overlay primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    private fun pickRegion() {
        startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_PICK })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY    -> checkStatus()
            REQ_PROJECTION -> {
                if (resultCode == RESULT_OK && data != null) {
                    startService(
                        Intent(this, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_START
                            putExtra(OverlayService.EXTRA_CODE, resultCode)
                            putExtra(OverlayService.EXTRA_DATA, data)
                        }
                    )
                    Toast.makeText(this, "ESP iniciado — abra o Rob Run", Toast.LENGTH_SHORT).show()
                    // Bring game to front — user opens game manually
                }
            }
        }
    }
}
