package com.signa.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.signa.app.R
import com.signa.app.ui.theme.SignaTheme
import kotlinx.coroutines.launch

@Composable
fun SignaApp(viewModel: SignaAppViewModel = hiltViewModel()) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    SignaAppContent(access, viewModel::continueAsGuest, viewModel::requestSignIn)
}

@Composable
internal fun SignaAppContent(
    access: AppAccess,
    onGuest: () -> Unit,
    onSignInRequired: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val onMessage: (Int) -> Unit = { message ->
        scope.launch { snackbar.showSnackbar(resources.getString(message)) }
    }
    when (access) {
        AppAccess.Loading -> Scaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        AppAccess.SignedOut -> {
            val controller = rememberNavController()
            Scaffold(containerColor = MaterialTheme.colorScheme.surface, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                AuthNavHost(controller, onGuest, onMessage, Modifier.padding(padding))
            }
        }
        AppAccess.Guest, AppAccess.Authenticated -> key(access) {
            MainApp(snackbar, onMessage, onSignInRequired)
        }
    }
}

@Composable
private fun MainApp(
    snackbar: SnackbarHostState,
    onMessage: (Int) -> Unit,
    onSignInRequired: () -> Unit
) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val selected = MainDestination.forRoute(entry?.destination?.route)
    val scope = rememberCoroutineScope()
    val previewMessage = stringResource(R.string.action_preview_message)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selected != null) {
                SignaBottomBar(
                    selected = selected,
                    onDestinationSelected = { destination ->
                        controller.navigate(destination.route) {
                            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        SignaNavHost(
            controller = controller,
            onPreviewAction = { scope.launch { snackbar.showSnackbar(previewMessage) } },
            onMessage = onMessage,
            onSignInRequired = onSignInRequired,
            modifier = Modifier.padding(padding)
        )
    }
}

@Preview(showBackground = true, name = "Sign in first")
@Preview(showBackground = true, name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignaAppPreview() {
    SignaTheme { SignaAppContent(AppAccess.SignedOut, {}, {}) }
}
