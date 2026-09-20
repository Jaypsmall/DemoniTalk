package com.example.demonitalk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.util.Locale

class AccessibilityController(
    private val context: Context
) {

    companion object {
        private const val TAG = "AccessibilityController"
    }

    /**
     * Ejecuta una acción utilizando AccessibilityService
     * cuando no puede resolverse mediante Root.
     */
    fun execute(action: String): Boolean {

        val cleanAction = action.trim()

        if (cleanAction.isEmpty()) {
            return false
        }

        Log.d(TAG, "Ejecutando acción: $cleanAction")

        // ---------------------------------------------------------
        // ABRIR APLICACIÓN POR NOMBRE
        // ---------------------------------------------------------

        if (cleanAction.startsWith("open_app:")) {

            val appName = cleanAction
                .removePrefix("open_app:")
                .trim()

            if (appName.isEmpty()) {
                return false
            }

            return launchAppByName(appName)
        }

        // ---------------------------------------------------------
        // ABRIR APLICACIÓN MEDIANTE NOMBRE DE PAQUETE
        //
        // Ejemplo:
        // com.whatsapp
        // com.android.settings
        // ---------------------------------------------------------

        if (isPackageName(cleanAction)) {
            return launchPackage(cleanAction)
        }

        // ---------------------------------------------------------
        // ACCESSIBILITY SERVICE
        // ---------------------------------------------------------

        val service = DemoniAccessibilityService.instance

        if (service == null) {
            Log.e(TAG, "AccessibilityService no está activo para: $cleanAction")
            // Intentar abrir ajustes de accesibilidad si no está activo y se requiere
            return false
        }

        // ---------------------------------------------------------
        // DISPATCHER DE ACCIONES
        // ---------------------------------------------------------

        return try {
            when {
                cleanAction.startsWith("type:") -> {
                    val text = cleanAction.removePrefix("type:")
                    Log.d(TAG, "Escribiendo texto: $text")
                    service.typeText(text)
                }

                cleanAction == "click_send" -> service.clickSendButton()
                cleanAction == "global_back" -> service.performBack()
                cleanAction == "global_home" -> service.performHome()
                cleanAction == "global_recents" -> service.performRecents()
                cleanAction == "click_first_chat" -> service.clickFirstConversation()

                cleanAction.startsWith("click_text:") -> {
                    val targetText = cleanAction.removePrefix("click_text:").trim()
                    if (targetText.isEmpty()) false else service.clickText(targetText)
                }

                cleanAction.startsWith("click_number:") -> {
                    val number = cleanAction.removePrefix("click_number:").trim()
                    if (number.isEmpty()) false else service.clickNumber(number)
                }

                cleanAction.startsWith("search_web:") -> {
                    val query = cleanAction.removePrefix("search_web:").trim()
                    if (query.isEmpty()) false else searchWeb(query)
                }

                cleanAction.startsWith("play_music:") -> {
                    val query = cleanAction.removePrefix("play_music:").trim()
                    if (query.isEmpty()) false else playMusic(query)
                }

                cleanAction == "media_next" -> sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                cleanAction == "media_previous" -> sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                cleanAction == "media_pause" -> sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                cleanAction == "media_play" -> sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
                cleanAction == "show_grid" -> { service.showGrid(); true }
                cleanAction == "show_numbers" -> { service.showNumbers(); true }
                cleanAction == "hide_overlays" -> { service.hideOverlays(); true }

                else -> {
                    Log.w(TAG, "Acción no soportada: $cleanAction")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando acción de accesibilidad: $cleanAction", e)
            false
        }
    }

    // -------------------------------------------------------------
    // LANZAR APP POR NOMBRE
    // -------------------------------------------------------------

    private fun launchAppByName(name: String): Boolean {

        val pm = context.packageManager

        val searchName = name
            .trim()
            .lowercase(Locale.getDefault())

        if (searchName.isEmpty()) {
            return false
        }

        val mainIntent = Intent(
            Intent.ACTION_MAIN,
            null
        ).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = try {

            pm.queryIntentActivities(
                mainIntent,
                PackageManager.MATCH_ALL
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error buscando aplicaciones",
                e
            )

            return false
        }

        // ---------------------------------------------------------
        // PRIMERA PASADA: COINCIDENCIA EXACTA
        // ---------------------------------------------------------

        val exactMatch = apps.firstOrNull { info ->

            val label = info
                .loadLabel(pm)
                .toString()
                .trim()
                .lowercase(Locale.getDefault())

            label == searchName
        }

        // ---------------------------------------------------------
        // SEGUNDA PASADA: COINCIDENCIA PARCIAL
        // ---------------------------------------------------------

        val foundApp = exactMatch ?: apps.firstOrNull { info ->

            val label = info
                .loadLabel(pm)
                .toString()
                .trim()
                .lowercase(Locale.getDefault())

            label.contains(searchName)
        }

        if (foundApp == null) {

            Log.e(
                TAG,
                "Aplicación no encontrada: $name"
            )

            return false
        }

        val activityInfo = foundApp.activityInfo

        val packageName = activityInfo.packageName
        val activityName = activityInfo.name

        Log.d(
            TAG,
            "Aplicación encontrada: $packageName/$activityName"
        )

        // ---------------------------------------------------------
        // INTENTO ROOT
        // ---------------------------------------------------------

        if (ShellUtils.isRootAvailable()) {

            val command =
                "am start -n $packageName/$activityName"

            Log.d(
                TAG,
                "Intentando abrir mediante Root: $command"
            )

            try {

                if (ShellUtils.executeCommand(command)) {
                    return true
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Error abriendo aplicación mediante Root",
                    e
                )
            }

            Log.w(
                TAG,
                "Root no pudo abrir la aplicación. Usando Intent."
            )
        }

        // ---------------------------------------------------------
        // FALLBACK: INTENT NORMAL
        // ---------------------------------------------------------

        return launchPackage(packageName)
    }

    // -------------------------------------------------------------
    // BÚSQUEDA WEB
    // -------------------------------------------------------------

    private fun searchWeb(query: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Búsqueda web iniciada: $query")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando búsqueda web", e)
            // Fallback a navegador normal si ACTION_WEB_SEARCH no está disponible
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=$query"))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    // -------------------------------------------------------------
    // CONTROL DE MÚSICA
    // -------------------------------------------------------------

    private fun playMusic(query: String): Boolean {
        return try {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Reproducción de música iniciada: $query")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando reproducción de música", e)
            false
        }
    }

    private fun sendMediaKey(keyCode: Int): Boolean {
        // Para YouTube Music y otros, a veces KEYCODE_MEDIA_PLAY (126) no funciona
        // si la app está en "sleep". Probamos con un Broadcast de sistema vía ROOT.
        if (ShellUtils.isRootAvailable()) {
            Log.d(TAG, "Enviando tecla media via ROOT: $keyCode")
            
            // Intentar el comando estándar
            ShellUtils.executeCommand("input keyevent $keyCode")
            
            // Si es PLAY, enviamos un broadcast extra para despertar a los reproductores tercos
            if (keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY) {
                // Comando "bruto" para forzar el Play en Android
                ShellUtils.executeCommand("am broadcast -a android.intent.action.MEDIA_BUTTON --ei android.intent.extra.KEY_EVENT $keyCode")
                // Reintento con PLAY_PAUSE (a veces es el único que despierta a YT Music)
                ShellUtils.executeCommand("input keyevent 85") 
            }
            return true
        }

        // Fallback: Enviar Intent de media
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
            audioManager.dispatchMediaKeyEvent(event)
            val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventUp)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando tecla media $keyCode", e)
            false
        }
    }

    // -------------------------------------------------------------
    // LANZAR PAQUETE
    // -------------------------------------------------------------

    private fun launchPackage(packageName: String): Boolean {

        val pm = context.packageManager

        val intent = try {

            pm.getLaunchIntentForPackage(packageName)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error obteniendo LaunchIntent para $packageName",
                e
            )

            return false
        }

        if (intent == null) {

            Log.e(
                TAG,
                "No existe actividad de lanzamiento para: $packageName"
            )

            return false
        }

        return try {

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )

            context.startActivity(intent)

            Log.d(
                TAG,
                "Aplicación lanzada: $packageName"
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "No se pudo lanzar $packageName",
                e
            )

            false
        }
    }

    // -------------------------------------------------------------
    // DETECTAR NOMBRE DE PAQUETE
    // -------------------------------------------------------------

    private fun isPackageName(value: String): Boolean {

        /*
         * Ejemplos válidos:
         *
         * com.whatsapp
         * com.android.settings
         * com.google.android.youtube
         */

        return value.matches(
            Regex(
                "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$"
            )
        )
    }
}