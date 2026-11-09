package com.signa.app.data.fake

import com.signa.app.domain.model.ContributionSummary
import com.signa.app.domain.repository.ContributionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeContributionRepository @Inject constructor() : ContributionRepository {
    override fun observeSummary(): Flow<ContributionSummary> = flowOf(FakeData.contributions)
}
