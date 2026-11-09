package com.signa.app.ui.navigation

import android.net.Uri
import com.signa.app.ui.sign.SIGN_ID_ARGUMENT

/** Navigation identifiers live here so callers do not construct route strings. */
internal object AppRoutes {
    const val MAP = "map"
    const val DRIVE = "drive"
    const val SIGN_LOOKUP = "sign"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val DRIVE_ACTIVE = "drive-active"
    const val PROFILE_PREVIEW = "profile"
    const val CONTRIBUTIONS = "contributions"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot"
    const val RESET_PASSWORD = "reset?email={email}"
    const val CHANGE_PASSWORD = "change-password"
    const val SIGN_ARGUMENT = SIGN_ID_ARGUMENT
    const val SIGN_DETAIL = "sign-detail/{$SIGN_ARGUMENT}"

    fun signDetail(id: String): String = "sign-detail/${Uri.encode(id)}"
    fun resetPassword(email: String): String = "reset?email=${Uri.encode(email)}"
}
