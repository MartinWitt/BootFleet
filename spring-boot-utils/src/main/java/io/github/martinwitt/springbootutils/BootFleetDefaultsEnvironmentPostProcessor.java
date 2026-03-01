package io.github.martinwitt.springbootutils;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Provides sensible default configuration for Spring Boot Actuator and Prometheus metrics in all
 * BootFleet services.
 *
 * <p>Defaults are applied at the lowest precedence so they can be overridden in any module's {@code
 * application.yaml}.
 *
 * <p>Defaults applied:
 *
 * <ul>
 *   <li>{@code management.endpoints.web.exposure.include} → {@code health,metrics,prometheus}
 *   <li>{@code management.endpoint.prometheus.access} → {@code unrestricted}
 *   <li>{@code management.metrics.enable.jvm} → {@code true}
 *   <li>{@code management.metrics.enable.process} → {@code true}
 *   <li>{@code management.metrics.enable.system} → {@code true}
 *   <li>{@code management.metrics.distribution.percentiles-histogram.http.server.requests} → {@code
 *       true}
 * </ul>
 */
public class BootFleetDefaultsEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "bootfleet-defaults";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("management.endpoints.web.exposure.include", "health,metrics,prometheus");
        defaults.put("management.endpoint.prometheus.access", "unrestricted");
        defaults.put("management.metrics.enable.jvm", "true");
        defaults.put("management.metrics.enable.process", "true");
        defaults.put("management.metrics.enable.system", "true");
        defaults.put(
                "management.metrics.distribution.percentiles-histogram.http.server.requests",
                "true");
        environment
                .getPropertySources()
                .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
