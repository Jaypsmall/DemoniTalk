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

    /**
     * Normaliza el texto reconocido por voz:
     * - elimina tildes
     * - convierte a minúsculas
     * - elimina espacios sobrantes
     * - unifica algunas palabras utilizadas como wake word
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
            .replace("demonio", "maquina")
            .replace("demoni talk", "maquina")
            .replace("demoni", "maquina")
            .replace("puta", "maquina")
            .replace("p***", "maquina")
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
        
        // ---------------------------------------------------------
        // SOPORTE MULTI-COMANDO
        // Detectamos si hay conectores como " y " o " luego "
        // ---------------------------------------------------------
        val multiCommandRegex = Regex("""\s+(y|luego|después)\s+(?=abre|abrir|pulsa|clic|escribe|manda|pon|reproduce|vuelve|inicio|recientes|buscar|busca|enviar)""")
        
        if (normalizedText.contains(multiCommandRegex)) {
            val parts = normalizedText.split(multiCommandRegex)
            if (parts.size > 1) {
                Log.d("CommandHandler", "Multi-comando detectado. Partes: $parts")
                executeSequence(parts, commands)
                return CommandResult.Executed
            }
        }

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
        // COMANDOS NATIVOS
        // ---------------------------------------------------------

        // ---------------------------------------------------------
        // ESCRIBIR
        // Ejemplos:
        // "escribe hola hermano"
        // "manda este mensaje hola"
        // ---------------------------------------------------------

        when {
            normalizedText.startsWith("escribe ") -> {

                val content = normalizedText
                    .removePrefix("escribe ")
                    .trim()

                if (content.isNotEmpty()) {
                    executeAsync("type:$content")
                    return CommandResult.Executed
                }
            }

            normalizedText == "escribe" -> {
                return CommandResult.Ignored
            }

            normalizedText.startsWith("manda este mensaje ") -> {

                val content = normalizedText
                    .removePrefix("manda este mensaje ")
                    .trim()

                if (content.isNotEmpty()) {
                    executeAsync("type:$content")
                    return CommandResult.Executed
                }
            }

            normalizedText == "manda este mensaje" -> {
                return CommandResult.Ignored
            }
        }

        // ---------------------------------------------------------
        // ABRIR APLICACIÓN
        // Ejemplos:
        // "abre WhatsApp"
        // "abrir youtube"
        // ---------------------------------------------------------

        when {
            normalizedText.startsWith("abre ") -> {

                val appName = normalizedText
                    .removePrefix("abre ")
                    .trim()

                if (appName.isNotEmpty()) {
                    executeAsync("open_app:$appName")
                    return CommandResult.Executed
                }
            }

            normalizedText.startsWith("abrir ") -> {

                val appName = normalizedText
                    .removePrefix("abrir ")
                    .trim()

                if (appName.isNotEmpty()) {
                    executeAsync("open_app:$appName")
                    return CommandResult.Executed
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
                return CommandResult.Executed
            }

            "inicio",
            "vete a casa",
            "ir a casa",
            "casa" -> {
                executeAsync("global_home")
                return CommandResult.Executed
            }

            "recientes",
            "aplicaciones recientes" -> {
                executeAsync("global_recents")
                return CommandResult.Executed
            }

            "enviar",
            "manda el mensaje" -> {
                executeAsync("click_send")
                return CommandResult.Executed
            }

            "cuadricula",
            "cuadricula de numeros",
            "muestra cuadricula" -> {
                executeAsync("show_grid")
                return CommandResult.Executed
            }

            "numeros",
            "muestra numeros",
            "mostrar numeros" -> {
                executeAsync("show_numbers")
                return CommandResult.Executed
            }

            "oculta todo",
            "limpia pantalla",
            "ocultar todo" -> {
                executeAsync("hide_overlays")
                return CommandResult.Executed
            }

            "silencio",
            "para de escuchar",
            "deja de escuchar",
            "deja de oir",
            "detente" -> {

                internalListener?.invoke("internal_stop")

                return CommandResult.Executed
            }

            // --- MEDIA CONTROL ---
            "siguiente cancion",
            "siguiente",
            "proxima" -> {
                executeAsync("media_next")
                return CommandResult.Executed
            }

            "cancion anterior",
            "anterior" -> {
                executeAsync("media_previous")
                return CommandResult.Executed
            }

            "para la musica",
            "para",
            "pausa",
            "deten la musica" -> {
                executeAsync("media_pause")
                return CommandResult.Executed
            }

            "reanuda la musica",
            "continua",
            "play" -> {
                executeAsync("media_play")
                return CommandResult.Executed
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
                return CommandResult.Executed
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
                return CommandResult.Executed
            }
        }

        // ---------------------------------------------------------
        // CLIC POR NÚMERO
        //
        // Ejemplos:
        // "pulsa 5"
        // "clic 5"
        // "número 5"
        // "el 5"
        // ---------------------------------------------------------

        val numberPattern = Pattern.compile(
            """(?:pulsa|clic|click|numero|el)\s+(\d+)"""
        )

        val matcher = numberPattern.matcher(normalizedText)

        if (matcher.find()) {

            val num = matcher.group(1)

            if (!num.isNullOrEmpty()) {

                Log.d(
                    "CommandHandler",
                    "Click por número detectado: $num"
                )

                executeAsync("click_number:$num")

                return CommandResult.Executed
            }
        }

        // ---------------------------------------------------------
        // CLICK POR TEXTO
        //
        // Ejemplo:
        // "pulsa enviar"
        // ---------------------------------------------------------

        if (normalizedText.startsWith("pulsa ")) {

            val targetText = normalizedText
                .removePrefix("pulsa ")
                .trim()

            if (targetText.isNotEmpty()) {

                executeAsync("click_text:$targetText")

                return CommandResult.Executed
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

            return CommandResult.Executed
        }

        // ---------------------------------------------------------
        // NO SE ENCONTRÓ NINGÚN COMANDO
        // ---------------------------------------------------------

        Log.d(
            "CommandHandler",
            "Comando no reconocido: '$normalizedText'"
        )

        return CommandResult.Ignored
    }

    /**
     * Coincidencia aproximada sencilla.
     *
     * Solo se utiliza con palabras suficientemente largas.
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
     * Ejecuta una serie de comandos uno tras otro con una pausa.
     */
    private fun executeSequence(parts: List<String>, commands: List<VoiceCommand>) {
        Thread {
            for (part in parts) {
                val cleanPart = part.trim()
                if (cleanPart.isNotEmpty()) {
                    Log.d("CommandHandler", "Ejecutando parte de secuencia: $cleanPart")
                    // Llamamos a execute de forma recursiva para cada parte, pero sin modo multi-comando
                    // Para evitar bucles infinitos, procesamos la lógica de cada parte aquí.
                    innerExecute(cleanPart, commands)
                    
                    // Pausa de 1.5 segundos entre comandos para que la UI cargue
                    try { Thread.sleep(1500) } catch (_: Exception) {}
                }
            }
        }.start()
    }

    /**
     * Versión simplificada de execute para partes de una secuencia.
     */
    private fun innerExecute(text: String, commands: List<VoiceCommand>) {
        // Esta lógica es igual a la de execute() pero sin wake word ni detección multi
        val normalizedText = text.normalize()

        // Aquí invocamos la misma lógica de los bloques when/if de execute()
        // Para no duplicar código, en una refactorización real extraeríamos esto.
        // Por ahora, procesamos los más importantes.
        
        when {
            normalizedText.startsWith("abre ") -> processAction("open_app:${normalizedText.removePrefix("abre ").trim()}")
            normalizedText.startsWith("escribe ") -> processAction("type:${normalizedText.removePrefix("escribe ").trim()}")
            normalizedText.startsWith("pulsa ") -> processAction("click_text:${normalizedText.removePrefix("pulsa ").trim()}")
            normalizedText == "enviar" -> processAction("click_send")
            normalizedText == "inicio" -> processAction("global_home")
            normalizedText == "vuelve atras" -> processAction("global_back")
            
            // Comandos de repositorio
            else -> {
                val command = commands.firstOrNull { it.trigger.normalize() == normalizedText || normalizedText.contains(it.trigger.normalize()) }
                command?.let { processAction(it.action) }
            }
        }
    }

    /**
     * Ejecuta la acción fuera del hilo principal.
     */
    private fun executeAsync(action: String) {

        Thread {
            try {
                processAction(action)
            } catch (e: Exception) {

                Log.e(
                    "CommandHandler",
                    "Error ejecutando acción '$action'",
                    e
                )
            }
        }.start()
    }

    /**
     * Motor principal de ejecución:
     *
     * 1. Intenta Root cuando está disponible.
     * 2. Si Root no puede ejecutar la acción,
     *    utiliza AccessibilityService.
     */
    private fun processAction(action: String) {

        val cleanAction = action.trim()

        if (cleanAction.isEmpty()) {
            return
        }

        Log.d(
            "CommandHandler",
            "Ejecutando acción: $cleanAction"
        )

        // ---------------------------------------------------------
        // ACCIONES INTERNAS
        // ---------------------------------------------------------

        if (cleanAction.startsWith("internal_")) {

            internalListener?.invoke(cleanAction)

            return
        }

        // ---------------------------------------------------------
        // ROOT
        // ---------------------------------------------------------

        var shellSuccess = false

        if (ShellUtils.isRootAvailable()) {

            shellSuccess = try {

                when {

                    cleanAction == "global_back" -> {
                        ShellUtils.executeCommand(
                            "input keyevent 4"
                        )
                    }

                    cleanAction == "global_home" -> {
                        ShellUtils.executeCommand(
                            "input keyevent 3"
                        )
                    }

                    cleanAction == "global_recents" -> {
                        ShellUtils.executeCommand(
                            "input keyevent 187"
                        )
                    }

                    cleanAction.startsWith("type:") -> {

                        val textToType =
                            cleanAction
                                .removePrefix("type:")

                        /*
                         * input text utiliza %s para espacios.
                         *
                         * También escapamos caracteres que pueden
                         * interferir con la ejecución del shell.
                         */
                        val escapedText = textToType
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace(" ", "%s")

                        ShellUtils.executeCommand(
                            "input text \"$escapedText\""
                        )
                    }

                    /*
                     * Las acciones de accesibilidad no se ejecutan
                     * directamente mediante shell aquí.
                     */
                    cleanAction.startsWith("open_app:") || cleanAction.startsWith("click_") || cleanAction == "show_grid" || cleanAction == "show_numbers" || cleanAction == "hide_overlays" || cleanAction == "click_send" -> {
                        false
                    }

                    else -> {
                        ShellUtils.executeCommand(cleanAction)
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    "CommandHandler",
                    "Error ejecutando Root: $cleanAction",
                    e
                )

                false
            }
        }

        // ---------------------------------------------------------
        // FALLBACK ACCESSIBILITY
        // ---------------------------------------------------------

        if (!shellSuccess) {

            Log.d(
                "CommandHandler",
                "Root no ejecutó '$cleanAction'. Usando AccessibilityService."
            )

            try {

                accessibilityController.execute(
                    cleanAction
                )

            } catch (e: Exception) {

                Log.e(
                    "CommandHandler",
                    "Error ejecutando AccessibilityService: $cleanAction",
                    e
                )
            }
        }
    }

    enum class CommandResult {
        Ignored,
        WakeWordOnly,
        Executed
    }
}