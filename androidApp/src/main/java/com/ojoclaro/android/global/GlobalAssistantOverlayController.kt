package com.ojoclaro.android.global

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class GlobalAssistantOverlayController(
    private val context: Context,
    private val onListen: () -> Unit,
    private val onSilence: () -> Unit,
    private val onStop: () -> Unit
) {
    private val appContext = context.applicationContext
    private val windowManager: WindowManager? =
        appContext.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null

    fun show(snapshot: ExternalConversationSnapshot): Boolean {
        if (!canDrawOverlay(appContext)) return false
        val manager = windowManager ?: return false
        hide()

        val view = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            background = ContextCompat.getDrawable(appContext, android.R.drawable.dialog_holo_light_frame)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Ojo Claro activo. Botones Escuchar, Callar y Detener."
        }

        val title = TextView(appContext).apply {
            text = "Ojo Claro activo"
            textSize = 16f
        }
        val listen = Button(appContext).apply {
            text = "Escuchar"
            setOnClickListener { onListen() }
        }
        val silence = Button(appContext).apply {
            text = "Callar"
            setOnClickListener { onSilence() }
        }
        val stop = Button(appContext).apply {
            text = "Detener"
            setOnClickListener { onStop() }
        }

        view.addView(title)
        view.addView(listen)
        view.addView(silence)
        view.addView(stop)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 160
        }

        return runCatching {
            manager.addView(view, params)
            overlayView = view
        }.isSuccess
    }

    fun hide() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { windowManager?.removeView(view) }
    }

    companion object {
        fun canDrawOverlay(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}
