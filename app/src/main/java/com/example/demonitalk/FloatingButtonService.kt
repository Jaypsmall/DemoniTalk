package com.example.demonitalk

import android.annotation.SuppressLint
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
import android.speech.tts.TextToSpeech
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
    private lateinit var tts: TextToSpeech
    private lateinit var commandHandler: CommandHandler
    private lateinit var repository: CommandRepository
    private lateinit var audioManager: AudioManager
    private var originalSystemVolume: Int = -1
    private var isContinuousMode = false
    private var isVigilanceMode = false
    private var isListening = false
    private val NOTIFICATION_ID = 123
    private val CHANNEL_ID = "DemoniTalk_Silent_v3"

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.getDefault()
            }
        }
        
        repository = CommandRepository(this)
        commandHandler = CommandHandler(this)
        commandHandler.setInternalListener { action ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val micButton = floatingView.findViewById<ImageView>(R.id.mic_button)
                when (action) {
                    "internal_continuous_on" -> if (!isContinuousMode) toggleContinuousMode(micButton)
                    "internal_continuous_off" -> if (isContinuousMode) toggleContinuousMode(micButton)
                    "internal_force_manual" -> {
                        isVigilanceMode = false
                        isContinuousMode = false
                        micButton.setBackgroundResource(R.drawable.bg_floating_button_active)
                    }
                }
            }
        }
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        // --- AQUÍ CAMBIAS LAS COORDENADAS ---
        params.x = 460 // Distancia desde la izquierda
        params.y = 948 // Distancia desde arriba
        // -------------------------------------

        windowManager.addView(floatingView, params)
        setupSpeechRecognizer()

        val micButton = floatingView.findViewById<ImageView>(R.id.mic_button).also {
            it.setOnTouchListener(object : View.OnTouchListener {
                private var initialX: Int = 0
                private var initialY: Int = 0
                private var initialTouchX: Float = 0.0f
                private var initialTouchY: Float = 0.0f
                private var isMoving = false
                private var clickCount = 0
                private val handler = android.os.Handler(android.os.Looper.getMainLooper())

                private val processClicksRunnable = Runnable {
                    when (clickCount) {
                        1 -> { // MODO MANUAL (AMARILLO)
                            isContinuousMode = false
                            isVigilanceMode = false
                            startListening()
                        }
                        2 -> { // MODO VIGILANCIA (AZUL - "DEMONI")
                            toggleVigilanceMode(it)
                        }
                        3 -> { // MODO CONTINUO (VERDE)
                            toggleContinuousMode(it)
                        }
                        4 -> {
                            val intent = Intent(this@FloatingButtonService, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }
                        5 -> { stopSelf() }
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
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (originalSystemVolume != -1) {
                        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                    }
                }, 600)
            }
        } catch (e: Exception) { }
    }

    private fun toggleContinuousMode(view: ImageView) {
        isContinuousMode = !isContinuousMode
        isVigilanceMode = false
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

    private fun toggleVigilanceMode(view: ImageView) {
        isVigilanceMode = !isVigilanceMode
        isContinuousMode = false
        if (isVigilanceMode) {
            muteAudio(true)
            view.setBackgroundResource(R.drawable.bg_floating_button_vigilance)
            Toast.makeText(this, "Modo Vigilancia 'Demoni' 🔵", Toast.LENGTH_SHORT).show()
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
                if (!isContinuousMode && !isVigilanceMode) muteAudio(false)
                val micButton = floatingView.findViewById<ImageView>(R.id.mic_button)
                when {
                    isVigilanceMode -> micButton.setBackgroundResource(R.drawable.bg_floating_button_vigilance)
                    isContinuousMode -> micButton.setBackgroundResource(R.drawable.bg_floating_button_continuous)
                    else -> micButton.setBackgroundResource(R.drawable.bg_floating_button_active)
                }
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
                if (isContinuousMode || isVigilanceMode) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isContinuousMode || isVigilanceMode) startListening()
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
                    val recognizedText = matches[0]
                    android.util.Log.d("DemoniTalk", "Recognized: $recognizedText")
                    
                    val result = commandHandler.execute(recognizedText, repository.loadCommands(), requireWakeWord = isVigilanceMode)
                    
                    if (isVigilanceMode && result == CommandHandler.CommandResult.WakeWordOnly) {
                        val responses = listOf(
                            "Dime, alma perdida", 
                            "Soy todo oídos, mortal", 
                            "¿Qué sacrificio pides?", 
                            "Tus deseos son órdenes en el infierno", 
                            "Habla, antes de que me arrepienta", 
                            "Te escucho, pequeño humano",
                            "¿Qué oscuridad traes hoy?",
                            "Ordena, si te atreves"
                        )
                        val response = responses.random()
                        
                        // Aseguramos volumen para oír la respuesta
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
                        
                        tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "DemoniWake")
                        
                        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    isVigilanceMode = false 
                                    startListening()
                                }
                            }
                            override fun onError(utteranceId: String?) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post { startListening() }
                            }
                        })
                        return
                    }
                }

                if (isContinuousMode || isVigilanceMode) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isContinuousMode || isVigilanceMode) startListening()
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
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
    }
}
