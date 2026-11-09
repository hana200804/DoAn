package com.signa.app.data.mapper

import com.signa.app.data.local.database.entity.TrafficSignEntity
import com.signa.app.domain.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class MapCoordinatesTest {
    @Test fun invalidOrPartialCoordinatesDoNotProduceMarkers() {
        assertNull(GeoPoint.fromOrNull(Double.NaN, 106.0))
        assertNull(GeoPoint.fromOrNull(10.0, Double.POSITIVE_INFINITY))
        assertNull(GeoPoint.fromOrNull(91.0, 106.0))
        assertNull(GeoPoint.fromOrNull(10.0, 181.0))
        assertNull(GeoPoint.fromOrNull(null, 106.0))
        val row = TrafficSignEntity("id", "code", "name", "meaning", "location", 50, 10.0, null)
        assertNull(row.toDomain().position)
        val valid = row.copy(latitude = 10.8, longitude = 106.7)
        assertEquals(valid, valid.toDomain().toEntity())
        assertEquals(GeoPoint(10.8, 106.7), valid.toDomain().position)
    }
}
