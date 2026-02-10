package io.github.martinwitt.configreloader.domain.model;

import java.util.Objects;

/** Value object representing a unique configuration resource identifier. */
public record ConfigResourceId(String namespace, String name, ConfigResourceType type) {
    public ConfigResourceId(String namespace, String name, ConfigResourceType type) {
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }
}
