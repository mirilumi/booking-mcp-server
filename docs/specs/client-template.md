# Client spec: <service-name>

<!--
Copy this file to docs/specs/<service-name>-client.md, fill it in, then run:
  /add-service-client docs/specs/<service-name>-client.md
-->

## Service
<!-- The downstream service this client calls, e.g. inventory-service -->

## Base URL
<!-- Per environment. Inside Kubernetes use the service DNS name, not the public ingress. -->
| Environment | Base URL |
|-------------|----------|
| local       | http://localhost:9000 |
| test (k8s)  | http://<service>:<port> |

## Authentication
<!-- Tick one. -->
- [ ] Forward the caller's `Authorization` header (calls made on behalf of the user)
- [ ] Service-to-service token (client credentials) - say where it comes from
- [ ] None

## Timeouts
<!-- Defaults are fine for most services. -->
- Connect: 2s
- Read: 10s

## Endpoints
<!-- One block per endpoint this MCP server needs (not every endpoint the service has).
     Copy the method signature from the service's controller if you can. -->

### <operationName>
- Method + path: `GET /api/...`
- Path variables:
- Query parameters:
- Request body: <!-- JSON example or "none" -->
- Response: <!-- JSON example; mark the fields the tools actually use -->
- Errors: <!-- status codes and what they mean -->
- Idempotent / safe to retry: yes / no
