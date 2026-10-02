package com.example.demonitalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.demonitalk.ui.theme.AshGrey
import com.example.demonitalk.ui.theme.DemoniTalkTheme
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: CommandRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = CommandRepository(this)
        
        // Solo verificamos el permiso de grabación al inicio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            var isDark by remember { mutableStateOf(repository.isDarkMode()) }
            DemoniTalkTheme(darkTheme = isDark) {
                CommandScreen(isDark, onThemeToggle = {
                    isDark = it
                    repository.saveDarkMode(it)
                })
            }
        }
    }

    private val requestPermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Permission denied for recording audio", Toast.LENGTH_SHORT).show()
        }
    }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CommandScreen(isDarkMode: Boolean, onThemeToggle: (Boolean) -> Unit) {
        var commands by remember { mutableStateOf(repository.loadCommands()) }
        var showDialog by remember { mutableStateOf(false) }
        var editingCommand by remember { mutableStateOf<VoiceCommand?>(null) }
        var showSuccessDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showStorageDialog by remember { mutableStateOf(false) }
        var showAiDialog by remember { mutableStateOf(false) }
        var isEnglish by remember { mutableStateOf(false) }
        
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let {
                try {
                    val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    val json = gson.toJson(commands)
                    contentResolver.openOutputStream(it)?.use { out ->
                        out.write(json.toByteArray())
                    }
                    showSuccessDialog = true
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        val json = reader.readText()
                        val type = object : TypeToken<List<VoiceCommand>>() {}.type
                        val importedCommands: List<VoiceCommand> = com.google.gson.Gson().fromJson(json, type)
                        commands = importedCommands
                        repository.saveCommands(commands)
                        Toast.makeText(this, "¡Comandos restaurados! 😈", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Fallo al importar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val titleShadow = Shadow(
            color = Color.Black.copy(alpha = 0.8f),
            offset = Offset(4f, 4f),
            blurRadius = 8f
        )
        val demoniTitle = buildAnnotatedString {
            withStyle(style = SpanStyle(
                color = com.example.demonitalk.ui.theme.HellRed,
                fontWeight = FontWeight.ExtraBold,
                shadow = titleShadow
            )) { append("De") }
            withStyle(style = SpanStyle(
                color = com.example.demonitalk.ui.theme.BrimstoneYellow,
                fontWeight = FontWeight.ExtraBold,
                shadow = titleShadow
            )) { append("moni") }
            withStyle(style = SpanStyle(
                color = if (isDarkMode) com.example.demonitalk.ui.theme.SoulWhite else com.example.demonitalk.ui.theme.AbyssBlack,
                fontWeight = FontWeight.ExtraBold,
                shadow = titleShadow
            )) { append("Talk 😈") }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = demoniTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 32.dp))
                        HorizontalDivider(modifier = Modifier.padding(bottom = 32.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        
                        DrawerButton(text = if (isEnglish) "Settings" else "Ajustes", icon = Icons.Default.Menu) { showSettingsDialog = true; scope.launch { drawerState.close() } }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = if (isEnglish) "Languages" else "Idiomas", icon = Icons.Default.Menu) { isEnglish = !isEnglish }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = if (isEnglish) "Import" else "Importar", icon = Icons.Default.Upload) { importLauncher.launch("*/*"); scope.launch { drawerState.close() } }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = if (isEnglish) "Export" else "Exportar", icon = Icons.Default.Download) { exportLauncher.launch("DemoniTalk_Backup.json"); scope.launch { drawerState.close() } }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = if (isEnglish) "Storage" else "Almacenamiento", icon = Icons.Default.Menu) { showStorageDialog = true; scope.launch { drawerState.close() } }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = if (isEnglish) "AI Config" else "Cerebro IA", icon = Icons.Default.Add, iconTint = com.example.demonitalk.ui.theme.DemoniPurple) { 
                            showAiDialog = true
                            scope.launch { drawerState.close() } 
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "DemoniTalk v2.0.1", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDarkMode) AshGrey else Color.DarkGray)
                            Text(text = "Created by JAYLIZ with ❤️", fontSize = 9.sp, color = (if (isDarkMode) AshGrey else Color.DarkGray).copy(0.7f))
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                            navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                            title = { Text(text = demoniTitle, style = MaterialTheme.typography.headlineMedium) },
                            actions = {
                                IconButton(onClick = { onThemeToggle(!isDarkMode) }) {
                                    Crossfade(targetState = isDarkMode, animationSpec = tween(500)) { dark ->
                                        Icon(if (dark) Icons.Default.DarkMode else Icons.Default.NightsStay, "Theme")
                                    }
                                }
                            }
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFF0600), Color(0xFFFFD600), Color(0xFFFF0600), Color.Transparent))))
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showDialog = true },
                        containerColor = com.example.demonitalk.ui.theme.HellRed,
                        contentColor = Color.White,
                        modifier = Modifier
                            .shadow(16.dp, FloatingActionButtonDefaults.shape, spotColor = com.example.demonitalk.ui.theme.HellRed)
                            .border(1.dp, Color.White.copy(0.4f), FloatingActionButtonDefaults.shape)
                    ) { Icon(Icons.Default.Add, "Add", modifier = Modifier.size(28.dp)) }
                },
                floatingActionButtonPosition = FabPosition.End
            ) { padding ->
                Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            DemoniButton(
                                text = if (isEnglish) "(Accessibility)" else "(Accesibilidad)",
                                containerColor = com.example.demonitalk.ui.theme.DemoniPurple,
                                onClick = { checkAndOpenAccessibility() }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Box(modifier = Modifier.weight(1f)) { DemoniButton(text = if (isEnglish) "Start Floating" else "Iniciar Botón", onClick = { startFloatingService() }) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) { DemoniButton(text = if (isEnglish) "Request Root" else "Solicitar Root", containerColor = MaterialTheme.colorScheme.error, onClick = { requestRoot() }) }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).border(1.dp, com.example.demonitalk.ui.theme.HellRed.copy(0.3f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = if (isDarkMode) com.example.demonitalk.ui.theme.Obsidian else Color.White.copy(0.9f)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = if (isEnglish) "QUICK ACTIONS" else "ACCIONES RÁPIDAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                ControlCircleButton(Color(0xFFFFD600)) { sendControlIntent("ACTION_MODE_YELLOW") }
                                Spacer(modifier = Modifier.width(16.dp))
                                ControlCircleButton(Color(0xFF2196F3)) { sendControlIntent("ACTION_MODE_BLUE") }
                                Spacer(modifier = Modifier.width(16.dp))
                                ControlCircleButton(Color(0xFF4CAF50)) { sendControlIntent("ACTION_MODE_GREEN") }
                                Spacer(modifier = Modifier.weight(1f))
                                ControlCircleButton(Color(0xFFFF0600)) { sendControlIntent("ACTION_MODE_RED") }
                            }
                        }
                    }

                    Text(text = if (isEnglish) "VOICE COMMANDS" else "COMANDOS DE VOZ", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (isDarkMode) AshGrey else Color.Gray, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 6.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp)) {
                        items(commands, key = { it.trigger + it.action }) { command ->
                            CommandItem(command, isDarkMode = isDarkMode, onDelete = { commands = commands - command; repository.saveCommands(commands) }, onEdit = { editingCommand = command })
                        }
                    }
                }

                if (showDialog) { AddCommandDialog(onDismiss = { showDialog = false }, onAdd = { t, a, r -> val nc = VoiceCommand(t, a, r); commands = commands + nc; repository.saveCommands(commands); showDialog = false }) }
                editingCommand?.let { ec -> AddCommandDialog(commandToEdit = ec, onDismiss = { editingCommand = null }, onAdd = { t, a, r -> val nc = commands.toMutableList(); val i = nc.indexOf(ec); if (i != -1) { nc[i] = VoiceCommand(t, a, r); commands = nc; repository.saveCommands(commands) }; editingCommand = null }) }
                if (showSuccessDialog) SuccessDialog(onDismiss = { showSuccessDialog = false }, isEnglish = isEnglish)
                if (showSettingsDialog) SettingsDialog(onDismiss = { showSettingsDialog = false }, isEnglish = isEnglish)
                if (showStorageDialog) StorageDialog(onDismiss = { showStorageDialog = false }, isEnglish = isEnglish, onCommandsUpdated = { commands = it })
                if (showAiDialog) AiConfigDialog(onDismiss = { showAiDialog = false }, isEnglish = isEnglish)
            }
        }
    }

    @Composable
    fun AiConfigDialog(onDismiss: () -> Unit, isEnglish: Boolean) {
        var key by remember { mutableStateOf(repository.getGeminiApiKey()) }
        var aiEnabled by remember { mutableStateOf(repository.isAiEnabled()) }
        var geminiEnabled by remember { mutableStateOf(repository.isGeminiEngineEnabled()) }
        var freeAiEnabled by remember { mutableStateOf(repository.isFreeAiEngineEnabled()) }
        var testing by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("CONFIGURACIÓN DE IA", color = com.example.demonitalk.ui.theme.DemoniPurple, fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Respuestas con IA",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.Switch(
                            checked = aiEnabled,
                            onCheckedChange = { 
                                aiEnabled = it
                                repository.saveAiEnabled(it)
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                    Spacer(Modifier.height(8.dp))

                    Text("MOTORES DE IA DISPONIBLES:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gemini API (Oficial)", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = geminiEnabled,
                            onCheckedChange = { 
                                geminiEnabled = it
                                repository.saveGeminiEngineEnabled(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("IA Gratuita (Pública)", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = freeAiEnabled,
                            onCheckedChange = { 
                                freeAiEnabled = it
                                repository.saveFreeAiEngineEnabled(it)
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Clave API Gemini (Opcional):", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = key, 
                        onValueChange = { key = it; testResult = null }, 
                        label = { Text("API Key") },
                        placeholder = { Text("Pega tu clave API aquí...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (testResult != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = testResult!!, 
                            fontSize = 11.sp, 
                            color = if (testResult!!.startsWith("¡Conectado")) Color(0xFF2E7D32) else com.example.demonitalk.ui.theme.HellRed
                        )
                    }
                }
            },
            confirmButton = { 
                TextButton(
                    enabled = !testing,
                    onClick = { 
                        repository.saveAiEnabled(aiEnabled)
                        repository.saveGeminiEngineEnabled(geminiEnabled)
                        repository.saveFreeAiEngineEnabled(freeAiEnabled)

                        if (key.trim().isNotEmpty()) {
                            testing = true
                            val cleanKey = key.trim()
                            repository.saveGeminiApiKey(cleanKey)
                            val assistant = AiAssistant(this@MainActivity)
                            scope.launch {
                                val reply = assistant.askGemini("Di Hola")
                                testing = false
                                testResult = if (!reply.isNullOrEmpty()) {
                                    "¡Conectado con éxito! 😈 Respuesta: $reply"
                                } else {
                                    "Error de conexión con Gemini."
                                }
                            }
                        } else {
                            onDismiss()
                        }
                    }
                ) { 
                    Text(if (testing) "PROBANDO..." else if (key.trim().isNotEmpty()) "PROBAR Y GUARDAR" else "GUARDAR", color = com.example.demonitalk.ui.theme.DemoniPurple, fontWeight = FontWeight.Bold) 
                } 
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CERRAR", color = AshGrey) } }
        )
    }

    @Composable
    fun ControlCircleButton(color: Color, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(44.dp).shadow(6.dp, CircleShape).border(2.dp, Color.White.copy(0.4f), CircleShape),
            shape = CircleShape,
            color = color
        ) { Box(modifier = Modifier.fillMaxSize()) }
    }

    @Composable
    fun CommandItem(command: VoiceCommand, isDarkMode: Boolean, onDelete: () -> Unit, onEdit: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).shadow(8.dp, RoundedCornerShape(16.dp)).border(1.dp, if (command.isRoot) com.example.demonitalk.ui.theme.BrimstoneYellow.copy(0.3f) else com.example.demonitalk.ui.theme.HellRed.copy(0.3f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) com.example.demonitalk.ui.theme.Obsidian else Color.White),
            onClick = onEdit
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = command.trigger.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = if (command.isRoot) com.example.demonitalk.ui.theme.BrimstoneYellow else MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = command.action, style = MaterialTheme.typography.bodySmall, color = if (isDarkMode) AshGrey else Color.Gray)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = com.example.demonitalk.ui.theme.HellRed.copy(0.7f)) }
            }
        }
    }

    @Composable
    fun AddCommandDialog(commandToEdit: VoiceCommand? = null, onDismiss: () -> Unit, onAdd: (String, String, Boolean) -> Unit) {
        var t by remember { mutableStateOf(commandToEdit?.trigger ?: "") }
        var a by remember { mutableStateOf(commandToEdit?.action ?: "") }
        var r by remember { mutableStateOf(commandToEdit?.isRoot ?: false) }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (commandToEdit == null) "NUEVO PACTO" else "EDITAR PACTO", color = com.example.demonitalk.ui.theme.HellRed, fontWeight = FontWeight.Black) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = t, 
                        onValueChange = { t = it }, 
                        label = { Text("Palabra Clave") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = a, 
                        onValueChange = { a = it }, 
                        label = { Text("Acción / Comando") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r, onCheckedChange = { r = it })
                        Text("Requiere Root 😈", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onAdd(t, a, r) }) { Text("SELLAR", fontWeight = FontWeight.Bold, color = com.example.demonitalk.ui.theme.HellRed) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR", color = AshGrey) } }
        )
    }

    @Composable
    fun StorageDialog(onDismiss: () -> Unit, isEnglish: Boolean, onCommandsUpdated: (List<VoiceCommand>) -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (isEnglish) "Storage" else "Almacenamiento", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text("Limpiar caché de comandos y reiniciar base de datos.", color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { repository.clearCache(); onCommandsUpdated(repository.loadCommands()); onDismiss() }) {
                        Icon(Icons.Default.DeleteForever, "Clear", tint = Color.Red)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } }
        )
    }

    @Composable
    fun SettingsDialog(onDismiss: () -> Unit, isEnglish: Boolean) {
        var p by remember { mutableStateOf(repository.getExportPath()) }
        var currentMode by remember { mutableStateOf(repository.getExecutionMode()) }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (isEnglish) "Settings" else "Ajustes del Sistema", color = com.example.demonitalk.ui.theme.HellRed, fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("RUTA DE EXPORTACIÓN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = p, 
                        onValueChange = { p = it }, 
                        label = { Text("Ruta de Respaldos") }, 
                        placeholder = { Text("Ej. /sdcard/Download") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                    Spacer(Modifier.height(12.dp))

                    Text("MODO DE EJECUCIÓN DE COMANDOS:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    val modes = listOf(
                        "HYBRID" to "Demoni (Híbrido Root + Accesibilidad)",
                        "ROOT" to "Solo Root Shell",
                        "ACCESSIBILITY" to "Solo DemoniAccessibility"
                    )

                    modes.forEach { (modeKey, modeLabel) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = (currentMode == modeKey),
                                onClick = { currentMode = modeKey }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = modeLabel,
                                fontSize = 12.sp,
                                color = if (currentMode == modeKey) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = { 
                TextButton(onClick = { 
                    repository.saveExportPath(p)
                    repository.saveExecutionMode(currentMode)
                    onDismiss()
                    Toast.makeText(this@MainActivity, "Ajustes guardados correctamente 😈", Toast.LENGTH_SHORT).show()
                }) { Text("GUARDAR", color = com.example.demonitalk.ui.theme.HellRed, fontWeight = FontWeight.Bold) } 
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR", color = AshGrey) } }
        )
    }

    @Composable
    fun SuccessDialog(onDismiss: () -> Unit, isEnglish: Boolean) {
        AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }, title = { Text("Éxito") }, text = { Text("Operación completada.") })
    }

    @Composable
    fun DrawerButton(text: String, icon: ImageVector, iconTint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), color = Color.Transparent) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(icon, null, tint = iconTint)
                Spacer(Modifier.width(16.dp))
                Text(text, fontWeight = FontWeight.Medium)
            }
        }
    }

    @Composable
    fun DemoniButton(text: String, containerColor: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = containerColor), elevation = ButtonDefaults.buttonElevation(8.dp)) {
            Text(text.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }

    private fun startFloatingService() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } else {
            // Solo aquí pedimos el permiso, porque el usuario pulsó el botón manualmente
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            startActivity(intent)
            Toast.makeText(this, "Concede el permiso para mostrar el botón flotante", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestRoot() {
        Thread {
            try {
                ShellUtils.resetRootCache()
                val available = ShellUtils.isRootAvailable()
                runOnUiThread { Toast.makeText(this, if (available) "Root Granted! 😈" else "Root Denied", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Root Error", Toast.LENGTH_SHORT).show() } }
        }.start()
    }

    private fun sendControlIntent(action: String) {
        val intent = Intent(this, FloatingButtonService::class.java).apply { this.action = action }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun checkAndOpenAccessibility() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Habilita DemoniTalk en Accesibilidad", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Accesibilidad ya activada 😈", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/${DemoniAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled == 1) {
            val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return settingValue?.contains(service) == true
        }
        return false
    }
}
