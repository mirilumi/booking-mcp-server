package io.github.mirilumi.mcp.booking.tools.availabilities

import io.github.mirilumi.mcp.booking.clients.availabilities.api.AvailabilityControllerApi
import io.github.mirilumi.mcp.booking.clients.availabilities.api.ValidDatesControllerApi
import io.github.mirilumi.mcp.booking.clients.availabilities.model.InValidDateResponse
import io.github.mirilumi.mcp.booking.clients.availabilities.model.JourneyDTO
import io.github.mirilumi.mcp.booking.clients.availabilities.model.PaxPrice
import io.github.mirilumi.mcp.booking.clients.availabilities.model.ProductDTO
import org.slf4j.LoggerFactory
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * Tools backed by the availabilities service. Specs: docs/specs/availabilities_get_valid_dates_with_prices.md,
 * docs/specs/availabilities_search_flights.md.
 */
@Component
class AvailabilitiesTools(
    private val validDates: ValidDatesControllerApi,
    private val availability: AvailabilityControllerApi,
    private val jsonMapper: JsonMapper
) {

    companion object {
        private val logger = LoggerFactory.getLogger(AvailabilitiesTools::class.java)
    }

    @McpTool(
        name = "availabilities_get_valid_dates_with_prices",
        description = "Get the dates with flights for a route over roughly the next 12 months, with the lowest " +
            "one-adult fare for each date. Dates without flights are left out. Use it to answer \"when can I fly " +
            "from X to Y\" or \"which day is cheapest\", before searching flights for a specific date. " +
            "Prices are in the returned currency.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getValidDatesWithPrices(
        @McpToolParam(description = "Origin airport IATA code, e.g. SKP")
        origin: String,
        @McpToolParam(description = "Destination airport IATA code, e.g. FCO")
        destination: String
    ): ValidDatesWithPrices {
        val from = origin.trim().uppercase()
        val to = destination.trim().uppercase()

        val response = callService { validDates.getValidDatesWithPrices(from, to).body }

        if (!response.result.isSuccess) {
            throw IllegalStateException("Valid dates are not available for $from-$to: ${response.result.msgs.orEmpty()}")
        }
        return response.toToolResult(from, to)
    }

    @McpTool(
        name = "availabilities_search_flights",
        description = "List all bookable one-way flights for a route on a specific date, each with its fares and a " +
            "detailed price breakdown for the whole group of passengers, so the user can choose. Use it after " +
            "availabilities_get_valid_dates_with_prices when the user has chosen a date, or when they ask which flights " +
            "fly on a given day. Flights are sorted by departure, fares by price; only fares with enough seats for the " +
            "group are listed. Prices are in the returned currency; total is for all passengers.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun searchFlights(
        @McpToolParam(description = "Origin airport IATA code, e.g. SKP")
        origin: String,
        @McpToolParam(description = "Destination airport IATA code, e.g. FCO")
        destination: String,
        @McpToolParam(description = "Departure date, ISO YYYY-MM-DD, e.g. 2026-11-05")
        date: String,
        @McpToolParam(description = "Number of adults (12+ years), default 1", required = false)
        adults: Int?,
        @McpToolParam(description = "Number of children (2-11 years), default 0", required = false)
        children: Int?,
        @McpToolParam(description = "Number of infants (under 2, no own seat), default 0", required = false)
        infants: Int?
    ): FlightSearchResult {
        val from = origin.trim().uppercase()
        val to = destination.trim().uppercase()
        val day = try {
            LocalDate.parse(date.trim())
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Date must be in YYYY-MM-DD format", e)
        }
        val passengers = linkedMapOf("ADT" to (adults ?: 1), "CHD" to (children ?: 0), "INF" to (infants ?: 0))
        if (passengers.values.any { it < 0 } || passengers.getValue("ADT") + passengers.getValue("CHD") < 1) {
            throw IllegalArgumentException("Passenger counts must not be negative and include at least one adult or child")
        }
        val travelling = passengers.filterValues { it > 0 }
        val seatsNeeded = passengers.getValue("ADT") + passengers.getValue("CHD")

        // The service reads only the date part of departureDate
        val response = callService {
            availability.getAvailability(
                origin = from,
                destination = to,
                departureDate = day.atStartOfDay(),
                paxTypes = travelling.keys.toList(),
                paxCount = travelling.values.toList(),
                returnDate = null,
                promoCode = null,
                currency = null,
                clubFares = null
            ).body
        }

        if (response.outbound.isEmpty()) throw IllegalStateException("No flights from $from to $to on $day")
        if (logger.isDebugEnabled) {
            logger.debug("Response for {}-{} on {}: {}", from, to, day, jsonMapper.writeValueAsString(response))
        }
        // 1. filter: each journey keeps only the products the group can book (enough seats, priced);
        //    journeys left without a bookable product drop out
        val bookableJourneys = response.outbound.mapNotNull { journey ->
            journey.products
                .filter { it.availability.units >= seatsNeeded && it.displayPrice != null && it.paxPrices.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
                ?.let { bookable -> journey.copy(products = bookable) }
        }
        if (bookableJourneys.isEmpty()) {
            throw IllegalStateException("No seats available from $from to $to on $day for $seatsNeeded passengers")
        }

        // 2. log the filtered result
        if (logger.isDebugEnabled) {
            logger.debug("Bookable journeys for {}-{} on {}: {}", from, to, day, jsonMapper.writeValueAsString(bookableJourneys))
        }

        // 3. map to the tool result: flights by departure, fares by price
        return FlightSearchResult(
            origin = from,
            destination = to,
            date = day,
            currency = bookableJourneys.first().products.first().paxPrices.first().currency,
            passengers = Passengers(passengers.getValue("ADT"), passengers.getValue("CHD"), passengers.getValue("INF")),
            flights = bookableJourneys.sortedBy { it.departure }.map { it.toFlight() }
        )
    }

    private fun <T : Any> callService(call: () -> T?): T = try {
        call()
    } catch (e: RestClientException) {
        throw IllegalStateException("The availabilities service could not be reached", e)
    } ?: throw IllegalStateException("The availabilities service could not be reached")

    private fun InValidDateResponse.toToolResult(origin: String, destination: String) = ValidDatesWithPrices(
        origin = origin,
        destination = destination,
        currency = calendar.firstOrNull()?.currency,
        dates = calendar.flatMap { year ->
            year.month.flatMap { month ->
                month.days.map { day -> DatePrice(LocalDate.of(year.year, month.month, day.day), day.price) }
            }
        }.sortedBy { it.date }
    )

    /** paxPrices.totalPrice is per passenger; the group pays it count times. */
    private fun List<PaxPrice>.groupTotal(): BigDecimal =
        fold(BigDecimal.ZERO) { sum, pax -> sum + pax.totalPrice * pax.count.toBigDecimal() }

    private fun JourneyDTO.toFlight() = Flight(
        departure = departure,
        arrival = arrival,
        stops = segments.size - 1,
        flightNumbers = segments.map { it.flightInfo.number() },
        segments = segments.map { Segment(it.origin, it.destination, it.departure, it.arrival, it.flightInfo.number()) },
        fares = products.map { it.toFare() }.sortedBy { it.pricePerAdult }
    )

    private fun ProductDTO.toFare() = Fare(
        fare = fares.firstOrNull()?.bookingClass,
        seatsLeft = availability.units,
        pricePerAdult = displayPrice!!.totalPrice,
        total = paxPrices.groupTotal(),
        perPassenger = paxPrices.filter { it.count > 0 }.map {
            PassengerPrice(
                type = it.paxType.value,
                count = it.count,
                basePrice = it.basePrice,
                taxesAndFees = it.taxesAndFees,
                discount = it.discount + it.promoCodeDiscount,
                pricePerPassenger = it.totalPrice,
                subtotal = it.totalPrice * it.count.toBigDecimal()
            )
        }
    )

    private fun io.github.mirilumi.mcp.booking.clients.availabilities.model.FlightInfo.number() =
        "$carrierCode $flightNumber${opSuffix.trim()}"

    data class ValidDatesWithPrices(
        val origin: String,
        val destination: String,
        val currency: String?,
        val dates: List<DatePrice>
    )

    data class DatePrice(val date: LocalDate, val price: BigDecimal)

    data class FlightSearchResult(
        val origin: String,
        val destination: String,
        val date: LocalDate,
        val currency: String,
        val passengers: Passengers,
        val flights: List<Flight>
    )

    data class Passengers(val adults: Int, val children: Int, val infants: Int)

    data class Flight(
        val departure: LocalDateTime,
        val arrival: LocalDateTime,
        val stops: Int,
        val flightNumbers: List<String>,
        val segments: List<Segment>,
        val fares: List<Fare>
    )

    data class Fare(
        val fare: String?,
        val seatsLeft: Int,
        val pricePerAdult: BigDecimal,
        val total: BigDecimal,
        val perPassenger: List<PassengerPrice>
    )

    data class Segment(
        val origin: String,
        val destination: String,
        val departure: LocalDateTime,
        val arrival: LocalDateTime,
        val flightNumber: String
    )

    data class PassengerPrice(
        val type: String,
        val count: Int,
        val basePrice: BigDecimal,
        val taxesAndFees: BigDecimal,
        val discount: BigDecimal,
        val pricePerPassenger: BigDecimal,
        val subtotal: BigDecimal
    )
}
