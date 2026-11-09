package com.signa.app.domain.model

/** demoCode is supplied only by the local fake backend. */
data class PasswordResetChallenge(
    val email: String,
    val expiresAtEpochMillis: Long,
    val demoCode: String? = null
)
