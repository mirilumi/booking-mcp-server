---
name: add-mcp-tool
description: Create a new MCP tool in booking-mcp-server from a filled-in tool spec (docs/specs/<tool>.md), with its test. Use when the user asks to add, create or implement an MCP tool.
argument-hint: <path to tool spec, e.g. docs/specs/search_flights.md>
---

# Add an MCP tool

Spec: $ARGUMENTS

## 1. Read and check the spec
- Read the spec file. If no path was given, list `docs/specs/*.md` (excluding the templates) and ask which one.
- Read `CLAUDE.md` for the tool conventions; they are requirements, not suggestions.
- Check the spec is complete enough to implement: name, description, every input with a format, output, behaviour,
  kind, at least one error case and an example. If something is missing or contradicts the conventions
  (e.g. a price or card number as input), stop and ask the user. Do not invent business rules.
- If the tool calls a downstream service, the client must exist in `clients/`. If it does not, tell the user
  to write a client spec and run `/add-service-client` first.

## 2. Write the tool
- Put it in `src/main/kotlin/io/github/mirilumi/mcp/booking/tools/`, in the existing class for that
  group if there is one, otherwise a new `<Group>Tools` `@Component`. Follow `EchoTools.kt` as the pattern.
- `@McpTool(name = ..., description = ..., annotations = McpTool.McpAnnotations(...))` with the hints from "Kind".
- One `@McpToolParam(description = ...)` per input; the description includes the format and an example.
  Optional inputs: nullable Kotlin type and `required = false`.
- Return a data class nested in the tools class (or in `tools/model/` if shared) with exactly the spec's output fields.
- Normalise inputs as the spec says. Map downstream errors to the messages in the spec's error table by
  throwing an exception with that message; the AI sees it as a tool error.

## 3. Write the test
- `src/test/kotlin/io/github/mirilumi/mcp/booking/tools/<Group>ToolsTest.kt`, following `EchoToolsTest.kt`:
  `@SpringBootTest` + `@AutoConfigureMockMvc`, `McpTestClient`, `initialize()` in `@BeforeEach`.
- Cover: the tool is listed with every input in its schema and the right annotations; the spec's example
  as the happy path; each error row in the spec.
- Stub downstream services (`@MockitoBean` on the client interface). Tests must never call real services.

## 4. Verify
- Run `./gradlew test --tests "*<Group>ToolsTest"`, then `./gradlew build`. Fix until both pass.
- If `echo` is still the only other tool and this is the first real tool, ask whether to delete `EchoTools` and its test.

## 5. Report
Tell the user: the tool name, the files created/changed, the test results, and anything in the spec you had
to interpret. Do not commit unless asked.
