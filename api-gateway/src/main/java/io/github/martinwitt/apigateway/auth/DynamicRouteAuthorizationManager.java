package io.github.martinwitt.apigateway.auth;

import io.github.martinwitt.apigateway.GatewayProperties;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.event.EventListener;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Decides per-request whether JWT authentication is required.
 *
 * <p>JWT-protected paths come from two sources:
 *
 * <ol>
 *   <li>Static: {@code gateway.auth.jwt.protected-paths} in {@code application.yml}.
 *   <li>Dynamic: annotation-based routes where {@code gateway.bootfleet.io/auth} contains {@code
 *       jwt}. Rebuilt on every {@link RefreshRoutesEvent}.
 * </ol>
 *
 * <p>All other paths are permitted without JWT (API key and public routes are handled separately).
 */
@Component
public class DynamicRouteAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {

    private static final Logger log =
            LoggerFactory.getLogger(DynamicRouteAuthorizationManager.class);

    /** Metadata key written by {@code AnnotationBasedRouteDefinitionLocator}. */
    public static final String METADATA_AUTH_MODE = "bootfleet.auth-mode";

    private final RouteDefinitionLocator routeDefinitionLocator;
    private final List<PathPattern> staticPatterns;
    private final AtomicReference<List<PathPattern>> dynamicPatterns =
            new AtomicReference<>(List.of());

    public DynamicRouteAuthorizationManager(
            RouteDefinitionLocator routeDefinitionLocator, GatewayProperties props) {
        this.routeDefinitionLocator = routeDefinitionLocator;
        var parser = PathPatternParser.defaultInstance;
        this.staticPatterns =
                props.getAuth().getJwt().getProtectedPaths().stream().map(parser::parse).toList();
    }

    @PostConstruct
    public void init() {
        refreshDynamicPatterns();
    }

    @EventListener(RefreshRoutesEvent.class)
    public void onRefreshRoutes(RefreshRoutesEvent ignored) {
        refreshDynamicPatterns();
    }

    @Override
    public Mono<AuthorizationResult> authorize(
            Mono<Authentication> authentication, AuthorizationContext context) {
        var path =
                PathContainer.parsePath(
                        context.getExchange()
                                .getRequest()
                                .getPath()
                                .pathWithinApplication()
                                .value());

        boolean jwtRequired =
                staticPatterns.stream().anyMatch(p -> p.matches(path))
                        || dynamicPatterns.get().stream().anyMatch(p -> p.matches(path));

        if (!jwtRequired) {
            return Mono.just(new AuthorizationDecision(true));
        }
        return authentication
                .<AuthorizationResult>map(
                        auth ->
                                new AuthorizationDecision(
                                        auth.isAuthenticated()
                                                && !(auth instanceof AnonymousAuthenticationToken)))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private void refreshDynamicPatterns() {
        var parser = PathPatternParser.defaultInstance;
        routeDefinitionLocator
                .getRouteDefinitions()
                .filter(
                        rd -> {
                            Object mode = rd.getMetadata().get(METADATA_AUTH_MODE);
                            return mode instanceof String s && s.contains("jwt");
                        })
                .flatMapIterable(
                        rd ->
                                rd.getPredicates().stream()
                                        .filter(p -> "Path".equals(p.getName()))
                                        .map(p -> p.getArgs().values().iterator().next())
                                        .toList())
                .map(parser::parse)
                .collectList()
                .subscribe(
                        patterns -> {
                            dynamicPatterns.set(patterns);
                            if (!patterns.isEmpty()) {
                                List<String> patternStrings = new ArrayList<>();
                                patterns.forEach(p -> patternStrings.add(p.getPatternString()));
                                log.debug("JWT-protected paths updated: {}", patternStrings);
                            }
                        },
                        err ->
                                log.warn(
                                        "Failed to refresh JWT-required paths: {}",
                                        err.getMessage()));
    }
}
