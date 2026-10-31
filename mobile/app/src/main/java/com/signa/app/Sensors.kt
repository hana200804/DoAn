package com.signa.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/** GPS qua LocationManager của nền tảng; tốc độ km/h và hướng đi. */
class LocationTracker(context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission") // gọi sau khi đã có quyền vị trí
    fun start(onFix: (Fix) -> Unit) {
        stop()
        val l = LocationListener { loc: Location ->
            onFix(Fix(loc.latitude, loc.longitude, loc.speed * 3.6f, loc.bearing.toDouble(), loc.time))
        }
        listener = l
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l)
    }

    fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }
}

/** Đọc cảnh báo tiếng Việt. */
class Speaker(context: Context) {
    private var ready = false
    private val tts = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.apply { language = Locale("vi", "VN") }

    fun say(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }

    fun shutdown() = tts.shutdown()
}
