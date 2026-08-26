package com.example.demonitalk

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions
import org.json.JSONArray

class LocalAIManager(context: Context, modelPath: String) {

    val llmInference: LlmInference

    init {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath) // Ruta al archivo .bin en almacenamiento local
            .setMaxTokens(128)
            .build()
        llmInference = createFromOptions(context, options)
    }

}

fun findCommandFromIntent(localAIManager: LocalAIManager, userSpeech: String, jsonCardsString: Unit): String? {
    // 1. Extraemos las tarjetas disponibles para explicárselas a la IA
    val cardsArray = JSONArray(jsonCardsString)
    val availableCommands = StringBuilder()

    for (i in 0 until cardsArray.length()) {
        val card = cardsArray.getJSONObject(i)
        val name = card.optString("name")
        val desc = card.optString("description", name)
        val command = card.optString("command")
        availableCommands.append("- ID: $name | Info: $desc | Shell: $command\n")
    }

    // 2. Prompt estructurado para forzar respuesta precisa
    val prompt = """
        Eres el interprete de comandos de un sistema Android Root.
        Analiza lo que pide el usuario y selecciona el comando exacto de la lista.
        
        Comandos disponibles:
        $availableCommands
        
        Si la peticion equivale a uno de los comandos (aunque use palabras distintas), responde UNICAMENTE con el comando Shell a ejecutar.
        Si no coincide con nada, responde: NONE.
        
        Usuario: "$userSpeech"
        Respuesta:
    """.trimIndent()

    // 3. Ejecutamos la inferencia local
    val result = localAIManager.llmInference.generateResponse(prompt).trim()

    return if (result.contains("NONE") || result.isEmpty()) null else result
}