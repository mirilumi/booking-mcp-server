# booking-mcp-server

General-purpose MCP server that exposes a booking flow (search, select, passengers, extras, payment link, status)
as tools for AI clients such as Claude and ChatGPT. It is not tied to any one company or project: it orchestrates
downstream booking/inventory services over HTTP and holds no business logic of its own. Integrations with a
specific project's backend go behind client interfaces in `clients/`, so the tools stay generic.

## Stack
- Kotlin 2.4, Java 25, Gradle 9 (Kotlin DSL, no version catalog: plugin versions live in `build.gradle.kts`,
  library versions come from the Spring Boot and Spring AI BOMs via `platform(...)`)
- Spring Boot 4.2 (RC), Spring AI 2.1 (RC) MCP server, WebMVC, Jackson 3 (`tools.jackson.*`, not `com.fasterxml.jackson.*`)
- MCP transport: Streamable HTTP on `/mcp`, `protocol: STATELESS` (no MCP session state; scales across pods)

## Commands
- Build + all tests: `./gradlew build`
- One test class: `./gradlew test --tests "*EchoToolsTest"`
- Run locally: `./gradlew bootRun` (port 9010, MCP endpoint `http://localhost:9010/mcp`)

## Layout
- `tools/` - one `@Component` class per tool group, methods annotated `@McpTool`
- `clients/` - one `@HttpExchange` interface per downstream service (see the `add-service-client` skill)
- `src/test/.../McpTestClient.kt` - speaks MCP to `/mcp` through MockMvc; every tool test uses it
- `docs/specs/` - specs for tools and clients, written from the templates there

## Conventions for tools
- Tool names: `snake_case`, verb first (`search_flights`, `get_booking_summary`).
- The `description` is written for the AI: what the tool does, when to use it, what it returns, units and currency.
  Each `@McpToolParam` description says the format (e.g. "IATA airport code, e.g. SKP").
- Set `McpTool.McpAnnotations`: `readOnlyHint = true` for lookups; `destructiveHint = true` for anything that
  changes a booking or moves money; `idempotentHint = true` when calling twice with the same input is safe.
- Return Kotlin data classes (they become the JSON the AI reads). Keep them small: only fields the AI needs.
- Normalise input the AI commonly gets slightly wrong (trim, uppercase codes) before calling downstream.
- State that spans several tool calls lives server-side, keyed by an id the tool returns (e.g. `cartId`), never in the MCP session.
- Never accept prices or totals as tool input: take keys (`offerKey`, `cartId`) and let the server price.
- Never take card or payment details as tool input: payment happens on a hosted payment page via a link.
- Every tool gets a test using `McpTestClient`: listed with the right schema/annotations, happy path, and one error case.
  Downstream services are stubbed in tests; tests never call real services.

## Skills
- `/add-mcp-tool docs/specs/<tool>.md` - creates a tool + test from a filled-in tool spec
- `/add-service-client docs/specs/<client>.md` - creates an `@HttpExchange` client + config from a filled-in client spec
