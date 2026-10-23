# booking-mcp-server

A [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server that exposes a travel booking flow as tools
AI assistants such as Claude and ChatGPT can call: search, select an offer, add passengers and extras, get a
payment link, and check booking status.

The server is an orchestration layer. It holds no booking logic of its own: tools call downstream booking or
inventory services through typed HTTP clients, so the same tools can sit in front of different backends.

> **Status:** early. The project skeleton, MCP transport, testing harness and development workflow are in place,
> with an example `echo` tool. Booking tools are being added one at a time from specs (see [Roadmap](#roadmap)).

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.4 on Java 25 |
| Framework | Spring Boot 4.2, Spring AI 2.1 MCP server (WebMVC) |
| MCP transport | Streamable HTTP on `/mcp`, stateless (no server-side MCP sessions, scales horizontally) |
| Downstream calls | Spring `@HttpExchange` declarative HTTP clients |
| Build | Gradle 9 (Kotlin DSL), Spring Boot + Spring AI BOMs via Gradle platforms |
| Tests | JUnit 6, MockMvc, a small MCP JSON-RPC test client |

## Running it

Requires JDK 25 (Gradle downloads it automatically if missing).

```bash
./gradlew bootRun
```

The MCP endpoint is `http://localhost:9010/mcp`.

Try it with curl:

```bash
curl -s http://localhost:9010/mcp \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello"}}}'
```

### Connecting an AI client

- **Claude Code:** `claude mcp add --transport http booking http://localhost:9010/mcp`
- **MCP Inspector:** `npx @modelcontextprotocol/inspector`, transport *Streamable HTTP*, URL `http://localhost:9010/mcp`

## Design

- **Tools describe themselves for the AI.** Each tool has a description written for the model (what it does, when
  to use it, what it returns) and parameter descriptions that state the expected format.
- **Behaviour hints.** Lookups are marked read-only; anything that changes a booking is marked destructive so the
  client asks the user to confirm.
- **The server owns prices and state.** Tools take keys (`offerKey`, `cartId`), never amounts. State that spans
  several calls lives server-side, keyed by an id the tool returns, not in the MCP session.
- **No payment data through the model.** Payment happens on a hosted payment page; a tool only returns the link.
- **Every tool is tested over MCP.** Tests call `/mcp` with real JSON-RPC requests (`initialize`, `tools/list`,
  `tools/call`) and stub downstream services.

## Project layout

```
src/main/kotlin/io/github/mirilumi/mcp/booking/
  tools/      MCP tools (@McpTool), one class per tool group
  clients/    @HttpExchange clients for downstream services
src/test/kotlin/.../McpTestClient.kt   MCP JSON-RPC client used by tool tests
docs/specs/                           Specs for tools and clients (templates included)
.claude/skills/                       Claude Code skills that implement a tool or client from a spec
```

## Development workflow

New tools and clients start as a short spec written from a template in [`docs/specs/`](docs/specs). With
[Claude Code](https://claude.com/claude-code), the project skills turn a spec into code and tests:

```
/add-service-client docs/specs/<service>-client.md
/add-mcp-tool docs/specs/<tool>.md
```

[`CLAUDE.md`](CLAUDE.md) holds the conventions both humans and the assistant follow.

```bash
./gradlew build   # compile + all tests
```

## Roadmap

- [x] MCP server skeleton (Streamable HTTP, stateless), example tool, MCP test harness
- [x] Spec templates and Claude Code skills for tools and clients
- [ ] `search_flights` - search offers for a route and dates
- [ ] `start_booking`, `add_passengers`, `add_extras` - cart steps with server-side state
- [ ] `get_booking_summary` - priced summary to confirm with the user
- [ ] `create_payment_link`, `get_booking_status` - hosted payment and confirmation
- [ ] OAuth 2.1 authorization for the MCP endpoint

## License

[MIT](LICENSE)
