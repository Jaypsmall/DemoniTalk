package com.example.demonitalk

import android.os.Build
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale

class DemoniAccessibilityService : AccessibilityService() {

    // =========================================================
    // VOZ
    // =========================================================

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var commandHandler: CommandHandler
    private lateinit var repository: CommandRepository

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isListening = false

    // =========================================================
    // OVERLAYS
    // =========================================================

    private var windowManager: WindowManager? = null

    private var gridOverlay: FrameLayout? = null
    private var numbersOverlay: FrameLayout? = null

    private var gridVisible = false
    private var numbersVisible = false

    /**
     * Relación:
     *
     * número -> AccessibilityNodeInfo
     *
     * Ejemplo:
     *
     * 1 -> botón WhatsApp
     * 2 -> botón Ajustes
     * 3 -> botón YouTube
     */
    private val numberedNodes =
        mutableMapOf<Int, AccessibilityNodeInfo>()

    /**
     * Guardamos también los rectángulos para poder dibujar
     * las etiquetas exactamente sobre los elementos.
     */
    private val numberedBounds =
        mutableMapOf<Int, Rect>()

    // =========================================================
    // INSTANCE
    // =========================================================

    companion object {

        private const val TAG = "DemoniTalk"

        var instance: DemoniAccessibilityService? = null
    }

    // =========================================================
    // SERVICE CONNECTED
    // =========================================================

    override fun onServiceConnected() {
        super.onServiceConnected()

        instance = this

        commandHandler = CommandHandler(this)
        repository = CommandRepository(this)

        windowManager =
            getSystemService(Context.WINDOW_SERVICE) as WindowManager

        Log.d(TAG, "Accessibility Service Connected")
    }

    // =========================================================
    // START / STOP LISTENING
    // =========================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val action = intent?.action

        when (action) {

            "ACTION_START_LISTENING" -> {
                startListening()
            }

            "ACTION_STOP_LISTENING" -> {
                stopListening()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun startListening() {

        mainHandler.post {

            if (isListening) {
                return@post
            }

            try {

                if (speechRecognizer == null) {

                    speechRecognizer =
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            SpeechRecognizer.isOnDeviceRecognitionAvailable(
                                applicationContext
                            )
                        ) {

                            Log.i(
                                TAG,
                                "Reconocimiento ON-DEVICE disponible. Usando motor local."
                            )

                            SpeechRecognizer.createOnDeviceSpeechRecognizer(
                                applicationContext
                            )

                        } else {

                            Log.w(
                                TAG,
                                "Reconocimiento ON-DEVICE no disponible. Usando reconocedor del sistema."
                            )

                            SpeechRecognizer.createSpeechRecognizer(
                                applicationContext
                            )
                        }

                    speechRecognizer?.setRecognitionListener(
                        speechListener
                    )
                }

                val intent =
                    Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                    ).apply {

                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE,
                            Locale.getDefault()
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_CALLING_PACKAGE,
                            packageName
                        )
                    }

                speechRecognizer?.startListening(intent)

                isListening = true

                Log.d(
                    TAG,
                    "Accessibility-based listening started"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Error starting accessibility listening",
                    e
                )
            }
        }
    }

    private fun stopListening() {

        mainHandler.post {

            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }

            isListening = false
        }
    }

    // =========================================================
    // SPEECH LISTENER
    // =========================================================

    private val speechListener =
        object : RecognitionListener {

            override fun onReadyForSpeech(
                params: Bundle?
            ) {
            }

            override fun onBeginningOfSpeech() {
            }

            override fun onRmsChanged(
                rmsdB: Float
            ) {
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
            }

            override fun onEndOfSpeech() {

                isListening = false
            }

            override fun onError(
                error: Int
            ) {

                isListening = false

                Log.e(
                    TAG,
                    "Accessibility Speech Error: $error"
                )
            }

            override fun onResults(
                results: Bundle?
            ) {

                isListening = false

                val matches =
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                if (!matches.isNullOrEmpty()) {

                    val text = matches[0]

                    Log.i(
                        TAG,
                        "Accessibility recognized: $text"
                    )

                    commandHandler.execute(
                        text,
                        repository.loadCommands()
                    )
                }
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
            }
        }

    // =========================================================
    // UNBIND
    // =========================================================

    override fun onUnbind(
        intent: Intent?
    ): Boolean {

        hideOverlays()

        instance = null

        return super.onUnbind(intent)
    }

    // =========================================================
    // ACCESSIBILITY EVENTS
    // =========================================================

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (event == null) {
            return
        }

        /*
         * Si tenemos números visibles y cambia la pantalla,
         * actualizamos las posiciones.
         *
         * Se usa un pequeño retraso para dejar que Android
         * termine de construir la nueva jerarquía.
         */

        if (numbersVisible || gridVisible) {

            mainHandler.removeCallbacksAndMessages(
                NUMBER_REFRESH_TOKEN
            )

            mainHandler.postDelayed(
                {
                    refreshOverlays()
                },
                50L
            )
        }
    }

    override fun onInterrupt() {
    }

    // =========================================================
    // ESCRIBIR TEXTO
    // =========================================================

    fun typeText(
        text: String
    ): Boolean {

        val rootNode =
            rootInActiveWindow
                ?: return false

        val focusedNode =
            rootNode.findFocus(
                AccessibilityNodeInfo.FOCUS_INPUT
            )
                ?: return false

        val arguments =
            Bundle()

        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )

        return focusedNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            arguments
        )
    }

    // =========================================================
    // CLICK SEND
    // =========================================================

    fun clickSendButton(): Boolean {

        val rootNode =
            rootInActiveWindow
                ?: return false

        val commonIds =
            listOf(
                "com.whatsapp:id/send",
                "com.google.android.apps.messaging:id/send_message_button_container"
            )

        for (id in commonIds) {

            val nodes =
                rootNode.findAccessibilityNodeInfosByViewId(id)

            for (node in nodes) {

                if (
                    node.isClickable ||
                    node.parent?.isClickable == true
                ) {

                    val target =
                        if (node.isClickable) {
                            node
                        } else {
                            node.parent
                        }

                    if (
                        target?.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        ) == true
                    ) {
                        return true
                    }
                }
            }
        }

        val sendWords =
            listOf(
                "enviar",
                "send",
                "mandar",
                "enviar mensaje",
                "post",
                "publicar"
            )

        return findAndClickByTextOrDesc(
            rootNode,
            sendWords
        )
    }

    // =========================================================
    // FIND CLICKABLE BY TEXT / DESCRIPTION
    // =========================================================

    private fun findAndClickByTextOrDesc(
        node: AccessibilityNodeInfo,
        words: List<String>
    ): Boolean {

        val desc =
            node.contentDescription
                ?.toString()
                ?.lowercase(Locale.getDefault())
                ?: ""

        val text =
            node.text
                ?.toString()
                ?.lowercase(Locale.getDefault())
                ?: ""

        for (word in words) {

            if (
                (desc.contains(word) || text.contains(word)) &&
                (
                        node.isClickable ||
                                node.parent?.isClickable == true
                        )
            ) {

                val target =
                    if (node.isClickable) {
                        node
                    } else {
                        node.parent
                    }

                if (
                    target?.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    ) == true
                ) {
                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            if (
                child != null &&
                findAndClickByTextOrDesc(
                    child,
                    words
                )
            ) {
                return true
            }
        }

        return false
    }

    // =========================================================
    // GLOBAL NAVIGATION
    // =========================================================

    fun performBack(): Boolean =
        performGlobalAction(
            GLOBAL_ACTION_BACK
        )

    fun performHome(): Boolean =
        performGlobalAction(
            GLOBAL_ACTION_HOME
        )

    fun performRecents(): Boolean =
        performGlobalAction(
            GLOBAL_ACTION_RECENTS
        )

    // =========================================================
    // FIRST CONVERSATION
    // =========================================================

    fun clickFirstConversation(): Boolean {

        val rootNode =
            rootInActiveWindow
                ?: return false

        val listIds =
            listOf(
                "com.whatsapp:id/conversations_list",
                "android:id/list",
                "org.telegram.messenger:id/chats_list"
            )

        for (id in listIds) {

            val nodes =
                rootNode.findAccessibilityNodeInfosByViewId(id)

            for (node in nodes) {

                if (clickFirstChild(node)) {
                    return true
                }
            }
        }

        return findAndClickFirstListElement(
            rootNode
        )
    }

    private fun findAndClickFirstListElement(
        node: AccessibilityNodeInfo
    ): Boolean {

        val className =
            node.className?.toString()
                ?: ""

        if (
            className.contains("ListView") ||
            className.contains("RecyclerView")
        ) {

            if (clickFirstChild(node)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)
                    ?: continue

            if (
                findAndClickFirstListElement(
                    child
                )
            ) {
                return true
            }
        }

        return false
    }

    private fun clickFirstChild(
        listNode: AccessibilityNodeInfo
    ): Boolean {

        if (listNode.childCount <= 0) {
            return false
        }

        for (i in 0 until listNode.childCount) {

            val child =
                listNode.getChild(i)
                    ?: continue

            if (isNodeOrParentClickable(child)) {

                clickNodeOrParent(child)

                return true
            }
        }

        return false
    }

    private fun isNodeOrParentClickable(
        node: AccessibilityNodeInfo
    ): Boolean {

        return node.isClickable ||
                node.parent?.isClickable == true
    }

    private fun clickNodeOrParent(
        node: AccessibilityNodeInfo
    ) {

        val target =
            if (node.isClickable) {
                node
            } else {
                node.parent
            }

        target?.performAction(
            AccessibilityNodeInfo.ACTION_CLICK
        )
    }

    // =========================================================
    // CLICK TEXT
    // =========================================================

    fun clickText(
        targetText: String
    ): Boolean {

        val rootNode =
            rootInActiveWindow
                ?: return false

        val nodes =
            rootNode.findAccessibilityNodeInfosByText(
                targetText
            )

        for (node in nodes) {

            if (isNodeOrParentClickable(node)) {

                clickNodeOrParent(node)

                return true
            }
        }

        return findAndClickByTextOrDesc(
            rootNode,
            listOf(
                targetText.lowercase(
                    Locale.getDefault()
                )
            )
        )
    }

    // =========================================================
    // CLICK NUMBER
    // =========================================================

    fun clickNumber(
        number: String
    ): Boolean {
        val num = number.trim().toIntOrNull() ?: return false

        Log.d(TAG, "Intentando pulsar número: $num")

        // 1. Intentamos pulsar mediante AccessibilityNodeInfo
        val storedNode = numberedNodes[num]
        if (storedNode != null) {
            try {
                val target = if (storedNode.isClickable) storedNode else storedNode.parent
                if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    Log.d(TAG, "Número $num pulsado mediante AccessibilityNodeInfo")
                    hideOverlays()
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pulsando nodo número $num", e)
            }
        }

        // 2. Si el nodo directo no consumió la acción, pulsamos por las coordenadas exactas de la etiqueta
        val bounds = numberedBounds[num]
        if (bounds != null) {
            val cx = bounds.centerX()
            val cy = bounds.centerY()

            if (ShellUtils.isRootAvailable()) {
                if (ShellUtils.executeCommand("input tap $cx $cy")) {
                    Log.d(TAG, "Número $num pulsado mediante Root Shell Tap ($cx, $cy)")
                    hideOverlays()
                    return true
                }
            }

            try {
                val path = android.graphics.Path().apply { moveTo(cx.toFloat(), cy.toFloat()) }
                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
                val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                if (dispatchGesture(gesture, null, null)) {
                    Log.d(TAG, "Número $num pulsado mediante dispatchGesture ($cx, $cy)")
                    hideOverlays()
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en dispatchGesture para número $num", e)
            }
        }

        // 3. Fallback final: buscar por texto
        val textSuccess = clickText(num.toString())
        if (textSuccess) hideOverlays()
        return textSuccess
    }

    // =========================================================
    // SHOW GRID
    // =========================================================

    fun showGrid() {

        mainHandler.post {

            Log.d(
                TAG,
                "Mostrando cuadrícula de accesibilidad"
            )

            gridVisible = true

            ensureGridOverlay()

            gridOverlay?.visibility =
                FrameLayout.VISIBLE

            refreshOverlays()
        }
    }

    // =========================================================
    // SHOW NUMBERS
    // =========================================================

    fun showNumbers() {

        mainHandler.post {

            Log.d(
                TAG,
                "Mostrando números de accesibilidad"
            )

            numbersVisible = true

            rebuildNumberMap()

            ensureNumbersOverlay()

            numbersOverlay?.visibility =
                FrameLayout.VISIBLE

            refreshOverlays()
        }
    }

    // =========================================================
    // HIDE ALL
    // =========================================================

    fun hideOverlays() {

        mainHandler.post {

            Log.d(
                TAG,
                "Ocultando todos los overlays"
            )

            gridVisible = false
            numbersVisible = false

            numberedNodes.clear()
            numberedBounds.clear()

            removeGridOverlay()
            removeNumbersOverlay()
        }
    }

    // =========================================================
    // REFRESH OVERLAYS
    // =========================================================

    private fun refreshOverlays() {

        mainHandler.post {

            if (numbersVisible) {

                rebuildNumberMap()

                ensureNumbersOverlay()

                drawNumbers()
            }

            if (gridVisible) {

                ensureGridOverlay()

                drawGrid()
            }
        }
    }

    // =========================================================
    // BUILD NUMBER MAP
    // =========================================================

    private fun rebuildNumberMap() {

        numberedNodes.clear()
        numberedBounds.clear()

        val root = rootInActiveWindow ?: return

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodes(root, candidates)

        val valid = candidates.filter { node ->
            if (!node.isVisibleToUser) return@filter false
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.width() > 10 && rect.height() > 10
        }

        val sorted = valid.sortedWith(
            compareBy<AccessibilityNodeInfo> {
                val rect = Rect()
                it.getBoundsInScreen(rect)
                rect.top
            }.thenBy {
                val rect = Rect()
                it.getBoundsInScreen(rect)
                rect.left
            }
        )

        var number = 1

        for (node in sorted) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Deduplicación para evitar superposición de etiquetas (overlap > 70%)
            val duplicate = numberedBounds.values.any { existing ->
                val overlapX = Math.max(0, Math.min(rect.right, existing.right) - Math.max(rect.left, existing.left))
                val overlapY = Math.max(0, Math.min(rect.bottom, existing.bottom) - Math.max(rect.top, existing.top))
                val overlapArea = overlapX * overlapY
                val area1 = rect.width() * rect.height()
                val area2 = existing.width() * existing.height()
                val minArea = Math.min(area1, area2)
                minArea > 0 && (overlapArea.toFloat() / minArea.toFloat()) > 0.7f
            }

            if (duplicate) continue

            numberedNodes[number] = node
            numberedBounds[number] = Rect(rect)

            number++
            if (number > 99) break
        }

        Log.d(TAG, "Elementos numerados: ${numberedNodes.size}")
    }

    // =========================================================
    // COLLECT CLICKABLE NODES
    // =========================================================

    private fun collectClickableNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>
    ) {

        try {

            if (
                node.isVisibleToUser &&
                (
                        node.isClickable ||
                                node.isLongClickable
                        )
            ) {

                result.add(node)
            }

            for (i in 0 until node.childCount) {

                val child =
                    node.getChild(i)
                        ?: continue

                collectClickableNodes(
                    child,
                    result
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error recorriendo AccessibilityNodeInfo",
                e
            )
        }
    }

    // =========================================================
    // GRID OVERLAY
    // =========================================================

    private fun ensureGridOverlay() {

        if (gridOverlay != null) {
            return
        }

        val wm =
            windowManager
                ?: return

        val overlay =
            FrameLayout(this)

        overlay.setBackgroundColor(
            Color.TRANSPARENT
        )

        val params =
            createOverlayParams()

        try {

            wm.addView(
                overlay,
                params
            )

            gridOverlay =
                overlay

        } catch (e: Exception) {

            Log.e(
                TAG,
                "No se pudo crear overlay de cuadrícula",
                e
            )
        }
    }

    // =========================================================
    // DRAW GRID
    // =========================================================

    private fun drawGrid() {

        val overlay =
            gridOverlay
                ?: return

        overlay.removeAllViews()

        val density =
            resources.displayMetrics.density

        val screenWidth =
            resources.displayMetrics.widthPixels

        val screenHeight =
            resources.displayMetrics.heightPixels

        /*
         * Cuadrícula 4 x 4.
         */

        val columns = 4
        val rows = 4

        val cellWidth =
            screenWidth / columns

        val cellHeight =
            screenHeight / rows

        /*
         * Dibujamos líneas verticales.
         */

        for (column in 1 until columns) {

            val line =
                ViewLine(
                    this,
                    Color.argb(
                        150,
                        255,
                        0,
                        0
                    )
                )

            val params =
                FrameLayout.LayoutParams(
                    dp(1),
                    screenHeight
                )

            params.leftMargin =
                column * cellWidth

            overlay.addView(
                line,
                params
            )
        }

        /*
         * Líneas horizontales.
         */

        for (row in 1 until rows) {

            val line =
                ViewLine(
                    this,
                    Color.argb(
                        150,
                        255,
                        0,
                        0
                    )
                )

            val params =
                FrameLayout.LayoutParams(
                    screenWidth,
                    dp(1)
                )

            params.topMargin =
                row * cellHeight

            overlay.addView(
                line,
                params
            )
        }

        /*
         * Bordes.
         */

        val border =
            ViewLine(
                this,
                Color.argb(
                    180,
                    255,
                    0,
                    0
                )
            )

        val borderParams =
            FrameLayout.LayoutParams(
                screenWidth,
                dp(2)
            )

        overlay.addView(
            border,
            borderParams
        )

        val bottomBorder =
            ViewLine(
                this,
                Color.argb(
                    180,
                    255,
                    0,
                    0
                )
            )

        val bottomParams =
            FrameLayout.LayoutParams(
                screenWidth,
                dp(2)
            )

        bottomParams.topMargin =
            screenHeight - dp(2)

        overlay.addView(
            bottomBorder,
            bottomParams
        )

        /*
         * No usamos realmente density para el tamaño de las
         * celdas, ya que screenWidth/screenHeight están en píxeles.
         * Se mantiene solamente para el grosor de las líneas.
         */
    }

    // =========================================================
    // NUMBERS OVERLAY
    // =========================================================

    private fun ensureNumbersOverlay() {

        if (numbersOverlay != null) {
            return
        }

        val wm =
            windowManager
                ?: return

        val overlay =
            FrameLayout(this)

        overlay.setBackgroundColor(
            Color.TRANSPARENT
        )

        val params =
            createOverlayParams()

        try {

            wm.addView(
                overlay,
                params
            )

            numbersOverlay =
                overlay

        } catch (e: Exception) {

            Log.e(
                TAG,
                "No se pudo crear overlay de números",
                e
            )
        }
    }

    // =========================================================
    // DRAW NUMBERS
    // =========================================================

    private fun drawNumbers() {

        val overlay = numbersOverlay ?: return
        overlay.removeAllViews()

        val density = resources.displayMetrics.density

        for ((number, bounds) in numberedBounds) {

            val label = TextView(this).apply {
                text = number.toString()
                setTextColor(Color.WHITE)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding((3 * density).toInt(), 0, (3 * density).toInt(), 0)

                // Etiqueta rediseñada: suave, redondeada y disimulada con fino borde rojo
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 10f * density
                    setColor(Color.argb(215, 18, 18, 22))
                    setStroke((1.5f * density).toInt(), Color.argb(240, 255, 6, 0))
                }
            }

            val badgeHeight = (22f * density).toInt()
            val badgeWidth = if (number > 9) (26f * density).toInt() else (22f * density).toInt()

            val params = FrameLayout.LayoutParams(
                badgeWidth,
                badgeHeight
            ).apply {
                // Posicionar sutilmente en la esquina superior izquierda del elemento
                leftMargin = bounds.left + (2 * density).toInt()
                topMargin = bounds.top + (2 * density).toInt()

                val maxX = resources.displayMetrics.widthPixels - badgeWidth
                val maxY = resources.displayMetrics.heightPixels - badgeHeight

                leftMargin = leftMargin.coerceIn(0, maxX.coerceAtLeast(0))
                topMargin = topMargin.coerceIn(0, maxY.coerceAtLeast(0))
            }

            overlay.addView(label, params)
        }
    }

    // =========================================================
    // OVERLAY PARAMS
    // =========================================================

    private fun createOverlayParams():
            WindowManager.LayoutParams {

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,

            /*
             * AccessibilityService puede utilizar
             * TYPE_ACCESSIBILITY_OVERLAY.
             *
             * Esto evita necesitar el permiso
             * android.permission.SYSTEM_ALERT_WINDOW.
             */

            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,

            /*
             * IMPORTANTÍSIMO:
             *
             * NOT_TOUCHABLE hace que la cuadrícula y los números
             * no bloqueen los toques de la aplicación debajo.
             */

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,

            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {

            gravity =
                Gravity.TOP or Gravity.START
        }
    }

    // =========================================================
    // REMOVE GRID
    // =========================================================

    private fun removeGridOverlay() {

        val overlay =
            gridOverlay
                ?: return

        try {

            windowManager?.removeView(
                overlay
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error eliminando grid overlay",
                e
            )
        }

        gridOverlay =
            null
    }

    // =========================================================
    // REMOVE NUMBERS
    // =========================================================

    private fun removeNumbersOverlay() {

        val overlay =
            numbersOverlay
                ?: return

        try {

            windowManager?.removeView(
                overlay
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error eliminando numbers overlay",
                e
            )
        }

        numbersOverlay =
            null
    }

    // =========================================================
    // DP -> PX
    // =========================================================

    private fun dp(
        value: Int
    ): Int {

        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }

    // =========================================================
    // REFRESH TOKEN
    // =========================================================

    private val NUMBER_REFRESH_TOKEN =
        Any()

    // =========================================================
    // SIMPLE LINE VIEW
    // =========================================================

    private class ViewLine(
        context: Context,
        color: Int
    ) : android.view.View(context) {

        init {
            setBackgroundColor(color)
        }
    }
}