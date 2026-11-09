package com.signa.app.domain.model

data class TrafficSign(
    val id: String,
    val code: String,
    val name: String,
    val meaning: String,
    val locationName: String,
    val speedLimitKmh: Int? = null,
    val position: GeoPoint? = null
)
