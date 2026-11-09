package com.signa.app.domain.model

/** Coordinates in WGS84; latitude is always the first argument. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(isValid(latitude, longitude)) }
    companion object {
        private fun isValid(lat: Double, lng: Double) = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
        fun fromOrNull(latitude: Double?, longitude: Double?): GeoPoint? =
            if (latitude != null && longitude != null && isValid(latitude, longitude)) GeoPoint(latitude, longitude) else null
    }
}
