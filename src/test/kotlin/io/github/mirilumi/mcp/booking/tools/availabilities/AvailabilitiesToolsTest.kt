package io.github.mirilumi.mcp.booking.tools.availabilities

import io.github.mirilumi.mcp.booking.McpTestClient
import io.github.mirilumi.mcp.booking.clients.availabilities.api.ValidDatesControllerApi
import io.github.mirilumi.mcp.booking.clients.availabilities.model.DayPrice
import io.github.mirilumi.mcp.booking.clients.availabilities.model.InValidDateResponse
import io.github.mirilumi.mcp.booking.clients.availabilities.model.InvalidDatesCalendar
import io.github.mirilumi.mcp.booking.clients.availabilities.model.MonthDate
import io.github.mirilumi.mcp.booking.clients.availabilities.model.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.ResponseEntity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.client.ResourceAccessException
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
class AvailabilitiesToolsTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var validDates: ValidDatesControllerApi

    private lateinit var mcp: McpTestClient

    @BeforeEach
    fun setUp() {
        mcp = McpTestClient(mockMvc)
        mcp.initialize()
    }

    @Test
    fun `tool is listed with its inputs and read-only hint`() {
        val tool = mcp.listTools().single { it.path("name").asString() == TOOL }

        val properties = tool.path("inputSchema").path("properties")
        assertTrue(properties.has("origin") && properties.has("destination")) { "tool was $tool" }
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean()) { "tool was $tool" }
        assertTrue(tool.path("annotations").path("idempotentHint").asBoolean()) { "tool was $tool" }
    }

    @Test
    fun `calendar is flattened into sorted dates with prices`() {
        `when`(validDates.getValidDatesWithPrices("SKP", "FCO")).thenReturn(
            ResponseEntity.ok(
                InValidDateResponse(
                    result = Result(isSuccess = true),
                    calendar = listOf(
                        InvalidDatesCalendar(
                            year = 2026, currency = "EUR",
                            month = listOf(
                                MonthDate(month = 12, days = listOf(DayPrice(3, BigDecimal("59.00")))),
                                MonthDate(month = 11, days = listOf(DayPrice(5, BigDecimal("49.99"))))
                            )
                        )
                    )
                )
            )
        )

        val result = mcp.callToolForJson(TOOL, mapOf("origin" to " skp ", "destination" to "fco"))

        verify(validDates).getValidDatesWithPrices("SKP", "FCO")
        assertEquals("SKP", result.path("origin").asString())
        assertEquals("EUR", result.path("currency").asString())
        val dates = result.path("dates")
        assertEquals("2026-11-05", dates[0].path("date").asString())
        assertEquals(0, BigDecimal("49.99").compareTo(dates[0].path("price").decimalValue()))
        assertEquals("2026-12-03", dates[1].path("date").asString())
    }

    @Test
    fun `unreachable service is reported as a tool error`() {
        `when`(validDates.getValidDatesWithPrices("SKP", "FCO")).thenThrow(ResourceAccessException("connection refused"))

        val result = mcp.callTool(TOOL, mapOf("origin" to "SKP", "destination" to "FCO"))

        assertTrue(result.path("isError").asBoolean()) { "result was $result" }
        assertTrue(result.toString().contains("could not be reached")) { "result was $result" }
    }

    @Test
    fun `unsuccessful result is reported as a tool error`() {
        `when`(validDates.getValidDatesWithPrices("SKP", "XXX")).thenReturn(
            ResponseEntity.ok(InValidDateResponse(result = Result(isSuccess = false, msgs = "route not served"), calendar = emptyList()))
        )

        val result = mcp.callTool(TOOL, mapOf("origin" to "SKP", "destination" to "XXX"))

        assertTrue(result.path("isError").asBoolean()) { "result was $result" }
        assertTrue(result.toString().contains("route not served")) { "result was $result" }
    }

    companion object {
        const val TOOL = "availabilities_get_valid_dates_with_prices"
    }
}
