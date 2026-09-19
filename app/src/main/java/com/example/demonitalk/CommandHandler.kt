package com.example.demonitalk

import android.content.Context
import android.util.Log
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

class CommandHandler(context: Context) {

    private var internalListener: ((String) -> Unit)? = null
    private val accessibilityController = AccessibilityController(context)

    fun setInternalListener(listener: (String) -> Unit) {
        internalListener = listener
    }

    private fun String.normalize(): String {
        val nfdNormalizedString = Normalizer.normalize(this, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        val base = pattern.matcher(nfdNormalizedString).replaceAll("").lowercase(Locale.getDefault()).trim()

        return base
            .replace("demonio", "maquina")
            .replace("demoni", "maquina")
            .replace("puta", "maquina")
            .replace("p***", "maquina")
            .replace("demoni talk", "maquina")
    }

    private val wakeWords: List<String>
        get() = listOf("hermano", "amigo", "maquina")

    fun execute(text: String, commands: List<VoiceCommand>, requireWakeWord: Boolean = false): CommandResult {
        var normalizedText = text.normalize()
        var wakeWordDetected = false

        if (requireWakeWord) {
            val foundWakeWord = wakeWords.firstOrNull { wakeWord ->
                normalizedText == wakeWord || normalizedText.startsWith("$wakeWord ")
            }

            if (foundWakeWord != null) {
                wakeWordDetected = true
                normalizedText = normalizedText.removePrefix(foundWakeWord).trim()
                Log.d("CommandHandler", "Wake-word '$foundWakeWord' detectado.")
            } else {
                return CommandResult.Ignored
            }
        }

        if (wakeWordDetected && normalizedText.isEmpty()) {
            return CommandResult.WakeWordOnly
        }

        // --- COMANDOS RÁPIDOS NATIVOS ---

        // Escribir
        if (normalizedText.startsWith("escribe") || normalizedText.startsWith("manda este mensaje")) {
            val content = normalizedText.replace("escribe", "").replace("manda este mensaje", "").trim()
            if (content.isNotEmpty()) {
                executeAsync("type:$content")
                return CommandResult.Executed
            }
        }

        // Abrir Apps
        if (normalizedText.startsWith("abre") || normalizedText.startsWith("abrir")) {
            val appName = normalizedText.replace("abre", "").replace("abrir", "").trim()
            if (appName.isNotEmpty()) {
                executeAsync("open_app:$appName")
                return CommandResult.Executed
            }
        }

        // Navegación y Control
        when (normalizedText) {
            "vuelve atras", "atras" -> { executeAsync("global_back"); return CommandResult.Executed }
            "inicio", "vete a casa" -> { executeAsync("global_home"); return CommandResult.Executed }
            "recientes" -> { executeAsync("global_recents"); return CommandResult.Executed }
            "enviar", "manda el mensaje" -> { executeAsync("click_send"); return CommandResult.Executed }
            "cuadricula" -> { executeAsync("show_grid"); return CommandResult.Executed }
            "numeros" -> { executeAsync("show_numbers"); return CommandResult.Executed }
            "oculta todo", "limpia pantalla" -> { executeAsync("hide_overlays"); return CommandResult.Executed }
            "silencio", "para de escuchar" -> { internalListener?.invoke("internal_stop"); return CommandResult.Executed }
        }

        // Click por número (Voice Access style)
        val numberPattern = Pattern.compile(".*?(?:pulsa|clic|numero|el)\\s+(\\d+).*?")
        val matcher = numberPattern.matcher(normalizedText)
        if (matcher.find()) {
            val num = matcher.group(1)
            if (num != null) {
                executeAsync("click_number:$num")
                return CommandResult.Executed
            }
        }

        // Click por texto directo
        if (normalizedText.startsWith("pulsa ")) {
            val targetText = normalizedText.replace("pulsa ", "").trim()
            if (targetText.isNotEmpty()) {
                executeAsync("click_text:$targetText")
                return CommandResult.Executed
            }
        }

        // --- REPOSITORIO DE COMANDOS PERSONALIZADOS ---
        val command = commands.firstOrNull {
            val trigger = it.trigger.normalize()
            normalizedText == trigger || normalizedText.contains(trigger) || isFuzzyMatch(normalizedText, trigger)
        }

        if (command != null) {
            if (command.action.startsWith("internal_")) {
                internalListener?.invoke(command.action)
            } else {
                executeAsync(command.action)
            }
            return CommandResult.Executed
        }

        return CommandResult.Ignored
    }

    private fun isFuzzyMatch(text: String, trigger: String): Boolean {
        if (text.length < 4 || trigger.length < 4) return false
        return text.contains(trigger.substring(0, (trigger.length * 0.7).toInt()))
    }

    private fun executeAsync(action: String) {
        Thread { processAction(action) }.start()
    }

    private fun processAction(action: String) {
        val cleanAction = action.trim()
        Log.d("CommandHandler", "Ejecutando acción: $cleanAction")

        if (cleanAction.startsWith("internal_")) {
            internalListener?.invoke(cleanAction)
            return
        }

        // LÓGICA DE MOTOR: 1. Root (si está concedido) -> 2. Accesibilidad (Fallback)
        var shellSuccess = false
        if (ShellUtils.isRootAvailable()) {
            shellSuccess = when {
                cleanAction == "global_back" -> ShellUtils.executeCommand("input keyevent 4")
                cleanAction == "global_home" -> ShellUtils.executeCommand("input keyevent 3")
                cleanAction == "global_recents" -> ShellUtils.executeCommand("input keyevent 187")
                cleanAction.startsWith("type:") -> {
                    val text = cleanAction.removePrefix("type:")
                    // Escapamos espacios para comando shell
                    ShellUtils.executeCommand("input text \"${text.replace(" ", "%s")}\"")
                }
                else -> {
                    // Si no es un comando predefinido, intentamos ejecutarlo directamente como shell
                    if (!cleanAction.startsWith("open_app:") && !cleanAction.startsWith("click_")) {
                        ShellUtils.executeCommand(cleanAction)
                    } else false
                }
            }
        }

        // Si el Root falló, no está disponible o la acción es exclusiva de Accesibilidad
        if (!shellSuccess) {
            accessibilityController.execute(cleanAction)
        }
    }

    enum class CommandResult { Ignored, WakeWordOnly, Executed }
}
