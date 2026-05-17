package io.github.martinwitt.apigateway.auth;

import io.github.martinwitt.apigateway.GatewayProperties;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that forwards JWT claims as upstream request headers.
 *
 * <p>Reads the already-validated {@link org.springframework.security.oauth2.jwt.Jwt} from the
 * Spring Security context — no JWT parsing here, Spring Security did that. Only fires when an
 * authenticated JWT is present; passes through silently otherwise.
 *
 * <p>Header names and claim names are configurable via {@code gateway.auth.jwt.*}.
 */
@Component
public class JwtClaimsHeaderFilter implements GlobalFilter, Ordered {

    private final GatewayProperties.Auth.Jwt jwtProps;

    public JwtClaimsHeaderFilter(GatewayProperties gatewayProperties) {
        this.jwtProps = gatewayProperties.getAuth().getJwt();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .flatMap(
                        jwt -> {
                            var mutated =
                                    exchange.mutate()
                                            .request(
                                                    r -> {
                                                        r.header(
                                                                jwtProps.getSubjectHeader(),
                                                                jwt.getSubject());
                                                        Optional.ofNullable(
                                                                        jwt.getClaimAsString(
                                                                                jwtProps
                                                                                        .getEmailClaim()))
                                                                .ifPresent(
                                                                        v ->
                                                                                r.header(
                                                                                        jwtProps
                                                                                                .getEmailHeader(),
                                                                                        v));
                                                        Optional.ofNullable(
                                                                        jwt.getClaimAsString(
                                                                                jwtProps
                                                                                        .getUsernameClaim()))
                                                                .ifPresent(
                                                                        v ->
                                                                                r.header(
                                                                                        jwtProps
                                                                                                .getUsernameHeader(),
                                                                                        v));
                                                    })
                                            .build();
                            return chain.filter(mutated);
                        })
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }
}
