package com.signa.app.data.fake

import com.signa.app.domain.model.ContributionSummary
import com.signa.app.domain.model.ContributionObservation
import com.signa.app.domain.model.ContributionStatus
import com.signa.app.domain.model.TrafficSign
import com.signa.app.domain.model.User
import com.signa.app.domain.model.GeoPoint

/** Runtime fixtures supplied through repositories. No Android resources or UI state. */
object FakeData {
    val signs = listOf(50, 60, 80).map { speed ->
        TrafficSign(
            id = "sample-speed-$speed",
            code = "P.127_$speed",
            name = "Giới hạn tốc độ $speed",
            meaning = "Tốc độ tối đa cho phép là $speed km/h.",
            locationName = "Bình Thạnh · Dữ liệu mẫu",
            speedLimitKmh = speed,
            // Fictional sample positions in Binh Thanh, not verified road-sign observations.
            position = when (speed) {
                50 -> GeoPoint(10.80318, 106.71430)
                60 -> GeoPoint(10.80790, 106.71685)
                else -> GeoPoint(10.79980, 106.70985)
            }
        )
    }
    val profile = User("sample-user", "Minh Anh", "minhanh@example.com")
    private val sampleNow = System.currentTimeMillis()
    val contributions = ContributionSummary(24, 18, 6, signs.mapIndexed { index, sign ->
        ContributionObservation(
            sign,
            if (index == 1) ContributionStatus.PENDING else ContributionStatus.VERIFIED,
            sampleNow - if (index == 2) 24 * 60 * 60 * 1_000L else 0L
        )
    })
}
