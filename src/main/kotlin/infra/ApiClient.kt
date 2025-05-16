package com.gdg.infra

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@Component
class ApiClient(
    private val webClient: WebClient
) {

    @Value("\${backend.url}") // 예: http://backend.internal:8080
    private lateinit var backendUrl: String

    fun sendAiRequest(roomId: String,senderName: String ,prompt: String): Mono<String> {
        //val endpoint = "/api/rooms/$roomId/ai-message"

        val endpoint = "/ai/ai-message"
        val roomIdLong = roomId.toLongOrNull()
        if (roomIdLong==null){
            return Mono.just("error : room id is not Long");
        }
        val uri = UriComponentsBuilder.fromHttpUrl("$backendUrl$endpoint")
            .queryParam("senderName", senderName)
            .queryParam("chatRoomId",roomId )
            .queryParam("prompt", prompt)
            .build()
            .toUriString()


        return webClient.get()
            .uri(uri)
            .retrieve()
            .bodyToMono(String::class.java)
    }
}