# Tool spec: <tool_name>

<!--
Copy this file to docs/specs/<tool_name>.md, fill it in, then run:
  /add-mcp-tool docs/specs/<tool_name>.md
Delete the hints (the HTML comments) as you go. Leave a section as "none" rather than deleting it.
-->

## Name
<!-- snake_case, verb first. e.g. search_flights, add_passengers, get_booking_summary -->

## Description for the AI
<!-- 2-4 sentences the AI reads to decide when to call this tool.
     Say what it does, when to use it, what it returns, units/currency, and what to call next. -->

## Inputs
<!-- One row per parameter. Format = how the value must look (the AI sees this). -->
| Name | Type | Required | Format / example | Notes |
|------|------|----------|------------------|-------|
|      |      | yes      |                  |       |

## Output
<!-- The fields the AI gets back. Keep only what it needs to answer the user or call the next tool. -->
```json
{
}
```

## Behaviour
<!-- What the tool does, step by step. Which service/endpoint it calls (see docs/specs/*-client.md).
     Any input normalisation (trim, uppercase). Any server-side state it reads or writes (and its key). -->
1.

## Kind
<!-- Tick one or more. -->
- [ ] Read-only lookup (readOnlyHint)
- [ ] Changes a booking or moves money (destructiveHint) - the AI must show the user a summary first
- [ ] Safe to call twice with the same input (idempotentHint)

## Errors
<!-- What can go wrong and what the AI should be told, e.g. "route not served -> say no flights on this route". -->
| Situation | Message to the AI |
|-----------|-------------------|
|           |                   |

## Example
<!-- A realistic call and result. Used to write the test. -->
Input:
```json
{
}
```
Output:
```json
{
}
```

## Out of scope
<!-- Anything this tool must NOT do. "none" is fine. -->
none
