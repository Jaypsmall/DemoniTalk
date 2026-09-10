package com.example.demonitalk

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import java.text.Normalizer
import java.util.regex.Pattern

class CommandHandler(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null
    private var internalListener: ((String) -> Unit)? = null
    private val accessibilityController = AccessibilityController(context)

    init {
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error getting camera ID", e)
        }
    }

    fun setInternalListener(listener: (String) -> Unit) {
        internalListener = listener
    }

    private fun String.normalize(): String {
        val nfdNormalizedString = Normalizer.normalize(this, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        val base = pattern.matcher(nfdNormalizedString).replaceAll("").lowercase().trim()

        // Mapeamos las palabras solicitadas a "maquina" para estandarizar el wake-word
        return base.replace("demonio", "maquina")
            .replace("demoni", "maquina")
            .replace("puta", "maquina")
            .replace("p***", "maquina")
    }

    private val wakeWords: List<String>
        get() = listOf(
            "hermano",
            "amigo",
            "maquina"
            )

    fun execute(text: String, commands: List<VoiceCommand>, requireWakeWord: Boolean = false): CommandResult {
        var normalizedText: String = text.normalize()
        var wakeWordDetected = false
        
        if (requireWakeWord) {
            val foundWakeWord = wakeWords.find { normalizedText.startsWith(it) }
            if (foundWakeWord != null) {
                wakeWordDetected = true
                // Quitamos la palabra de activación encontrada
                normalizedText = normalizedText.removePrefix(foundWakeWord).trim()
                Log.d("CommandHandler", "Wake-word '$foundWakeWord' detected! Remaining: '$normalizedText'")
            } else {
                Log.d("CommandHandler", "Wake-word not found in vigilance mode.")
                return CommandResult.Ignored
            }
        }

        // Si solo se dijo la palabra mágica sin nada más
        if (wakeWordDetected && normalizedText.isEmpty()) {
            return CommandResult.WakeWordOnly
        }

        // Caso especial para escribir mensajes
        if (normalizedText.startsWith("manda este mensaje") || normalizedText.startsWith("escribe")) {
            val content = normalizedText
                .replace("manda este mensaje", "")
                .replace("escribe", "")
                .trim()
            if (content.isNotEmpty()) {
                Log.d("CommandHandler", "Executing type command: $content")
                Thread { processAction("type:$content") }.start()
                return CommandResult.Executed
            }
        }

        // Caso: "envia el mensaje" o "dale a enviar"
        if (normalizedText.contains("envia el mensaje") || normalizedText.contains("dale a enviar") || normalizedText == "enviar") {
            Thread { processAction("click_send") }.start()
            return CommandResult.Executed
        }

        // Caso: "abre la aplicacion [NOMBRE]" o "abre [NOMBRE]"
        if (normalizedText.startsWith("abre la aplicacion") || normalizedText.startsWith("abre")) {
            val appName = normalizedText
                .replace("abre la aplicacion", "")
                .replace("abre", "")
                .trim()
            if (appName.isNotEmpty()) {
                Thread { processAction("open_app:$appName") }.start()
                return CommandResult.Executed
            }
        }

        // Navegación básica
        when (normalizedText) {
            "vuelve atras", "atras" -> {
                Thread { processAction("global_back") }.start()
                return CommandResult.Executed
            }
            "ve a inicio", "pantalla de inicio", "vete a casa" -> {
                Thread { processAction("global_home") }.start()
                return CommandResult.Executed
            }
            "aplicaciones recientes", "recientes" -> {
                Thread { processAction("global_recents") }.start()
                return CommandResult.Executed
            }
            "abre el primer chat", "primer chat", "primer mensaje" -> {
                Thread { processAction("click_first_chat") }.start()
                return CommandResult.Executed
            }
            "desactivar escucha", "deja de escuchar", "para de escuchar", "silencio" -> {
                internalListener?.invoke("internal_stop")
                return CommandResult.Executed
            }
        }

        Log.d("CommandHandler", "Searching for command in: '$normalizedText'")
        
        val command = commands.find { 
            val trigger = it.trigger.normalize()
            normalizedText == trigger || normalizedText.contains(trigger) || trigger.contains(normalizedText) ||
            isFuzzyMatch(normalizedText, trigger)
        }

        return if (command != null) {
            if (command.action.startsWith("internal_")) {
                internalListener?.invoke(command.action)
            } else {
                Thread { processAction(command.action) }.start()
            }
            CommandResult.Executed
        } else {
            CommandResult.Ignored
        }
    }

    enum class CommandResult {
        Ignored,            // No se detectó nada relevante
        WakeWordOnly,       // Se dijo "Demoni" pero nada más
        Executed,           // Se ejecutó un comando (directo o tras wake-word)
    }

    private fun isFuzzyMatch(text: String, trigger: String): Boolean {
        if (text.length < 4 || trigger.length < 4) return false
        // Si el trigger está contenido en un 80% o viceversa
        return text.contains(trigger.substring(0, (trigger.length * 0.8).toInt()))
    }

    private fun processAction(action: String) {
        // Acciones inteligentes: si hay Root, usamos comandos de sistema que son más fiables
        if (ShellUtils.isRootAvailable()) {
            when (action) {
                "global_back" -> {
                    ShellUtils.executeCommand("input keyevent 4")
                    return
                }
                "global_home" -> {
                    ShellUtils.executeCommand("input keyevent 3")
                    return
                }
                "global_recents" -> {
                    ShellUtils.executeCommand("input keyevent 187")
                    return
                }
                "click_send" -> {
                    // Para enviar, primero intentamos por accesibilidad (es más preciso)
                    // pero si falla o no está activo, no podemos hacer mucho más por shell simple
                    accessibilityController.execute(action)
                    return
                }
            }
        }

        // Si no hay Root o es una acción específica, usamos el AccessibilityController
        if (action.startsWith("open_app:") || 
            action == "click_send" || 
            action.startsWith("global_")) {
            Log.d("CommandHandler", "Executing Smart Action: $action")
            accessibilityController.execute(action)
            return
        }

        // Comandos internos de alta velocidad
        when (action) {
            "torch_on" -> {
                toggleFlashlight(true)
                return
            }
            "torch_off" -> {
                toggleFlashlight(false)
                return
            }
        }

        if (ShellUtils.isRootAvailable()) {
            Log.d("CommandHandler", "Executing via Root: $action")
            if (action.startsWith("type:")) {
                val text = action.removePrefix("type:")
                ShellUtils.executeCommand("input text \"$text\"")
            } else {
                ShellUtils.executeCommand(action)
            }
        } else {
            Log.d("CommandHandler", "Executing via Accessibility: $action")
            accessibilityController.execute(action)
        }
    }

    private fun toggleFlashlight(status: Boolean) {
        try {
            cameraId?.let {
                cameraManager.setTorchMode(it, status)
                Log.d("CommandHandler", "Flashlight set to $status")
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error toggling flashlight", e)
        }
    }
}
