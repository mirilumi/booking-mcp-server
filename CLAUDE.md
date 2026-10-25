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
- One test class: `./gradlew test --tests "*AvailabilitiesToolsTest"`
- Run locally: `./gradlew bootRun` (port 9010, MCP endpoint `http://localhost:9010/mcp`).
  Needs `AUTH_BASE_URL`, `AUTH_CLIENT_ID`, `AUTH_CLIENT_SECRET` in the environment; never write their values into files.

## Layout
- `tools/<service>/` - the tools backed by one downstream service, in a `<Service>Tools` `@Component`
  (see `tools/availabilities/`); `tools/` itself only for tools that use no downstream service
- `clients/<service>/` - one package per downstream service, named like `specs/<service>/`: its
  `<Service>ClientConfig` registers the clients (see `clients/availabilities/` and the `add-service-client` skill).
- Code that uses generated classes stays in `clients/<service>/` or `tools/<service>/`: the build leaves both out
  when `specs/<service>/spec.json` is missing, so this public repo still compiles without private specs.
- `clients/auth/` - `ServiceAuthConfig`: service token for downstream calls (`docs/design/service-auth.md`)
- `security/SecurityConfig` - keeps `/mcp` and health open, denies everything else
- `src/test/.../McpTestClient.kt` - speaks MCP to `/mcp` through MockMvc; every tool test uses it
- `src/test/resources/config/application.yml` - dummy `AUTH_*` values so tests start; layered on top of the main config
- `specs/<service>/spec.json` - OpenAPI spec per downstream service (gitignored); the build generates
  `...clients.<service>.api` / `.model` from it into `build/generated/openapi/<service>` (see `specs/README.md`)
- `docs/specs/` - specs for tools and clients, written from the templates there
- `docs/design/` - design decisions (e.g. `service-auth.md`)

## Conventions for tools
- Tool names: `snake_case`, `<service>_<verb>_<thing>`, starting with the service the tool calls
  (`availabilities_get_valid_dates_with_prices`, `booking_cart_add_passengers`; hyphens become underscores).
  Tools that call no downstream service have no prefix.
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
- `/add-service-spec <service> <path-or-url>` - adds or refreshes `specs/<service>/spec.json` and generates its models/clients
- `/add-service-client <service> [docs/specs/<service>-client.md]` - adds `clients/<service>/`: registers the generated (or hand-written) clients with config, auth and a stub test
