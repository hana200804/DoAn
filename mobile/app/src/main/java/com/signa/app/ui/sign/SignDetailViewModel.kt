package com.signa.app.ui.sign

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.repository.SignRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal const val SIGN_ID_ARGUMENT = "signId"

@HiltViewModel
class SignDetailViewModel @Inject constructor(
    repository: SignRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val signId: String? = savedStateHandle[SIGN_ID_ARGUMENT]
    val state = (signId?.let(repository::observeSign) ?: flowOf(null))
        .map { SignDetailUiState(sign = it, isLoading = false) }
        .catch { emit(SignDetailUiState(isLoading = false, hasError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignDetailUiState())
}
