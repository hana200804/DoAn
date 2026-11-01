package com.signa.core

/**
 * Biển trên bản đồ mà xe đã đi qua nhưng camera không thấy (docs/04-thiet-ke-ai.md §8): biển cùng
 * chiều, ở phía trước xe, được đến gần trong [nearM] rồi ra xa quá [passedM], và camera không xác
 * nhận biển cùng mã trong [seenWindowMs]. "Đã qua" xét theo khoảng cách chứ không theo hướng, vì xe
 * đứng yên thì GPS không có hướng.
 */
class PassMonitor(
    private val nearM: Double = 20.0,
    private val passedM: Double = 50.0,
    private val seenWindowMs: Long = 30_000,
) {
    private val approached = HashMap<String, String>() // clusterId → mã biển
    private val lastSeen = HashMap<String, Long>() // mã biển → lần camera xác nhận gần nhất

    fun cameraSaw(code: String, nowMs: Long) {
        lastSeen[code] = nowMs
    }

    /**
     * [around]: clusterId → (mã biển, khoảng cách m) của biển quanh xe; [ahead]: các cụm cùng chiều ở
     * phía trước, chỉ những biển này camera mới nhìn được. Trả các cụm cần gửi quan sát âm.
     */
    fun update(around: Map<String, Pair<String, Double>>, ahead: Set<String>, nowMs: Long): List<String> {
        around.forEach { (id, v) -> if (id in ahead && v.second <= nearM) approached[id] = v.first }
        val missed = ArrayList<String>()
        approached.entries.removeIf { (id, code) ->
            val d = around[id]?.second
            val passed = d == null || d > passedM
            val seen = lastSeen[code]?.let { nowMs - it <= seenWindowMs } == true
            if (passed && !seen) missed += id
            passed
        }
        return missed
    }
}
