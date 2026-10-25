package io.github.mirilumi.mcp.booking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc

/**
 * The MCP endpoint itself, independent of any tool: it must work even in a build without private specs,
 * where every service-backed tool is left out.
 */
@SpringBootTest
@AutoConfigureMockMvc
class McpEndpointTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var mcp: McpTestClient

    @BeforeEach
    fun setUp() {
        mcp = McpTestClient(mockMvc)
    }

    @Test
    fun `initialize identifies the server and offers tools`() {
        val result = mcp.initialize()

        assertEquals("booking-mcp-server", result.path("serverInfo").path("name").asString())
        assertTrue(result.path("capabilities").has("tools")) { "result was $result" }
    }

    @Test
    fun `tools list is reachable without a token`() {
        mcp.initialize()

        assertTrue(mcp.listTools().isArray)
    }
}
