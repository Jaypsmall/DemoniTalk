package com.example.demonitalk

import android.content.Context
import android.util.Log

class AccessibilityController(private val context: Context) {

    fun execute(action: String): Boolean {
        // Acciones que NO requieren el servicio de accesibilidad activo
        if (action.startsWith("open_app:")) {
            val appName = action.removePrefix("open_app:").trim()
            return launchAppByName(appName)
        }

        val service = DemoniAccessibilityService.instance
        if (service == null) {
            Log.e("AccessibilityController", "Service not running for action: $action")
            return false
        }

        return when {
            action.startsWith("type:") -> {
                val text = action.removePrefix("type:")
                service.typeText(text)
            }
            action == "click_send" -> service.clickSendButton()
            action == "global_back" -> service.performBack()
            action == "global_home" -> service.performHome()
            action == "global_recents" -> service.performRecents()
            action == "click_first_chat" -> service.clickFirstConversation()
            
            // Si parece un nombre de paquete, intentamos lanzarlo
            action.contains(".") && !action.contains(" ") -> {
                val intent = context.packageManager.getLaunchIntentForPackage(action)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun launchAppByName(name: String): Boolean {
        val pm = context.packageManager
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        
        val apps = pm.queryIntentActivities(mainIntent, 0)
        
        val foundApp = apps.find { 
            val label = it.loadLabel(pm).toString().lowercase()
            label == name.lowercase() || label.contains(name.lowercase())
        }

        return foundApp?.activityInfo?.let { activityInfo ->
            val pkg = activityInfo.packageName
            val activity = activityInfo.name
            Log.d("AccessibilityController", "Launching via AM: $pkg/$activity")
            
            // Si tenemos Root, usamos am start que es infalible
            if (ShellUtils.isRootAvailable()) {
                ShellUtils.executeCommand("am start -n $pkg/$activity")
            } else {
                // Si no hay root, usamos el intent normal
                val intent = pm.getLaunchIntentForPackage(pkg)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            true
        } ?: run {
            Log.e("AccessibilityController", "App not found with name: $name")
            false
        }
    }
}
