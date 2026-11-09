package com.signa.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

internal enum class AppAccess { Loading, SignedOut, Guest, Authenticated }

@HiltViewModel
class SignaAppViewModel @Inject constructor(repository: AuthRepository) : ViewModel() {
    private val mutableAccess = MutableStateFlow(AppAccess.Loading)
    internal val access = mutableAccess.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.catch { emit(null) }.collect { session ->
                mutableAccess.value = if (session == null) AppAccess.SignedOut else AppAccess.Authenticated
            }
        }
    }

    fun continueAsGuest() {
        if (mutableAccess.value == AppAccess.SignedOut) mutableAccess.value = AppAccess.Guest
    }

    fun requestSignIn() {
        if (mutableAccess.value == AppAccess.Guest) mutableAccess.value = AppAccess.SignedOut
    }
}
