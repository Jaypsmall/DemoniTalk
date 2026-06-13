package com.example.demonitalk

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CommandRepository(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("DemoniTalkPrefs", Context.MODE_PRIVATE)
    private val COMMANDS_KEY = "commands"
    private val DARK_MODE_KEY = "dark_mode"

    fun saveDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(DARK_MODE_KEY, enabled).apply()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean(DARK_MODE_KEY, true) // Default to true because it's "Demoni"
    }

    fun saveCommands(commands: List<VoiceCommand>) {
        prefs.edit().putString(COMMANDS_KEY, gson.toJson(commands)).apply()
    }

    fun loadCommands(): List<VoiceCommand> {
        val json = prefs.getString(COMMANDS_KEY, null)
        return if (json == null) {
            listOf(
                VoiceCommand("cámara", "com.android.camera"),
                VoiceCommand("reboot", "reboot", true),
                VoiceCommand("pydroid", "ru.iiec.pydroid3"),
                VoiceCommand("termux", "com.termux"),
                VoiceCommand("ajustes", "com.android.settings"),
                VoiceCommand("hexcolor", "com.psbank.hexcolor"),
                VoiceCommand("música", "com.google.android.music"),
                VoiceCommand("encender linterna", "torch_on"),
                VoiceCommand("apagar linterna", "torch_off"),
                VoiceCommand("cerrar cámara", "am force-stop com.huawei.camera", true),
                VoiceCommand("cerrar todo", "input keyevent 187 && sleep 1 && input tap 540 1800", true)
            )
        } else {
            val type = object : TypeToken<List<VoiceCommand>>() {}.type
            gson.fromJson(json, type)
        }
    }
}
