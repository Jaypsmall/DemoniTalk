package com.example.demonitalk

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AiAssistant(context: Context) {

    private val repository = CommandRepository(context)

    fun isAiEnabled(): Boolean {
        return repository.isAiEnabled()
    }

    fun isAiAvailable(): Boolean {
        return isAiEnabled()
    }

    fun askFreeAi(prompt: String): String? {
        if (!isAiAvailable()) return null

        val finalPrompt = buildPrompt(prompt)
        val resGet = fetchPollinationsGet(finalPrompt)
        if (!resGet.isNullOrEmpty()) return resGet

        val resPost = fetchPollinationsPost(finalPrompt)
        if (!resPost.isNullOrEmpty()) return resPost

        return null
    }

    private fun buildPrompt(prompt: String): String {
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

    fun fetchPollinationsGet(prompt: String): String? {
        return try {
            val systemPrompt = "Eres Demoni, asistente de voz en español. Responde en máximo 2 frases. REGLA: SIN EMOJIS (se leen en voz alta). Si piden acciones/apps coloquiales, usa [CMD:acciones] (ej: [CMD:com.whatsapp; click_first_chat; type:texto; click_send] o [CMD:open_app:instagram] o [CMD:torch_on])."
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            val encodedSystem = URLEncoder.encode(systemPrompt, "UTF-8")

            val urlString = "https://text.pollinations.ai/$encodedPrompt?system=$encodedSystem"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            connection.setRequestProperty("Accept", "*/*")
            connection.connectTimeout = 8000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                if (responseText.isNotEmpty()) return responseText
            }
            null
        } catch (e: Exception) {
            Log.e("AiAssistant", "Error en IA Gratuita (GET): ${e.message}")
            null
        }
    }

    internal fun fetchPollinationsPost(prompt: String): String? {
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

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                if (responseText.isNotEmpty()) return responseText
            }
            null
        } catch (e: Exception) {
            Log.e("AiAssistant", "Error en IA Gratuita (POST): ${e.message}")
            null
        }
    }
}
