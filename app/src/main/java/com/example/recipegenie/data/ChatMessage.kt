package com.example.recipegenie.data

data class ChatMessage(
    val text: String,
    val isFromAi: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)