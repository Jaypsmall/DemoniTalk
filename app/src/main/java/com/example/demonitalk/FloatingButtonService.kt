package com.example.demonitalk

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import java.util.Locale

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var commandHandler: CommandHandler
    private lateinit var repository: CommandRepository
    private lateinit var audioManager: AudioManager
    
    private var originalSystemVolume: Int = -1
    private var isContinuousMode = false
    private var isVigilanceMode = false
    private var isListening = false
    private var isWaitingForCommandAfterWake = false
    private var isServiceDestroyed = false
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val NOTIFICATION_ID = 123
    private val CHANNEL_ID = "DemoniTalk_Silent_v3"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            when (action) {
                "ACTION_MODE_YELLOW" -> {
                    isContinuousMode = false; isVigilanceMode = false; isWaitingForCommandAfterWake = true
                    startListening()
                }
                "ACTION_MODE_BLUE" -> {
                    isVigilanceMode = true; isContinuousMode = false; isWaitingForCommandAfterWake = false
                    updateButtonUI()
                    startListening()
                }
                "ACTION_MODE_GREEN" -> {
                    isContinuousMode = true; isVigilanceMode = false; isWaitingForCommandAfterWake = false
                    updateButtonUI()
                    startListening()
                }
                "ACTION_MODE_RED" -> {
                    stopEverything()
                }
                "ACTION_STOP_SERVICE" -> stopSelf()
            }
        }
        return START_STICKY
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isServiceDestroyed = false
        createNotificationChannel()
        startForegroundService()

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR)
                try { tts.language = Locale.getDefault() } catch (e: Exception) { }
        }

        repository = CommandRepository(this)
        commandHandler = CommandHandler(this)
        commandHandler.setInternalListener { action ->
            mainHandler.post {
                when (action) {
                    "internal_stop" -> stopEverything()
                    "internal_continuous_on" -> if (!isContinuousMode) {
                        isContinuousMode = true; isVigilanceMode = false
                        updateButtonUI(); startListening()
                    }
                }
            }
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Solo intentamos crear la vista si tenemos el permiso
        if (android.provider.Settings.canDrawOverlays(this)) {
            createFloatingView()
        } else {
            Log.i("DemoniTalk", "Iniciando servicio sin interfaz flotante (falta permiso de superposición)")
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 460; params.y = 948
        
        try {
            windowManager.addView(floatingView, params)
            
            floatingView.findViewById<ImageView>(R.id.mic_button).setOnTouchListener(object : View.OnTouchListener {
                private var initialX: Int = 0; private var initialY: Int = 0
                private var initialTouchX: Float = 0f; private var initialTouchY: Float = 0f
                private var isMoving = false; private var clickCount = 0
                private val processClicksRunnable = Runnable {
                    when (clickCount) {
                        1 -> { isContinuousMode = false; isVigilanceMode = false; isWaitingForCommandAfterWake = true; startListening() }
                        2 -> { isVigilanceMode = !isVigilanceMode; isContinuousMode = false; updateButtonUI(); if(isVigilanceMode) startListening() else stopEverything() }
                        3 -> { isContinuousMode = !isContinuousMode; isVigilanceMode = false; updateButtonUI(); if(isContinuousMode) startListening() else stopEverything() }
                        4 -> { 
                            val intent = Intent(this@FloatingButtonService, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            startActivity(intent) 
                        }
                        5 -> stopSelf()
                    }
                    clickCount = 0
                }
                override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> { initialX = params.x; initialY = params.y; initialTouchX = event.rawX; initialTouchY = event.rawY; isMoving = false; return true }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt(); val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isMoving = true; mainHandler.removeCallbacks(processClicksRunnable); clickCount = 0
                                params.x = initialX + dx; params.y = initialY + dy
                                try { windowManager.updateViewLayout(floatingView, params) } catch(e: Exception) {}
                            }
                            return true
                        }
                        android.view.MotionEvent.ACTION_UP -> { if (!isMoving) { clickCount++; mainHandler.removeCallbacks(processClicksRunnable); mainHandler.postDelayed(processClicksRunnable, 350) }; return true }
                    }
                    return false
                }
            })
        } catch (e: Exception) {
            Log.e("DemoniTalk", "Error al añadir vista flotante: ${e.message}")
        }
    }

    private fun stopEverything() {
        isContinuousMode = false; isVigilanceMode = false; isListening = false; isWaitingForCommandAfterWake = false
        muteAudio(false)
        try { speechRecognizer?.stopListening(); speechRecognizer?.cancel() } catch (e: Exception) {}
        updateButtonUI()
    }

    private fun updateButtonUI() {
        if (!::floatingView.isInitialized) return
        val micButton = try { floatingView.findViewById<ImageView>(R.id.mic_button) } catch(e: Exception) { null } ?: return
        when {
            isWaitingForCommandAfterWake -> micButton.setImageResource(R.drawable.amarillo)
            isContinuousMode -> micButton.setImageResource(R.drawable.verde)
            isVigilanceMode -> micButton.setImageResource(R.drawable.azul)
            else -> micButton.setImageResource(R.drawable.rojo)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "DemoniTalk Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icono_demoni2)
            .setContentTitle("DemoniTalk Activo")
            .setContentText("Escuchando comandos de voz...")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("DemoniTalk", "Servicio Foreground iniciado correctamente")
        } catch (e: Exception) {
            Log.e("DemoniTalk", "Error al iniciar Foreground Service: ${e.message}")
        }
    }

    private fun muteAudio(mute: Boolean) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) {
                return
            }

            if (mute) {
                if (originalSystemVolume == -1) originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            } else {
                mainHandler.postDelayed({ 
                    if (!isServiceDestroyed && originalSystemVolume != -1) {
                        try { audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0) } catch(e: Exception) {}
                    }
                }, 600)
            }
        } catch (e: Exception) { }
    }

    private fun setupSpeechRecognizer() {
        if (speechRecognizer != null) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("DemoniTalk", "Speech Recognition no disponible en este dispositivo")
            return
        }

        Log.d("DemoniTalk", "Configurando SpeechRecognizer...")
        try {
            // Usamos applicationContext para mayor estabilidad en servicios
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
            speechRecognizer?.setRecognitionListener(speechListener)
        } catch (e: Exception) {
            Log.e("DemoniTalk", "Error al crear SpeechRecognizer: ${e.message}")
        }
    }

    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("DemoniTalk", ">>> RECOGNIZER READY: ¡Habla ahora! <<<")
            isListening = true
            updateButtonUI()
        }
        override fun onBeginningOfSpeech() {
            Log.d("DemoniTalk", ">>> EMPEZÓ A HABLAR <<<")
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Log.d("DemoniTalk", ">>> FIN DE VOZ DETECTADO <<<")
            isListening = false
            muteAudio(true)
        }
        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red agotado"
                SpeechRecognizer.ERROR_NO_MATCH -> "No se encontró coincidencia (Silencio)"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
                else -> "Error desconocido: $error"
            }
            Log.e("DemoniTalk", "!!! ERROR RECONOCIMIENTO [$error]: $message !!!")

            isListening = false
            muteAudio(false)
            if (error == 9 || error == 5 || error == 3) {
                Log.w("DemoniTalk", "Reiniciando SpeechRecognizer por error crítico...")
                try { speechRecognizer?.destroy() } catch(e: Exception) {}
                speechRecognizer = null
            }
            if (isContinuousMode || isVigilanceMode) {
                mainHandler.postDelayed({ if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) startListening() }, 1000)
            } else {
                isWaitingForCommandAfterWake = false; updateButtonUI()
            }
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            muteAudio(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d("DemoniTalk", "Resultados obtenidos: $matches")

            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.i("DemoniTalk", "Texto reconocido: $text")
                val result = commandHandler.execute(text, repository.loadCommands(), isVigilanceMode && !isWaitingForCommandAfterWake)
                
                if (isWaitingForCommandAfterWake) {
                    isWaitingForCommandAfterWake = false
                    isVigilanceMode = false
                } else if (isVigilanceMode && result == CommandHandler.CommandResult.WakeWordOnly) {
                    Log.d("DemoniTalk", "Wake word detectado, esperando comando...")
                    handleWakeWordResponse()
                    return
                }
            }

            isWaitingForCommandAfterWake = false
            if (isContinuousMode || isVigilanceMode) {
                mainHandler.postDelayed({ if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) startListening() }, 500)
            } else { updateButtonUI() }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partial.isNullOrEmpty()) {
                Log.v("DemoniTalk", "Resultado parcial: ${partial[0]}")
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListening() {
        if (isServiceDestroyed) return

        mainHandler.post {
            if (isListening) {
                Log.w("DemoniTalk", "Intento de escuchar mientras ya se está escuchando. Cancelando sesión previa...")
                try { speechRecognizer?.cancel() } catch(e: Exception) {}
                isListening = false
            }

            try {
                setupSpeechRecognizer()
                
                if (speechRecognizer == null) {
                    Log.e("DemoniTalk", "No se pudo iniciar escucha: SpeechRecognizer es NULL")
                    return@post
                }

                // Limpiar cualquier estado previo antes de empezar
                speechRecognizer?.cancel()

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                    // Quitamos temporalmente el modo offline para diagnosticar el Error 5
                    // putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                
                Log.d("DemoniTalk", ">>> LLAMANDO A startListening() <<<")
                muteAudio(true)
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e("DemoniTalk", "Error al iniciar captura de voz: ${e.message}")
                isListening = false
                muteAudio(false)
            }
        }
    }

    private fun handleWakeWordResponse() {
        if (isServiceDestroyed) return
        isWaitingForCommandAfterWake = true
        val response = listOf("¿Abrimos el Super?", "Que dice mi socio", "Dime", "como esta la cosa Bro", "Soy todo oídos", "Que dice mi Amo", "¿Necesitas algo?", "¿Qué sacrificio pides?", "Tus deseos son órdenes", "Habla", "Te escucho", "¿Qué hay?", "Ordena").random()
        mainHandler.post { updateButtonUI() }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { isListening = false }
            override fun onDone(id: String?) { mainHandler.post { if (!isServiceDestroyed) startListening() } }
            override fun onError(id: String?) { mainHandler.post { if (!isServiceDestroyed) startListening() } }
        })
        tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "DemoniWake")
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        super.onDestroy()
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        if (::floatingView.isInitialized) try { windowManager.removeView(floatingView) } catch(e: Exception) {}
        speechRecognizer?.destroy(); speechRecognizer = null
    }
}
