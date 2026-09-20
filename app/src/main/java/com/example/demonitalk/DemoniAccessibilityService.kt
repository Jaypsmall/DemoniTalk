package com.example.demonitalk

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class DemoniAccessibilityService : AccessibilityService() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var commandHandler: CommandHandler
    private lateinit var repository: CommandRepository
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false

    companion object {
        var instance: DemoniAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        commandHandler = CommandHandler(this)
        repository = CommandRepository(this)
        Log.d("DemoniTalk", "Accessibility Service Connected")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "ACTION_START_LISTENING") {
            startListening()
        } else if (action == "ACTION_STOP_LISTENING") {
            stopListening()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startListening() {
        mainHandler.post {
            if (isListening) return@post
            
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                    speechRecognizer?.setRecognitionListener(speechListener)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("DemoniTalk", "Accessibility-based listening started")
            } catch (e: Exception) {
                Log.e("DemoniTalk", "Error starting accessibility listening: ${e.message}")
            }
        }
    }

    private fun stopListening() {
        mainHandler.post {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }
        override fun onError(error: Int) {
            isListening = false
            Log.e("DemoniTalk", "Accessibility Speech Error: $error")
            // Reintentar si es necesario (p.ej. en modo continuo)
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.i("DemoniTalk", "Accessibility recognized: $text")
                commandHandler.execute(text, repository.loadCommands())
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
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

    fun clickText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (isNodeOrParentClickable(node)) {
                clickNodeOrParent(node)
                return true
            }
        }
        // Búsqueda difusa/recursiva si falla la directa
        return findAndClickByTextOrDesc(rootNode, listOf(targetText.lowercase()))
    }

    fun clickNumber(number: String): Boolean {
        // Por ahora simulamos la pulsación por número buscando el texto del número en pantalla
        // En una versión más avanzada, asignaríamos IDs a los elementos visibles
        return clickText(number)
    }

    fun showGrid() {
        Log.d("DemoniTalk", "Mostrando cuadrícula de accesibilidad (Overlay)")
        // TODO: Implementar vista de cuadrícula con WindowManager
    }

    fun showNumbers() {
        Log.d("DemoniTalk", "Mostrando números de accesibilidad (Overlay)")
        // TODO: Implementar vista de etiquetas numéricas
    }

    fun hideOverlays() {
        Log.d("DemoniTalk", "Ocultando todos los overlays de accesibilidad")
        // TODO: Eliminar vistas del WindowManager
    }
}
