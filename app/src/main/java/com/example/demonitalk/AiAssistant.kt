package com.example.demonitalk

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AiAssistant(context: Context, private val ttsManager: TtsManager?) { // <--- Añadimos el TtsManager aquí

    private val repository = CommandRepository(context)

    fun isAiEnabled(): Boolean {
        return repository.isAiEnabled()
    }

    fun isAiAvailable(): Boolean {
        return isAiEnabled()
    }

    fun askFreeAi(prompt: String): String? {
        if (!isAiAvailable()) {
            Log.d("AiAssistant", "IA desactivada en preferencias.")
            ttsManager?.speakText("La inteligencia artificial está desactivada en las preferencias.")
            return null
        }

        val finalPrompt = buildPrompt(prompt)

        // Try POST with models: openai, deepseek, mistral, unity, grok
        val models = listOf("openai", "deepseek", "mistral", "unity", "grok")
        for (model in models) {
            val resPost = fetchPollinationsPost(finalPrompt, model)
            if (!resPost.isNullOrEmpty()) {
                Log.d("AiAssistant", "Respuesta obtenida via POST (model=$model): $resPost")

                // ¡AQUÍ ESTÁ LA MAGIA! Limpiamos y reproducimos por voz automáticamente
                speakResponse(resPost)

                return resPost
            }
        }

        // Fallback to GET across multiple models
        val getModels = listOf("openai", "mistral", "deepseek")
        for (model in getModels) {
            val resGet = fetchPollinationsGet(finalPrompt, model)
            if (!resGet.isNullOrEmpty()) {
                Log.d("AiAssistant", "Respuesta obtenida via GET (model=$model): $resGet")

                // ¡AQUÍ TAMBIÉN!
                speakResponse(resGet)

                return resGet
            }
        }

        Log.w("AiAssistant", "Todos los métodos y modelos de IA fallaron.")
        return null
    }

    private fun speakResponse(text: String) {
        if (ttsManager != null) {
            // Limpiamos reemplazando espacios raros de Unicode y caracteres invisibles
            val safeText = text
                .replace("\u00A0", " ") // Espacio de no separación
                .replace(Regex("[\\p{C}\\p{Cf}]"), "") // Caracteres de control invisibles
                .trim()

            val cleanedText = ttsManager.cleanTextForSpeech(safeText).trim()

            // Si la limpieza fue demasiado agresiva y quedó vacío, usamos el texto original saneado
            val finalSpeechText = if (cleanedText.isNotEmpty()) cleanedText else safeText

            Log.d("AiAssistant", "Forzando voz en altavoz para: '$finalSpeechText'")
            ttsManager.speakText(finalSpeechText)
        } else {
            Log.w("AiAssistant", "TtsManager es nulo, no se puede hablar.")
        }
    }

    private fun buildPrompt(prompt: String): String {
        // ... (tu código de buildPrompt se mantiene igual)
        val instructions = repository.getAiInstructions().trim()
        val memory = repository.getAiMemory().trim()
        return buildString {
            appendLine("Eres Demoni, asistente de voz en español. Responde corto, claro y sin emojis.")
            if (instructions.isNotEmpty()) {
                appendLine()
                appendLine("Instrucciones del usuario:")
                appendLine(instructions)
            }
            if (memory.isNotEmpty()) {
                appendLine()
                appendLine("Memoria guardada:")
                appendLine(memory)
            }
            appendLine()
            appendLine("Usuario:")
            append(prompt.trim())
        }
    }

    internal fun fetchPollinationsPost(prompt: String, model: String = "openai"): String? {
        // ... (tu código fetchPollinationsPost se mantiene igual)
        return try {
            val url = URL("https://text.pollinations.ai/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.doOutput = true

            val escapedPrompt = com.google.gson.Gson().toJson(prompt)
            val jsonInputString = """
                {
                  "model": "$model",
                  "messages": [
                    {"role": "system", "content": "Eres Demoni, asistente de voz en español. Responde directo y corto en máximo 2 frases. SIN EMOJIS."},
                    {"role": "user", "content": $escapedPrompt}
                  ]
                }
            """.trimIndent()

            connection.outputStream.use { os ->
                val input = jsonInputString.toByteArray(charset("utf-8"))
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                if (responseText.isNotEmpty()) {
                    if (responseText.startsWith("{")) {
                        try {
                            val map = com.google.gson.Gson().fromJson(responseText, Map::class.java)
                            val choices = map["choices"] as? List<*>
                            if (!choices.isNullOrEmpty()) {
                                val choice = choices[0] as? Map<*, *>
                                val message = choice?.get("message") as? Map<*, *>
                                val content = (message?.get("content") as? String)?.trim()
                                if (!content.isNullOrBlank()) return content
                            }
                        } catch (e: Exception) {
                            Log.e("AiAssistant", "Error parsing POST JSON ($model): ${e.message}")
                        }
                    }
                    return responseText
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun fetchPollinationsGet(prompt: String, model: String = "openai"): String? {
        // ... (tu código fetchPollinationsGet se mantiene igual)
        return try {
            val systemPrompt = "Eres Demoni, asistente de voz en español. Responde en máximo 2 frases. REGLA: SIN EMOJIS."
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            val encodedSystem = URLEncoder.encode(systemPrompt, "UTF-8")
            val urlString = "https://text.pollinations.ai/$encodedPrompt?system=$encodedSystem&model=$model"

            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                if (responseText.isNotEmpty()) {
                    if (responseText.startsWith("{")) {
                        try {
                            val map = com.google.gson.Gson().fromJson(responseText, Map::class.java)
                            val choices = map["choices"] as? List<*>
                            if (!choices.isNullOrEmpty()) {
                                val choice = choices[0] as? Map<*, *>
                                val message = choice?.get("message") as? Map<*, *>
                                val content = (message?.get("content") as? String)?.trim()
                                if (!content.isNullOrBlank()) return content
                            }
                        } catch (_: Exception) {}
                    }
                    return responseText
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}