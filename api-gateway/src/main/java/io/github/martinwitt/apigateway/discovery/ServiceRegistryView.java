package io.github.martinwitt.apigateway.discovery;

import java.time.Instant;
import java.util.List;

public record ServiceRegistryView(
        List<RouteInfo> routes, List<String> discoveredServices, Instant timestamp) {}
