package com.signa.app.ui.account

import com.signa.app.domain.model.User
import com.signa.app.domain.model.VehicleType
import com.signa.app.domain.model.AuthError

data class AccountUiState(
    val user: User? = null,
    val vehicleType: VehicleType = VehicleType.CAR,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val operationError: AuthError? = null,
    val isSigningOut: Boolean = false
)
