package com.example.demonitalk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class FloatingUiManager(
    private val context: Context,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onTripleTap: () -> Unit,
    private val onQuadrupleTap: () -> Unit,
    private val onQuintupleTap: () -> Unit
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isViewCreated(): Boolean = floatingView != null

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun createFloatingView() {
        if (floatingView != null) return
        if (!Settings.canDrawOverlays(context)) return

        try {
            val inflater = LayoutInflater.from(context)
            floatingView = inflater.inflate(R.layout.layout_floating_button, null)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }

            windowManager.addView(floatingView, params)

            val micButton = floatingView?.findViewById<ImageView>(R.id.mic_button) ?: return

            micButton.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isMoving = false
                private var clickCount = 0

                private val processClicksRunnable = Runnable {
                    when (clickCount) {
                        1 -> onSingleTap()
                        2 -> onDoubleTap()
                        3 -> onTripleTap()
                        4 -> onQuadrupleTap()
                        5 -> onQuintupleTap()
                    }
                    clickCount = 0
                }

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isMoving = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()

                            if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                                isMoving = true
                                mainHandler.removeCallbacks(processClicksRunnable)
                                clickCount = 0
                                params.x = initialX + dx
                                params.y = initialY + dy

                                try {
                                    floatingView?.let { windowManager.updateViewLayout(it, params) }
                                } catch (e: Exception) {
                                    Log.e("FloatingUiManager", "Error moviendo botón: ${e.message}")
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isMoving) {
                                clickCount++
                                mainHandler.removeCallbacks(processClicksRunnable)
                                mainHandler.postDelayed(processClicksRunnable, 350)
                            }
                            return true
                        }
                    }
                    return false
                }
            })

        } catch (e: Exception) {
            Log.e("FloatingUiManager", "Error al añadir vista flotante: ${e.message}", e)
            floatingView = null
        }
    }

    fun updateButtonUI(
        isWaitingForCommandAfterWake: Boolean,
        isContinuousMode: Boolean,
        isVigilanceMode: Boolean
    ) {
        val view = floatingView ?: return
        val micButton = view.findViewById<ImageView>(R.id.mic_button) ?: return

        try {
            when {
                isWaitingForCommandAfterWake -> micButton.setImageResource(R.drawable.amarillo)
                isContinuousMode -> micButton.setImageResource(R.drawable.verde)
                isVigilanceMode -> micButton.setImageResource(R.drawable.azul)
                else -> micButton.setImageResource(R.drawable.rojo)
            }
        } catch (e: Exception) {
            Log.e("FloatingUiManager", "Error actualizando botón: ${e.message}")
        }
    }

    fun destroy() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        floatingView = null
        mainHandler.removeCallbacksAndMessages(null)
    }
}
