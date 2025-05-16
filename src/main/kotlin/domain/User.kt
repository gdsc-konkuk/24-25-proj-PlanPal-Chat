package com.gdg.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
data class User(
    @Id
    val id: Long = 0L,
    val name: String = "",
    val profileImageUrl: String = ""
)
