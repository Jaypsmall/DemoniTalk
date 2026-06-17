package com.example.demonitalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.demonitalk.ui.theme.DemoniPurple
import com.example.demonitalk.ui.theme.DemoniTalkTheme
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private lateinit var repository: CommandRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = CommandRepository(this)

        checkPermissions()

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

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Permission denied for recording audio", Toast.LENGTH_SHORT).show()
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
        
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                try {
                    contentResolver.openInputStream(it)?.use { inputStream ->
                        val reader = InputStreamReader(inputStream)
                        val type = object : TypeToken<List<VoiceCommand>>() {}.type
                        val importedCommands: List<VoiceCommand> = com.google.gson.Gson().fromJson(reader, type)
                        commands = importedCommands
                        repository.saveCommands(commands)
                        Toast.makeText(this, "Comandos Importados! 😈", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al importar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val titleShadow = Shadow(
            color = Color.Black.copy(alpha = 0.8f),
            offset = Offset(6f, 6f),
            blurRadius = 12f
        )
        val demoniTitle = buildAnnotatedString {
            withStyle(style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                shadow = titleShadow
            )) {
                append("De")
            }
            withStyle(style = SpanStyle(
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.ExtraBold,
                shadow = titleShadow
            )) {
                append("moni")
            }
            withStyle(style = SpanStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                shadow = titleShadow
            )) {
                append("Talk 😈")
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = demoniTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 32.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                        
                        DrawerButton(text = "Ajustes", icon = Icons.Default.Menu) { 
                            showSettingsDialog = true
                            scope.launch { drawerState.close() }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = "Idiomas", icon = Icons.Default.Menu) { /* TODO */ }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = "Importar", icon = Icons.Default.Upload) {
                            importLauncher.launch("application/json")
                            scope.launch { drawerState.close() }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = "Exportar", icon = Icons.Default.Download) { 
                            exportCommands { showSuccessDialog = true }
                            scope.launch { drawerState.close() }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerButton(text = "Ayuda", icon = Icons.Default.Menu) { /* TODO */ }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Text(
                            text = "v1.0 - Edición Demoniaca",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            },
                            title = {
                                Text(
                                    text = demoniTitle,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            },
                            actions = {
                                IconButton(onClick = { onThemeToggle(!isDarkMode) }) {
                                    Crossfade(targetState = isDarkMode, animationSpec = tween(500)) { dark ->
                                        Icon(
                                            if (dark) Icons.Default.DarkMode else Icons.Default.NightsStay,
                                            contentDescription = "Toggle Dark Mode"
                                        )
                                    }
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFFC0C0C0),
                                            Color.White,
                                            Color(0xFFC0C0C0),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .shadow(elevation = 12.dp, shape = FloatingActionButtonDefaults.shape, ambientColor = Color.Black, spotColor = Color.Black)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), FloatingActionButtonDefaults.shape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Command")
                    }
                },
                floatingActionButtonPosition = FabPosition.End
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background) // Negro en oscuro, Plata brillante en claro
                ) {
                    DemoniButton(
                        text = "Iniciar Botón Flotante",
                        onClick = { startFloatingService() }
                    )

                    DemoniButton(
                        text = "Solicitar Acceso Root",
                        containerColor = MaterialTheme.colorScheme.error,
                        onClick = { requestRoot() }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(commands, key = { it.trigger + it.action }) { command ->
                            CommandItem(
                                command,
                                onDelete = {
                                    commands = commands - command
                                    repository.saveCommands(commands)
                                },
                                onEdit = {
                                    editingCommand = command
                                }
                            )
                        }
                    }
                }

                // Animación fluida para los diálogos
                AnimatedVisibility(
                    visible = showDialog,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    AddCommandDialog(
                        onDismiss = { showDialog = false },
                        onAdd = { trigger, action, isRoot ->
                            val newCommand = VoiceCommand(trigger, action, isRoot)
                            commands = commands + newCommand
                            repository.saveCommands(commands)
                            showDialog = false
                        }
                    )
                }

                AnimatedVisibility(
                    visible = editingCommand != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    if (editingCommand != null) {
                        AddCommandDialog(
                            commandToEdit = editingCommand,
                            onDismiss = { editingCommand = null },
                            onAdd = { trigger, action, isRoot ->
                                val newCommands = commands.toMutableList()
                                val index = newCommands.indexOf(editingCommand)
                                if (index != -1) {
                                    newCommands[index] = VoiceCommand(trigger, action, isRoot)
                                    commands = newCommands
                                    repository.saveCommands(commands)
                                }
                                editingCommand = null
                            }
                        )
                    }
                }

                // Diálogo de Éxito al Exportar
                if (showSuccessDialog) {
                    SuccessDialog(onDismiss = { showSuccessDialog = false })
                }

                if (showSettingsDialog) {
                    SettingsDialog(onDismiss = { showSettingsDialog = false })
                }
            }
        }
    }

    @Composable
    fun SettingsDialog(onDismiss: () -> Unit) {
        var path by remember { mutableStateOf(repository.getExportPath()) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Configuración ⚙️", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        text = "Ruta de Exportación Custom",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = path,
                        onValueChange = { path = it },
                        placeholder = { Text("Ej: /storage/emulated/0/Download") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Si se deja vacío, usará Descargas por defecto.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.saveExportPath(path)
                    onDismiss()
                }) {
                    Text("GUARDAR")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("CANCELAR")
                }
            }
        )
    }

    @Composable
    fun SuccessDialog(onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "ENTRADAS GUARDADAS",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.3f), offset = Offset(2f, 2f), blurRadius = 4f)
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tu configuración ha sido exportada con éxito a la carpeta de Descargas.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    DemoniButton(text = "ACEPTAR", onClick = onDismiss)
                }
            }
        }
    }

    @Composable
    fun DrawerButton(
        text: String,
        icon: ImageVector,
        onClick: () -> Unit
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    @Composable
    fun DemoniButton(
        text: String,
        containerColor: Color = MaterialTheme.colorScheme.primary,
        onClick: () -> Unit
    ) {
        val isDark = !MaterialTheme.colorScheme.background.copy(alpha = 1f).let { 
            it.red + it.green + it.blue > 1.0f 
        }

        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(
                    elevation = 16.dp, 
                    shape = RoundedCornerShape(12.dp), // Un poco más "cuadrado" pero elegante
                    ambientColor = Color.Black, 
                    spotColor = Color.Black
                )
                .border(
                    width = 1.5.dp, 
                    color = Color.White.copy(alpha = if (isDark) 0.4f else 0.6f), // Brillo metálico
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = containerColor),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 10.dp,
                pressedElevation = 2.dp
            )
        ) {
            Text(
                text = text, 
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.3f), offset = Offset(2f, 2f), blurRadius = 4f)
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun CommandItem(
        command: VoiceCommand,
        modifier: Modifier = Modifier,
        onDelete: () -> Unit,
        onEdit: () -> Unit
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(8.dp, shape = RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp, 
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue < 1.0f }) 
                    Color(0xFF1A1A1A) else Color.White.copy(alpha = 0.9f)
            ),
            onClick = onEdit
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Trigger: ${command.trigger}", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Action: ${command.action}", style = MaterialTheme.typography.bodySmall)
                    if (command.isRoot) {
                        Text(
                            text = "ROOT ACCESS", 
                            color = MaterialTheme.colorScheme.tertiary, 
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(40.dp)
                        .border(
                            width = 1.dp, 
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), // Igual que el borde de tarjetas
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    private fun exportCommands(onSuccess: () -> Unit) {
        val commands = repository.loadCommands()
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(commands)
        val fileName = "DemoniTalk_Backup_${System.currentTimeMillis()}.json"
        
        val customPath = repository.getExportPath().trim()

        try {
            if (customPath.isNotEmpty()) {
                val dir = File(customPath)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(json.toByteArray()) }
                onSuccess()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                    onSuccess()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { it.write(json.toByteArray()) }
                onSuccess()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun AddCommandDialog(
        commandToEdit: VoiceCommand? = null,
        onDismiss: () -> Unit, 
        onAdd: (String, String, Boolean) -> Unit
    ) {
        var trigger by remember { mutableStateOf(commandToEdit?.trigger ?: "") }
        var action by remember { mutableStateOf(commandToEdit?.action ?: "") }
        var isRoot by remember { mutableStateOf(commandToEdit?.isRoot ?: false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (commandToEdit == null) "Add Command" else "Edit Command") },
            text = {
                Column {
                    TextField(value = trigger, onValueChange = { trigger = it }, label = { Text("Trigger Word") })
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = action, 
                        onValueChange = { action = it }, 
                        label = { Text("Action (Pkg, Monkey, Bash, etc.)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 150.dp),
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isRoot, onCheckedChange = { isRoot = it })
                        Text("Root Command")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if(trigger.isNotBlank() && action.isNotBlank()) onAdd(trigger, action, isRoot) }) {
                    Text(if (commandToEdit == null) "Add" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    private fun startFloatingService() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            Toast.makeText(this, "Permission required to draw overlay", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun requestRoot() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = java.io.DataOutputStream(process.outputStream)
                os.writeBytes("exit\n")
                os.flush()
                runOnUiThread {
                    Toast.makeText(this, "Root request sent. Check Magisk/SU", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Root failed or not available", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
