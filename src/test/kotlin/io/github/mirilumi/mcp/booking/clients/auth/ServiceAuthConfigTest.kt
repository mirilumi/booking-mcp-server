package io.github.mirilumi.mcp.booking.clients.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType

class ServiceAuthConfigTest {

    private fun registration(clientId: String, secret: String, tokenUri: String) =
        ClientRegistration.withRegistrationId("backend")
            .clientId(clientId)
            .clientSecret(secret)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(tokenUri)
            .build()

    @Test
    fun `configured registration passes`() {
        assertDoesNotThrow {
            ServiceAuthConfig.requireConfigured(registration("id", "secret", "http://auth/oauth2/token"))
        }
    }

    @Test
    fun `unset environment variables are named in the startup error`() {
        val error = assertThrows<IllegalStateException> {
            ServiceAuthConfig.requireConfigured(
                registration("\${AUTH_CLIENT_ID}", "\${AUTH_CLIENT_SECRET}", "\${AUTH_BASE_URL}/oauth2/token")
            )
        }

        assertEquals(
            "Downstream service auth is not configured: set AUTH_CLIENT_ID, AUTH_CLIENT_SECRET, AUTH_BASE_URL " +
                "(see docs/design/service-auth.md)",
            error.message
        )
    }
}
