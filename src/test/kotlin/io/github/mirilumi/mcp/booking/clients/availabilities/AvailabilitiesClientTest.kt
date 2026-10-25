package io.github.mirilumi.mcp.booking.clients.availabilities

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.mirilumi.mcp.booking.clients.availabilities.api.AvailabilityControllerApi
import io.github.mirilumi.mcp.booking.clients.availabilities.api.ValidDatesControllerApi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

/** The generated availabilities client calls the service with the service token and parses its responses. */
@SpringBootTest
class AvailabilitiesClientTest {
    @Autowired
    private lateinit var validDates: ValidDatesControllerApi

    @Autowired
    private lateinit var availability: AvailabilityControllerApi

    @BeforeEach
    fun reset() = downstreamRequests.clear()

    @Test
    fun `valid dates with prices are fetched with the service token`() {
        val response = validDates.getValidDatesWithPrices("SKP", "FCO")

        val request = downstreamRequests.single()
        assertEquals("/api/v3/validDates/SKP/FCO", request.path)
        assertEquals("Bearer service-token", request.authorization)

        val day = response.body!!.calendar.single().month.single().days.single()
        assertEquals(5, day.day)
        assertEquals(0, BigDecimal("49.99").compareTo(day.price))
    }

    @Test
    fun `flight availability uses the service's date and time formats`() {
        val response = availability.getAvailability(
            "SKP", "FCO", LocalDateTime.of(2026, 11, 5, 0, 0), listOf("ADT", "CHD"), listOf(2, 1), null, null, null, null
        )

        val request = downstreamRequests.single()
        assertEquals("/api/v2/flights/availability", request.path)
        // the service parses departureDate with pattern yyyy-MM-dd and reads only the date part
        assertTrue(request.query.contains("departureDate=2026-11-05")) { "query was ${request.query}" }
        assertTrue(request.query.contains("paxTypes=ADT") && request.query.contains("paxTypes=CHD")) { "query was ${request.query}" }
        // the service sends LocalDateTime values without an offset
        val journey = response.body!!.outbound.single()
        assertEquals(LocalDateTime.of(2026, 11, 5, 6, 0), journey.departure)
        assertEquals(LocalDateTime.of(2026, 11, 5, 7, 30), journey.segments.single().arrival)
        assertEquals(9, journey.products.single().availability.units)
    }

    data class RecordedRequest(val path: String, val query: String, val authorization: String?)

    companion object {
        private val downstreamRequests = CopyOnWriteArrayList<RecordedRequest>()

        private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/oauth2/token") { exchange ->
                exchange.respond("""{"access_token":"service-token","token_type":"bearer","expires_in":3600}""")
            }
            createContext("/api/") { exchange ->
                val uri = exchange.requestURI
                downstreamRequests += RecordedRequest(uri.path, uri.rawQuery.orEmpty(), exchange.requestHeaders.getFirst("Authorization"))
                exchange.respond(if (uri.path.startsWith("/api/v2/flights/availability")) AVAILABILITY_JSON else VALID_DATES_JSON)
            }
            start()
        }

        private const val VALID_DATES_JSON = """{"result":{"isSuccess":true},
            "calendar":[{"year":2026,"currency":"EUR","month":[{"month":11,"days":[{"day":5,"price":49.99}]}]}]}"""

        /** Shape and formats as the availabilities service returns them (LocalDateTime without offset). */
        private const val AVAILABILITY_JSON = """{"outbound":[{"reference":"j1","origin":"SKP","destination":"FCO",
            "departure":"2026-11-05T06:00:00","arrival":"2026-11-05T07:30:00",
            "segments":[{"reference":"s1","origin":"SKP","destination":"FCO","departure":"2026-11-05T06:00:00",
              "arrival":"2026-11-05T07:30:00","status":"CONFIRMED",
              "flightInfo":{"carrierCode":"W6","flightNumber":"1001","opSuffix":" "}}],
            "products":[{"name":"BASIC","fares":[],"availability":{"units":9},
              "paxPrices":[{"currency":"EUR","basePrice":50.00,"taxesAndFees":14.99,"discount":0,"paxType":"ADT",
                "count":2,"promoCodeDiscount":0,"totalPrice":64.99}]}]}]}"""

        private fun HttpExchange.respond(body: String) {
            val bytes = body.toByteArray()
            responseHeaders.add("Content-Type", "application/json")
            sendResponseHeaders(200, bytes.size.toLong())
            responseBody.use { it.write(bytes) }
        }

        @JvmStatic
        @DynamicPropertySource
        fun stubServer(registry: DynamicPropertyRegistry) {
            val url = "http://localhost:${server.address.port}"
            registry.add("AUTH_BASE_URL") { url }
            registry.add("spring.http.serviceclient.availabilities.base-url") { url }
        }

        @JvmStatic
        @AfterAll
        fun stopServer() = server.stop(0)
    }
}
