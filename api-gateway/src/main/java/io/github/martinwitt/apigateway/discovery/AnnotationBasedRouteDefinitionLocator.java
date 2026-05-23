package io.github.martinwitt.apigateway.discovery;

import io.github.martinwitt.apigateway.GatewayProperties;
import io.github.martinwitt.apigateway.auth.ApiKeyGatewayFilterFactory;
import io.github.martinwitt.apigateway.auth.DynamicRouteAuthorizationManager;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Builds gateway routes dynamically from Kubernetes service annotations.
 *
 * <p>Any service with the label {@code gateway.bootfleet.io/expose=true} becomes a route.
 * Annotations control path, auth, and strip-prefix. If no path annotation is set the service name
 * is used as the path segment (Option C default behaviour).
 *
 * <p>Supported annotations:
 *
 * <pre>
 * gateway.bootfleet.io/path:             "/mcp/my-service/**"
 * gateway.bootfleet.io/strip-prefix:     "2"
 * gateway.bootfleet.io/auth:             "jwt | api-key | jwt+api-key | none"
 * gateway.bootfleet.io/api-key-env:      "MY_SECRET_ENV_VAR"
 * gateway.bootfleet.io/api-key-header:   "X-My-Key"
 * gateway.bootfleet.io/jwt-required-scope: "mcp:read"  (stored in metadata for future use)
 * </pre>
 *
 * <p>JWT enforcement is handled by Spring Security via {@link DynamicRouteAuthorizationManager};
 * the auth mode is stored in route metadata so the manager can read it on refresh.
 */
@Component
@ConditionalOnBean(CoreV1Api.class)
public class AnnotationBasedRouteDefinitionLocator implements RouteDefinitionLocator {

    private static final Logger log =
            LoggerFactory.getLogger(AnnotationBasedRouteDefinitionLocator.class);

    private static final Set<String> VALID_AUTH_MODES =
            Set.of("none", "jwt", "api-key", "jwt+api-key");

    static final String LABEL_EXPOSE = "gateway.bootfleet.io/expose";
    static final String ANN_PATH = "gateway.bootfleet.io/path";
    static final String ANN_STRIP_PREFIX = "gateway.bootfleet.io/strip-prefix";
    static final String ANN_AUTH = "gateway.bootfleet.io/auth";
    static final String ANN_API_KEY_ENV = "gateway.bootfleet.io/api-key-env";
    static final String ANN_API_KEY_HEADER = "gateway.bootfleet.io/api-key-header";
    static final String ANN_JWT_SCOPE = "gateway.bootfleet.io/jwt-required-scope";

    private final CoreV1Api coreV1Api;
    private final Environment environment;
    private final GatewayProperties gatewayProperties;

    @Value("${spring.cloud.kubernetes.client.namespace:default}")
    private String namespace;

    @Value("${spring.cloud.kubernetes.discovery.all-namespaces:false}")
    private boolean allNamespaces;

    public AnnotationBasedRouteDefinitionLocator(
            CoreV1Api coreV1Api, Environment environment, GatewayProperties gatewayProperties) {
        this.coreV1Api = coreV1Api;
        this.environment = environment;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Mono.fromCallable(
                        () ->
                                allNamespaces
                                        ? coreV1Api
                                                .listServiceForAllNamespaces()
                                                .labelSelector(LABEL_EXPOSE + "=true")
                                                .execute()
                                                .getItems()
                                        : coreV1Api
                                                .listNamespacedService(namespace)
                                                .labelSelector(LABEL_EXPOSE + "=true")
                                                .execute()
                                                .getItems())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(this::toRouteDefinition)
                .onErrorResume(
                        ApiException.class,
                        e -> {
                            log.warn(
                                    "Could not load services from Kubernetes ({}): {}",
                                    e.getCode(),
                                    e.getMessage());
                            return Flux.empty();
                        });
    }

    private RouteDefinition toRouteDefinition(V1Service service) {
        V1ObjectMeta meta = service.getMetadata();
        String serviceId = meta.getName();
        String serviceNamespace = meta.getNamespace() != null ? meta.getNamespace() : namespace;
        Map<String, String> annotations =
                meta.getAnnotations() != null ? meta.getAnnotations() : Map.of();

        String path = annotations.getOrDefault(ANN_PATH, "/" + serviceId + "/**");
        int strip;
        try {
            strip =
                    Integer.parseInt(
                            annotations.getOrDefault(
                                    ANN_STRIP_PREFIX, String.valueOf(pathDepth(path))));
        } catch (NumberFormatException e) {
            log.warn(
                    "Service '{}/{}' has invalid strip-prefix annotation '{}', using default",
                    serviceNamespace,
                    serviceId,
                    annotations.get(ANN_STRIP_PREFIX));
            strip = pathDepth(path);
        }
        String authMode = annotations.getOrDefault(ANN_AUTH, "none").toLowerCase();
        if (!VALID_AUTH_MODES.contains(authMode)) {
            log.warn(
                    "Service '{}/{}' has unknown auth mode '{}', treating as public (none)",
                    serviceNamespace,
                    serviceId,
                    authMode);
            authMode = "none";
        }

        var route = new RouteDefinition();
        route.setId(serviceNamespace + "/" + serviceId);
        route.setUri(URI.create("lb://" + serviceId));
        route.setPredicates(List.of(pathPredicate(path)));

        // Store auth mode in metadata — DynamicRouteAuthorizationManager reads this on refresh
        route.getMetadata().put(DynamicRouteAuthorizationManager.METADATA_AUTH_MODE, authMode);

        List<FilterDefinition> filters = new ArrayList<>();
        filters.add(stripPrefixFilter(strip));
        if (authMode.contains("api-key")) {
            filters.add(apiKeyFilter(serviceId, annotations));
        }
        route.setFilters(filters);

        log.debug(
                "Route from annotation: {}/{} → {} (path={}, auth={})",
                serviceNamespace,
                serviceId,
                route.getUri(),
                path,
                authMode);
        return route;
    }

    private PredicateDefinition pathPredicate(String path) {
        var predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("pattern", path);
        return predicate;
    }

    private FilterDefinition stripPrefixFilter(int parts) {
        var filter = new FilterDefinition();
        filter.setName("StripPrefix");
        filter.addArg("parts", String.valueOf(parts));
        return filter;
    }

    private FilterDefinition apiKeyFilter(String serviceId, Map<String, String> annotations) {
        String envVar = annotations.get(ANN_API_KEY_ENV);
        String apiKey =
                envVar != null
                        ? environment.getProperty(envVar, "")
                        : environment.getProperty(
                                serviceId.toUpperCase().replace('-', '_') + "_API_KEY", "");
        if (apiKey.isEmpty()) {
            log.warn(
                    "Service '{}' has auth=api-key but no key found "
                            + "(set annotation {} or env var {}_API_KEY)",
                    serviceId,
                    ANN_API_KEY_ENV,
                    serviceId.toUpperCase().replace('-', '_'));
        }
        String headerName =
                annotations.getOrDefault(
                        ANN_API_KEY_HEADER,
                        gatewayProperties.getAuth().getApiKey().getDefaultHeaderName());

        var filter = new FilterDefinition();
        filter.setName(ApiKeyGatewayFilterFactory.NAME);
        filter.addArg("headerName", headerName);
        filter.addArg("requiredKey", apiKey);
        return filter;
    }

    /** Counts the non-wildcard path segments — "/mcp/foo/**" → 2, "/foo/**" → 1. */
    static int pathDepth(String path) {
        String trimmed = path.replaceAll("/\\*\\*$", "").replaceAll("^/+", "");
        return trimmed.isEmpty() ? 0 : trimmed.split("/").length;
    }
}
