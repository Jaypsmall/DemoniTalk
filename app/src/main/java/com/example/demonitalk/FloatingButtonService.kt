package com.example.demonitalk

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var commandHandler: CommandHandler
    private lateinit var repository: CommandRepository
    private lateinit var audioManager: AudioManager
    private lateinit var aiAssistant: AiAssistant

    private var originalSystemVolume: Int = -1

    private var isContinuousMode = false
    private var isVigilanceMode = false
    private var isListening = false
    private var isWaitingForCommandAfterWake = false
    private var isServiceDestroyed = false
    private var isTtsReady = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val notificationId = 123
    private val channelId = "DemoniTalk_Silent_v3"

    /*
     * Evita que varias llamadas retrasadas a startListening()
     * se acumulen después de errores o resultados.
     */
    private var restartRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (isServiceDestroyed) return START_NOT_STICKY

        if (floatingView == null && android.provider.Settings.canDrawOverlays(this)) {
            createFloatingView()
        }

        when (intent?.action) {

            "ACTION_MODE_YELLOW" -> {
                stopPendingRestart()

                isContinuousMode = false
                isVigilanceMode = false
                isWaitingForCommandAfterWake = true

                updateButtonUI()
                startListening()
            }

            "ACTION_MODE_BLUE" -> {
                stopPendingRestart()

                isContinuousMode = false
                isVigilanceMode = true
                isWaitingForCommandAfterWake = false

                updateButtonUI()
                startListening()
            }

            "ACTION_MODE_GREEN" -> {
                stopPendingRestart()

                isContinuousMode = true
                isVigilanceMode = false
                isWaitingForCommandAfterWake = false

                updateButtonUI()
                startListening()
            }

            "ACTION_MODE_RED" -> {
                stopEverything()
            }

            "ACTION_STOP_SERVICE" -> {
                stopEverything()
                stopSelf()
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

        audioManager =
            getSystemService(AUDIO_SERVICE) as AudioManager

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        /*
         * TextToSpeech
         */
        tts = TextToSpeech(this) { status ->

            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true

                try {
                    val result = tts.setLanguage(Locale.getDefault())

                    if (
                        result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.w(
                            "DemoniTalk",
                            "Idioma TTS no disponible: ${Locale.getDefault()}"
                        )
                    }

                } catch (e: Exception) {
                    Log.e(
                        "DemoniTalk",
                        "Error configurando TTS: ${e.message}"
                    )
                }

            } else {
                isTtsReady = false

                Log.e(
                    "DemoniTalk",
                    "No se pudo inicializar TextToSpeech"
                )
            }
        }

        /*
         * Comandos y Asistente IA
         */
        repository = CommandRepository(this)
        aiAssistant = AiAssistant(this)

        commandHandler = CommandHandler(this)

        commandHandler.setInternalListener { action ->

            mainHandler.post {

                if (isServiceDestroyed) return@post

                when (action) {

                    "internal_stop" -> {
                        stopEverything()
                    }

                    "internal_continuous_on", "internal_mode_green" -> {

                        stopPendingRestart()

                        isContinuousMode = true
                        isVigilanceMode = false
                        isWaitingForCommandAfterWake = false

                        updateButtonUI()
                        startListening()
                    }

                    "internal_mode_blue", "internal_vigilance_on" -> {

                        stopPendingRestart()

                        isContinuousMode = false
                        isVigilanceMode = true
                        isWaitingForCommandAfterWake = false

                        updateButtonUI()
                        startListening()
                    }

                    "internal_mode_yellow" -> {

                        stopPendingRestart()

                        isContinuousMode = false
                        isVigilanceMode = false
                        isWaitingForCommandAfterWake = true

                        updateButtonUI()
                        startListening()
                    }
                }
            }
        }

        /*
         * Interfaz flotante.
         *
         * Si Android no permite overlays, el servicio continúa
         * funcionando sin botón.
         */
        if (android.provider.Settings.canDrawOverlays(this)) {
            createFloatingView()
        } else {
            Log.i(
                "DemoniTalk",
                "Servicio iniciado sin interfaz flotante: falta permiso de superposición"
            )
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingView() {

        if (isServiceDestroyed) return

        if (floatingView != null) return

        floatingView = LayoutInflater
            .from(this)
            .inflate(R.layout.layout_floating_button, null)

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        try {

            windowManager.addView(floatingView, params)

            val micButton =
                floatingView?.findViewById<ImageView>(R.id.mic_button)

            if (micButton == null) {
                Log.e(
                    "DemoniTalk",
                    "No se encontró R.id.mic_button"
                )
                return
            }

            micButton.setOnTouchListener(
                object : View.OnTouchListener {

                    private var initialX = 0
                    private var initialY = 0

                    private var initialTouchX = 0f
                    private var initialTouchY = 0f

                    private var isMoving = false
                    private var clickCount = 0

                    private val processClicksRunnable =
                        Runnable {

                            when (clickCount) {

                                /*
                                 * 1 toque = amarillo
                                 */
                                1 -> {

                                    isContinuousMode = false
                                    isVigilanceMode = false
                                    isWaitingForCommandAfterWake = true

                                    updateButtonUI()
                                    startListening()
                                }

                                /*
                                 * 2 toques = azul
                                 */
                                2 -> {

                                    if (isVigilanceMode) {

                                        stopEverything()

                                    } else {

                                        isVigilanceMode = true
                                        isContinuousMode = false
                                        isWaitingForCommandAfterWake = false

                                        updateButtonUI()
                                        startListening()
                                    }
                                }

                                /*
                                 * 3 toques = verde
                                 */
                                3 -> {

                                    if (isContinuousMode) {

                                        stopEverything()

                                    } else {

                                        isContinuousMode = true
                                        isVigilanceMode = false
                                        isWaitingForCommandAfterWake = false

                                        updateButtonUI()
                                        startListening()
                                    }
                                }

                                /*
                                 * 4 toques = MainActivity
                                 */
                                4 -> {

                                    try {

                                        val intent =
                                            Intent(
                                                this@FloatingButtonService,
                                                MainActivity::class.java
                                            ).apply {
                                                addFlags(
                                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                                )
                                            }

                                        startActivity(intent)

                                    } catch (e: Exception) {

                                        Log.e(
                                            "DemoniTalk",
                                            "Error abriendo MainActivity: ${e.message}"
                                        )
                                    }
                                }

                                /*
                                 * 5 toques = detener servicio
                                 */
                                5 -> {

                                    stopEverything()
                                    stopSelf()
                                }
                            }

                            clickCount = 0
                        }

                    override fun onTouch(
                        v: View,
                        event: android.view.MotionEvent
                    ): Boolean {

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

                                val dx =
                                    (event.rawX - initialTouchX).toInt()

                                val dy =
                                    (event.rawY - initialTouchY).toInt()

                                if (
                                    kotlin.math.abs(dx) > 10 ||
                                    kotlin.math.abs(dy) > 10
                                ) {

                                    isMoving = true

                                    mainHandler.removeCallbacks(
                                        processClicksRunnable
                                    )

                                    clickCount = 0

                                    params.x = initialX + dx
                                    params.y = initialY + dy

                                    try {

                                        floatingView?.let {
                                            windowManager.updateViewLayout(
                                                it,
                                                params
                                            )
                                        }

                                    } catch (e: Exception) {

                                        Log.e(
                                            "DemoniTalk",
                                            "Error moviendo botón: ${e.message}"
                                        )
                                    }
                                }

                                return true
                            }

                            android.view.MotionEvent.ACTION_UP -> {

                                if (!isMoving) {

                                    clickCount++

                                    mainHandler.removeCallbacks(
                                        processClicksRunnable
                                    )

                                    mainHandler.postDelayed(
                                        processClicksRunnable,
                                        350
                                    )
                                }

                                return true
                            }
                        }

                        return false
                    }
                }
            )

            updateButtonUI()

        } catch (e: Exception) {

            Log.e(
                "DemoniTalk",
                "Error al añadir vista flotante: ${e.message}",
                e
            )

            floatingView = null
        }
    }

    private fun stopEverything() {

        isContinuousMode = false
        isVigilanceMode = false
        isListening = false
        isWaitingForCommandAfterWake = false

        stopPendingRestart()

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        muteAudio(false)

        updateButtonUI()
    }

    private fun updateButtonUI() {

        val view = floatingView ?: return

        val micButton =
            try {
                view.findViewById<ImageView>(R.id.mic_button)
            } catch (_: Exception) {
                null
            } ?: return

        try {

            when {

                isWaitingForCommandAfterWake ->
                    micButton.setImageResource(R.drawable.amarillo)

                isContinuousMode ->
                    micButton.setImageResource(R.drawable.verde)

                isVigilanceMode ->
                    micButton.setImageResource(R.drawable.azul)

                else ->
                    micButton.setImageResource(R.drawable.rojo)
            }

        } catch (e: Exception) {

            Log.e(
                "DemoniTalk",
                "Error actualizando botón: ${e.message}"
            )
        }
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val serviceChannel =
                NotificationChannel(
                    channelId,
                    "DemoniTalk Service",
                    NotificationManager.IMPORTANCE_LOW
                )

            serviceChannel.setSound(null, null)
            serviceChannel.enableVibration(false)

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {

        val notification =
            NotificationCompat.Builder(
                this,
                channelId
            )
                .setSmallIcon(R.drawable.icono_demoni2)
                .setContentTitle("DemoniTalk Activo")
                .setContentText("Escuchando comandos de voz...")
                .setSilent(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )

            } else {

                startForeground(
                    notificationId,
                    notification
                )
            }

            Log.d(
                "DemoniTalk",
                "Foreground Service iniciado correctamente"
            )

        } catch (e: Exception) {

            Log.e(
                "DemoniTalk",
                "Error al iniciar Foreground Service: ${e.message}",
                e
            )
        }
    }

    private fun muteAudio(mute: Boolean) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted) {
                    return
                }
            }

            if (mute) {
                if (originalSystemVolume == -1) {
                    originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                }
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            } else {
                if (originalSystemVolume != -1) {
                    val volumeToRestore = originalSystemVolume
                    originalSystemVolume = -1
                    mainHandler.postDelayed({
                        if (!isServiceDestroyed) {
                            try {
                                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, volumeToRestore, 0)
                            } catch (e: Exception) {
                                Log.d("DemoniTalk", "No se pudo restaurar volumen: ${e.message}")
                            }
                        }
                    }, 600)
                }
            }
        } catch (e: SecurityException) {
            Log.d("DemoniTalk", "Permiso de política de notificaciones no otorgado para cambiar volumen DND: ${e.message}")
        } catch (e: Exception) {
            Log.d("DemoniTalk", "Error gestionando audio: ${e.message}")
        }
    }

    private fun setupSpeechRecognizer() {

        if (speechRecognizer != null) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            Log.e(
                "DemoniTalk",
                "Speech Recognition no disponible en este dispositivo"
            )

            return
        }

        try {

            Log.d(
                "DemoniTalk",
                "Configurando SpeechRecognizer..."
            )

            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(
                    applicationContext
                )

            speechRecognizer?.setRecognitionListener(
                speechListener
            )

        } catch (e: Exception) {

            Log.e(
                "DemoniTalk",
                "Error creando SpeechRecognizer: ${e.message}",
                e
            )

            speechRecognizer = null
        }
    }

    private val speechListener =
        object : RecognitionListener {

            override fun onReadyForSpeech(
                params: Bundle?
            ) {

                Log.d(
                    "DemoniTalk",
                    ">>> RECOGNIZER READY: ¡Habla ahora! <<<"
                )

                isListening = true

                updateButtonUI()
            }

            override fun onBeginningOfSpeech() {

                Log.d(
                    "DemoniTalk",
                    ">>> EMPEZÓ A HABLAR <<<"
                )
            }

            override fun onRmsChanged(
                rmsdB: Float
            ) {
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
            }

            override fun onEndOfSpeech() {

                Log.d(
                    "DemoniTalk",
                    ">>> FIN DE VOZ DETECTADO <<<"
                )

                isListening = false

                muteAudio(true)
            }

            @SuppressLint("SwitchIntDef")
            override fun onError(
                error: Int
            ) {

                val message =
                    when (error) {

                        SpeechRecognizer.ERROR_AUDIO ->
                            "Error de audio"

                        SpeechRecognizer.ERROR_CLIENT ->
                            "Error del cliente"

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Permisos insuficientes"

                        SpeechRecognizer.ERROR_NETWORK ->
                            "Error de red"

                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Tiempo de espera de red agotado"

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "No se encontró coincidencia"

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Reconocedor ocupado"

                        SpeechRecognizer.ERROR_SERVER ->
                            "Error del servidor"

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No se detectó voz"

                        12 ->
                            "Idioma no soportado (Error 12)"

                        else ->
                            "Error desconocido: $error"
                    }

                Log.e(
                    "DemoniTalk",
                    "!!! ERROR RECONOCIMIENTO [$error]: $message !!!"
                )

                isListening = false

                muteAudio(false)

                /*
                 * Reconstruimos el reconocedor cuando queda
                 * en un estado potencialmente bloqueado.
                 */
                if (
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_SERVER ||
                    error == 12
                ) {
                    resetSpeechRecognizer()
                }

                /*
                 * Conservamos la recuperación ROOT que ya tenía
                 * DemoniTalk, pero sin ejecutar comandos desde
                 * el hilo principal.
                 */
                if (
                    error ==
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                    error ==
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                ) {

                    if (ShellUtils.isRootAvailable()) {

                        Log.w(
                            "DemoniTalk",
                            "Error crítico detectado. Intentando recuperación por ROOT..."
                        )

                        Thread {

                            try {

                                ShellUtils.executeCommand(
                                    "appops set $packageName RECORD_AUDIO allow"
                                )

                                ShellUtils.executeCommand(
                                    "appops set $packageName PROJECT_MEDIA allow"
                                )

                                if (
                                    error ==
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                                ) {

                                    ShellUtils.executeCommand(
                                        "am force-stop com.google.android.googlequicksearchbox"
                                    )
                                }

                            } catch (e: Exception) {

                                Log.e(
                                    "DemoniTalk",
                                    "Error en recuperación ROOT: ${e.message}"
                                )
                            }

                        }.start()

                    } else {

                        Log.e(
                            "DemoniTalk",
                            "No hay ROOT disponible para recuperación del reconocimiento"
                        )
                    }
                }

                if (
                    isContinuousMode ||
                    isVigilanceMode
                ) {

                    scheduleListeningRestart(2000)

                } else {

                    isWaitingForCommandAfterWake = false
                    updateButtonUI()
                }
            }

            override fun onResults(
                results: Bundle?
            ) {

                isListening = false

                muteAudio(false)

                val matches =
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                Log.d(
                    "DemoniTalk",
                    "Resultados obtenidos: $matches"
                )

                if (!matches.isNullOrEmpty()) {

                    val text = matches[0]

                    Log.i(
                        "DemoniTalk",
                        "Texto reconocido: $text"
                    )

                    try {

                        val result =
                            commandHandler.execute(
                                text,
                                repository.loadCommands(),
                                isVigilanceMode &&
                                        !isWaitingForCommandAfterWake
                            )

                        val lowerText = text.lowercase(Locale.getDefault())

                        /*
                         * Modo amarillo:
                         * una frase reconocida = comando.
                         */
                        if (isWaitingForCommandAfterWake) {

                            isWaitingForCommandAfterWake = false
                            isVigilanceMode = false

                        } else if (
                            isVigilanceMode &&
                            result ==
                            CommandHandler.CommandResult.WakeWordOnly
                        ) {

                            Log.d(
                                "DemoniTalk",
                                "Wake word detectado, esperando comando..."
                            )

                            handleWakeWordResponse()

                            return
                        } else if (
                            result == CommandHandler.CommandResult.Ignored
                        ) {

                            val cleanPrompt = lowerText
                                .replace(Regex("""\bdemoni\b"""), "")
                                .replace(Regex("""\bdemonio\b"""), "")
                                .trim()

                            val promptToSend = if (cleanPrompt.isNotEmpty()) cleanPrompt else lowerText

                            if (promptToSend.isNotEmpty() && aiAssistant.isAiAvailable()) {
                                Log.d(
                                    "DemoniTalk",
                                    "Comando no reconocido localmente. Procesando con IA: $promptToSend"
                                )

                                processWithAiAndSpeak(promptToSend)

                                return
                            }
                        }

                    } catch (e: Exception) {

                        Log.e(
                            "DemoniTalk",
                            "Error ejecutando comando: ${e.message}",
                            e
                        )
                    }
                }

                isWaitingForCommandAfterWake = false

                if (
                    isContinuousMode ||
                    isVigilanceMode
                ) {

                    scheduleListeningRestart(500)

                } else {

                    updateButtonUI()
                }
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {

                val partial =
                    partialResults?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                if (!partial.isNullOrEmpty()) {

                    Log.v(
                        "DemoniTalk",
                        "Resultado parcial: ${partial[0]}"
                    )
                }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
            }
        }

    private fun startListening() {

        if (isServiceDestroyed) return

        mainHandler.post {

            if (isServiceDestroyed) return@post

            /*
             * Cancelamos cualquier reinicio pendiente.
             */
            stopPendingRestart()

            /*
             * Si ya estaba escuchando, no creamos otra sesión.
             */
            if (isListening) {

                Log.w(
                    "DemoniTalk",
                    "startListening() llamado mientras ya estaba escuchando"
                )

                return@post
            }

            try {

                setupSpeechRecognizer()

                if (speechRecognizer == null) {

                    Log.e(
                        "DemoniTalk",
                        "SpeechRecognizer es NULL"
                    )

                    return@post
                }

                /*
                 * Limpiamos una sesión anterior.
                 */
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {
                }

                val intent =
                    Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                    ).apply {

                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )

                        // Probamos con un tag de idioma más genérico
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE,
                            "es"
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_CALLING_PACKAGE,
                            packageName
                        )
                    }

                Log.d(
                    "DemoniTalk",
                    ">>> LLAMANDO A startListening() <<<"
                )

                muteAudio(true)

                speechRecognizer?.startListening(intent)

                isListening = true

            } catch (e: Exception) {

                Log.e(
                    "DemoniTalk",
                    "Error al iniciar captura de voz: ${e.message}",
                    e
                )

                isListening = false

                muteAudio(false)

                if (
                    isContinuousMode ||
                    isVigilanceMode
                ) {
                    scheduleListeningRestart(2000)
                }
            }
        }
    }

    private fun scheduleListeningRestart(
        delay: Long
    ) {

        if (isServiceDestroyed) return

        stopPendingRestart()

        restartRunnable =
            Runnable {

                restartRunnable = null

                if (
                    !isServiceDestroyed &&
                    (isContinuousMode || isVigilanceMode) &&
                    !isListening
                ) {

                    startListening()
                }
            }

        mainHandler.postDelayed(
            restartRunnable!!,
            delay
        )
    }

    private fun stopPendingRestart() {

        restartRunnable?.let {
            mainHandler.removeCallbacks(it)
        }

        restartRunnable = null
    }

    private fun resetSpeechRecognizer() {

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null
        isListening = false
    }

    private fun handleWakeWordResponse() {

        if (isServiceDestroyed) return

        isWaitingForCommandAfterWake = true

        val responses =
            listOf(
                "¿Abrimos el Super?",
                "Que dice mi socio",
                "Dime",
                "como esta la cosa Bro",
                "Soy todo oídos",
                "Que dice mi Amo",
                "¿Necesitas algo?",
                "¿Qué sacrificio pides?",
                "Tus deseos son órdenes",
                "Habla",
                "Te escucho",
                "¿Qué hay?",
                "Ordena"
            )

        val response =
            responses.random()

        updateButtonUI()

        /*
         * Si TTS todavía no está listo, simplemente
         * continuamos escuchando.
         */
        if (!isTtsReady) {

            Log.w(
                "DemoniTalk",
                "TTS todavía no está preparado"
            )

            startListening()
            return
        }

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(
                    id: String?
                ) {
                    isListening = false
                }

                override fun onDone(
                    id: String?
                ) {

                    mainHandler.post {

                        if (!isServiceDestroyed) {
                            startListening()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(
                    id: String?
                ) {

                    mainHandler.post {

                        if (!isServiceDestroyed) {
                            startListening()
                        }
                    }
                }
            }
        )

        try {

            tts.speak(
                response,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "DemoniWake"
            )

        } catch (e: Exception) {

            Log.e(
                "DemoniTalk",
                "Error ejecutando TTS: ${e.message}"
            )

            startListening()
        }
    }

    private fun speakText(textToSpeak: String, onDoneCallback: (() -> Unit)? = null) {
        if (!isTtsReady) {
            onDoneCallback?.invoke()
            return
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                isListening = false
            }

            override fun onDone(id: String?) {
                mainHandler.post {
                    onDoneCallback?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                mainHandler.post {
                    onDoneCallback?.invoke()
                }
            }
        })

        try {
            tts.speak(
                textToSpeak,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "DemoniSpeech_${System.currentTimeMillis()}"
            )
        } catch (e: Exception) {
            Log.e("DemoniTalk", "Error ejecutando TTS: ${e.message}")
            onDoneCallback?.invoke()
        }
    }

    private fun removeEmojis(text: String): String {
        return text
            .replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u1F300-\u1F9FF]"""), "")
            .replace(Regex("""[🚀😈🤖⚡🔥✨👍]"""), "")
            .trim()
    }

    private fun processWithAiAndSpeak(prompt: String) {
        if (isServiceDestroyed) return

        if (!aiAssistant.isAiAvailable()) {
            Log.w("DemoniTalk", "IA no está disponible o no hay motores activos.")
            isWaitingForCommandAfterWake = false
            if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) {
                scheduleListeningRestart(500)
            } else {
                updateButtonUI()
            }
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val aiReply = aiAssistant.askGemini(prompt)
            if (!aiReply.isNullOrEmpty()) {
                val cleanReply = removeEmojis(aiReply)

                // Verificamos si la IA incluyó una orden/accion interpretada [CMD:accion]
                val cmdRegex = Regex("""\[CMD:(.+?)]""", RegexOption.IGNORE_CASE)
                val match = cmdRegex.find(cleanReply)

                if (match != null) {
                    val commandAction = match.groupValues[1].trim()
                    val speechText = cleanReply.replace(cmdRegex, "").trim()

                    Log.d("DemoniTalk", "IA ejecutando comando coloquial: $commandAction")
                    commandHandler.execute(commandAction, repository.loadCommands(), false)

                    if (speechText.isNotEmpty()) {
                        speakText(speechText) {
                            isWaitingForCommandAfterWake = false
                            if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) {
                                startListening()
                            } else {
                                updateButtonUI()
                            }
                        }
                    } else {
                        isWaitingForCommandAfterWake = false
                        if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) {
                            startListening()
                        } else {
                            updateButtonUI()
                        }
                    }
                } else {
                    speakText(cleanReply) {
                        isWaitingForCommandAfterWake = false
                        if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) {
                            startListening()
                        } else {
                            updateButtonUI()
                        }
                    }
                }
            } else {
                Log.w("DemoniTalk", "Sin respuesta de IA o fallo de conexión. Silencio.")
                isWaitingForCommandAfterWake = false
                if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) {
                    scheduleListeningRestart(500)
                } else {
                    updateButtonUI()
                }
            }
        }
    }

    override fun onDestroy() {

        isServiceDestroyed = true

        stopPendingRestart()

        isContinuousMode = false
        isVigilanceMode = false
        isListening = false
        isWaitingForCommandAfterWake = false

        muteAudio(false)

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        if (::tts.isInitialized) {

            try {
                tts.stop()
            } catch (_: Exception) {
            }

            try {
                tts.shutdown()
            } catch (_: Exception) {
            }
        }

        floatingView?.let {

            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }

        floatingView = null

        mainHandler.removeCallbacksAndMessages(null)

        super.onDestroy()

        Log.d(
            "DemoniTalk",
            "FloatingButtonService destruido correctamente"
        )
    }
}