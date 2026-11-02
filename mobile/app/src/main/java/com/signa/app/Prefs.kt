package com.signa.app

import android.content.Context
import com.signa.core.VehicleType

/** Phiên đăng nhập và cài đặt (FR-13), lưu trong SharedPreferences. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("signa", Context.MODE_PRIVATE)

    /** Phiên đăng nhập: chủ tài khoản và token luôn được đọc, ghi cùng nhau. */
    data class Auth(val userId: String, val session: String)

    @get:Synchronized
    @set:Synchronized
    var auth: Auth?
        get() = p.getString("auth", null)?.split('\n')?.takeIf { it.size == 2 }?.let { Auth(it[0], it[1]) }
        set(v) = p.edit().putString("auth", v?.let { "${it.userId}\n${it.session}" }).apply()

    /** Chỉ xoá nếu phiên hiện tại vẫn là [expected]: phản hồi 401 của phiên cũ không đăng xuất phiên mới. */
    @Synchronized
    fun clearAuthIf(expected: Auth) {
        if (auth == expected) auth = null
    }

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
