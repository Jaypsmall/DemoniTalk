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

    fun isAiAvailable(): Boolean {
        if (!isAiEnabled()) return false
        val geminiReady = repository.isGeminiEngineEnabled() && hasApiKey()
        val freeReady = repository.isFreeAiEngineEnabled()
        return geminiReady || freeReady
    }

    suspend fun askGemini(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isAiEnabled()) {
            Log.d("AiAssistant", "IA desactivada por la configuración del usuario.")
            return@withContext null
        }

        val isGeminiAllowed = repository.isGeminiEngineEnabled()
        val isFreeAllowed = repository.isFreeAiEngineEnabled()

        val apiKey = repository.getGeminiApiKey().trim()

        // Si SOLO la IA Gratuita está activada (o Gemini no tiene API key ni está activo)
        val useOnlyFree = isFreeAllowed && (!isGeminiAllowed || apiKey.isEmpty())

        if (useOnlyFree) {
            Log.d("AiAssistant", "Solo IA Gratuita activa: respondiendo directo sin tocar Gemini...")
            val freeReply = askFreePublicAi(prompt)
            if (!freeReply.isNullOrEmpty()) {
                Log.d("AiAssistant", "Respuesta exitosa de IA Gratuita: $freeReply")
                return@withContext freeReply
            }
            return@withContext null
        }

        // 1. Si el motor Gemini API está activado y hay clave, lo intentamos
        if (isGeminiAllowed && apiKey.isNotEmpty()) {
            val modelsToTry = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-3.8-flash")
            val systemInstructionText = """
                Eres Demoni, un asistente inteligente de voz en español para Android, directo y resolutivo. Responde en máximo 2 frases cortas.
                REGLA CRÍTICA: NO incluyas NINGÚN emoji ni emoticono (como 🚀, 😈, etc.) porque la voz los lee en voz alta.

                CAPACIDAD DE EJECUCIÓN:
                Si el usuario pide realizar una acción o controlar el teléfono (de forma directa o coloquial), DEBES incluir la etiqueta '[CMD:acciones]' en tu respuesta.
                Puedes encadenar múltiples acciones separadas por punto y coma (;) para secuencias completas.

                COMANDOS DISPONIBLES:
                - Abrir apps: open_app:nombre_app (ej: open_app:whatsapp, open_app:instagram) o paquete (ej: com.whatsapp, com.android.camera).
                - Chats y Mensajes: click_first_chat (abre primer chat), type:texto (escribe el texto), click_send (envía el mensaje).
                - Navegación: global_back, global_home, global_recents.
                - Pulsar elementos: click_text:texto, click_number:numero.
                - Linterna: torch_on, torch_off.
                - Música/Web: play_music:cancion, search_web:consulta.

                EJEMPLO: "Ábreme el primer chat de whatsapp y dile Hola" -> "[CMD:com.whatsapp; click_first_chat; type:Hola; click_send] Claro, enviando mensaje en WhatsApp."
            """.trimIndent()

            for (modelName in modelsToTry) {
                try {
                    Log.d("AiAssistant", "Intentando con modelo Gemini: $modelName")
                    val model = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey,
                        systemInstruction = content {
                            text(systemInstructionText)
                        }
                    )

                    val response = model.generateContent(prompt)
                    val reply = response.text?.trim()
                    if (!reply.isNullOrEmpty()) {
                        Log.d("AiAssistant", "Respuesta exitosa de Gemini ($modelName): $reply")
                        return@withContext reply
                    }
                } catch (e: Exception) {
                    Log.w("AiAssistant", "Fallo modelo Gemini $modelName: ${e.message}")
                }
            }
        }

        // 2. Si Gemini falló pero la IA Gratuita está activada, se usa como respaldo
        if (isFreeAllowed) {
            Log.d("AiAssistant", "Usando motor de IA gratuito como respaldo...")
            val freeReply = askFreePublicAi(prompt)
            if (!freeReply.isNullOrEmpty()) {
                Log.d("AiAssistant", "Respuesta exitosa de IA Gratuita: $freeReply")
                return@withContext freeReply
            }
        }

        Log.e("AiAssistant", "No se pudo obtener respuesta (o ningún motor de IA activo)")
        null
    }

    private fun askFreePublicAi(prompt: String): String? {
        return try {
            val systemPrompt = "Eres Demoni, asistente de voz en español. Responde en máximo 2 frases. REGLA: SIN EMOJIS (se leen en voz alta). Si piden acciones/apps coloquiales, usa [CMD:acciones] (ej: [CMD:com.whatsapp; click_first_chat; type:texto; click_send] o [CMD:open_app:instagram] o [CMD:torch_on])."
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            val encodedSystem = URLEncoder.encode(systemPrompt, "UTF-8")

            val urlString = "https://text.pollinations.ai/$encodedPrompt?system=$encodedSystem"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 5000

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
