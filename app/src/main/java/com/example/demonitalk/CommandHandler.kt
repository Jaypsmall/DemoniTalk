package com.example.demonitalk

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.util.Log
import java.io.DataOutputStream
import java.text.Normalizer
import java.util.regex.Pattern

class CommandHandler(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null

    init {
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error getting camera ID", e)
        }
    }

    private fun String.normalize(): String {
        val nfdNormalizedString = Normalizer.normalize(this, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(nfdNormalizedString).replaceAll("").lowercase().trim()
    }

    fun execute(text: String, commands: List<VoiceCommand>) {
        val normalizedText = text.normalize()
        Log.d("CommandHandler", "Searching for command in: '$normalizedText'")
        
        // Mejora del motor: Búsqueda por coincidencia más flexible
        val command = commands.find { 
            val trigger = it.trigger.normalize()
            // Coincidencia exacta o contenida
            normalizedText.contains(trigger) || trigger.contains(normalizedText) ||
            // Opcional: podrías añadir Levenshtein para errores leves de dictado
            isFuzzyMatch(normalizedText, trigger)
        }

        if (command != null) {
            Thread {
                processAction(command.action, command.isRoot)
            }.start()
        } else {
            Log.d("CommandHandler", "No command found for: $text")
        }
    }

    private fun isFuzzyMatch(text: String, trigger: String): Boolean {
        if (text.length < 4 || trigger.length < 4) return false
        // Si el trigger está contenido en un 80% o viceversa
        return text.contains(trigger.substring(0, (trigger.length * 0.8).toInt()))
    }

    private fun processAction(action: String, isRoot: Boolean) {
        // Comandos internos de alta velocidad
        when (action) {
            "torch_on" -> {
                toggleFlashlight(true)
                return
            }
            "torch_off" -> {
                toggleFlashlight(false)
                return
            }
        }

        if (isRoot) {
            executeRootCommand(action)
        } else {
            executeNormalCommand(action)
        }
    }

    private fun toggleFlashlight(status: Boolean) {
        try {
            cameraId?.let {
                cameraManager.setTorchMode(it, status)
                Log.d("CommandHandler", "Flashlight set to $status")
            }
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error toggling flashlight", e)
        }
    }

    private fun executeNormalCommand(action: String) {
        Log.d("CommandHandler", "Attempting to execute action: $action")
        try {
            // Si parece un nombre de paquete, intentamos lanzarlo como App
            if (action.contains(".") && !action.contains(" ") && !action.contains("/") && !action.contains("-")) {
                val intent = context.packageManager.getLaunchIntentForPackage(action)
                if (intent != null) {
                    Log.d("CommandHandler", "Launching app: $action")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            }

            // Si no es un paquete o falla el intent, lo ejecutamos como comando shell
            Log.d("CommandHandler", "Executing as shell command: $action")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", action))
            
            // Hilo para capturar errores y que no se bloquee el proceso
            Thread {
                val error = process.errorStream.bufferedReader().readText()
                if (error.isNotEmpty()) Log.e("CommandHandler", "Shell Error: $error")
                val output = process.inputStream.bufferedReader().readText()
                if (output.isNotEmpty()) Log.d("CommandHandler", "Shell Output: $output")
            }.start()
            
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error executing command: $action", e)
        }
    }

    private fun executeRootCommand(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
        } catch (e: Exception) {
            Log.e("CommandHandler", "Error executing root command", e)
        }
    }
}
