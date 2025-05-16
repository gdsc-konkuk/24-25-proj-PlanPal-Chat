package com.gdg.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
data class ChatRoom(
    @Id
    val id: Long = 0L,
    val limitUsers: Int = 0
)
