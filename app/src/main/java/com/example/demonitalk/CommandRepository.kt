package com.example.demonitalk

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import androidx.core.content.edit

class CommandRepository(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("DemoniTalkPrefs", Context.MODE_PRIVATE)
    private val COMMANDS_KEY = "commands"
    private val DARK_MODE_KEY = "dark_mode"
    private val EXPORT_PATH_KEY = "export_path"
    private val GEMINI_API_KEY = "gemini_api_key"
    private val AI_ENABLED_KEY = "ai_enabled"
    private val EXECUTION_MODE_KEY = "execution_mode"
    private val GEMINI_ENGINE_ENABLED_KEY = "gemini_engine_enabled"
    private val FREE_AI_ENGINE_ENABLED_KEY = "free_ai_engine_enabled"

    fun saveExecutionMode(mode: String) {
        prefs.edit { putString(EXECUTION_MODE_KEY, mode) }
    }

    fun getExecutionMode(): String {
        return prefs.getString(EXECUTION_MODE_KEY, "HYBRID") ?: "HYBRID"
    }

    fun saveGeminiEngineEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(GEMINI_ENGINE_ENABLED_KEY, enabled) }
    }

    fun isGeminiEngineEnabled(): Boolean {
        return prefs.getBoolean(GEMINI_ENGINE_ENABLED_KEY, true)
    }

    fun saveFreeAiEngineEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(FREE_AI_ENGINE_ENABLED_KEY, enabled) }
    }

    fun isFreeAiEngineEnabled(): Boolean {
        return prefs.getBoolean(FREE_AI_ENGINE_ENABLED_KEY, true)
    }

    fun saveAiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(AI_ENABLED_KEY, enabled) }
    }

    fun isAiEnabled(): Boolean {
        return prefs.getBoolean(AI_ENABLED_KEY, true)
    }

    fun saveGeminiApiKey(key: String) {
        prefs.edit { putString(GEMINI_API_KEY, key) }
    }

    fun getGeminiApiKey(): String {
        return prefs.getString(GEMINI_API_KEY, "") ?: ""
    }

    fun saveDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean(DARK_MODE_KEY, enabled) }
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean(DARK_MODE_KEY, true)
    }

    fun saveExportPath(path: String) {
        prefs.edit { putString(EXPORT_PATH_KEY, path) }
    }

    fun getExportPath(): String {
        return prefs.getString(EXPORT_PATH_KEY, "") ?: ""
    }

    private var cachedCommands: List<VoiceCommand>? = null

    fun saveCommands(commands: List<VoiceCommand>) {
        cachedCommands = commands
        prefs.edit { putString(COMMANDS_KEY, gson.toJson(commands)) }
    }

    fun clearCache() {
        cachedCommands = null
        prefs.edit { remove(COMMANDS_KEY) }
    }

    fun getBackupFiles(): List<File> {
        val customPath = getExportPath().trim()
        val dir = if (customPath.isNotEmpty()) {
            File(customPath)
        } else {
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        return dir.listFiles { _, name -> name.endsWith(".json") && name.startsWith("DemoniTalk_Backup_") }?.toList() ?: emptyList<File>()
    }

    fun loadCommands(): List<VoiceCommand> {
        cachedCommands?.let { return it }

        val json = prefs.getString(COMMANDS_KEY, null)
        val currentCommands = if (json == null) {
            getDefaultCommands()
        } else {
            val type = object : TypeToken<List<VoiceCommand>>() {}.type
            try {
                gson.fromJson<List<VoiceCommand>>(json, type)
            } catch (_: Exception) {
                getDefaultCommands()
            }
        }

        // Filtramos duplicados por el trigger (palabra mágica)
        val uniqueCommands = currentCommands.distinctBy { it.trigger.lowercase().trim() }.toMutableList()
        
        // Añadimos solo los nuevos que falten realmente
        val defaults = getDefaultCommands()
        var modified = false
        for (default in defaults) {
            if (uniqueCommands.none { it.trigger.lowercase().trim() == default.trigger.lowercase().trim() }) {
                uniqueCommands.add(default)
                modified = true
            }
        }

        if (modified) {
            prefs.edit { putString(COMMANDS_KEY, gson.toJson(uniqueCommands)) }
        }
        cachedCommands = uniqueCommands
        return uniqueCommands
    }

    private fun getDefaultCommands(): List<VoiceCommand> {
        return listOf(
            VoiceCommand("cámara", "com.android.camera"),
            VoiceCommand("reboot", "reboot", true),
            VoiceCommand("ajustes", "com.android.settings"),
            VoiceCommand("activar escucha", "internal_continuous_on"),
            VoiceCommand("desactivar escucha", "internal_stop"),
            VoiceCommand("activa c4", "internal_mode_blue"),
            VoiceCommand("modo azul", "internal_mode_blue"),
            VoiceCommand("encender foco", "torch_on"),
            VoiceCommand("apagar foco", "torch_off"),
            VoiceCommand("primer chat", "click_first_chat"),
            VoiceCommand("mostrar números", "show_numbers"),
            VoiceCommand("actualizar", "show_numbers"),
            VoiceCommand("cerrar todo", "input keyevent 187 && sleep 1 && input tap 540 1800", true)
        )
    }
}
