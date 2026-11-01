package com.signa.core

/** Hộp chữ nhật theo pixel của khung hình. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height
    val centerX get() = (left + right) / 2

    /** [sub] là biển phụ của biển chính này: nằm dưới, chồng ngang ≥ 50%, cách ≤ 0,5 chiều cao (docs/04-thiet-ke-ai.md §6). */
    fun holdsSubSign(sub: Box): Boolean {
        val overlap = minOf(right, sub.right) - maxOf(left, sub.left)
        return sub.top > top + height / 2 &&
            sub.top - bottom <= 0.5f * height &&
            overlap >= 0.5f * minOf(width, sub.width)
    }

    fun iou(other: Box): Float {
        val w = minOf(right, other.right) - maxOf(left, other.left)
        val h = minOf(bottom, other.bottom) - maxOf(top, other.top)
        if (w <= 0 || h <= 0) return 0f
        val inter = w * h
        return inter / (area + other.area - inter)
    }
}

/** Một phát hiện của detector: hộp, chỉ số nhóm biển, độ tin cậy. */
data class Detection(val box: Box, val group: Int, val score: Float)

/** Chuỗi phát hiện của cùng một biển qua nhiều khung hình (docs/04-thiet-ke-ai.md §4). */
class Track internal constructor(val id: Int, box: Box, val group: Int) {
    var box: Box = box
        internal set
    var confirmed = false
        internal set
    var frames = 1
        internal set
    internal var history = 1
    internal var misses = 0
    private var classifiedArea = 0f
    private val votes = HashMap<String, Double>()

    /**
     * Cộng phiếu của một lần phân loại, trọng số theo diện tích hộp (biển gần rõ hơn). [probs] là
     * toàn bộ phân bố; lần phân loại có xác suất cao nhất dưới [Tracker.MIN_CLASS_PROB] bị bỏ.
     */
    fun vote(probs: Map<String, Float>) {
        if ((probs.values.maxOrNull() ?: 0f) < Tracker.MIN_CLASS_PROB) return
        val weight = box.area.toDouble()
        probs.forEach { (code, p) -> votes.merge(code, p * weight, Double::plus) }
        classifiedArea = box.area
    }

    /** Có cần chạy classifier ở khung này không: track mới, hộp lớn lên ≥ 1,5 lần, hoặc phiếu chưa ổn. */
    fun needsClassification(): Boolean =
        classifiedArea == 0f || box.area >= 1.5f * classifiedArea || share() < Tracker.VOTE_SHARE

    /** Tỉ lệ phiếu của mã dẫn đầu. */
    fun share(): Double {
        val total = votes.values.sum()
        return if (total == 0.0) 0.0 else votes.values.max() / total
    }

    /** Mã của track nếu phiếu đủ ổn định, ngược lại null (SPLIT_VOTE). */
    fun label(): String? =
        if (share() >= Tracker.VOTE_SHARE) votes.maxByOrNull { it.value }?.key else null

    /** Xác suất chuẩn hoá của tối đa n mã dẫn đầu. */
    fun topProbs(n: Int = 3): List<Pair<String, Double>> {
        val total = votes.values.sum().takeIf { it > 0 } ?: return emptyList()
        return votes.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value / total }
    }
}

/**
 * Ghép phát hiện vào track theo IoU, hai bước như ByteTrack: phát hiện tin cậy cao ghép trước,
 * phát hiện tin cậy thấp chỉ được nối vào track đã có.
 */
// shortcut: ghép theo hộp của khung trước, không dùng Kalman; bổ sung nếu thực địa cho thấy hay mất track khi xe nhanh
class Tracker(
    private val confirmHits: Int = 3,
    private val window: Int = 5,
    private val maxMisses: Int = 10,
    private val minIou: Float = 0.3f,
    private val highScore: Float = 0.5f,
) {
    private val tracks = mutableListOf<Track>()
    private var nextId = 1
    private val mask = (1 shl window) - 1

    /** active: track đang theo dõi; ended: track đã xác nhận vừa kết thúc, mỗi biển xuất hiện một lần. */
    data class Result(val active: List<Track>, val ended: List<Track>)

    fun update(detections: List<Detection>): Result {
        val unmatched = tracks.toMutableSet()
        val (high, low) = detections.sortedByDescending { it.score }.partition { it.score >= highScore }
        val leftover = mutableListOf<Detection>()
        for (d in high) if (!match(d, unmatched)) leftover += d
        for (d in low) match(d, unmatched)

        for (t in unmatched) {
            t.history = (t.history shl 1) and mask
            t.misses++
        }
        for (d in leftover) tracks += Track(nextId++, d.box, d.group)

        val ended = mutableListOf<Track>()
        tracks.removeAll { t ->
            if (!t.confirmed && Integer.bitCount(t.history) >= confirmHits) t.confirmed = true
            val dead = if (t.confirmed) t.misses > maxMisses else t.history == 0
            if (dead && t.confirmed) ended += t
            dead
        }
        return Result(tracks.toList(), ended)
    }

    private fun match(d: Detection, candidates: MutableSet<Track>): Boolean {
        val best = candidates
            .filter { it.group == d.group }
            .maxByOrNull { it.box.iou(d.box) }
            ?.takeIf { it.box.iou(d.box) >= minIou }
            ?: return false
        best.box = d.box
        best.history = ((best.history shl 1) or 1) and mask
        best.misses = 0
        best.frames++
        candidates -= best
        return true
    }

    companion object {
        const val VOTE_SHARE = 0.6
        const val MIN_CLASS_PROB = 0.5f
    }
}
