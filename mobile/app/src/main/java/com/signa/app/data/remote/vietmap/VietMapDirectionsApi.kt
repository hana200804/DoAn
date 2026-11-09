package com.signa.app.data.remote.vietmap

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Query

interface VietMapDirectionsApi {
    @GET("autocomplete/v4") suspend fun search(
        @Query("text") text: String, @Query("focus") focus: String?, @Query("display_type") displayType: Int = 1
    ): List<SuggestionDto>
    @GET("place/v4") suspend fun place(@Query("refid") refId: String): PlaceDto
    @GET("route/v4") suspend fun route(
        @Query("point") points: List<String>, @Query("vehicle") vehicle: String,
        @Query("capacity") capacity: Int?, @Query("time") departureTime: String,
        @Query("points_encoded") encoded: Boolean = true
    ): RoutesDto
}

@Serializable data class SuggestionDto(@SerialName("ref_id") val refId: String, val display: String = "", val name: String = "", val address: String = "")
@Serializable data class PlaceDto(val display: String = "", val name: String = "", val address: String = "", val lat: Double? = null, val lng: Double? = null)
@Serializable data class RoutesDto(val code: JsonPrimitive? = null, val paths: List<RoutePathDto> = emptyList())
@Serializable data class RoutePathDto(
    val distance: Double, val time: Double, val points: String,
    @SerialName("points_encoded") val pointsEncoded: Boolean = true
)
