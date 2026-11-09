package com.signa.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "traffic_signs")
data class TrafficSignEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val meaning: String,
    val locationName: String,
    val speedLimitKmh: Int?,
    val latitude: Double? = null,
    val longitude: Double? = null
)
