package com.signa.app.ui.sign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SignDetailRoute(
    onBack: () -> Unit,
    onReport: () -> Unit,
    viewModel: SignDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignDetailScreen(state = state, onBack = onBack, onReport = onReport)
}
