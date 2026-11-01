package com.signa.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.signa.app.R
import com.signa.app.ui.auth.AuthScreen
import com.signa.app.ui.drive.DriveScreen
import com.signa.app.ui.map.MapScreen
import com.signa.app.ui.settings.SettingsScreen
import com.signa.app.ui.sign.SignDetailScreen
import com.signa.app.ui.sign.SignLookupScreen
import com.signa.app.ui.theme.SignaTheme

private enum class MainDestination(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int
) {
    Drive("drive", R.string.nav_drive, R.drawable.ic_drive),
    Map("map", R.string.nav_map, R.drawable.ic_map),
    Sign("sign", R.string.nav_sign, R.drawable.ic_sign),
    Settings("settings", R.string.nav_settings, R.drawable.ic_settings),
    Account("account", R.string.nav_account, R.drawable.ic_account)
}

@Composable
fun SignaApp() {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = route == destination.route ||
                            (route == "sign-detail" && destination == MainDestination.Map) ||
                            (route in listOf("register", "forgot", "reset", "change-password") && destination == MainDestination.Account),
                        onClick = {
                            controller.navigate(destination.route) {
                                popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(controller, startDestination = "drive", modifier = Modifier.padding(padding)) {
            composable("drive") { DriveScreen() }
            composable("map") { MapScreen(onOpenSign = { controller.navigate("sign-detail") }) }
            composable("sign") { SignLookupScreen() }
            composable("settings") { SettingsScreen() }
            composable("account") {
                AuthScreen(
                    onRegister = { controller.navigate("register") },
                    onForgot = { controller.navigate("forgot") },
                    onChangePassword = { controller.navigate("change-password") }
                )
            }
            composable("register") { AuthScreen(mode = "register", onBack = { controller.popBackStack() }) }
            composable("forgot") {
                AuthScreen(mode = "forgot", onBack = { controller.popBackStack() }, onReset = { controller.navigate("reset") })
            }
            composable("reset") { AuthScreen(mode = "reset", onBack = { controller.popBackStack() }) }
            composable("change-password") { AuthScreen(mode = "change", onBack = { controller.popBackStack() }) }
            composable("sign-detail") { SignDetailScreen(onBack = { controller.popBackStack() }) }
        }
    }
}

@Preview(showBackground = true, name = "App Light")
@Preview(showBackground = true, name = "App Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignaAppPreview() {
    SignaTheme { SignaApp() }
}
