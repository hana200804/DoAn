package com.signa.app.domain.repository

import com.signa.app.domain.model.ContributionSummary
import kotlinx.coroutines.flow.Flow

interface ContributionRepository {
    fun observeSummary(): Flow<ContributionSummary>
}
