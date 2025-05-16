package com.gdg.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.gdg.domain.ChatMessage
import com.gdg.dto.chat.ChatResponse
import com.gdg.session.SessionRegistry
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component

@Component
class RedisSubscriber(
    private val sessionRegistry: SessionRegistry,
    private val objectMapper: ObjectMapper,
    private val container: RedisMessageListenerContainer
) {

    @PostConstruct
    fun subscribe() {
        val chatListener = MessageListener { message, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                val body = String(message.body)
                val chat = objectMapper.readValue(body, ChatMessage::class.java)

                val response = ChatResponse.from(
                    chat.senderName,
                    chat.text,
                    chat.timestamp
                )
                val payload = objectMapper.writeValueAsString(response)

                sessionRegistry.broadcast(
                    chat.roomId,
                    payload,
                    excludeSessionId = chat.senderSessionId
                )
            }
        }

        val refreshMapListener = MessageListener { message, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                val body = String(message.body)
                val request = objectMapper.readTree(body)
                val roomId = request["roomId"].asText()
                val excludeSessionId = request["excludeSessionId"]?.asText()

                val payload = objectMapper.writeValueAsString(
                    mapOf("type" to "refreshMap")
                )

                sessionRegistry.broadcast(roomId, payload, excludeSessionId)
            }
        }

        val refreshScheduleListener = MessageListener { message, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                val body = String(message.body)
                val request = objectMapper.readTree(body)
                val roomId = request["roomId"].asText()
                val excludeSessionId = request["excludeSessionId"]?.asText()

                val payload = objectMapper.writeValueAsString(
                    mapOf("type" to "refreshSchedule")
                )

                sessionRegistry.broadcast(roomId, payload, excludeSessionId)
            }
        }

        container.addMessageListener(refreshMapListener, ChannelTopic("refreshMap"))
        container.addMessageListener(refreshScheduleListener, ChannelTopic("refreshSchedule"))
        container.addMessageListener(chatListener, ChannelTopic("chat"))
    }
}
