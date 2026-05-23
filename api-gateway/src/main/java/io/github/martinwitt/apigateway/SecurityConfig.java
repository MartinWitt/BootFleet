package io.github.martinwitt.apigateway;

import io.github.martinwitt.apigateway.auth.DynamicRouteAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security configuration.
 *
 * <p>JWT validation is handled entirely by Spring Security's built-in OAuth2 resource server
 * support. {@link DynamicRouteAuthorizationManager} decides per-request which paths require an
 * authenticated JWT; all other paths (API-key routes, public routes) are permitted.
 *
 * <p>After a successful JWT validation, {@link
 * io.github.martinwitt.apigateway.auth.JwtClaimsHeaderFilter} enriches the upstream request with
 * claim headers ({@code X-Auth-Sub} etc.) by reading from the Spring Security context — no
 * secondary JWT decoding.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain gatewaySecurityChain(
            ServerHttpSecurity http, DynamicRouteAuthorizationManager authorizationManager) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().access(authorizationManager))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
