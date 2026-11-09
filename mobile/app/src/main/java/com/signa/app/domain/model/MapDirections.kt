package com.signa.app.domain.model

data class PlaceSuggestion(val refId: String, val label: String)
data class MapDestination(val refId: String?, val name: String, val address: String, val point: GeoPoint)
enum class RoutingVehicle(val apiName: String) { CAR("car"), TRUCK("truck") }
data class RoutingOptions(val vehicle: RoutingVehicle, val truckWeightKg: Int? = null)
data class RoadRoute(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationMillis: Long,
    val options: RoutingOptions
)
enum class MapServiceProblem {
    MissingKey, Network, AccessDenied, QuotaUnavailable, RateLimited, InvalidRequest,
    InvalidResponse, Server, NoRoute, UnsupportedVehicle, WeightRequired
}
class MapServiceException(val problem: MapServiceProblem) : Exception(problem.name)
