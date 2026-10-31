package com.signa.app

import android.content.Context
import com.signa.core.VehicleType

/** Phiên đăng nhập và cài đặt (FR-13), lưu trong SharedPreferences. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("signa", Context.MODE_PRIVATE)

    var session: String?
        get() = p.getString("session", null)
        set(v) = p.edit().putString("session", v).apply()

    var displayName: String?
        get() = p.getString("displayName", null)
        set(v) = p.edit().putString("displayName", v).apply()

    var vehicle: VehicleType
        get() = VehicleType.valueOf(p.getString("vehicle", VehicleType.CAR.name)!!)
        set(v) = p.edit().putString("vehicle", v.name).apply()

    var voice: Boolean
        get() = p.getBoolean("voice", true)
        set(v) = p.edit().putBoolean("voice", v).apply()

    var contribute: Boolean
        get() = p.getBoolean("contribute", true)
        set(v) = p.edit().putBoolean("contribute", v).apply()

    var wifiOnly: Boolean
        get() = p.getBoolean("wifiOnly", true)
        set(v) = p.edit().putBoolean("wifiOnly", v).apply()
}
