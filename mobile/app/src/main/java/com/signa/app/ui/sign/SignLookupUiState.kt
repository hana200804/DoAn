package com.signa.app.ui.sign

import com.signa.app.domain.model.TrafficSign

data class SignLookupUiState(
    val signs: List<TrafficSign> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false
)
