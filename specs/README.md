# specs

One folder per downstream service. The build generates Kotlin models and `@HttpExchange` interfaces from each spec.

```
specs/
  <service>/
    spec.json                  OpenAPI 3 spec (gitignored for private services)
    README.md                  where the spec comes from, base URL, auth (committed)
    type-mappings.properties   optional generator type overrides (committed)
```

| From `specs/<service>/spec.json` | You get |
|---|---|
| Gradle task | `generate<Service>Client` (runs automatically before `compileKotlin`) |
| Output | `build/generated/openapi/<service>/` (never committed) |
| Packages | `io.github.mirilumi.mcp.booking.clients.<service>.api` (one interface per controller tag) and `.model` |

- Service folder names are lowercase, hyphenated, matching the service (e.g. `availabilities`, `booking-cart`).
  Hyphens are dropped in the package name (`booking-cart` -> `...clients.bookingcart`).
- `spec.json` / `spec.yaml` are gitignored: a fresh clone has no specs, generates nothing for them and still builds.
- To refresh a spec, download it again over `spec.json`; the next build regenerates the code.
- `type-mappings.properties` overrides generated types, e.g. `DateTime=java.time.LocalDateTime` for a service whose
  timestamps have no offset (`"2026-11-05T06:00:00"`); the default `OffsetDateTime` cannot parse those.
- Add a service with the Claude Code skill `/add-service-spec <service> <path-or-url>`, then register its clients
  with `/add-service-client <service>` (code in `clients/<service>/`).
- A service folder without `spec.json` makes the build leave out `clients/<service>/` and `tools/<service>/`, so the project still compiles.

## Services

| Service | Folder |
|---|---|
| availabilities | [`availabilities/`](availabilities/README.md) |
