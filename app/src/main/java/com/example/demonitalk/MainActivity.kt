package com.example.demonitalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.demonitalk.ui.theme.DemoniTalkTheme

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

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            val titleShadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = Offset(4f, 4f),
                                blurRadius = 8f
                            )
                            Text(
                                text = buildAnnotatedString {
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
                                },
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
                    // Línea Plateada Ultra Potente
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
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                DemoniButton(
                    text = "Start Floating Button",
                    onClick = { startFloatingService() }
                )

                DemoniButton(
                    text = "Request Root Access",
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
        }
    }

    @Composable
    fun DemoniButton(
        text: String,
        containerColor: Color = MaterialTheme.colorScheme.primary,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(elevation = 12.dp, shape = ButtonDefaults.shape, ambientColor = Color.Black, spotColor = Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.4f), ButtonDefaults.shape),
            colors = ButtonDefaults.buttonColors(containerColor = containerColor),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }

    @Composable
    fun CommandItem(command: VoiceCommand, onDelete: () -> Unit, onEdit: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .shadow(4.dp, shape = CardDefaults.shape)
                .border(
                    width = 1.dp, 
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), // Mini borde demoniaco
                    shape = CardDefaults.shape
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
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
