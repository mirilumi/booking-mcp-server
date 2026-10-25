# Tool spec: availabilities_search_flights

## Name
availabilities_search_flights

## Description for the AI
List all bookable one-way flights for a route on a specific date, each with its fares and a detailed price breakdown
for the whole group of passengers, so the user can choose. Use it after `availabilities_get_valid_dates_with_prices`
when the user has chosen a date, or when they ask which flights fly on a given day. Flights are sorted by departure,
fares by price; only fares with enough seats for the group are listed. Prices are in the returned currency; `total`
is for all passengers.

## Inputs
| Name | Type | Required | Format / example | Notes |
|------|------|----------|------------------|-------|
| origin | string | yes | IATA airport code, e.g. SKP | trimmed and uppercased |
| destination | string | yes | IATA airport code, e.g. FCO | trimmed and uppercased |
| date | string | yes | departure date, ISO `YYYY-MM-DD`, e.g. 2026-11-05 | |
| adults | integer | no | 1 | default 1 |
| children | integer | no | 0 | default 0 |
| infants | integer | no | 0 | default 0 |

## Output
```json
{
  "origin": "SKP", "destination": "FCO", "date": "2026-11-05", "currency": "EUR",
  "passengers": { "adults": 2, "children": 1, "infants": 0 },
  "flights": [
    {
      "departure": "2026-11-05T06:00:00", "arrival": "2026-11-05T07:30:00", "stops": 0,
      "flightNumbers": ["W6 1001"],
      "segments": [ { "origin": "SKP", "destination": "FCO", "departure": "...", "arrival": "...", "flightNumber": "W6 1001" } ],
      "fares": [
        {
          "fare": "BASIC", "seatsLeft": 9, "pricePerAdult": 40.00, "total": 160.00,
          "perPassenger": [
            { "type": "ADT", "count": 2, "basePrice": 40.00, "taxesAndFees": 0, "discount": 0, "pricePerPassenger": 40.00, "subtotal": 80.00 },
            { "type": "CHD", "count": 1, "basePrice": 80.00, "taxesAndFees": 0, "discount": 0, "pricePerPassenger": 80.00, "subtotal": 80.00 }
          ]
        }
      ]
    }
  ]
}
```
- `pricePerAdult`: the product's display price (one adult).
- `total`: the price for the whole group; `perPassenger` breaks it down per passenger type.

## Behaviour
1. Normalise origin/destination (trim, uppercase); validate the date and the counts (none negative, at least one
   adult or child).
2. Call the availabilities service: `AvailabilityControllerApi.getAvailability(origin, destination,
   departureDate = date at 00:00, paxTypes/paxCount for the non-zero counts)` (`GET /api/v2/flights/availability`,
   service token). The service reads only the date part of `departureDate`.
3. Filter: each journey keeps only the products the group can book, `availability.units` >= adults + children
   (infants need no seat), with a `displayPrice` and `paxPrices`. Journeys left without a bookable product drop out.
4. Log the filtered journeys as JSON (DEBUG).
5. Map: flights sorted by departure; per flight its fares sorted by `pricePerAdult`; per fare `total` = sum of
   `paxPrices.totalPrice x count` (`totalPrice` is per passenger) and the per-passenger breakdown.

## Kind
- [x] Read-only lookup (readOnlyHint)
- [ ] Changes a booking or moves money (destructiveHint)
- [x] Safe to call twice with the same input (idempotentHint)

## Errors
| Situation | Message to the AI |
|-----------|-------------------|
| invalid counts | "Passenger counts must not be negative and include at least one adult or child" |
| invalid date | "Date must be in YYYY-MM-DD format" |
| no outbound journeys | "No flights from ORIGIN to DESTINATION on DATE" |
| journeys exist but no fare has enough seats | "No seats available from ORIGIN to DESTINATION on DATE for N passengers" |
| availabilities service unreachable or returns an error | "The availabilities service could not be reached" |

## Example
Input:
```json
{ "origin": "skp", "destination": "FCO", "date": "2026-11-05", "adults": 2, "children": 1 }
```
Output: as above, with every bookable flight and fare of that day.

## Out of scope
Choosing for the user, return flights (`returnDate`), promo codes, club fares, holding or booking the flight.
