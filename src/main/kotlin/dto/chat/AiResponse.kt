package com.gdg.dto.chat

data class AiResponse(
    val type: String,
    val senderName: String,
    val text: String,
    val timestamp: Long
)