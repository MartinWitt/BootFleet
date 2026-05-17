package io.github.martinwitt.apigateway.discovery;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Service;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically checks whether the set of annotated K8s services has changed and fires a {@link
 * RefreshRoutesEvent} when it has, causing Spring Cloud Gateway to rebuild its route table without
 * a restart.
 */
@Component
@ConditionalOnBean(CoreV1Api.class)
public class RouteRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(RouteRefreshScheduler.class);

    private final CoreV1Api coreV1Api;
    private final ApplicationEventPublisher publisher;
    private final AtomicReference<String> lastSnapshot = new AtomicReference<>("");

    @Value("${spring.cloud.kubernetes.client.namespace:default}")
    private String namespace;

    @Value("${spring.cloud.kubernetes.discovery.all-namespaces:false}")
    private boolean allNamespaces;

    public RouteRefreshScheduler(CoreV1Api coreV1Api, ApplicationEventPublisher publisher) {
        this.coreV1Api = coreV1Api;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${gateway.route-refresh-interval-ms:30000}")
    public void checkForChanges() {
        try {
            List<V1Service> services =
                    allNamespaces
                            ? coreV1Api
                                    .listServiceForAllNamespaces()
                                    .labelSelector(
                                            AnnotationBasedRouteDefinitionLocator.LABEL_EXPOSE
                                                    + "=true")
                                    .execute()
                                    .getItems()
                            : coreV1Api
                                    .listNamespacedService(namespace)
                                    .labelSelector(
                                            AnnotationBasedRouteDefinitionLocator.LABEL_EXPOSE
                                                    + "=true")
                                    .execute()
                                    .getItems();

            String snapshot = buildSnapshot(services);
            String previous = lastSnapshot.getAndSet(snapshot);

            if (!snapshot.equals(previous)) {
                log.info("Kubernetes services changed — refreshing gateway routes");
                publisher.publishEvent(new RefreshRoutesEvent(this));
            }
        } catch (ApiException e) {
            log.warn("Route refresh check failed ({}): {}", e.getCode(), e.getMessage());
        }
    }

    /** Stable fingerprint: sorted list of "name:annotations-hash" entries. */
    private String buildSnapshot(List<V1Service> services) {
        return services.stream()
                .map(
                        s -> {
                            String name = Objects.requireNonNullElse(s.getMetadata().getName(), "");
                            Map<String, String> ann = s.getMetadata().getAnnotations();
                            String annHash =
                                    ann == null
                                            ? ""
                                            : ann.entrySet().stream()
                                                    .filter(
                                                            e ->
                                                                    e.getKey()
                                                                            .startsWith(
                                                                                    "gateway.bootfleet.io/"))
                                                    .sorted(Map.Entry.comparingByKey())
                                                    .map(e -> e.getKey() + "=" + e.getValue())
                                                    .collect(Collectors.joining(","));
                            return name + ":" + annHash;
                        })
                .sorted()
                .collect(Collectors.joining("|"));
    }
}
