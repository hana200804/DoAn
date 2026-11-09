package com.signa.app.data.remote.vietmap

import com.signa.app.data.repository.VietMapDirectionsRepository
import com.signa.app.domain.model.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VietMapDirectionsRepositoryTest {
    private val server = MockWebServer()
    private val keys = VietMapApiKeys("search-test-key", "route-test-key")
    private lateinit var repository: VietMapDirectionsRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-09T03:00:00Z"), ZoneOffset.UTC)
    private val origin = GeoPoint(38.5, -120.2)
    private val destination = GeoPoint(43.252, -126.453)
    private val routeJson = """{"code":"OK","paths":[{"distance":1234.5,"time":120001,"points_encoded":true,"points":"_p~iF~ps|U_ulLnnqC_mqNvxq`@"}]}"""

    @Before fun prepare() {
        server.start()
        repository = VietMapDirectionsRepository(VietMapApiFactory.create(server.url("/api/"), keys, Json { ignoreUnknownKeys = true }), keys, clock)
    }
    @After fun close() { server.shutdown() }
    private suspend fun expect(problem: MapServiceProblem, call: suspend () -> Unit) {
        try { call(); fail("Expected $problem") } catch (failure: MapServiceException) { assertEquals(problem, failure.problem); assertFalse(failure.message.orEmpty().contains("test-key")) }
    }

    @Test fun autocompleteUsesSearchKeyAndNoAppSession() = runTest {
        server.enqueue(MockResponse().setBody("""[{"ref_id":"auto:place-id","display":"Landmark 81","extra":true}]"""))
        assertEquals(listOf(PlaceSuggestion("auto:place-id", "Landmark 81")), repository.search(" Landmark ", GeoPoint(10.8, 106.7)))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/autocomplete/v4", request.requestUrl!!.encodedPath)
        assertEquals("search-test-key", request.requestUrl!!.queryParameter("apikey"))
        assertEquals("10.8,106.7", request.requestUrl!!.queryParameter("focus"))
        assertEquals("Landmark", request.requestUrl!!.queryParameter("text"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test fun resolvesRefIdVerbatimAndPreservesLatitudeLongitudeOrder() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"","display":"Address","lat":10.79494,"lng":106.7218}"""))
        val selected = repository.resolve(PlaceSuggestion("geocode:abc/def+ghi", "Selected address"))
        assertEquals(GeoPoint(10.79494,106.7218), selected.point)
        assertEquals("Selected address", selected.name)
        assertEquals("geocode:abc/def+ghi", server.takeRequest().requestUrl!!.queryParameter("refid"))
    }

    @Test fun routeUsesRouteKeyTruckCapacityAndMilliseconds() = runTest {
        server.enqueue(MockResponse().setBody(routeJson))
        val road = repository.route(origin, destination, RoutingOptions(RoutingVehicle.TRUCK, 5000))
        assertEquals(listOf(origin, GeoPoint(40.7,-120.95), destination), road.points)
        assertEquals(1234.5, road.distanceMeters, .001)
        assertEquals(120001L, road.durationMillis)
        val request = server.takeRequest().requestUrl!!
        assertEquals("route-test-key", request.queryParameter("apikey"))
        assertEquals(listOf("38.5,-120.2", "43.252,-126.453"), request.queryParameterValues("point"))
        assertEquals("truck", request.queryParameter("vehicle"))
        assertEquals("5000", request.queryParameter("capacity"))
        assertEquals("true", request.queryParameter("points_encoded"))
        assertEquals(clock.instant().toString(), request.queryParameter("time"))
    }

    @Test fun carDoesNotSendTruckCapacityAndMissingWeightDoesNotCallApi() = runTest {
        expect(MapServiceProblem.WeightRequired) { repository.route(origin,destination,RoutingOptions(RoutingVehicle.TRUCK)) }
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setBody(routeJson))
        repository.route(origin,destination,RoutingOptions(RoutingVehicle.CAR,5000))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("capacity"))
    }

    @Test fun quotaAndDeniedAreDistinctFromServerAndMalformedData() = runTest {
        server.enqueue(MockResponse().setResponseCode(423).setBody("Your request is limited"))
        expect(MapServiceProblem.QuotaUnavailable) { repository.search("Landmark", null) }
        server.enqueue(MockResponse().setResponseCode(401))
        expect(MapServiceProblem.AccessDenied) { repository.search("Landmark", null) }
        server.enqueue(MockResponse().setResponseCode(503))
        expect(MapServiceProblem.Server) { repository.search("Landmark", null) }
        server.enqueue(MockResponse().setBody("invalid json"))
        expect(MapServiceProblem.InvalidResponse) { repository.search("Landmark", null) }
    }

    @Test fun invalidPlaceCoordinateAndPolylineAreRejected() = runTest {
        server.enqueue(MockResponse().setBody("""{"lat":95.0,"lng":106.0}"""))
        expect(MapServiceProblem.InvalidResponse) { repository.resolve(PlaceSuggestion("auto:id","Address")) }
        server.enqueue(MockResponse().setBody("""{"code":"OK","paths":[{"distance":10,"time":1000,"points":"_p~iF"}]}"""))
        expect(MapServiceProblem.InvalidResponse) { repository.route(origin,destination,RoutingOptions(RoutingVehicle.CAR)) }
    }

    @Test fun emptyRouteHasNoInventedGeometryAndRedirectDoesNotLeakKey() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":"OK","paths":[]}"""))
        expect(MapServiceProblem.NoRoute) { repository.route(origin,destination,RoutingOptions(RoutingVehicle.CAR)) }
        val external = MockWebServer()
        external.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", external.url("/redirect")))
            expect(MapServiceProblem.InvalidResponse) { repository.search("Landmark",null) }
            assertNull(external.takeRequest(100,TimeUnit.MILLISECONDS))
        } finally { external.shutdown() }
    }
}
