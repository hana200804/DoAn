package com.signa.app.ui.contributions

import com.signa.app.domain.model.ContributionSummary

data class ContributionsUiState(
    val summary: ContributionSummary? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)
