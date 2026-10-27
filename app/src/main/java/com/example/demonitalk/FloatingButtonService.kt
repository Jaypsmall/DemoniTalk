package com.example.demonitalk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FloatingButtonService : Service() {

    private lateinit var uiManager: FloatingUiManager
    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var ttsManager: TtsManager

    private lateinit var commandHandler: CommandHandler
    private lateinit var repository: CommandRepository
    private lateinit var aiAssistant: AiAssistant

    private var isContinuousMode = false
    private var isVigilanceMode = false
    private var isWaitingForCommandAfterWake = false
    private var isServiceDestroyed = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationId = 123
    private val channelId = "DemoniTalk_Silent_v3"
    private var restartRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServiceDestroyed) return START_NOT_STICKY

        if (!uiManager.isViewCreated() && Settings.canDrawOverlays(this)) {
            uiManager.createFloatingView()
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

            "ACTION_MODE_RED" -> stopEverything()
            "ACTION_STOP_SERVICE" -> {
                stopEverything()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        isServiceDestroyed = false

        createNotificationChannel()
        startForegroundService()

        repository = CommandRepository(this)
        aiAssistant = AiAssistant(this)
        commandHandler = CommandHandler(this)
        ttsManager = TtsManager(this)

        // UI Manager con callbacks de toques (1 a 5 toques)
        uiManager = FloatingUiManager(
            context = this,
            onSingleTap = {
                isContinuousMode = false
                isVigilanceMode = false
                isWaitingForCommandAfterWake = true
                updateButtonUI()
                startListening()
            },
            onDoubleTap = {
                if (isVigilanceMode) {
                    stopEverything()
                } else {
                    isVigilanceMode = true
                    isContinuousMode = false
                    isWaitingForCommandAfterWake = false
                    updateButtonUI()
                    startListening()
                }
            },
            onTripleTap = {
                if (isContinuousMode) {
                    stopEverything()
                } else {
                    isContinuousMode = true
                    isVigilanceMode = false
                    isWaitingForCommandAfterWake = false
                    updateButtonUI()
                    startListening()
                }
            },
            onQuadrupleTap = {
                try {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("FloatingButtonService", "Error abriendo MainActivity: ${e.message}")
                }
            },
            onQuintupleTap = {
                stopEverything()
                stopSelf()
            }
        )

        // Speech Manager con callbacks de eventos de voz
        speechManager = SpeechRecognitionManager(
            context = this,
            onReady = { updateButtonUI() },
            onSpeechResults = { text -> handleSpeechResults(text) },
            onErrorRestart = { scheduleListeningRestart(2000) },
            onErrorStopWaiting = {
                isWaitingForCommandAfterWake = false
                updateButtonUI()
            },
            isModeActive = { isContinuousMode || isVigilanceMode }
        )

        // Listener de comandos internos
        commandHandler.setInternalListener { action ->
            mainHandler.post {
                if (isServiceDestroyed) return@post
                when (action) {
                    "internal_stop" -> stopEverything()
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

        if (Settings.canDrawOverlays(this)) {
            uiManager.createFloatingView()
        }
    }

    private fun cleanPromptForAi(text: String): String {
        var result = text.trim()
        val wakeWords = listOf("demonio", "demoni", "hermano", "amigo", "maquina")
        for (w in wakeWords) {
            if (result.startsWith("$w ")) {
                result = result.removePrefix("$w ").trim()
            }
            if (result.endsWith(" $w")) {
                result = result.removeSuffix(" $w").trim()
            }
            if (result == w) {
                result = ""
            }
        }
        return result
    }

    private fun handlePartialSpeechResults(text: String): Boolean {
        if (text.isBlank()) return false
        val clean = text.lowercase(Locale.getDefault()).trim()

        if (clean.startsWith("escribe ") ||
            clean.startsWith("manda este mensaje ") ||
            clean.startsWith("reproduce ") ||
            clean.startsWith("pon ") ||
            clean.startsWith("busca ") ||
            clean.startsWith("buscar ")
        ) {
            return false
        }

        val result = try {
            commandHandler.execute(
                text,
                repository.loadCommands(),
                isVigilanceMode && !isWaitingForCommandAfterWake
            )
        } catch (e: Exception) {
            Log.e("FloatingButtonService", "Error ejecutando comando parcial: ${e.message}", e)
            CommandHandler.CommandResult.Ignored
        }

        if (result == CommandHandler.CommandResult.Executed) {
            mainHandler.post {
                if (isWaitingForCommandAfterWake) {
                    isWaitingForCommandAfterWake = false
                    isVigilanceMode = false
                }
                if (isContinuousMode || isVigilanceMode) {
                    scheduleListeningRestart(500)
                } else {
                    updateButtonUI()
                }
            }
            return true
        } else if (isVigilanceMode && !isWaitingForCommandAfterWake && result == CommandHandler.CommandResult.WakeWordOnly) {
            mainHandler.post { handleWakeWordResponse() }
            return true
        }

        return false
    }

    private fun handleSpeechResults(text: String) {
        CoroutineScope(Dispatchers.Default).launch {
            val lowerText = text.lowercase(Locale.getDefault())

            val result = try {
                commandHandler.execute(
                    text,
                    repository.loadCommands(),
                    isVigilanceMode && !isWaitingForCommandAfterWake,
                )
            } catch (e: Exception) {
                Log.e("FloatingButtonService", "Error ejecutando comando: ${e.message}", e)
                CommandHandler.CommandResult.Ignored
            }

            withContext(Dispatchers.Main) {
                if (isWaitingForCommandAfterWake) {
                    isWaitingForCommandAfterWake = false
                    isVigilanceMode = false
                } else if (isVigilanceMode && result == CommandHandler.CommandResult.WakeWordOnly) {
                    handleWakeWordResponse()
                    return@withContext
                } else if (result == CommandHandler.CommandResult.Ignored) {
                    val promptForAi = cleanPromptForAi(lowerText)
                    if (promptForAi.isNotEmpty() && aiAssistant.isAiAvailable()) {
                        processWithAiAndSpeak(promptForAi)
                        return@withContext
                    }
                }

                isWaitingForCommandAfterWake = false

                if (isContinuousMode || isVigilanceMode) {
                    scheduleListeningRestart(500)
                } else {
                    updateButtonUI()
                }
            }
        }
    }

    private fun CommandHandler.execute(
        text: String,
        commands: kotlin.collections.List<com.example.demonitalk.VoiceCommand>,
        requireWakeWord: Boolean
    ) {
    }

    private fun startListening() {
        if (isServiceDestroyed) return
        mainHandler.post {
            if (isServiceDestroyed) return@post
            stopPendingRestart()
            speechManager.startListening()
        }
    }

    private fun stopEverything() {
        isContinuousMode = false
        isVigilanceMode = false
        isWaitingForCommandAfterWake = false
        stopPendingRestart()
        speechManager.stopListening()
        updateButtonUI()
    }

    private fun updateButtonUI() {
        uiManager.updateButtonUI(
            isWaitingForCommandAfterWake,
            isContinuousMode,
            isVigilanceMode
        )
    }

    private fun scheduleListeningRestart(delay: Long) {
        if (isServiceDestroyed) return
        stopPendingRestart()

        restartRunnable = Runnable {
            restartRunnable = null
            if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode) && !speechManager.isListening) {
                startListening()
            }
        }

        mainHandler.postDelayed(restartRunnable!!, delay)
    }

    private fun stopPendingRestart() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
    }

    private fun handleWakeWordResponse() {
        if (isServiceDestroyed) return

        isWaitingForCommandAfterWake = true
        val responses = listOf(
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

        val response = responses.random()
        updateButtonUI()

        ttsManager.speakText(
            textToSpeak = response,
            onDoneCallback = {
                if (!isServiceDestroyed) {
                    startListening()
                }
            }
        )
    }

    private fun processWithAiAndSpeak(prompt: String) {
        if (isServiceDestroyed) return

        if (!aiAssistant.isAiAvailable()) {
            ttsManager.speakText("La IA está desactivada o sin motor configurado.") {
                isWaitingForCommandAfterWake = false
                if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) {
                    scheduleListeningRestart(500)
                } else {
                    updateButtonUI()
                }
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val aiReply = aiAssistant.askFreeAi(prompt)

            if (!aiReply.isNullOrBlank()) {
                val cleanSpeech = ttsManager.cleanTextForSpeech(aiReply)
                val finalSpeech = if (cleanSpeech.isNotEmpty()) cleanSpeech else ""

                withContext(Dispatchers.Main) {
                    if (finalSpeech.isNotEmpty()) {
                        ttsManager.speakText(finalSpeech) {
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
                }
            } else {
                Log.w("FloatingButtonService", "Sin respuesta de IA o fallo de conexión.")
                withContext(Dispatchers.Main) {
                    ttsManager.speakText("No pude obtener respuesta de la IA en este momento.") {
                        isWaitingForCommandAfterWake = false
                        if (!isServiceDestroyed && (isContinuousMode || isVigilanceMode)) {
                            scheduleListeningRestart(500)
                        } else {
                            updateButtonUI()
                        }
                    }
                }
            }
        }
    }

    private fun String.substringOrDefault(startChar: String, endChar: String): String {
        val s = this.indexOf(startChar)
        val e = this.lastIndexOf(endChar)
        return if (s != -1 && e != -1 && e > s) this.substring(s, e + 1) else this
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "DemoniTalk Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, channelId)
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
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e("FloatingButtonService", "Error al iniciar Foreground Service: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        stopPendingRestart()

        isContinuousMode = false
        isVigilanceMode = false
        isWaitingForCommandAfterWake = false

        if (::speechManager.isInitialized) speechManager.destroy()
        if (::ttsManager.isInitialized) ttsManager.destroy()
        if (::uiManager.isInitialized) uiManager.destroy()

        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}