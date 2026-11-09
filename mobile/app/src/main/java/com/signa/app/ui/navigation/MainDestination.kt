package com.signa.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.signa.app.R

internal enum class MainDestination(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int
) {
    Map(AppRoutes.MAP, R.string.nav_map, R.drawable.ic_map),
    Drive(AppRoutes.DRIVE, R.string.nav_drive, R.drawable.ic_drive),
    Sign(AppRoutes.SIGN_LOOKUP, R.string.nav_sign, R.drawable.ic_sign),
    Settings(AppRoutes.SETTINGS, R.string.nav_settings, R.drawable.ic_settings),
    Account(AppRoutes.ACCOUNT, R.string.nav_account, R.drawable.ic_account);

    companion object {
        fun forRoute(route: String?): MainDestination? {
            if (route == AppRoutes.PROFILE_PREVIEW) return Account
            return entries.firstOrNull { it.route == route }
        }
    }
}
