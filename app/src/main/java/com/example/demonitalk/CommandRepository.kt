package com.example.demonitalk

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CommandRepository(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("DemoniTalkPrefs", Context.MODE_PRIVATE)
    private val COMMANDS_KEY = "commands"
    private val DARK_MODE_KEY = "dark_mode"
    private val EXPORT_PATH_KEY = "export_path"

    fun saveDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(DARK_MODE_KEY, enabled).apply()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean(DARK_MODE_KEY, true)
    }

    fun saveExportPath(path: String) {
        prefs.edit().putString(EXPORT_PATH_KEY, path).apply()
    }

    fun getExportPath(): String {
        return prefs.getString(EXPORT_PATH_KEY, "") ?: ""
    }

    fun saveCommands(commands: List<VoiceCommand>) {
        prefs.edit().putString(COMMANDS_KEY, gson.toJson(commands)).apply()
    }

    fun clearCache() {
        prefs.edit().remove(COMMANDS_KEY).apply()
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
        val json = prefs.getString(COMMANDS_KEY, null)
        val currentCommands = if (json == null) {
            getDefaultCommands()
        } else {
            val type = object : TypeToken<List<VoiceCommand>>() {}.type
            try {
                gson.fromJson<List<VoiceCommand>>(json, type)
            } catch (e: Exception) {
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

        if (modified) saveCommands(uniqueCommands)
        return uniqueCommands
    }

    private fun getDefaultCommands(): List<VoiceCommand> {
        return listOf(
            VoiceCommand("cámara", "com.android.camera"),
            VoiceCommand("reboot", "reboot", true),
            VoiceCommand("ajustes", "com.android.settings"),
            VoiceCommand("activar escucha", "internal_continuous_on"),
            VoiceCommand("desactivar escucha", "internal_continuous_off"),
            VoiceCommand("encender foco", "torch_on"),
            VoiceCommand("apagar foco", "torch_off"),
            VoiceCommand("cerrar todo", "input keyevent 187 && sleep 1 && input tap 540 1800", true)
        )
    }
}
