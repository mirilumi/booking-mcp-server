---
name: add-mcp-tool
description: Create a new MCP tool in booking-mcp-server from a filled-in tool spec (docs/specs/<tool>.md), with its test. Use when the user asks to add, create or implement an MCP tool.
argument-hint: <path to tool spec, e.g. docs/specs/availabilities_get_valid_dates_with_prices.md>
---

# Add an MCP tool

Spec: $ARGUMENTS

## 1. Read and check the spec
- Read the spec file. If no path was given, list `docs/specs/*.md` (excluding the templates) and ask which one.
- Read `CLAUDE.md` for the tool conventions; they are requirements, not suggestions.
- Check the spec is complete enough to implement: name, description, every input with a format, output, behaviour,
  kind, at least one error case and an example. If something is missing or contradicts the conventions
  (e.g. a price or card number as input), stop and ask the user. Do not invent business rules.
- The name must start with the service it calls: `<service>_<verb>_<thing>` (hyphens -> underscores), e.g.
  `availabilities_get_valid_dates_with_prices`. Tools that call no downstream service have no prefix. Fix the spec
  name with the user if it does not follow this.
- If the tool calls a downstream service, its client must exist in `clients/<pkg>/` (`<pkg>` = service name without
  hyphens). If it does not, tell the user to run `/add-service-spec` and `/add-service-client` first.

## 2. Write the tool
- Service-backed tools go in `src/main/kotlin/io/github/mirilumi/mcp/booking/tools/<pkg>/`, in that service's
  `<Service>Tools` `@Component` (create it if missing). Follow `tools/availabilities/AvailabilitiesTools.kt`.
  Only tools that use no downstream service go directly in `tools/`.
  Keeping service code in `tools/<pkg>/` matters: the build leaves it out when the service's spec is missing.
- `@McpTool(name = ..., description = ..., annotations = McpTool.McpAnnotations(...))` with the hints from "Kind".
- One `@McpToolParam(description = ...)` per input; the description includes the format and an example.
  Optional inputs: nullable Kotlin type and `required = false`.
- Inject the client interface (generated `...clients.<pkg>.api.*Api` or hand-written) and map its response to a
  small data class nested in the tools class with exactly the spec's output fields. Never return the downstream model.
- Normalise inputs as the spec says. Map downstream errors to the messages in the spec's error table by throwing an
  exception with that message (catch `RestClientException` for "service unreachable"); the AI sees it as a tool error.

## 3. Write the test
- `src/test/kotlin/io/github/mirilumi/mcp/booking/tools/<pkg>/<Service>ToolsTest.kt`, following
  `AvailabilitiesToolsTest.kt`: `@SpringBootTest` + `@AutoConfigureMockMvc`, `McpTestClient`, `initialize()` in `@BeforeEach`.
- Cover: the tool is listed with every input in its schema and the right annotations; the spec's example as the
  happy path (including input normalisation); each error row in the spec (`isError` true, message present).
- Stub the client with `@MockitoBean` on the interface. Tests must never call real services.

## 4. Verify
- Run `./gradlew test --tests "*<Service>ToolsTest"`, then `./gradlew build`. Fix until both pass.
- For service-backed tools, check the public-repo case: move `specs/<service>/spec.json` aside, `./gradlew clean build`
  must pass and log "No spec for <service>: skipping their clients and tools", then put the spec back.

## 5. Report
Tell the user: the tool name, the files created/changed, the test results, and anything in the spec you had
to interpret. Do not commit unless asked.
