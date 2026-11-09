package com.signa.app.domain.repository

import com.signa.app.domain.model.GeoPoint
import com.signa.app.domain.model.MapDestination
import com.signa.app.domain.model.PlaceSuggestion
import com.signa.app.domain.model.RoadRoute
import com.signa.app.domain.model.RoutingOptions

interface DirectionsRepository {
    suspend fun search(text: String, focus: GeoPoint?): List<PlaceSuggestion>
    suspend fun resolve(suggestion: PlaceSuggestion): MapDestination
    suspend fun route(origin: GeoPoint, destination: GeoPoint, options: RoutingOptions): RoadRoute
}
