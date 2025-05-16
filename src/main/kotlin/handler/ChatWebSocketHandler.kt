package com.gdg.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.gdg.domain.ChatMessage
import com.gdg.infra.ApiClient
import com.gdg.redis.RedisPublisher
import com.gdg.repository.ChatMessageRepository
import com.gdg.repository.ChatRoomRepository
import com.gdg.repository.UserRepository
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
    private val apiClient: ApiClient,
    private val userRepository: UserRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository
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
        val joinMsg = """{"type":"chat","text":"$senderName has entered the chat."}"""
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
                session.sendMessage(TextMessage("""{"type":"error","text":"Invalid session data"}"""))
                return
            }

            val user = userRepository.findByName(senderName)
                .orElseThrow { IllegalArgumentException("유저를 찾을 수 없습니다.") }

            val profileImageUrl = user.profileImageUrl

            val chatRoom = chatRoomRepository.findById(roomId.toLong())
                .orElseThrow { IllegalArgumentException("채팅방을 찾을 수 없습니다.") }

            val limitUsers = chatRoom.limitUsers // To Do : 채팅방 유저 제한 구현

            when (type) {
                "chat" -> {
                    val chatMessage = ChatMessage(
                        type="chat",
                        roomId = roomId,
                        imgUrl = profileImageUrl,
                        senderName = senderName,
                        text = text,
                        senderSessionId = sessionId
                    )
                    val payload = objectMapper.writeValueAsString(chatMessage)
                    redisPublisher.publish("chat", payload)
                    chatMessageRepository.save(chatMessage).subscribe()
                }

                "aiRequest" -> {
                    val aiRequestChatMessage = ChatMessage(
                        type="aiRequest",
                        roomId = roomId,
                        imgUrl = profileImageUrl,
                        senderName = senderName,
                        text = text,
                        senderSessionId = sessionId
                    )
                    val userPayload = objectMapper.writeValueAsString(aiRequestChatMessage)
                    redisPublisher.publish("ai", userPayload)
                    chatMessageRepository.save(aiRequestChatMessage).subscribe()

                    apiClient.sendAiRequest(
                        roomId= roomId,
                        senderName = senderName,
                        prompt=text
                    ).subscribe({ aiResponse ->
                        val aiResponseChatMessage = ChatMessage(
                            type="aiResponse",
                            roomId = roomId,
                            imgUrl = "ai",
                            senderName = "ai",
                            text = aiResponse,
                            senderSessionId = sessionId
                        )
                        val aiPayload = objectMapper.writeValueAsString(aiResponseChatMessage)
                        redisPublisher.publish("ai", aiPayload)
                        chatMessageRepository.save(aiResponseChatMessage).subscribe()
                    }, { error ->
                        val errorMessage = mapOf(
                            "type" to "error",
                            "test" to "AI 응답 처리 중 오류 발생: ${error.message}",
                            "roomId" to roomId
                        )
                        val errorPayload = objectMapper.writeValueAsString(errorMessage)
                        redisPublisher.publish("chat", errorPayload)
                    })
                }


                "refreshMap", "refreshSchedule" -> {
                    val request = mapOf(
                        "roomId" to roomId,
                        "senderSessionId" to sessionId
                    )
                    val payload = objectMapper.writeValueAsString(request)
                    redisPublisher.publish(type, payload)
                }
                else -> {
                    logger.warn("Unknown message type received: $type")
                    session.sendMessage(TextMessage("""{"type":"error","message":"Unknown type"}"""))
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling message: ${e.message}", e)
            session.sendMessage(TextMessage("""{"type":"error","text":"Invalid message format"}"""))
        }
    }

    private fun getRoomId(session: WebSocketSession): String? =
        session.uri?.query?.split("&")?.find { it.startsWith("roomId=") }?.substringAfter("=")

    private fun getSenderName(session: WebSocketSession): String? =
        session.uri?.query?.split("&")?.find { it.startsWith("userName=") }?.substringAfter("=")
}