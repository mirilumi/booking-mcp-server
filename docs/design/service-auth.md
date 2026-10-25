# Design: authenticating calls to downstream services

Status: implemented (`clients/auth/ServiceAuthConfig.kt`, `security/SecurityConfig.kt`, `ServiceAuthTest`)

## Problem
Downstream services require a bearer JWT. The MCP server needs its own token to call them; there is no user
behind most calls (search, availability), so the user's identity cannot be used.

## Decision
Use the OAuth2 **client credentials** grant via Spring Security's OAuth2 client. The MCP server authenticates
as itself; Spring fetches, caches and renews the token. No token code of our own.

```
booking-mcp-server ──POST {AUTH_BASE_URL}/oauth2/token──▶ auth server
                   ◀── access_token (expires_in ~12h)
                   ──GET /api/... Authorization: Bearer <token>──▶ downstream service
```

## Token request
`POST {AUTH_BASE_URL}/oauth2/token`, `application/x-www-form-urlencoded`:

| Field | Value |
|---|---|
| `grant_type` | `client_credentials` |
| `client_id` | `AUTH_CLIENT_ID` |
| `client_secret` | `AUTH_CLIENT_SECRET` (in the body: `client_secret_post`) |

No `scope` is sent. From the response only `access_token`, `token_type` and `expires_in` are used; other fields are ignored.

## Configuration
All values come from environment variables; none are committed.

```yaml
spring.security.oauth2.client:
  registration.backend:
    client-id: ${AUTH_CLIENT_ID}
    client-secret: ${AUTH_CLIENT_SECRET}
    client-authentication-method: client_secret_post
    authorization-grant-type: client_credentials
  provider.backend:
    token-uri: ${AUTH_BASE_URL}/oauth2/token
```

## Using it from a client
A client opts in with `@ClientRegistrationId("backend")` on its `@HttpExchange` interface (or a single method).
An `OAuth2RestClientHttpServiceGroupConfigurer` bean adds the interceptor that attaches the token.
Clients without the annotation send no token.

```kotlin
@ClientRegistrationId("backend")
@HttpExchange("/api")
interface InventoryClient { ... }
```

## Behaviour
- The token is cached in memory and reused until shortly before `expires_in`, then fetched again.
- Each pod has its own token (fine: tokens are cheap and long-lived).
- If the auth server is down, the tool call fails with a clear error; nothing is retried in a loop.
- Tokens and the client secret are never logged.
- Startup fails with a message naming the missing variable if any `AUTH_*` value is unset.

## Security of `/mcp`
Adding Spring Security secures every endpoint by default. A `SecurityFilterChain` keeps `/mcp` and
`/actuator/health` open for now, as today. OAuth on `/mcp` itself is separate work (see the README roadmap).

## Tests
Stub token server and stub downstream service (`com.sun.net.httpserver.HttpServer`), no real services:
- token request matches the table above (method, path, form fields, no Basic header)
- one token fetch for several downstream calls
- a new token is fetched after the cached one expires
- downstream request carries `Authorization: Bearer <token>`; clients without the annotation carry none
- `/mcp` is still reachable without a token

## Out of scope
- Acting on behalf of a user (forwarding or exchanging the user's token): needed later for user-owned bookings.
- OAuth on the `/mcp` endpoint.

## Open questions
- Does the auth server require any extra header on the token request (e.g. platform or device)? If so, add it
  with a customised token-response client.
- Production should use a dedicated client for this server rather than a shared app client.
