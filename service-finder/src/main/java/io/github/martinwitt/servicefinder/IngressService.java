package io.github.martinwitt.servicefinder;

import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class IngressService {

    private final KubernetesClient client;

    public IngressService(KubernetesClient client) {
        this.client = client;
    }

    public List<ServiceEntry> findAll() {
        List<ServiceEntry> entries = new ArrayList<>();
        entries.addAll(findIngresses());
        entries.addAll(findHttpRoutes());
        return entries;
    }

    private List<ServiceEntry> findIngresses() {
        return client.network().v1().ingresses().inAnyNamespace().list().getItems().stream()
                .map(this::ingressToEntry)
                .toList();
    }

    private List<ServiceEntry> findHttpRoutes() {
        return client.resources(HTTPRoute.class).inAnyNamespace().list().getItems().stream()
                .map(this::httpRouteToEntry)
                .toList();
    }

    private ServiceEntry ingressToEntry(Ingress ingress) {
        String name = ingress.getMetadata().getName();
        String namespace = ingress.getMetadata().getNamespace();

        boolean hasTls =
                ingress.getSpec().getTls() != null && !ingress.getSpec().getTls().isEmpty();
        String scheme = hasTls ? "https" : "http";

        List<String> urls =
                Optional.ofNullable(ingress.getSpec().getRules()).orElse(List.of()).stream()
                        .filter(rule -> rule.getHost() != null)
                        .map(rule -> scheme + "://" + rule.getHost())
                        .distinct()
                        .toList();

        return new ServiceEntry(name, namespace, urls);
    }

    private ServiceEntry httpRouteToEntry(HTTPRoute route) {
        String name = route.getMetadata().getName();
        String namespace = route.getMetadata().getNamespace();

        List<String> urls =
                Optional.ofNullable(route.getSpec().getHostnames()).orElse(List.of()).stream()
                        .map(host -> "https://" + host)
                        .distinct()
                        .toList();

        return new ServiceEntry(name, namespace, urls);
    }
}
