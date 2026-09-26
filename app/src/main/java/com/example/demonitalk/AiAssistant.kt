package com.example.demonitalk

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AiAssistant(context: Context) {

    private val repository = CommandRepository(context)

    fun isAiEnabled(): Boolean {
        return repository.isAiEnabled()
    }

    fun hasApiKey(): Boolean {
        return repository.getGeminiApiKey().trim().isNotEmpty()
    }

    suspend fun askGemini(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!repository.isAiEnabled()) {
            Log.d("AiAssistant", "IA desactivada por la configuración del usuario.")
            return@withContext null
        }

        val apiKey = repository.getGeminiApiKey().trim()

        // 1. Si el usuario ingresó clave de Gemini, la probamos
        if (apiKey.isNotEmpty()) {
            val modelsToTry = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-pro")

            for (modelName in modelsToTry) {
                try {
                    Log.d("AiAssistant", "Intentando con modelo: $modelName")
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        systemInstruction = content {
                            text("Eres Demoni, un asistente inteligente de voz en español, directo, ingenioso y servicial. Responde en un máximo de 2 frases cortas para lectura fluida por sintetizador de voz.")
                        }
                    )

                    val response = model.generateContent(prompt)
                    val reply = response.text?.trim()
                    if (!reply.isNullOrEmpty()) {
                        Log.d("AiAssistant", "Respuesta exitosa de $modelName: $reply")
                        return@withContext reply
                    }
                } catch (e: Exception) {
                    Log.w("AiAssistant", "Fallo modelo $modelName: ${e.message}")
                }
            }
        }

        // 2. FALLBACK 100% GRATUITO SIN CLAVE (IA Pública)
        Log.d("AiAssistant", "Usando motor de IA gratuito sin clave...")
        val freeReply = askFreePublicAi(prompt)
        if (!freeReply.isNullOrEmpty()) {
            Log.d("AiAssistant", "Respuesta exitosa de IA Gratuita: $freeReply")
            return@withContext freeReply
        }

        Log.e("AiAssistant", "No se pudo obtener respuesta de ningún motor de IA")
        null
    }

    private fun askFreePublicAi(prompt: String): String? {
        return try {
            val systemPrompt = "Eres Demoni, un asistente inteligente de voz en español, directo e ingenioso. Responde de forma muy corta en máximo 2 frases para voz."
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            val encodedSystem = URLEncoder.encode(systemPrompt, "UTF-8")

            val urlString = "https://text.pollinations.ai/$encodedPrompt?system=$encodedSystem"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                responseText.ifEmpty { null }
            } else {
                Log.w("AiAssistant", "Servidor de IA Gratuita respondió con código: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e("AiAssistant", "Error en IA Gratuita: ${e.message}")
            null
        }
    }
}
