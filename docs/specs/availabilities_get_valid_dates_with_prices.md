# Tool spec: availabilities_get_valid_dates_with_prices

## Name
availabilities_get_valid_dates_with_prices

## Description for the AI
Get the dates with flights for a route over roughly the next 12 months, with the lowest one-adult fare for each date.
Dates without flights are left out. Use it to answer "when can I fly from X to Y" or "which day is cheapest", before
searching flights for a specific date. Prices are in the returned currency.

## Inputs
| Name | Type | Required | Format / example | Notes |
|------|------|----------|------------------|-------|
| origin | string | yes | IATA airport code, e.g. SKP | trimmed and uppercased |
| destination | string | yes | IATA airport code, e.g. FCO | trimmed and uppercased |

## Output
```json
{
  "origin": "SKP",
  "destination": "FCO",
  "currency": "EUR",
  "dates": [ { "date": "2026-11-05", "price": 49.99 } ]
}
```
Dates sorted ascending, one entry per date. Empty `dates` when the route has no flights.

## Behaviour
1. Normalise origin and destination (trim, uppercase).
2. Call the availabilities service: `ValidDatesControllerApi.getValidDatesWithPrices(origin, destination)`
   (`GET /api/v3/validDates/{origin}/{destination}`, service token).
3. Flatten the year -> month -> day calendar into `dates` (ISO date + price); currency from the response.

## Kind
- [x] Read-only lookup (readOnlyHint)
- [ ] Changes a booking or moves money (destructiveHint)
- [x] Safe to call twice with the same input (idempotentHint)

## Errors
| Situation | Message to the AI |
|-----------|-------------------|
| response `result.isSuccess` is false | "Valid dates are not available for ORIGIN-DESTINATION: <msgs>" |
| availabilities service unreachable or returns an error | "The availabilities service could not be reached" |

## Example
Input:
```json
{ "origin": " skp ", "destination": "fco" }
```
Output:
```json
{ "origin": "SKP", "destination": "FCO", "currency": "EUR", "dates": [ { "date": "2026-11-05", "price": 49.99 } ] }
```

## Out of scope
Searching flights for a specific date, booking.
