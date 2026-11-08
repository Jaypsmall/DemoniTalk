package com.example.demonitalk

import java.util.Locale

class OfflineFallbackManager {

    private val offlineResponses = mapOf(
        "hola" to listOf("¿Qué tal, socio?", "Hola Peca, aquí ando offline pero operativo.", "¡Wassup! ¿Qué se cuenta?"),
        "como estas" to listOf("Al 100 por ciento, modo offline activo.", "Sin internet pero con toda la energía, jefe."),
        "que hora es" to listOf("Revisa tu pantalla, socio, que estoy sin red.", "No tengo acceso a servidores de hora exacta ahora mismo."),
        "adios" to listOf("Aquí me quedo en la sombra cuidando el equipo.", "Nos vemos, socio. Apagando motores."),
        "gracias" to listOf("Para eso estamos, jefe.", "De nada, bro.")
    )

    fun getLocalResponse(query: String): String? {
        val cleanQuery = query.lowercase(Locale.getDefault()).trim()

        // Buscamos coincidencia exacta o parcial con palabras clave
        for ((key, responses) in offlineResponses) {
            if (cleanQuery.contains(key)) {
                return responses.random()
            }
        }

        // Si no hay respuesta específica offline, devolvemos una genérica por defecto
        if (cleanQuery.isNotEmpty()) {
            return listOf(
                "Estoy sin internet, socio, y ese comando se me escapa.",
                "No tengo conexión a la red para procesar eso ahora mismo.",
                "Sin datos móviles ni Wi-Fi, mi alcance es limitado, jefe."
            ).random()
        }

        return null
    }
}