package io.github.mirilumi.mcp.booking

import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Talks MCP (JSON-RPC over Streamable HTTP) to the app's /mcp endpoint through MockMvc,
 * the same way a real client such as Claude does. Use it from tool tests.
 */
class McpTestClient(
    private val mockMvc: MockMvc,
    private val customize: (MockHttpServletRequestBuilder) -> Unit = {}
) {
    private val json = JsonMapper.builder().build()
    private val ids = AtomicInteger()

    fun initialize(): JsonNode = request(
        "initialize",
        """{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}"""
    )

    /** All tools the server exposes (the `tools` array of `tools/list`). */
    fun listTools(): JsonNode = request("tools/list").path("tools")

    /** Calls a tool; returns the `result` object (`content`, `structuredContent`, `isError`). */
    fun callTool(name: String, arguments: Map<String, Any?>): JsonNode =
        request("tools/call", json.writeValueAsString(mapOf("name" to name, "arguments" to arguments)))

    /** The tool's JSON output, parsed from the first text content block. */
    fun callToolForJson(name: String, arguments: Map<String, Any?>): JsonNode {
        val result = callTool(name, arguments)
        assertTrue(!result.path("isError").asBoolean(false)) { "tool $name returned an error: $result" }
        return json.readTree(result.path("content")[0].path("text").asString())
    }

    fun request(method: String, params: String? = null): JsonNode {
        val body = buildString {
            append("""{"jsonrpc":"2.0","id":${ids.incrementAndGet()},"method":"$method"""")
            if (params != null) append(""","params":$params""")
            append("}")
        }
        val response = send(body)
        assertTrue(response.path("error").isMissingNode) { "$method failed: $response" }
        return response.path("result")
    }

    private fun send(body: String): JsonNode {
        val request = post("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .content(body)
        customize(request)

        var result = mockMvc.perform(request).andReturn()
        if (result.request.isAsyncStarted) {
            result = mockMvc.perform(asyncDispatch(result)).andReturn()
        }
        val content = result.response.contentAsString
        assertTrue(result.response.status in 200..299) { "MCP call failed with ${result.response.status}: $content" }

        // Responses come back as plain JSON or as Server-Sent Events ("data: {...}")
        val payload = content.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .lastOrNull() ?: content
        return json.readTree(payload)
    }
}
