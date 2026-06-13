package com.example.demonitalk

data class VoiceCommand(
    val trigger: String,
    val action: String,
    val isRoot: Boolean = false
)
