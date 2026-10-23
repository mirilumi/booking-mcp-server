package io.github.mirilumi.mcp.booking.clients

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.registry.ImportHttpServices
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.text.Charsets.UTF_8

/** docs/design/service-auth.md: client credentials token attached to opted-in downstream clients. */
@SpringBootTest
class ServiceAuthTest {
    @Autowired
    private lateinit var securedClient: SecuredStubClient

    @Autowired
    private lateinit var publicClient: PublicStubClient

    @Autowired
    private lateinit var authorizedClients: OAuth2AuthorizedClientService

    @BeforeEach
    fun reset() {
        // Tool calls have no user, so the token is cached under the anonymous principal
        authorizedClients.removeAuthorizedClient("backend", "anonymousUser")
        tokenRequests.clear()
        downstreamRequests.clear()
        expiresInSeconds = 3600
    }

    @Test
    fun `token request uses client credentials in the form body`() {
        securedClient.ping()

        val request = tokenRequests.single()
        assertEquals("POST", request.method)
        assertEquals("/oauth2/token", request.path)
        assertEquals(
            mapOf("grant_type" to "client_credentials", "client_id" to "test-client", "client_secret" to "test-secret"),
            request.form
        )
        assertNull(request.authorization, "client secret must not also be sent as a Basic header")
    }

    @Test
    fun `downstream calls carry the token and reuse it while it is valid`() {
        repeat(4) { securedClient.ping() }

        assertEquals(1, tokenRequests.size, "a valid cached token must be reused")
        assertEquals(4, downstreamRequests.size)
        downstreamRequests.forEach { assertEquals("Bearer token-$tokenCounter", it.authorization) }
    }

    @Test
    fun `a new token is fetched once the cached one is about to expire`() {
        // Spring renews tokens within 60s of expiry, so a 30s token is renewed on every call
        expiresInSeconds = 30

        repeat(3) { securedClient.ping() }

        assertEquals(3, tokenRequests.size)
    }

    @Test
    fun `clients without ClientRegistrationId send no token`() {
        publicClient.ping()

        assertNull(downstreamRequests.single().authorization)
        assertEquals(0, tokenRequests.size)
    }

    @ClientRegistrationId("backend")
    @HttpExchange("/api")
    interface SecuredStubClient {
        @GetExchange("/ping")
        fun ping(): String
    }

    @HttpExchange("/api")
    interface PublicStubClient {
        @GetExchange("/ping")
        fun ping(): String
    }

    @TestConfiguration
    @ImportHttpServices(group = "stub-secured", types = [SecuredStubClient::class])
    @ImportHttpServices(group = "stub-public", types = [PublicStubClient::class])
    class StubClients

    data class RecordedRequest(val method: String, val path: String, val authorization: String?, val form: Map<String, String>)

    companion object {
        private val tokenRequests = CopyOnWriteArrayList<RecordedRequest>()
        private val downstreamRequests = CopyOnWriteArrayList<RecordedRequest>()

        @Volatile
        private var expiresInSeconds = 3600

        @Volatile
        private var tokenCounter = 0

        private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/oauth2/token") { exchange ->
                val form = exchange.requestBody.readAllBytes().toString(UTF_8).split("&").filter { it.isNotEmpty() }
                    .associate { pair -> pair.substringBefore("=").decode() to pair.substringAfter("=").decode() }
                tokenRequests += RecordedRequest(
                    exchange.requestMethod, exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), form
                )
                tokenCounter++
                // Extra fields like the real auth server sends; they must be ignored
                exchange.respond(
                    """{"access_token":"token-$tokenCounter","token_type":"bearer","expires_in":$expiresInSeconds,
                       "scope":"read write","platform":["ios"],"jti":"abc"}""",
                    "application/json"
                )
            }
            createContext("/api/ping") { exchange ->
                downstreamRequests += RecordedRequest(
                    exchange.requestMethod, exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), emptyMap()
                )
                exchange.respond("pong", "text/plain")
            }
            start()
        }

        private fun String.decode() = URLDecoder.decode(this, UTF_8)

        private fun com.sun.net.httpserver.HttpExchange.respond(body: String, contentType: String) {
            val bytes = body.toByteArray()
            responseHeaders.add("Content-Type", contentType)
            sendResponseHeaders(200, bytes.size.toLong())
            responseBody.use { it.write(bytes) }
        }

        @JvmStatic
        @DynamicPropertySource
        fun stubServers(registry: DynamicPropertyRegistry) {
            val url = "http://localhost:${server.address.port}"
            registry.add("AUTH_BASE_URL") { url }
            registry.add("spring.http.serviceclient.stub-secured.base-url") { url }
            registry.add("spring.http.serviceclient.stub-public.base-url") { url }
        }

        @JvmStatic
        @AfterAll
        fun stopServer() = server.stop(0)
    }
}
