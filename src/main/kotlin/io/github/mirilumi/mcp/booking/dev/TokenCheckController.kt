package io.github.mirilumi.mcp.booking.dev

import io.github.mirilumi.mcp.booking.clients.auth.ServiceAuthConfig
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Dev-only check that the service token can be fetched from the auth server in AUTH_BASE_URL.
 * Run with the `dev` profile and open GET /dev/token-check. Never returns the token itself.
 */
@Profile("dev")
@RestController
class TokenCheckController(private val authorizedClientManager: OAuth2AuthorizedClientManager) {

    @GetMapping("/dev/token-check")
    fun tokenCheck(): TokenCheck {
        val token = authorizedClientManager.authorize(
            OAuth2AuthorizeRequest.withClientRegistrationId(ServiceAuthConfig.REGISTRATION_ID)
                .principal("dev-token-check")
                .build()
        )?.accessToken ?: error("auth server returned no access token")

        return TokenCheck(token.tokenType.value, token.expiresAt, token.scopes)
    }

    data class TokenCheck(val tokenType: String, val expiresAt: Instant?, val scopes: Set<String>)
}
