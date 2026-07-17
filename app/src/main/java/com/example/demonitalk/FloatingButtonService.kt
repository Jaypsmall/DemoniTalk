package com.example.demonitalk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var commandHandler: CommandHandler
    private lateinit var repository: CommandRepository
    private lateinit var audioManager: AudioManager
    private var originalSystemVolume: Int = -1
    private var isContinuousMode = false
    private var isListening = false
    private val NOTIFICATION_ID = 123
    private val CHANNEL_ID = "DemoniTalk_Silent_v3"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        
        repository = CommandRepository(this)
        commandHandler = CommandHandler(this)
        commandHandler.setInternalListener { action ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val micButton = floatingView.findViewById<ImageView>(R.id.mic_button)
                when (action) {
                    "internal_continuous_on" -> if (!isContinuousMode) toggleContinuousMode(micButton)
                    "internal_continuous_off" -> if (isContinuousMode) toggleContinuousMode(micButton)
                }
            }
        }
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 923
        params.y = 890

        windowManager.addView(floatingView, params)
        setupSpeechRecognizer()

        val micButton = floatingView.findViewById<ImageView>(R.id.mic_button)
        micButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0.0f
            private var initialTouchY: Float = 0.0f
            private var isMoving = false
            private var clickCount = 0
            private val handler = android.os.Handler(android.os.Looper.getMainLooper())
            
            private val processClicksRunnable = Runnable {
                when (clickCount) {
                    1 -> { if (!isContinuousMode) startListening() else toggleContinuousMode(micButton) }
                    2 -> toggleContinuousMode(micButton)
                    3 -> {
                        val intent = Intent(this@FloatingButtonService, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    4 -> { stopSelf() }
                }
                clickCount = 0
            }

            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMoving = true
                            handler.removeCallbacks(processClicksRunnable)
                            clickCount = 0
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            clickCount++
                            handler.removeCallbacks(processClicksRunnable)
                            handler.postDelayed(processClicksRunnable, 350)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "DemoniTalk Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icono_demoni)
            .setContentTitle("DemoniTalk Activo")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun muteAudio(mute: Boolean) {
        try {
            if (mute) {
                if (originalSystemVolume == -1) originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!isContinuousMode && originalSystemVolume != -1) {
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                    }
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2, 0)
                }, 600)
            }
        } catch (e: Exception) { }
    }

    private fun toggleContinuousMode(view: ImageView) {
        isContinuousMode = !isContinuousMode
        if (isContinuousMode) {
            muteAudio(true)
            view.setBackgroundResource(R.drawable.bg_floating_button_continuous)
            Toast.makeText(this, "Modo Continuo 🟢", Toast.LENGTH_SHORT).show()
            startListening()
        } else {
            isListening = false
            muteAudio(false)
            view.setBackgroundResource(R.drawable.bg_floating_button)
            speechRecognizer.cancel()
            Toast.makeText(this, "Modo Manual 🔴", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSpeechRecognizer() {
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                if (!isContinuousMode) muteAudio(false)
                val micButton = floatingView.findViewById<ImageView>(R.id.mic_button)
                micButton.setBackgroundResource(if (isContinuousMode) R.drawable.bg_floating_button_continuous else R.drawable.bg_floating_button_active)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { 
                isListening = false 
                muteAudio(true)
            }
            override fun onError(error: Int) {
                isListening = false
                muteAudio(false)
                android.util.Log.e("DemoniTalk", "Speech Error: $error")
                if (isContinuousMode) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isContinuousMode) startListening()
                    }, 1000)
                } else {
                    floatingView.findViewById<ImageView>(R.id.mic_button).setBackgroundResource(R.drawable.bg_floating_button)
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                muteAudio(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    android.util.Log.d("DemoniTalk", "Recognized: ${matches[0]}")
                    commandHandler.execute(matches[0], repository.loadCommands())
                }
                if (isContinuousMode) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isContinuousMode) startListening()
                    }, 800)
                } else {
                    floatingView.findViewById<ImageView>(R.id.mic_button).setBackgroundResource(R.drawable.bg_floating_button)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (isListening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 30000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 30000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30000L)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                muteAudio(true)
                speechRecognizer.cancel()
                speechRecognizer.startListening(intent)
            }
        } catch (e: Exception) {
            isListening = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
    }
}
