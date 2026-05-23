package io.github.martinwitt.apigateway.discovery;

import io.github.martinwitt.apigateway.auth.ApiKeyGatewayFilterFactory;
import io.github.martinwitt.apigateway.auth.DynamicRouteAuthorizationManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ServiceFinderService {

    private final RouteDefinitionLocator routeDefinitionLocator;
    private final Optional<ReactiveDiscoveryClient> discoveryClient;

    public ServiceFinderService(
            RouteDefinitionLocator routeDefinitionLocator,
            Optional<ReactiveDiscoveryClient> discoveryClient) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.discoveryClient = discoveryClient;
    }

    public Mono<ServiceRegistryView> getRegistryView() {
        Mono<List<RouteInfo>> routes =
                routeDefinitionLocator.getRouteDefinitions().map(this::toRouteInfo).collectList();

        Mono<List<String>> services =
                discoveryClient
                        .map(client -> client.getServices().collectList())
                        .orElse(Mono.just(List.of()));

        return Mono.zip(routes, services)
                .map(tuple -> new ServiceRegistryView(tuple.getT1(), tuple.getT2(), Instant.now()));
    }

    private RouteInfo toRouteInfo(RouteDefinition rd) {
        List<String> predicates =
                rd.getPredicates().stream().map(p -> p.getName() + "=" + p.getArgs()).toList();
        List<String> filters =
                rd.getFilters().stream()
                        .map(
                                f -> {
                                    if (ApiKeyGatewayFilterFactory.NAME.equals(f.getName())) {
                                        var safeArgs = new HashMap<>(f.getArgs());
                                        safeArgs.replaceAll(
                                                (k, v) -> "requiredKey".equals(k) ? "***" : v);
                                        return f.getName() + "=" + safeArgs;
                                    }
                                    return f.getName() + "=" + f.getArgs();
                                })
                        .toList();

        // Auth is enabled when the route has an API-key filter OR is JWT-protected via metadata
        Object authMode = rd.getMetadata().get(DynamicRouteAuthorizationManager.METADATA_AUTH_MODE);
        boolean hasApiKey =
                rd.getFilters().stream()
                        .anyMatch(f -> ApiKeyGatewayFilterFactory.NAME.equals(f.getName()));
        boolean hasJwt = authMode instanceof String s && s.contains("jwt");
        boolean authEnabled = hasApiKey || hasJwt;

        return new RouteInfo(rd.getId(), rd.getUri().toString(), predicates, filters, authEnabled);
    }
}
