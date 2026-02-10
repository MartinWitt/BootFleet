package io.github.martinwitt.configreloader.domain.model;

import java.util.Objects;

/** Value object representing a unique workload identifier. */
public record WorkloadId(String namespace, String name, WorkloadType type) {
    public WorkloadId(String namespace, String name, WorkloadType type) {
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    public String toQualifiedName() {
        return namespace + "/" + name;
    }
}
