package com.signa.app.ui.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signa.app.domain.repository.ContributionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ContributionsViewModel @Inject constructor(repository: ContributionRepository) : ViewModel() {
    val state = repository.observeSummary()
        .map { ContributionsUiState(summary = it, isLoading = false) }
        .catch { emit(ContributionsUiState(isLoading = false, hasError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContributionsUiState())
}
