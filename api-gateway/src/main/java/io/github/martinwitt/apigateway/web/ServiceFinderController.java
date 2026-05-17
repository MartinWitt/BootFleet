package io.github.martinwitt.apigateway.web;

import io.github.martinwitt.apigateway.discovery.ServiceFinderService;
import io.github.martinwitt.apigateway.discovery.ServiceRegistryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/gateway")
class ServiceFinderController {

    private final ServiceFinderService service;

    ServiceFinderController(ServiceFinderService service) {
        this.service = service;
    }

    @GetMapping("/services")
    Mono<ServiceRegistryView> services() {
        return service.getRegistryView();
    }
}
