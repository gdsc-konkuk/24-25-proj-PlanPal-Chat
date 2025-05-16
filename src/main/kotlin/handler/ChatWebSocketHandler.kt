package com.gdg.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.gdg.domain.ChatMessage
import com.gdg.infra.ApiClient
import com.gdg.redis.RedisPublisher
import com.gdg.session.SessionRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class ChatWebSocketHandler(
    private val redisPublisher: RedisPublisher,
    private val sessionRegistry: SessionRegistry,
    private val objectMapper: ObjectMapper,
    private val apiClient: ApiClient
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val roomId = getRoomId(session)
        val senderName = getSenderName(session)

        if (roomId == null || senderName == null) {
            logger.warn("Invalid connection attempt: missing roomId or userName. Closing session.")
            session.close(CloseStatus.BAD_DATA)
            return
        }

        sessionRegistry.add(roomId, session)
        val joinMsg = """{"type":"chat","text":"$senderName 님이 입장하셨습니다."}"""
        sessionRegistry.broadcast(roomId, joinMsg)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val roomId = getRoomId(session)
        if (roomId != null) {
            sessionRegistry.remove(roomId, session)
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val json = objectMapper.readTree(message.payload)
            val type = json["type"]?.asText()
            val text = json["text"]?.asText() ?: ""

            val roomId = getRoomId(session)
            val senderName = getSenderName(session)
            val sessionId = session.id

            if (roomId == null || senderName == null) {
                logger.warn("Invalid session data: roomId or userName missing.")
                session.sendMessage(TextMessage("""{"type":"error","message":"Invalid session data"}"""))
                return
            }

            when (type) {
                "chat" -> {
                    val chatMessage = ChatMessage(
                        roomId = roomId,
                        senderName = senderName,
                        content = text,
                        senderSessionId = sessionId
                    )
                    val payload = objectMapper.writeValueAsString(chatMessage)
                    redisPublisher.publish("chat", payload)
                }
                "ai" -> {
                    // AI 백엔드 로직
                }
                "refreshMap", "refreshSchedule" -> {
                    val request = mapOf(
                        "roomId" to roomId,
                        "excludeSessionId" to sessionId
                    )
                    val payload = objectMapper.writeValueAsString(request)
                    redisPublisher.publish(typeToTopic(type), payload)
                }
                else -> {
                    logger.warn("Unknown message type received: $type")
                    session.sendMessage(TextMessage("""{"type":"error","message":"Unknown type"}"""))
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling message: ${e.message}", e)
            session.sendMessage(TextMessage("""{"type":"error","message":"Invalid message format"}"""))
        }
    }

    private fun getRoomId(session: WebSocketSession): String? =
        session.uri?.query?.split("&")?.find { it.startsWith("roomId=") }?.substringAfter("=")

    private fun getSenderName(session: WebSocketSession): String? =
        session.uri?.query?.split("&")?.find { it.startsWith("userName=") }?.substringAfter("=")

    private fun typeToTopic(type: String) = when (type) {
        "refreshMap" -> "refresh-map"
        "refreshSchedule" -> "refresh-schedule"
        else -> error("Unknown type: $type")
    }
}