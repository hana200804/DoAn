package com.signa.app.domain.repository

import com.signa.app.domain.model.LocationResult

interface LocationRepository {
    /** Foreground, cancellable one-shot location request. Never substitutes sample coordinates. */
    suspend fun currentLocation(): LocationResult
}
