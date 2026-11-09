package com.signa.app.data.repository

import com.signa.app.data.remote.vietmap.PolylineDecoder
import com.signa.app.data.remote.vietmap.VietMapApiKeys
import com.signa.app.data.remote.vietmap.VietMapDirectionsApi
import com.signa.app.domain.model.*
import com.signa.app.domain.repository.DirectionsRepository
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class VietMapDirectionsRepository @Inject constructor(
    private val api: VietMapDirectionsApi,
    private val keys: VietMapApiKeys,
    private val clock: Clock
) : DirectionsRepository {
    override suspend fun search(text: String, focus: GeoPoint?): List<PlaceSuggestion> = request(keys.search) {
        val items = api.search(text.trim(), focus?.parameter())
        val mapped = items.mapNotNull {
            val label = it.display.ifBlank { listOf(it.name, it.address).filter(String::isNotBlank).joinToString(", ") }
            if (it.refId.isBlank() || label.isBlank()) null else PlaceSuggestion(it.refId, label)
        }.distinctBy { it.refId }
        if (items.isNotEmpty() && mapped.isEmpty()) fail(MapServiceProblem.InvalidResponse)
        mapped.take(10)
    }

    override suspend fun resolve(suggestion: PlaceSuggestion): MapDestination = request(keys.search) {
        val place = api.place(suggestion.refId)
        val point = GeoPoint.fromOrNull(place.lat, place.lng) ?: fail(MapServiceProblem.InvalidResponse)
        MapDestination(suggestion.refId, place.name.ifBlank { suggestion.label }, place.display.ifBlank { suggestion.label }, point)
    }

    override suspend fun route(origin: GeoPoint, destination: GeoPoint, options: RoutingOptions): RoadRoute = request(keys.route) {
        if (options.vehicle == RoutingVehicle.TRUCK && (options.truckWeightKg == null || options.truckWeightKg <= 0)) fail(MapServiceProblem.WeightRequired)
        val response = api.route(listOf(origin.parameter(), destination.parameter()), options.vehicle.apiName,
            if (options.vehicle == RoutingVehicle.TRUCK) options.truckWeightKg else null, clock.instant().toString())
        when (response.code?.content?.uppercase(java.util.Locale.ROOT)) {
            null, "OK", "200" -> Unit
            "401", "403", "REQUEST_DENIED" -> fail(MapServiceProblem.AccessDenied)
            "423", "OVER_QUERY_LIMIT" -> fail(MapServiceProblem.QuotaUnavailable)
            "429" -> fail(MapServiceProblem.RateLimited)
            "ZERO_RESULTS", "NO_ROUTE", "NOT_FOUND" -> fail(MapServiceProblem.NoRoute)
            else -> fail(MapServiceProblem.InvalidResponse)
        }
        val path = response.paths.firstOrNull() ?: fail(MapServiceProblem.NoRoute)
        if (!path.pointsEncoded || !path.distance.isFinite() || path.distance < 0 || !path.time.isFinite() || path.time < 0 || path.time >= Long.MAX_VALUE.toDouble()) fail(MapServiceProblem.InvalidResponse)
        val geometry = try { PolylineDecoder.decode(path.points) } catch (_: IllegalArgumentException) { fail(MapServiceProblem.InvalidResponse) }
        RoadRoute(origin, destination, geometry, path.distance, kotlin.math.ceil(path.time).toLong(), options)
    }

    private suspend fun <T> request(key: String, block: suspend () -> T): T {
        if (key.isBlank()) fail(MapServiceProblem.MissingKey)
        try { return block() }
        catch (error: HttpException) {
            error.response()?.errorBody()?.close()
            fail(when (error.code()) {
                400, 422 -> MapServiceProblem.InvalidRequest
                401, 403 -> MapServiceProblem.AccessDenied
                423 -> MapServiceProblem.QuotaUnavailable
                429 -> MapServiceProblem.RateLimited
                in 500..599 -> MapServiceProblem.Server
                else -> MapServiceProblem.InvalidResponse
            })
        } catch (_: SerializationException) { fail(MapServiceProblem.InvalidResponse) }
        catch (_: IOException) { fail(MapServiceProblem.Network) }
    }
    private fun GeoPoint.parameter() = "$latitude,$longitude"
    private fun fail(problem: MapServiceProblem): Nothing = throw MapServiceException(problem)
}
