package com.gdg.dto

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AiResponse(
    val type: String,
    val senderName: String,
    val text: String,
    val sendAt: Long,
    val imgUrl: String,
){
    companion object {
        private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"))

        fun from(type:String , senderName: String, text: String, epochMillis: Long, imgUrl: String): ChatResponse {
            val isoString = formatter.format(Instant.ofEpochMilli(epochMillis))
            return ChatResponse(
                type=type,
                senderName = senderName,
                text = text,
                sendAt = isoString,
                imgUrl = imgUrl
            )
        }
    }
}