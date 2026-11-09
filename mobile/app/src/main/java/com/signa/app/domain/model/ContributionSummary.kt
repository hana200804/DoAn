package com.signa.app.domain.model

enum class ContributionStatus { VERIFIED, PENDING }

data class ContributionObservation(
    val sign: TrafficSign,
    val status: ContributionStatus,
    val observedAtEpochMillis: Long
)

data class ContributionSummary(
    val sent: Int,
    val verified: Int,
    val pending: Int,
    val recentObservations: List<ContributionObservation>
)
