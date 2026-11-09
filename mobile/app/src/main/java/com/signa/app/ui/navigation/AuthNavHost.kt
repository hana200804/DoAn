package com.signa.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.signa.app.R
import com.signa.app.ui.auth.AuthMode
import com.signa.app.ui.auth.AuthRoute

/** This graph has no main-app destinations and never displays the bottom bar. */
@Composable
internal fun AuthNavHost(
    controller: NavHostController,
    onGuest: () -> Unit,
    onMessage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val goBack: () -> Unit = { controller.popBackStack() }
    val returnToLogin: () -> Unit = {
        controller.navigate(AppRoutes.LOGIN) {
            popUpTo(AppRoutes.LOGIN) { inclusive = true }
            launchSingleTop = true
        }
    }
    NavHost(controller, startDestination = AppRoutes.LOGIN, modifier = modifier) {
        composable(AppRoutes.LOGIN) {
            AuthRoute(
                mode = AuthMode.Login,
                onRegister = { controller.navigate(AppRoutes.REGISTER) },
                onForgot = { controller.navigate(AppRoutes.FORGOT_PASSWORD) },
                onGuest = onGuest
            )
        }
        composable(AppRoutes.REGISTER) {
            AuthRoute(mode = AuthMode.Register, onBack = goBack, onRegistered = {
                onMessage(R.string.auth_register_success)
                returnToLogin()
            })
        }
        composable(AppRoutes.FORGOT_PASSWORD) {
            AuthRoute(
                mode = AuthMode.ForgotPassword,
                onBack = goBack,
                onReset = { email -> controller.navigate(AppRoutes.resetPassword(email)) }
            )
        }
        composable(
            AppRoutes.RESET_PASSWORD,
            arguments = listOf(navArgument("email") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) {
            AuthRoute(mode = AuthMode.ResetPassword, onBack = goBack, onPasswordReset = {
                onMessage(R.string.auth_reset_success)
                returnToLogin()
            })
        }
    }
}
