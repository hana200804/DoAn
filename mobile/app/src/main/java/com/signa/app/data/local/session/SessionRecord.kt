package com.signa.app.data.local.session

import com.signa.app.domain.model.Session
import com.signa.app.domain.model.SessionSource
import com.signa.app.domain.model.User
import com.signa.app.domain.model.VehicleType
import kotlinx.serialization.Serializable

@Serializable
internal data class SessionRecord(
    val id: String,
    val userId: String,
    val displayName: String,
    val email: String,
    val vehicleType: String,
    val expiresAtEpochMillis: Long,
    val source: String
) {
    fun toDomain() = Session(
        id = id,
        user = User(userId, displayName, email, VehicleType.entries.firstOrNull { it.name == vehicleType } ?: VehicleType.CAR),
        expiresAtEpochMillis = expiresAtEpochMillis,
        source = SessionSource.valueOf(source)
    )

    companion object {
        fun from(session: Session) = SessionRecord(
            session.id, session.user.id, session.user.displayName, session.user.email,
            session.user.vehicleType.name, session.expiresAtEpochMillis, session.source.name
        )
    }
}
