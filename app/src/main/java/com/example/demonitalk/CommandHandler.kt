package com.example.demonitalk

import android.content.Context
import android.util.Log
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

class CommandHandler(private val context: Context) {

    private var internalListener: ((String) -> Unit)? = null

    private val accessibilityController = AccessibilityController(context)

    fun setInternalListener(listener: (String) -> Unit) {
        internalListener = listener
    }

    /**
     * Normaliza el texto reconocido por voz:
     * - elimina tildes
     * - convierte a minúsculas
     * - elimina espacios sobrantes
     * - unifica palabras utilizadas como wake word
     *
     * IMPORTANTE:
     * Solo sustituye palabras completas.
     * Así "demoni" puede convertirse en "maquina",
     * pero "demonizar" NO se modifica.
     */
    private fun String.normalize(): String {

        val nfdNormalizedString =
            Normalizer.normalize(this, Normalizer.Form.NFD)

        val pattern =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

        val base = pattern
            .matcher(nfdNormalizedString)
            .replaceAll("")
            .lowercase(Locale.getDefault())
            .trim()
            .replace(Regex("\\s+"), " ")

        return base
            .replace(Regex("""\bdemonio\b"""), "maquina")
            .replace(Regex("""\bdemoni\s+talk\b"""), "maquina")
            .replace(Regex("""\bdemoni\b"""), "maquina")
            .replace(Regex("""\bputa\b"""), "maquina")
            .replace(Regex("""\bp\*\*\*\b"""), "maquina")
            .trim()
    }

    /**
     * Palabras que pueden activar el modo de escucha por wake word.
     */
    private val wakeWords: List<String>
        get() = listOf(
            "hermano",
            "amigo",
            "maquina"
        )

    /**
     * Procesa el texto reconocido y devuelve el resultado de la ejecución.
     */
    fun execute(
        text: String,
        commands: List<VoiceCommand>,
        requireWakeWord: Boolean = false
    ): CommandResult {

        var normalizedText = text.normalize()

        var wakeWordDetected = false

        Log.d(
            "CommandHandler",
            "Texto original: '$text' -> normalizado: '$normalizedText'"
        )

        // ---------------------------------------------------------
        // WAKE WORD
        // ---------------------------------------------------------

        if (requireWakeWord) {

            val foundWakeWord = wakeWords.firstOrNull { wakeWord ->
                normalizedText == wakeWord ||
                        normalizedText.startsWith("$wakeWord ")
            }

            if (foundWakeWord != null) {

                wakeWordDetected = true

                normalizedText = normalizedText
                    .removePrefix(foundWakeWord)
                    .trim()

                Log.d(
                    "CommandHandler",
                    "Wake-word '$foundWakeWord' detectado. Comando: '$normalizedText'"
                )

            } else {
                return CommandResult.Ignored
            }
        }

        // Solo se ha dicho "hermano", "amigo" o "máquina".
        if (wakeWordDetected && normalizedText.isEmpty()) {
            return CommandResult.WakeWordOnly
        }

        // ---------------------------------------------------------
        // DETECCIÓN MULTI-COMANDO / SECUENCIAS DE ACCIONES
        // Detectamos conectores como *, ;, ,, &&, "luego", "después", "entonces",
        // o "y" seguido de verbo/comando.
        // ---------------------------------------------------------
        val sequenceRegex = Regex(
            """\s*[*;,]\s*(?=abre|abrir|pulsa|clic|click|escribe|manda|enviar|pon|reproduce|vuelve|atras|inicio|casa|recientes|buscar|busca|primer|primera|cerrar|activar|desactivar|activa|modo|silencio|detente|cuadricula|numeros|oculta|\*)|\s+(?:luego|después|despues|entonces)\s+|\s+y\s+(?=abre|abrir|pulsa|clic|click|escribe|manda|enviar|pon|reproduce|vuelve|atras|inicio|casa|recientes|buscar|busca|primer|primera|cerrar|activar|desactivar|activa|modo|silencio|detente|cuadricula|numeros|oculta)"""
        )

        val parts = normalizedText
            .split(sequenceRegex)
            .map { it.trim().replace(Regex("^[*;,\\s]+"), "").trim() }
            .filter { it.isNotEmpty() }

        if (parts.size > 1) {
            Log.d("CommandHandler", "Secuencia de comandos detectada (${parts.size} pasos): $parts")
            executeSequence(parts, commands)
            return CommandResult.Executed
        }

        // Si es un solo comando, lo ejecutamos directamente
        val executed = executeSingleCommand(normalizedText, commands)
        return if (executed) CommandResult.Executed else CommandResult.Ignored
    }

    /**
     * Procesa un comando o acción individual.
     */
    fun executeSingleCommand(
        text: String,
        commands: List<VoiceCommand>
    ): Boolean {

        var normalizedText = text.normalize()

        // Eliminar asteriscos, comas o símbolos al inicio si los hay
        normalizedText = normalizedText.replace(Regex("^[*;,\\s]+"), "").trim()

        if (normalizedText.isEmpty()) return false

        Log.d("CommandHandler", "Ejecutando comando individual: '$normalizedText'")

        // ---------------------------------------------------------
        // ESCRIBIR / MENSAJES
        // Ejemplos:
        // "escribe hola hermano"
        // "manda este mensaje hola"
        // ---------------------------------------------------------
        when {
            normalizedText.startsWith("escribe ") -> {
                val content = normalizedText.removePrefix("escribe ").trim()
                if (content.isNotEmpty()) {
                    executeAsync("type:$content")
                    return true
                }
            }

            normalizedText == "escribe" -> return false

            normalizedText.startsWith("manda este mensaje ") -> {
                val content = normalizedText.removePrefix("manda este mensaje ").trim()
                if (content.isNotEmpty()) {
                    executeAsync("type:$content")
                    return true
                }
            }

            normalizedText == "manda este mensaje" -> return false
        }

        // ---------------------------------------------------------
        // ABRIR APLICACIÓN
        // Ejemplos:
        // "abre WhatsApp"
        // "abrir youtube"
        // ---------------------------------------------------------
        when {
            normalizedText.startsWith("abre ") -> {
                val appName = normalizedText.removePrefix("abre ").trim()
                if (appName.isNotEmpty()) {
                    executeAsync("open_app:$appName")
                    return true
                }
            }

            normalizedText.startsWith("abrir ") -> {
                val appName = normalizedText.removePrefix("abrir ").trim()
                if (appName.isNotEmpty()) {
                    executeAsync("open_app:$appName")
                    return true
                }
            }
        }

        // ---------------------------------------------------------
        // NAVEGACIÓN Y CONTROL
        // ---------------------------------------------------------
        when (normalizedText) {

            "vuelve atras",
            "atras",
            "volver atras" -> {
                executeAsync("global_back")
                return true
            }

            "inicio",
            "vete a casa",
            "ir a casa",
            "casa" -> {
                executeAsync("global_home")
                return true
            }

            "recientes",
            "aplicaciones recientes" -> {
                executeAsync("global_recents")
                return true
            }

            "enviar",
            "manda el mensaje",
            "enviar mensaje" -> {
                executeAsync("click_send")
                return true
            }

            "primer chat",
            "primer mensaje",
            "primera conversacion",
            "primer contacto" -> {
                executeAsync("click_first_chat")
                return true
            }

            "cuadricula",
            "cuadricula de numeros",
            "muestra cuadricula" -> {
                executeAsync("show_grid")
                return true
            }

            "numeros",
            "muestra numeros",
            "mostrar numeros" -> {
                executeAsync("show_numbers")
                return true
            }

            "oculta todo",
            "limpia pantalla",
            "ocultar todo" -> {
                executeAsync("hide_overlays")
                return true
            }

            "silencio",
            "para de escuchar",
            "deja de escuchar",
            "deja de oir",
            "detente" -> {
                internalListener?.invoke("internal_stop")
                return true
            }

            "activa c4",
            "activa c 4",
            "activar c4",
            "activar c 4",
            "modo azul",
            "modo vigilancia",
            "activar vigilancia" -> {
                internalListener?.invoke("internal_mode_blue")
                return true
            }

            "activar escucha",
            "modo verde",
            "modo continuo" -> {
                internalListener?.invoke("internal_continuous_on")
                return true
            }

            // --- MEDIA CONTROL ---
            "siguiente cancion",
            "siguiente",
            "proxima" -> {
                executeAsync("media_next")
                return true
            }

            "cancion anterior",
            "anterior" -> {
                executeAsync("media_previous")
                return true
            }

            "para la musica",
            "para",
            "pausa",
            "deten la musica" -> {
                executeAsync("media_pause")
                return true
            }

            "reanuda la musica",
            "continua",
            "play" -> {
                executeAsync("media_play")
                return true
            }
        }

        // ---------------------------------------------------------
        // REPRODUCIR MÚSICA (CON PARÁMETROS)
        // ---------------------------------------------------------
        if (normalizedText.startsWith("reproduce ") || normalizedText.startsWith("pon ")) {
            val query = normalizedText
                .replaceFirst("reproduce ", "")
                .replaceFirst("pon ", "")
                .trim()

            if (query.isNotEmpty()) {
                executeAsync("play_music:$query")
                return true
            }
        }

        // ---------------------------------------------------------
        // BÚSQUEDA WEB
        // ---------------------------------------------------------
        if (normalizedText.startsWith("busca ") || normalizedText.startsWith("buscar ")) {
            val query = normalizedText
                .replaceFirst("busca ", "")
                .replaceFirst("buscar ", "")
                .trim()

            if (query.isNotEmpty()) {
                executeAsync("search_web:$query")
                return true
            }
        }

        // ---------------------------------------------------------
        // CLIC POR NÚMERO (ej. "toca el 5", "presiona 5", "cinco")
        // ---------------------------------------------------------
        val normalizedNumbersText = normalizedText
            .replace(Regex("""\buno\b"""), "1")
            .replace(Regex("""\bdos\b"""), "2")
            .replace(Regex("""\btres\b"""), "3")
            .replace(Regex("""\bcuatro\b"""), "4")
            .replace(Regex("""\bcinco\b"""), "5")
            .replace(Regex("""\bseis\b"""), "6")
            .replace(Regex("""\bsiete\b"""), "7")
            .replace(Regex("""\bocho\b"""), "8")
            .replace(Regex("""\bnueve\b"""), "9")
            .replace(Regex("""\bdiez\b"""), "10")

        val numberPattern = Pattern.compile(
            """(?:pulsa|pulsar|clic|click|numero|el|toca|tocar|presiona|presionar|selecciona|seleccionar|opcion)\s+(?:el\s+)?(\d+)"""
        )
        val matcher = numberPattern.matcher(normalizedNumbersText)

        if (matcher.find()) {
            val num = matcher.group(1)
            if (!num.isNullOrEmpty()) {
                Log.d("CommandHandler", "Click por número detectado: $num")
                executeAsync("click_number:$num")
                return true
            }
        }

        if (normalizedNumbersText.matches(Regex("""^\d+$"""))) {
            Log.d("CommandHandler", "Número aislado detectado: $normalizedNumbersText")
            executeAsync("click_number:$normalizedNumbersText")
            return true
        }

        // ---------------------------------------------------------
        // CLICK POR TEXTO (ej. "pulsa enviar", "toca enviar")
        // ---------------------------------------------------------
        if (normalizedText.startsWith("pulsa ") || normalizedText.startsWith("toca ") || normalizedText.startsWith("presiona ")) {
            val targetText = normalizedText
                .replaceFirst("pulsa ", "")
                .replaceFirst("toca ", "")
                .replaceFirst("presiona ", "")
                .trim()
            if (targetText.isNotEmpty()) {
                executeAsync("click_text:$targetText")
                return true
            }
        }

        // ---------------------------------------------------------
        // COMANDOS PERSONALIZADOS DEL REPOSITORIO
        // ---------------------------------------------------------
        val command = commands.firstOrNull { voiceCommand ->
            val trigger = voiceCommand.trigger.normalize()
            normalizedText == trigger ||
                    normalizedText.contains(trigger) ||
                    isFuzzyMatch(normalizedText, trigger)
        }

        if (command != null) {
            Log.d(
                "CommandHandler",
                "Comando personalizado encontrado: '${command.trigger}' -> '${command.action}'"
            )

            if (command.action.startsWith("internal_")) {
                internalListener?.invoke(command.action)
            } else {
                executeAsync(command.action)
            }
            return true
        }

        Log.d("CommandHandler", "Comando no reconocido: '$normalizedText'")
        return false
    }

    /**
     * Coincidencia aproximada sencilla.
     */
    private fun isFuzzyMatch(
        text: String,
        trigger: String
    ): Boolean {

        if (text.length < 4 || trigger.length < 4) {
            return false
        }

        val prefixLength =
            (trigger.length * 0.7)
                .toInt()
                .coerceAtLeast(1)

        val prefix = trigger.substring(
            0,
            prefixLength.coerceAtMost(trigger.length)
        )

        return text.contains(prefix)
    }

    /**
     * Ejecuta una serie de comandos uno tras otro con una pausa entre ellos.
     */
    private fun executeSequence(parts: List<String>, commands: List<VoiceCommand>) {
        Thread {
            for (part in parts) {
                val cleanPart = part.trim().replace(Regex("^[*;,\\s]+"), "").trim()
                if (cleanPart.isNotEmpty()) {
                    Log.d("CommandHandler", "Ejecutando paso de secuencia: '$cleanPart'")
                    executeSingleCommand(cleanPart, commands)
                    
                    // Pausa de 1.8 segundos entre comandos para que la UI cargue
                    try { Thread.sleep(1800) } catch (_: Exception) {}
                }
            }
        }.start()
    }

    /**
     * Ejecuta la acción fuera del hilo principal.
     */
    private fun executeAsync(action: String) {
        Thread {
            try {
                processAction(action)
            } catch (e: Exception) {
                Log.e("CommandHandler", "Error ejecutando acción '$action'", e)
            }
        }.start()
    }

    /**
     * Motor principal de ejecución:
     *
     * 1. Si la acción contiene múltiples sub-acciones separadas por coma o punto y coma,
     *    las ejecuta en secuencia.
     * 2. Intenta Root cuando está disponible.
     * 3. Si Root no puede ejecutar la acción, utiliza AccessibilityService.
     */
    private fun processAction(action: String) {

        val cleanAction = action.trim()

        if (cleanAction.isEmpty()) {
            return
        }

        Log.d("CommandHandler", "Ejecutando acción: $cleanAction")

        // ---------------------------------------------------------
        // ACCIONES MULTI-PASO EN CONFIGURACIÓN DE COMANDO
        // Ejemplo: "open_app:whatsapp, click_first_chat, type:hola, click_send"
        // ---------------------------------------------------------
        if ((cleanAction.contains(",") || cleanAction.contains(";")) && !cleanAction.startsWith("type:")) {
            val subActions = cleanAction.split(Regex("""[,;]""")).map { it.trim() }.filter { it.isNotEmpty() }
            if (subActions.size > 1) {
                Thread {
                    for (sub in subActions) {
                        processAction(sub)
                        try { Thread.sleep(1500) } catch (_: Exception) {}
                    }
                }.start()
                return
            }
        }

        // ---------------------------------------------------------
        // ACCIONES INTERNAS
        // ---------------------------------------------------------

        if (cleanAction.startsWith("internal_")) {
            internalListener?.invoke(cleanAction)
            return
        }

        // ---------------------------------------------------------
        // MODO DE EJECUCIÓN (HYBRID / ROOT / ACCESSIBILITY)
        // ---------------------------------------------------------
        val repository = CommandRepository(context)
        val mode = repository.getExecutionMode()

        val allowRoot = (mode == "HYBRID" || mode == "ROOT")
        val allowAccessibility = (mode == "HYBRID" || mode == "ACCESSIBILITY")

        var shellSuccess = false

        if (allowRoot && ShellUtils.isRootAvailable()) {
            shellSuccess = try {
                when {
                    cleanAction == "global_back" -> ShellUtils.executeCommand("input keyevent 4")
                    cleanAction == "global_home" -> ShellUtils.executeCommand("input keyevent 3")
                    cleanAction == "global_recents" -> ShellUtils.executeCommand("input keyevent 187")
                    cleanAction.startsWith("type:") -> {
                        val textToType = cleanAction.removePrefix("type:")
                        val escapedText = textToType.replace("\\", "\\\\").replace("\"", "\\\"").replace(" ", "%s")
                        ShellUtils.executeCommand("input text \"$escapedText\"")
                    }
                    cleanAction.startsWith("open_app:") || cleanAction.startsWith("click_") || cleanAction == "show_grid" || cleanAction == "show_numbers" || cleanAction == "hide_overlays" || cleanAction == "click_send" -> false
                    else -> ShellUtils.executeCommand(cleanAction)
                }
            } catch (e: Exception) {
                Log.e("CommandHandler", "Error ejecutando Root: $cleanAction", e)
                false
            }
        }

        if (!shellSuccess && allowAccessibility) {
            Log.d("CommandHandler", "Ejecutando con AccessibilityService: '$cleanAction'")
            try {
                accessibilityController.execute(cleanAction)
            } catch (e: Exception) {
                Log.e("CommandHandler", "Error ejecutando AccessibilityService: $cleanAction", e)
            }
        }
    }

    enum class CommandResult {
        Ignored,
        WakeWordOnly,
        Executed
    }
}