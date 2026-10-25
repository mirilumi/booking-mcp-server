# availabilities

Flight availability, valid dates and low fares.

| | |
|---|---|
| Spec | `spec.json` (gitignored), OpenAPI 3.0 |
| Get it from | `GET /v3/api-docs` on the running service; needs a bearer token |
| Generated task | `generateAvailabilitiesClient` |
| Packages | `...clients.availabilities.api`, `...clients.availabilities.model` |
| Interfaces | `AvailabilityControllerApi`, `LowFareControllerApi`, `ValidDatesControllerApi` |
| Auth | service token (`backend` registration) |
| Client config | `clients/availabilities/AvailabilitiesClientConfig.kt` (group `availabilities`) |
| Base URL | `AVAILABILITIES_BASE_URL`, default `http://localhost:9008` |
| Type mappings | `DateTime=java.time.LocalDateTime` (the service sends timestamps without an offset) |
| Tools | `availabilities_get_valid_dates_with_prices`, `availabilities_search_flights` |

Known spec quirks:
- `departureDate` is documented as `date-time`, but the service parses it as `yyyy-MM-dd` and reads only the date
  part, so the generated client's `2026-11-05T00:00:00` works. Better fixed at the source (document it as `date`).

Refresh:

```bash
curl -s -H "Authorization: Bearer $TOKEN" <base-url>/v3/api-docs -o specs/availabilities/spec.json
```
