package com.signa.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.model.AuthError
import com.signa.app.domain.model.AuthException
import com.signa.app.domain.repository.AuthRepository
import com.signa.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AuthRepository, settings: SettingsRepository
) : ViewModel() {
    private val operationError = MutableStateFlow<AuthError?>(null)
    private val signingOut = MutableStateFlow(false)
    val state = combine(repository.session, operationError, signingOut, settings.settings.catch { emit(com.signa.app.domain.model.AppSettings()) }) { session, error, busy, preferences ->
        AccountUiState(user = session?.user, vehicleType = preferences.vehicleType, isLoading = false, operationError = error, isSigningOut = busy)
    }.catch { emit(AccountUiState(isLoading = false, hasError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun logout() {
        if (signingOut.value) return
        signingOut.value = true
        operationError.value = null
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (error: AuthException) {
                operationError.value = error.error
            } catch (_: IOException) {
                operationError.value = AuthError.Storage
            } finally {
                signingOut.value = false
            }
        }
    }
}
