package com.signa.core

import java.time.LocalTime
import java.util.PriorityQueue

enum class SignGroup { PROHIBITORY, MANDATORY, WARNING, AUXILIARY, OTHER }

enum class VehicleType { CAR, BUS, TRUCK }

/** Thông tin một mã biển trong danh mục. */
data class SignInfo(
    val code: String,
    val group: SignGroup,
    val name: String,
    val speedKmh: Int? = null,
    val endsSpeedLimit: Boolean = false,
)

/** Điều kiện của biển phụ (docs/04-thiet-ke-ai.md §6). */
data class Conditions(
    val timeWindows: List<Pair<LocalTime, LocalTime>> = emptyList(),
    val include: Set<VehicleType> = emptySet(),
    val exclude: Set<VehicleType> = emptySet(),
) {
    fun applies(vehicle: VehicleType, time: LocalTime): Boolean {
        if (include.isNotEmpty() && vehicle !in include) return false
        if (vehicle in exclude) return false
        if (timeWindows.isEmpty()) return true
        return timeWindows.any { (from, to) ->
            if (from <= to) time >= from && time < to else time >= from || time < to // qua nửa đêm
        }
    }
}

data class Speech(val text: String, val priority: Int, val atMs: Long)

/**
 * Máy trạng thái cảnh báo (docs/04-thiet-ke-ai.md §5): giới hạn tốc độ hiện hành, biển phía trước,
 * hàng đợi giọng nói có ưu tiên và chống lặp.
 */
class WarningEngine(
    private val catalog: Map<String, SignInfo>,
    var vehicle: VehicleType = VehicleType.CAR,
    private val clock: () -> LocalTime = LocalTime::now,
) {
    var limitKmh: Int? = null
        private set

    private var overSinceMs: Long? = null
    private var lastSpeedWarnMs = Long.MIN_VALUE / 2
    private var lastSpokenMs = Long.MIN_VALUE / 2
    private val announcedKeys = HashSet<String>()
    private val lastByCode = HashMap<String, Long>()
    private val queue = PriorityQueue<Speech>(compareBy<Speech>({ it.priority }, { it.atMs }))

    /**
     * Một biển đã xác nhận, từ camera (key = "track:<id>") hoặc từ bản đồ (key = "map:<clusterId>").
     * [conditions] null mà [hasSubSign] true nghĩa là biển phụ chưa đọc được.
     */
    fun onSign(key: String, code: String, conditions: Conditions?, hasSubSign: Boolean, nowMs: Long) {
        val info = catalog[code] ?: return
        if (!announcedKeys.add(key)) return
        // Cùng một biển thấy cả từ bản đồ lẫn camera chỉ đọc một lần
        val last = lastByCode[code]
        if (last != null && nowMs - last < SAME_SIGN_MS) return
        if (conditions != null && !conditions.applies(vehicle, clock())) return
        lastByCode[code] = nowMs
        val suffix = if (hasSubSign && conditions == null) ", có biển phụ, chú ý điều kiện" else ""

        when {
            info.endsSpeedLimit -> limitKmh = null
            info.speedKmh != null -> if (limitKmh != info.speedKmh) {
                limitKmh = info.speedKmh
                enqueue("Giới hạn ${info.speedKmh}$suffix", 2, nowMs)
            }
            info.group == SignGroup.PROHIBITORY || info.group == SignGroup.MANDATORY ->
                enqueue("Phía trước ${info.name.lowercase()}$suffix", 2, nowMs)
            info.group == SignGroup.WARNING -> enqueue("Chú ý, ${info.name.lowercase()}$suffix", 3, nowMs)
            else -> Unit
        }
    }

    /** Biển trên bản đồ tải trước: chỉ cảnh báo khi đã vào phạm vi max(50 m, tốc độ × 6 giây). */
    fun onMapSign(
        clusterId: String,
        code: String,
        conditions: Conditions?,
        hasSubSign: Boolean,
        distanceAheadM: Double,
        speedKmh: Float,
        nowMs: Long,
    ) {
        if (distanceAheadM <= maxOf(50.0, speedKmh / 3.6 * 6)) {
            onSign("map:$clusterId", code, conditions, hasSubSign, nowMs)
        }
    }

    /** Quên các biển có [prefix] không còn trong [current] (đã đi qua) để lượt đi sau được cảnh báo lại. */
    fun keepOnly(prefix: String, current: Set<String>) {
        announcedKeys.removeIf { it.startsWith(prefix) && it !in current }
    }

    fun onSpeed(kmh: Float, nowMs: Long) {
        val limit = limitKmh
        if (limit == null || kmh <= limit + SPEED_TOLERANCE) {
            overSinceMs = null
            return
        }
        val since = overSinceMs ?: nowMs.also { overSinceMs = it }
        if (nowMs - since >= OVER_SPEED_MS && nowMs - lastSpeedWarnMs >= REPEAT_SPEED_MS) {
            lastSpeedWarnMs = nowMs
            enqueue("Vượt tốc độ, giới hạn $limit", 1, nowMs)
        }
    }

    /** Câu cần đọc ngay, hoặc null. Tối đa một câu mỗi 4 giây; câu chờ quá 5 giây bị bỏ. */
    fun nextSpeech(nowMs: Long): Speech? {
        queue.removeIf { nowMs - it.atMs > STALE_MS }
        if (nowMs - lastSpokenMs < MIN_GAP_MS) return null
        return queue.poll()?.also { lastSpokenMs = nowMs }
    }

    private fun enqueue(text: String, priority: Int, nowMs: Long) {
        queue += Speech(text, priority, nowMs)
    }

    companion object {
        const val SPEED_TOLERANCE = 3
        const val OVER_SPEED_MS = 3_000L
        const val REPEAT_SPEED_MS = 30_000L
        const val MIN_GAP_MS = 4_000L
        const val STALE_MS = 5_000L
        const val SAME_SIGN_MS = 15_000L
    }
}
