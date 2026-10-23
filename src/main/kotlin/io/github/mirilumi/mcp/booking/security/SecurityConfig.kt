package io.github.mirilumi.mcp.booking.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Spring Security is on the classpath for the OAuth2 client, which would otherwise lock every endpoint.
 * `/mcp` stays open for now; securing it with OAuth is separate work (see README roadmap).
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .authorizeHttpRequests {
            it.requestMatchers("/mcp", "/actuator/health", "/actuator/health/**").permitAll()
            // Dev-only endpoints; the controllers exist only with the `dev` profile
            it.requestMatchers("/dev/**").permitAll()
            // Spring Boot forwards failed requests here; without it every 404/405 would show up as an empty 403
            it.requestMatchers("/error").permitAll()
            it.anyRequest().denyAll()
        }
        // MCP clients POST JSON-RPC without CSRF tokens or cookies
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .build()
}
