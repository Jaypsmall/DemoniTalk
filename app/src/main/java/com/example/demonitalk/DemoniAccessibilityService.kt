package com.example.demonitalk

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class DemoniAccessibilityService : AccessibilityService() {

    companion object {
        var instance: DemoniAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun clickSendButton(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        // Buscamos botones con textos comunes de envío
        val sendWords = listOf("enviar", "send", "mandar", "post", "publicar")
        
        for (word in sendWords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(word)
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }
        
        // Búsqueda por Content Description o IDs comunes si falla el texto
        return findAndClickSendIcon(rootNode)
    }

    private fun findAndClickSendIcon(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && (node.contentDescription?.toString()?.lowercase()?.contains("send") == true || 
            node.contentDescription?.toString()?.lowercase()?.contains("enviar") == true)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findAndClickSendIcon(child)) return true
        }
        return false
    }

    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
}
