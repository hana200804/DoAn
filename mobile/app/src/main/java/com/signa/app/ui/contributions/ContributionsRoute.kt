package com.signa.app.ui.contributions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ContributionsRoute(
    onBack: () -> Unit,
    onOpenSign: (String) -> Unit,
    viewModel: ContributionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContributionsScreen(state = state, onBack = onBack, onOpenSign = onOpenSign)
}
