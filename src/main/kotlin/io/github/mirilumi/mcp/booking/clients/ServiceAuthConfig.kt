package io.github.mirilumi.mcp.booking.clients

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer

/**
 * Attaches a client credentials token to downstream calls (docs/design/service-auth.md).
 * Only `@HttpExchange` clients annotated with `@ClientRegistrationId("backend")` get a token.
 */
@Configuration
class ServiceAuthConfig {

    /**
     * Not tied to an HTTP request or a user, so it also works from tool calls. Tokens are cached in memory
     * and fetched again shortly before they expire.
     */
    @Bean
    fun authorizedClientManager(
        clientRegistrations: ClientRegistrationRepository,
        authorizedClients: OAuth2AuthorizedClientService
    ): OAuth2AuthorizedClientManager {
        requireConfigured(clientRegistrations.findByRegistrationId(REGISTRATION_ID))
        return AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClients).apply {
            setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build())
        }
    }

    @Bean
    fun oauth2HttpServiceGroupConfigurer(manager: OAuth2AuthorizedClientManager): OAuth2RestClientHttpServiceGroupConfigurer =
        OAuth2RestClientHttpServiceGroupConfigurer.from(manager)

    companion object {
        const val REGISTRATION_ID = "backend"

        /**
         * Unset environment variables stay as literal `${AUTH_...}` text and would only fail on the first
         * downstream call, so fail at startup instead.
         */
        fun requireConfigured(registration: ClientRegistration?) {
            check(registration != null) { "Missing spring.security.oauth2.client.registration.$REGISTRATION_ID" }
            val unset = mapOf(
                "AUTH_CLIENT_ID" to registration.clientId,
                "AUTH_CLIENT_SECRET" to registration.clientSecret,
                "AUTH_BASE_URL" to registration.providerDetails.tokenUri
            ).filterValues { it.isNullOrBlank() || "\${" in it }.keys
            check(unset.isEmpty()) {
                "Downstream service auth is not configured: set ${unset.joinToString()} (see docs/design/service-auth.md)"
            }
        }
    }
}
