---
name: add-service-client
description: Add an HTTP client for a downstream service in booking-mcp-server under clients/<service>/ - registering the interfaces generated from specs/<service>/spec.json, or hand-written ones from a client spec (docs/specs/<service>-client.md). Use when the user wants the MCP server to call another backend service.
argument-hint: <service-name> [path to client spec, e.g. docs/specs/inventory-client.md]
---

# Add a downstream service client

Arguments: $ARGUMENTS

Every service lives under the same name in three places (see `specs/README.md`):

| | Path / package |
|---|---|
| spec | `specs/<service>/spec.json` (gitignored) |
| generated code | `...clients.<pkg>.api` / `.model` (from the build, never edited) |
| this skill's code | `src/main/kotlin/.../clients/<pkg>/`, tests in `src/test/kotlin/.../clients/<pkg>/` |

`<service>` is the lowercase hyphenated folder name; `<pkg>` is the same without hyphens (`booking-cart` -> `bookingcart`).
`clients/auth/` holds the shared token setup (`ServiceAuthConfig`); it is not a downstream service.
Follow `clients/availabilities/` as the reference implementation.

## 1. Decide: generated or hand-written
- `specs/<service>/spec.json` exists -> **generated**: do not write interfaces or DTOs. Look at
  `build/generated/openapi/<service>/src/main/kotlin/.../api/` (run `./gradlew generate<Service>Client` if missing)
  and pick the interfaces the tools need.
- No spec but the service has springdoc -> suggest `/add-service-spec` first; generated is the default.
- Otherwise -> **hand-written** from a client spec (`docs/specs/<service>-client.md`, from `docs/specs/client-template.md`).
  Check every endpoint has method, path, parameters, request and response; read the service's controller if the
  user has the source. Ask about anything missing.

## 2. Write the client (`clients/<pkg>/`)
- `<Service>ClientConfig.kt` (`@Configuration`), like `AvailabilitiesClientConfig`:
  - `@ImportHttpServices(group = <Service>ClientConfig.GROUP, types = [...])` with the generated (or hand-written)
    interfaces; `GROUP = "<service>"`.
- Hand-written only: `<Service>Client.kt` with `@HttpExchange` (common prefix) and one `@GetExchange` /
  `@PostExchange` / ... per endpoint, mirroring the service's controller; DTOs in `clients/<pkg>/model/` as data classes
  with only the fields the tools use (or `tools.jackson.databind.JsonNode` for pass-through responses).

## 3. Configure it
- `application.yml`, under `spring.http.serviceclient`: a `<service>` group with
  `base-url: ${<SERVICE>_BASE_URL:<local URL>}` (local default, env var override, no real hosts committed),
  `connect-timeout: 2s`, `read-timeout: 10s`.
- Auth "service token":
  - generated interfaces can't carry `@ClientRegistrationId`, so add the group bean from `AvailabilitiesClientConfig`
    (`RestClientHttpServiceGroupConfigurer` setting `clientRegistrationId(ServiceAuthConfig.REGISTRATION_ID)` as a
    default request attribute for this group);
  - hand-written interfaces may instead use `@ClientRegistrationId("backend")` on the interface.
  - A different auth server: stop and ask; it needs its own registration in `spring.security.oauth2.client`.
- Auth "forward the caller's Authorization header": a `RestClientHttpServiceGroupConfigurer` for this group adding an
  interceptor that copies `Authorization` from the current request (`RequestContextHolder`).
- Auth "none": nothing.

## 4. Test it (`src/test/kotlin/.../clients/<pkg>/<Service>ClientTest.kt`)
- Like `AvailabilitiesClientTest`: one `com.sun.net.httpserver.HttpServer` stub on a random port serving
  `/oauth2/token` (service token) and the service's paths; `@DynamicPropertySource` sets `AUTH_BASE_URL` and
  `spring.http.serviceclient.<service>.base-url` to it.
- For each interface method the tools use: assert the request (method, path, query, body, `Authorization: Bearer ...`)
  and the parsed response.
- Run `./gradlew build` and fix until it passes.
- For generated clients also check the public-repo case: move `specs/<service>/spec.json` aside, run
  `./gradlew clean build` (it must pass and log "No spec for <service>: skipping their clients"), then put it back.
  This works because the build leaves out `clients/<pkg>/` when the spec is missing, so keep all code that touches
  generated classes inside `clients/<pkg>/`.

## 5. Report
Service, interfaces registered, auth, config keys, files created/changed and test results. Do not commit unless asked.
