package io.github.mirilumi.mcp.booking.clients.availabilities

import io.github.mirilumi.mcp.booking.clients.auth.ServiceAuthConfig
import io.github.mirilumi.mcp.booking.clients.availabilities.api.AvailabilityControllerApi
import io.github.mirilumi.mcp.booking.clients.availabilities.api.LowFareControllerApi
import io.github.mirilumi.mcp.booking.clients.availabilities.api.ValidDatesControllerApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

/**
 * Clients for the availabilities service, generated from specs/availabilities/spec.json.
 * Base URL and timeouts: spring.http.serviceclient.availabilities.* in application.yml.
 */
@Configuration
@ImportHttpServices(
    group = AvailabilitiesClientConfig.GROUP,
    types = [AvailabilityControllerApi::class, LowFareControllerApi::class, ValidDatesControllerApi::class]
)
class AvailabilitiesClientConfig {

    /**
     * Generated interfaces can't carry @ClientRegistrationId, so every request of this group gets the
     * same request attribute that the annotation would set: the service token is attached by ServiceAuthConfig.
     */
    @Bean
    fun availabilitiesServiceToken(): RestClientHttpServiceGroupConfigurer = RestClientHttpServiceGroupConfigurer { groups ->
        groups.filterByName(GROUP).forEachClient { _, builder ->
            builder.defaultRequest { it.attributes(clientRegistrationId(ServiceAuthConfig.REGISTRATION_ID)) }
        }
    }

    companion object {
        const val GROUP = "availabilities"
    }
}
