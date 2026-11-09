package com.signa.app.data.remote.vietmap

import com.signa.app.domain.model.GeoPoint

/** VietMap Route v4 with points_encoded=true returns Google polyline precision 5. */
internal object PolylineDecoder {
    fun decode(encoded: String): List<GeoPoint> {
        var index = 0
        var lat = 0L
        var lng = 0L
        fun delta(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(index < encoded.length && shift <= 30)
                val chunk = encoded[index++].code - 63
                require(chunk in 0..63)
                result = result or ((chunk and 31).toLong() shl shift)
                if (chunk < 32) break
                shift += 5
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        val result = ArrayList<GeoPoint>()
        while (index < encoded.length) {
            require(result.size < 100_000)
            lat += delta(); lng += delta()
            result.add(requireNotNull(GeoPoint.fromOrNull(lat / 100_000.0, lng / 100_000.0)))
        }
        require(result.size >= 2)
        return result
    }
}
