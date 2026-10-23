package io.github.mirilumi.mcp.booking.tools

import io.github.mirilumi.mcp.booking.McpTestClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest
@AutoConfigureMockMvc
class EchoToolsTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var mcp: McpTestClient

    @BeforeEach
    fun setUp() {
        mcp = McpTestClient(mockMvc)
        mcp.initialize()
    }

    @Test
    fun `echo is listed with its input schema and read-only hint`() {
        val tool = mcp.listTools().single { it.path("name").asString() == "echo" }

        assertTrue(tool.path("inputSchema").path("properties").has("message")) { "tool was $tool" }
        assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean()) { "tool was $tool" }
    }

    @Test
    fun `echo returns the message and its length`() {
        val result = mcp.callToolForJson("echo", mapOf("message" to "hello"))

        assertEquals("hello", result.path("message").asString())
        assertEquals(5, result.path("length").asInt())
    }
}
