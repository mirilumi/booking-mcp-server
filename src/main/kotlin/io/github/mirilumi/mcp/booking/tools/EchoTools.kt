package io.github.mirilumi.mcp.booking.tools

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

/**
 * Example tool showing the conventions. Delete it once the first real tool exists.
 */
@Component
class EchoTools {

    @McpTool(
        name = "echo",
        description = "Returns the given message unchanged. Use it to check that the MCP server is reachable.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun echo(
        @McpToolParam(description = "Text to send back")
        message: String
    ): EchoResult = EchoResult(message = message, length = message.length)

    data class EchoResult(val message: String, val length: Int)
}
