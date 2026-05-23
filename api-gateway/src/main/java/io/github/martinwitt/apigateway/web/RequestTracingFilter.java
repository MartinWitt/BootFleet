package io.github.martinwitt.apigateway.web;

import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Ensures every request and its response carry an {@code X-Request-ID} header.
 *
 * <p>If the incoming request already has the header the value is reused; otherwise a random UUID is
 * generated. The same ID is written back on the response so clients can correlate logs.
 */
@Component
@Order(-1)
public class RequestTracingFilter implements WebFilter {

    static final String HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        final String id = requestId;
        exchange.getResponse().getHeaders().set(HEADER, id);
        ServerWebExchange mutated = exchange.mutate().request(r -> r.header(HEADER, id)).build();
        return chain.filter(mutated);
    }
}
