package com.signa.app.ui.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.repository.SignRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SignLookupViewModel @Inject constructor(repository: SignRepository) : ViewModel() {
    val state = repository.observeSigns()
        .map { SignLookupUiState(signs = it) }
        .catch { emit(SignLookupUiState(hasError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignLookupUiState(isLoading = true))
}
