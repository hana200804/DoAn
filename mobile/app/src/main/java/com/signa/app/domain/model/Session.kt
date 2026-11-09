package com.signa.app.domain.model

enum class SessionSource { FAKE, REMOTE }

data class Session(
    val id: String,
    val user: User,
    val expiresAtEpochMillis: Long,
    val source: SessionSource
)
