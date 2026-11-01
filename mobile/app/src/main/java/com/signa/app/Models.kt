package com.signa.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.signa.core.Box
import com.signa.core.Detection
import com.signa.core.SignGroup
import com.signa.core.SignInfo
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.pow
import kotlin.math.roundToInt

/** Bộ mô hình do `signa-ai train` xuất ra: detector, classifier và manifest.json. */
class ModelBundle(
    val version: String,
    val labelMapVersion: Int,
    val groups: List<String>,
    val catalog: Map<String, SignInfo>,
    val signHeightM: Map<String, Double>,
    val detector: Detector,
    val classifier: Classifier,
) {
    companion object {
        fun load(context: Context): ModelBundle {
            val m = JSONObject(context.assets.open("models/manifest.json").bufferedReader().readText())
            val det = m.getJSONObject("detector")
            val cls = m.getJSONObject("classifier")
            val groups = det.getJSONArray("groups").let { a -> List(a.length()) { a.getString(it) } }
            val catalog = m.getJSONArray("catalog").let { a ->
                List(a.length()) { i ->
                    val c = a.getJSONObject(i)
                    SignInfo(
                        code = c.getString("code"),
                        group = SignGroup.valueOf(c.getString("group").uppercase()),
                        name = c.getString("name"),
                        speedKmh = c.optInt("speed_kmh").takeIf { c.has("speed_kmh") },
                        endsSpeedLimit = c.optBoolean("ends_speed_limit"),
                    )
                }.associateBy { it.code }
            }
            val heights = m.getJSONObject("signHeightM").let { o -> o.keys().asSequence().associateWith { o.getDouble(it) } }
            return ModelBundle(
                version = m.getString("version"),
                labelMapVersion = m.getInt("labelMapVersion"),
                groups = groups,
                catalog = catalog,
                signHeightM = heights,
                detector = Detector(mapAsset(context, det.getString("file")), det.getInt("inputSize")),
                classifier = Classifier(
                    mapAsset(context, cls.getString("file")),
                    cls.getInt("inputSize"),
                    cls.getJSONArray("codes").let { a -> List(a.length()) { a.getString(it) } },
                    cls.getDouble("temperature"),
                ),
            )
        }

        private fun mapAsset(context: Context, name: String): ByteBuffer =
            context.assets.openFd("models/$name").use { fd ->
                FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }

        /** GPU delegate phải được tạo và chạy trên cùng một luồng: gọi trên [SignaApp.inference]. */
        internal fun interpreter(model: ByteBuffer): Interpreter {
            val compat = CompatibilityList()
            if (compat.isDelegateSupportedOnThisDevice) {
                val gpu = GpuDelegate(compat.bestOptionsForThisDevice)
                try {
                    return Interpreter(model, Interpreter.Options().addDelegate(gpu))
                } catch (e: Exception) {
                    gpu.close()
                    Log.w(SignaApp.TAG, "GPU delegate lỗi, chuyển sang CPU", e)
                }
            }
            return Interpreter(model, Interpreter.Options().setNumThreads(4)) // CPU (XNNPACK)
        }
    }
}

/** YOLOv8 xuất bởi Ultralytics: đầu ra [1, 4 + số nhóm, N], hộp dạng cx, cy, w, h. */
class Detector(model: ByteBuffer, private val inputSize: Int) {
    private val interpreter = ModelBundle.interpreter(model)

    fun detect(frame: Bitmap, minScore: Float = 0.25f, nmsIou: Float = 0.5f): List<Detection> {
        val scale = inputSize / maxOf(frame.width, frame.height).toFloat()
        val w = (frame.width * scale).roundToInt()
        val h = (frame.height * scale).roundToInt()
        val padX = (inputSize - w) / 2f
        val padY = (inputSize - h) / 2f
        val letterbox = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        Canvas(letterbox).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(Bitmap.createScaledBitmap(frame, w, h, true), padX, padY, null)
        }
        val out = run(interpreter, letterbox)
        val shape = interpreter.getOutputTensor(0).shape()
        val channels = shape[1]
        val n = shape[2]
        // Ultralytics TFLite thường trả toạ độ đã chuẩn hoá về [0, 1]
        val coordScale = if ((0 until n).maxOf { out[it] } <= 2f) inputSize.toFloat() else 1f

        val dets = ArrayList<Detection>()
        for (i in 0 until n) {
            var best = 0f
            var group = 0
            for (c in 4 until channels) {
                val s = out[c * n + i]
                if (s > best) { best = s; group = c - 4 }
            }
            if (best < minScore) continue
            val cx = out[i] * coordScale
            val cy = out[n + i] * coordScale
            val bw = out[2 * n + i] * coordScale
            val bh = out[3 * n + i] * coordScale
            dets += Detection(
                Box((cx - bw / 2 - padX) / scale, (cy - bh / 2 - padY) / scale, (cx + bw / 2 - padX) / scale, (cy + bh / 2 - padY) / scale),
                group,
                best,
            )
        }
        return nms(dets, nmsIou)
    }

    private fun nms(dets: List<Detection>, iou: Float): List<Detection> {
        val kept = ArrayList<Detection>()
        for (d in dets.sortedByDescending { it.score }) {
            if (kept.none { it.group == d.group && it.box.iou(d.box) > iou }) kept += d
        }
        return kept
    }
}

/** YOLOv8-cls: đầu ra xác suất theo thứ tự [codes]; áp nhiệt độ đã hiệu chỉnh. */
class Classifier(model: ByteBuffer, private val inputSize: Int, private val codes: List<String>, private val temperature: Double) {
    private val interpreter = ModelBundle.interpreter(model)

    // shortcut: kéo giãn ảnh cắt về hình vuông thay vì cắt giữa như lúc huấn luyện; đo lại nếu độ chính xác trên máy thấp hơn tập test
    /** Toàn bộ phân bố xác suất; cắt top-N trước khi bỏ phiếu sẽ thổi phồng tỉ lệ phiếu. */
    fun classify(crop: Bitmap): Map<String, Float> {
        val probs = run(interpreter, Bitmap.createScaledBitmap(crop, inputSize, inputSize, true))
        val scaled = probs.map { it.toDouble().coerceAtLeast(1e-12).pow(1 / temperature) }
        val sum = scaled.sum()
        return scaled.indices.associate { codes[it] to (scaled[it] / sum).toFloat() }
    }
}

/** Chạy mô hình một đầu vào ảnh, một đầu ra; tự lượng tử hoá nếu mô hình INT8 hoàn toàn. */
private fun run(interpreter: Interpreter, bitmap: Bitmap): FloatArray {
    val input = interpreter.getInputTensor(0)
    val output = interpreter.getOutputTensor(0)
    val out = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
    interpreter.run(inputBuffer(bitmap, input), out)
    out.rewind()
    return FloatArray(output.numElements()) { readValue(out, output) }
}

private fun inputBuffer(bitmap: Bitmap, t: Tensor): ByteBuffer {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val isFloat = t.dataType() == DataType.FLOAT32
    val buf = ByteBuffer.allocateDirect(pixels.size * 3 * if (isFloat) 4 else 1).order(ByteOrder.nativeOrder())
    val q = t.quantizationParams()
    for (p in pixels) {
        for (shift in intArrayOf(16, 8, 0)) {
            val v = ((p shr shift) and 0xFF) / 255f
            if (isFloat) {
                buf.putFloat(v)
            } else {
                val qv = (v / q.scale + q.zeroPoint).roundToInt()
                buf.put((if (t.dataType() == DataType.UINT8) qv.coerceIn(0, 255) else qv.coerceIn(-128, 127)).toByte())
            }
        }
    }
    buf.rewind()
    return buf
}

private fun readValue(buf: ByteBuffer, t: Tensor): Float = when (t.dataType()) {
    DataType.FLOAT32 -> buf.float
    DataType.UINT8 -> ((buf.get().toInt() and 0xFF) - t.quantizationParams().zeroPoint) * t.quantizationParams().scale
    else -> (buf.get().toInt() - t.quantizationParams().zeroPoint) * t.quantizationParams().scale
}
