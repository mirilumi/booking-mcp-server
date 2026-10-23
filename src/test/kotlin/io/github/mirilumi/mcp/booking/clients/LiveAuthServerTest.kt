package io.github.mirilumi.mcp.booking.clients

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import java.time.Instant

/**
 * Fetches a real token from the auth server in AUTH_BASE_URL. Skipped unless AUTH_LIVE_TEST=true, so it never
 * runs in normal builds or CI. Prints only the token's type and expiry, never the token itself.
 *
 *   AUTH_LIVE_TEST=true AUTH_BASE_URL=... AUTH_CLIENT_ID=... AUTH_CLIENT_SECRET=... \
 *     ./gradlew test --tests "*LiveAuthServerTest" --rerun
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AUTH_LIVE_TEST", matches = "true")
class LiveAuthServerTest {
    @Autowired
    private lateinit var authorizedClientManager: OAuth2AuthorizedClientManager

    @Test
    fun `gets a client credentials token from the real auth server`() {
        val client = authorizedClientManager.authorize(
            OAuth2AuthorizeRequest.withClientRegistrationId(ServiceAuthConfig.REGISTRATION_ID)
                .principal("live-auth-test")
                .build()
        )

        val token = requireNotNull(client?.accessToken) { "auth server returned no access token" }
        assertTrue(token.tokenValue.isNotBlank())
        assertTrue(token.expiresAt!!.isAfter(Instant.now()))
        println("Live auth OK: ${token.tokenType.value} token, expires at ${token.expiresAt}, scopes ${token.scopes}")
    }
}
