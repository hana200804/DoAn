package com.signa.app.ui.sign

import com.signa.app.domain.model.TrafficSign

data class SignDetailUiState(
    val sign: TrafficSign? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)
