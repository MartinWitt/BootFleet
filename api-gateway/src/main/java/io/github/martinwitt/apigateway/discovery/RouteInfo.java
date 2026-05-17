package io.github.martinwitt.apigateway.discovery;

import java.util.List;

public record RouteInfo(
        String id,
        String uri,
        List<String> predicates,
        List<String> filters,
        boolean authEnabled) {}
