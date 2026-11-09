package com.signa.app.ui.sign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SignLookupRoute(
    onAction: () -> Unit,
    onOpenSign: (String) -> Unit,
    viewModel: SignLookupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignLookupScreen(state = state, onAction = onAction, onOpenSign = onOpenSign)
}
