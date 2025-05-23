package com.gdg.dto

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ChatResponse(
    val type: String = "chat",
    val senderName: String,
    val text: String,
    val sendAt: String,
    val imgUrl: String
) {
    companion object {
        private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"))

        fun from(senderName: String, text: String, epochMillis: Long, imgUrl: String): ChatResponse {
            val isoString = formatter.format(Instant.ofEpochMilli(epochMillis))
            return ChatResponse(
                senderName = senderName,
                text = text,
                sendAt = isoString,
                imgUrl = imgUrl
            )
        }
    }
}
