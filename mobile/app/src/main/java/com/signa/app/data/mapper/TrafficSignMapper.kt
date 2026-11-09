package com.signa.app.data.mapper

import com.signa.app.data.local.database.entity.TrafficSignEntity
import com.signa.app.domain.model.GeoPoint
import com.signa.app.domain.model.TrafficSign

fun TrafficSignEntity.toDomain() = TrafficSign(id, code, name, meaning, locationName, speedLimitKmh, GeoPoint.fromOrNull(latitude, longitude))
fun TrafficSign.toEntity() = TrafficSignEntity(id, code, name, meaning, locationName, speedLimitKmh, position?.latitude, position?.longitude)
