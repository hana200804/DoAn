package com.signa.app

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Giữ các đối tượng dùng chung của ứng dụng (DI thủ công). */
class SignaApp : Application() {
    lateinit var prefs: Prefs
    lateinit var api: Api
    lateinit var signs: SignStore
    lateinit var queue: ObservationQueue

    /** Null khi chưa chép bộ mô hình vào assets/models. */
    var models: ModelBundle? = null
        private set
    var modelError: String? = null
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Một luồng sống cùng ứng dụng để nạp và chạy mô hình (yêu cầu của GPU delegate). */
    val inference: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        prefs = Prefs(this)
        api = Api(BuildConfig.API_BASE_URL, prefs)
        signs = SignStore(File(filesDir, "signs.json"), api)
        queue = ObservationQueue(File(filesDir, "queue"), api, prefs, this)
        runCatching { models = inference.submit<ModelBundle> { ModelBundle.load(this) }.get() }.onFailure {
            modelError = (it.cause ?: it).message
            Log.w(TAG, "Chưa nạp được mô hình", it)
        }
        scope.launch {
            while (isActive) {
                delay(UPLOAD_INTERVAL_MS)
                val m = models ?: continue
                if (queue.canUpload()) {
                    runCatching { queue.uploadOnce(m.version, m.labelMapVersion) }
                        .onFailure { Log.w(TAG, "Gửi quan sát lỗi, sẽ thử lại", it) }
                }
            }
        }
    }

    companion object {
        const val TAG = "Signa"
        private const val UPLOAD_INTERVAL_MS = 30_000L
    }
}

val Context.app: SignaApp get() = applicationContext as SignaApp
