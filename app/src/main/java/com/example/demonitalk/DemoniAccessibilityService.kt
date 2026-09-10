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
        
        // 1. Intentar por IDs conocidos de apps populares
        val commonIds = listOf(
            "com.whatsapp:id/send",
            "com.google.android.apps.messaging:id/send_message_button_container"
        )
        
        for (id in commonIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (node.isClickable || node.parent?.isClickable == true) {
                    val target = if (node.isClickable) node else node.parent
                    target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }

        // 2. Intentar por descripción de contenido (iconos) o texto
        val sendWords = listOf("enviar", "send", "mandar", "enviar mensaje", "post", "publicar")
        return findAndClickByTextOrDesc(rootNode, sendWords)
    }

    private fun findAndClickByTextOrDesc(node: AccessibilityNodeInfo, words: List<String>): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        
        for (word in words) {
            if ((desc.contains(word) || text.contains(word)) && (node.isClickable || node.parent?.isClickable == true)) {
                val target = if (node.isClickable) node else node.parent
                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findAndClickByTextOrDesc(child, words)) return true
        }
        return false
    }

    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun clickFirstConversation(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        // 1. Intentar por IDs conocidos
        val listIds = listOf(
            "com.whatsapp:id/conversations_list",
            "android:id/list",
            "org.telegram.messenger:id/chats_list"
        )
        
        for (id in listIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                if (clickFirstChild(node)) return true
            }
        }

        // 2. Si fallan los IDs, buscar cualquier lista (ListView o RecyclerView)
        return findAndClickFirstListElement(rootNode)
    }

    private fun findAndClickFirstListElement(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.contains("ListView") == true || node.className?.contains("RecyclerView") == true) {
            if (clickFirstChild(node)) return true
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickFirstListElement(child)) return true
        }
        return false
    }

    private fun clickFirstChild(listNode: AccessibilityNodeInfo): Boolean {
        if (listNode.childCount > 0) {
            for (i in 0 until listNode.childCount) {
                val child = listNode.getChild(i) ?: continue
                // En las listas, a veces el primer hijo es un header o algo no clicable.
                // Buscamos el primero que sea clicable o tenga contenido útil.
                if (isNodeOrParentClickable(child)) {
                    clickNodeOrParent(child)
                    return true
                }
            }
        }
        return false
    }

    private fun isNodeOrParentClickable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || node.parent?.isClickable == true
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo) {
        val target = if (node.isClickable) node else node.parent
        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
