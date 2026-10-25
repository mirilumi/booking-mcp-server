package io.github.mirilumi.mcp.booking.tools.availabilities

import io.github.mirilumi.mcp.booking.McpTestClient
import io.github.mirilumi.mcp.booking.clients.availabilities.api.AvailabilityControllerApi
import io.github.mirilumi.mcp.booking.clients.availabilities.model.Availability
import io.github.mirilumi.mcp.booking.clients.availabilities.model.AvailabilityResponse
import io.github.mirilumi.mcp.booking.clients.availabilities.model.FlightInfo
import io.github.mirilumi.mcp.booking.clients.availabilities.model.JourneyDTO
import io.github.mirilumi.mcp.booking.clients.availabilities.model.PaxPrice
import io.github.mirilumi.mcp.booking.clients.availabilities.model.ProductDTO
import io.github.mirilumi.mcp.booking.clients.availabilities.model.ProductPrice
import io.github.mirilumi.mcp.booking.clients.availabilities.model.SegmentDTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.ResponseEntity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class SearchFlightsToolTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var availability: AvailabilityControllerApi

    private lateinit var mcp: McpTestClient

    @BeforeEach
    fun setUp() {
        mcp = McpTestClient(mockMvc)
        mcp.initialize()
    }

    @Test
    fun `tool is listed with required and optional inputs`() {
        val tool = mcp.listTools().single { it.path("name").asString() == TOOL }

        val schema = tool.path("inputSchema")
        listOf("origin", "destination", "date", "adults", "children", "infants").forEach {
            assertTrue(schema.path("properties").has(it)) { "missing $it in $schema" }
        }
        val required = schema.path("required").values().map { it.asString() }.toSet()
        assertEquals(setOf("origin", "destination", "date"), required)
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean()) { "tool was $tool" }
    }

    @Test
    fun `all bookable flights and fares are returned, sorted, with group prices`() {
        stubAvailability(
            AvailabilityResponse(
                outbound = listOf(
                    journey(
                        "W6 1003", LocalDateTime.of(2026, 11, 5, 18, 0),
                        product("PLUS", seats = 9, adult = "90.00", child = "80.00"),
                        product("BASIC", seats = 9, adult = "45.00", child = "30.00")
                    ),
                    journey(
                        "W6 1001", LocalDateTime.of(2026, 11, 5, 6, 0),
                        // only 2 seats for 3 passengers needing seats: not bookable, left out
                        product("PROMO", seats = 2, adult = "30.00", child = "25.00"),
                        product("BASIC", seats = 9, adult = "40.00", child = "80.00"),
                        // no display price: left out
                        product("SPECIAL", seats = 9, adult = "10.00", child = "10.00", withDisplayPrice = false)
                    ),
                    journey(
                        "W6 1005", LocalDateTime.of(2026, 11, 5, 21, 0),
                        // no bookable fare at all: the whole flight is left out
                        product("BASIC", seats = 1, adult = "35.00", child = "35.00")
                    )
                )
            )
        )

        val result = mcp.callToolForJson(
            TOOL, mapOf("origin" to " skp ", "destination" to "fco", "date" to "2026-11-05", "adults" to 2, "children" to 1)
        )

        verify(availability).getAvailability(
            "SKP", "FCO", LocalDateTime.of(2026, 11, 5, 0, 0), listOf("ADT", "CHD"), listOf(2, 1), null, null, null, null
        )
        assertEquals("EUR", result.path("currency").asString())
        assertEquals(2, result.path("passengers").path("adults").asInt())
        assertEquals(1, result.path("passengers").path("children").asInt())

        // flights sorted by departure; W6 1005 left out
        val flights = result.path("flights")
        assertEquals(listOf("W6 1001", "W6 1003"), flights.values().map { it.path("flightNumbers")[0].asString() })

        // W6 1001: only BASIC remains
        val early = flights[0]
        assertEquals(listOf("BASIC"), fareNames(early))
        val basic = early.path("fares")[0]
        assertEquals(0, BigDecimal("40.00").compareTo(basic.path("pricePerAdult").decimalValue()))
        assertEquals(0, BigDecimal("160.00").compareTo(basic.path("total").decimalValue())) // 2 x 40 + 1 x 80
        val adults = basic.path("perPassenger").values().single { it.path("type").asString() == "ADT" }
        assertEquals(2, adults.path("count").asInt())
        assertEquals(0, BigDecimal("80.00").compareTo(adults.path("subtotal").decimalValue()))

        // W6 1003: both fares, sorted by price per adult
        val late = flights[1]
        assertEquals(listOf("BASIC", "PLUS"), fareNames(late))
        assertEquals(0, BigDecimal("120.00").compareTo(late.path("fares")[0].path("total").decimalValue())) // 2 x 45 + 1 x 30
        assertEquals(0, late.path("stops").asInt())
    }

    @Test
    fun `defaults to one adult`() {
        stubAvailability(AvailabilityResponse(outbound = listOf(journey("W6 1001", LocalDateTime.of(2026, 11, 5, 6, 0), product("BASIC", 9, "64.99", "50.00")))))

        mcp.callToolForJson(TOOL, mapOf("origin" to "SKP", "destination" to "FCO", "date" to "2026-11-05"))

        verify(availability).getAvailability("SKP", "FCO", LocalDateTime.of(2026, 11, 5, 0, 0), listOf("ADT"), listOf(1), null, null, null, null)
    }

    @Test
    fun `no flights on the date is a tool error`() {
        stubAvailability(AvailabilityResponse(outbound = emptyList()))

        assertToolError(mapOf("origin" to "SKP", "destination" to "FCO", "date" to "2026-11-05"), "No flights from SKP to FCO on 2026-11-05")
    }

    @Test
    fun `no fare with enough seats is a tool error`() {
        stubAvailability(AvailabilityResponse(outbound = listOf(journey("W6 1001", LocalDateTime.of(2026, 11, 5, 6, 0), product("BASIC", 1, "64.99", "50.00")))))

        assertToolError(
            mapOf("origin" to "SKP", "destination" to "FCO", "date" to "2026-11-05", "adults" to 2),
            "No seats available from SKP to FCO on 2026-11-05 for 2 passengers"
        )
    }

    @Test
    fun `invalid input is rejected before calling the service`() {
        assertToolError(mapOf("origin" to "SKP", "destination" to "FCO", "date" to "05/11/2026"), "Date must be in YYYY-MM-DD format")
        assertToolError(mapOf("origin" to "SKP", "destination" to "FCO", "date" to "2026-11-05", "adults" to 0), "at least one adult or child")
        verifyNoInteractions(availability)
    }

    private fun fareNames(flight: JsonNode) = flight.path("fares").values().map { it.path("fare").asString() }

    private fun stubAvailability(response: AvailabilityResponse) {
        `when`(availability.getAvailability(anyArg(), anyArg(), anyArg(), anyArg(), anyArg(), anyArg(), anyArg(), anyArg(), anyArg()))
            .thenReturn(ResponseEntity.ok(response))
    }

    /** Mockito's any() returns null, which Kotlin rejects for non-null parameters; the matcher is still registered. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyArg(): T {
        any<T>()
        return null as T
    }

    private fun assertToolError(arguments: Map<String, Any?>, message: String) {
        val result = mcp.callTool(TOOL, arguments)
        assertTrue(result.path("isError").asBoolean()) { "expected an error, result was $result" }
        assertTrue(result.toString().contains(message)) { "expected '$message' in $result" }
    }

    private fun journey(flightNumber: String, departure: LocalDateTime, vararg products: ProductDTO): JourneyDTO {
        val (carrier, number) = flightNumber.split(" ")
        val segment = SegmentDTO(
            origin = "SKP", destination = "FCO", departure = departure, arrival = departure.plusMinutes(90),
            flightInfo = FlightInfo(carrierCode = carrier, flightNumber = number, opSuffix = " "),
            reference = "seg-$flightNumber", status = SegmentDTO.Status.CONFIRMED
        )
        return JourneyDTO(
            segments = listOf(segment), reference = "jny-$flightNumber", products = products.toList(),
            origin = "SKP", destination = "FCO", departure = departure, arrival = departure.plusMinutes(90)
        )
    }

    /** Per-passenger prices for 2 adults and 1 child; like the service, the display price is the one-adult price. */
    private fun product(name: String, seats: Int, adult: String, child: String, withDisplayPrice: Boolean = true) = ProductDTO(
        name = name,
        fares = emptyList(),
        availability = Availability(units = seats),
        paxPrices = listOf(paxPrice(PaxPrice.PaxType.ADT, 2, adult), paxPrice(PaxPrice.PaxType.CHD, 1, child)),
        displayPrice = if (withDisplayPrice) {
            ProductPrice(
                currency = "EUR", basePrice = BigDecimal(adult), taxesAndFees = BigDecimal.ZERO, discount = BigDecimal.ZERO,
                promoCodeDiscount = BigDecimal.ZERO, totalPrice = BigDecimal(adult)
            )
        } else null
    )

    private fun paxPrice(type: PaxPrice.PaxType, count: Int, total: String) = PaxPrice(
        currency = "EUR", basePrice = BigDecimal(total), taxesAndFees = BigDecimal.ZERO, discount = BigDecimal.ZERO,
        paxType = type, count = count, promoCodeDiscount = BigDecimal.ZERO, totalPrice = BigDecimal(total)
    )

    companion object {
        const val TOOL = "availabilities_search_flights"
    }
}
