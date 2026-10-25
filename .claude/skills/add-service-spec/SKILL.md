---
name: add-service-spec
description: Add or refresh a downstream service's OpenAPI spec in booking-mcp-server (specs/<service>/spec.json) so Kotlin models and @HttpExchange interfaces are generated for it. Use when the user wants to add a new service to the MCP server, generate models from a Swagger/OpenAPI spec, or update an existing spec.
argument-hint: <service-name> <path or URL to the OpenAPI JSON/YAML>
---

# Add a service spec

Arguments: $ARGUMENTS

Read `specs/README.md` first: it defines the folder layout and naming. The build needs no changes; every
`specs/<service>/spec.json` is picked up automatically.

## 1. Work out the service name and source
- Service name: lowercase, hyphenated, matching the real service (`availabilities`, `booking-cart`). If the user gave
  a different form (`AvailabilityService`, `availabilities-service`), propose the folder name and confirm it.
  Drop a `-service` suffix.
- Source: a local file path, or a URL to `/v3/api-docs`. If neither was given, ask. Never invent a spec.
- A URL usually needs a bearer token. Ask the user to run the download themselves, or to provide the token for this
  one call; never write a token into any file.

## 2. Get the spec into place
- Target: `specs/<service>/spec.json` (YAML: `spec.yaml`, but prefer JSON).
- Copy or download it there. If the folder already has a spec, this is a refresh: say so, and summarise what changed
  (paths and schemas added or removed) by comparing old and new before overwriting.
- Validate before going on: it must be OpenAPI 3 (`"openapi": "3.x"`) with `info` and `paths`. A file that is an error
  response (e.g. `{"errorCode": ..., "message": ...}`, "No static resource v3/api-docs") is not a spec: stop and tell
  the user, it usually means springdoc is not active on that service or the token is missing.
- Confirm it is gitignored: `git check-ignore -v specs/<service>/spec.json` must match. Specs of private services
  must never be committed (this repo is public).

## 3. Write the service README
- `specs/<service>/README.md` (committed), following `specs/availabilities/README.md`: one-line purpose, where the spec
  comes from, the generated task and packages, the generated interface names, the auth it needs, a refresh command
  with `<base-url>` and `$TOKEN` placeholders. No real hosts, client ids, tokens or company names.
- Add the service to the "Services" table in `specs/README.md`.

## 4. Generate and check
- Run `./gradlew generate<Service>Client compileKotlin` (task name: service name in PascalCase, e.g.
  `booking-cart` -> `generateBookingCartClient`).
- List what was generated under `build/generated/openapi/<service>/src/main/kotlin/.../clients/<package>/`:
  the `api/` interfaces and the number of `model/` classes.
- Look for weak spots in the spec that make poor Kotlin and report them: properties typed `kotlin.Any`,
  untyped `Map`s, interfaces named `ApiApi`/`DefaultApi` (missing `@Tag` on controllers), operations without a
  summary. These are fixed in the source service (types, `@Schema`, `@Tag`, `@Operation`), not here.
- Check date-time handling: the default mapping for OpenAPI `date-time` is `OffsetDateTime`, which fails on values
  without an offset. Spring services using `LocalDateTime` send exactly that (`"2026-11-05T06:00:00"`). Ask the user or
  read the service's DTOs; if so, add `specs/<service>/type-mappings.properties` with
  `DateTime=java.time.LocalDateTime` (committed) and regenerate. Also note query parameters documented as `date-time`
  that the service actually parses as a plain date.
- Run `./gradlew build`; it must pass.

## 5. Report
Service folder, generated interfaces, model count, spec weak spots, and the next step: to call the service,
register the generated interfaces as clients with `/add-service-client` (base URL, timeouts, auth). Do not commit
unless asked.
