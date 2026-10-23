---
name: add-service-client
description: Create an @HttpExchange HTTP client for a downstream service in booking-mcp-server from a filled-in client spec (docs/specs/<service>-client.md). Use when the user wants the MCP server to call another backend service.
argument-hint: <path to client spec, e.g. docs/specs/inventory-client.md>
---

# Add a downstream service client

Spec: $ARGUMENTS

## 1. Read and check the spec
- Read the spec file. If no path was given, list `docs/specs/*-client.md` and ask which one.
- Check every endpoint has method, path, parameters, request and response. If the user has the service's source,
  read its controller to confirm paths and types instead of guessing. Ask about anything missing.

## 2. Write the client
- `src/main/kotlin/io/github/mirilumi/mcp/booking/clients/<Service>Client.kt`: an interface with
  `@HttpExchange` (common path prefix) and one `@GetExchange` / `@PostExchange` / ... method per endpoint,
  using `@PathVariable`, `@RequestParam`, `@RequestBody` like the service's controller.
- DTOs in `clients/<service>/` as Kotlin data classes with only the fields the tools use. If a response is
  only passed through to the AI, return `tools.jackson.databind.JsonNode` instead of modelling it.
- Register it in `clients/HttpClientsConfig.kt` (create it if missing):
  `@ImportHttpServices(group = "<service>", types = [<Service>Client::class])`.

## 3. Configure it
- `application.yml`: `spring.http.serviceclient.<service>.base-url` with the local URL from the spec, plus
  `connect-timeout` and `read-timeout`. Add a comment with the Kubernetes URL.
- Auth "forward the caller's Authorization header": add a `RestClientHttpServiceGroupConfigurer` bean in
  `HttpClientsConfig` that, for this group, adds a request interceptor copying `Authorization` from the
  current request (`RequestContextHolder`). Reuse it if it already exists.
- Auth "service-to-service token": stop and ask the user how the token is obtained.

## 4. Test it
- `src/test/kotlin/io/github/mirilumi/mcp/booking/clients/<Service>ClientTest.kt`: start a stub with
  `com.sun.net.httpserver.HttpServer` on a random port, point the base URL at it via `@DynamicPropertySource`,
  call each client method, and assert the request (method, path, query, body, forwarded header) and the parsed response.
- Run `./gradlew build` and fix until it passes.

## 5. Report
List the endpoints covered, files created/changed, and test results. Do not commit unless asked.
