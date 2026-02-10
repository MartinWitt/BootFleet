package io.github.martinwitt.configreloader.domain.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Domain entity representing a workload and its configuration dependencies. */
public record WorkloadConfiguration(
        WorkloadId workloadId, Set<ConfigResourceId> configDependencies, boolean watchEnabled) {
    public WorkloadConfiguration(
            WorkloadId workloadId, Set<ConfigResourceId> configDependencies, boolean watchEnabled) {
        this.workloadId = Objects.requireNonNull(workloadId, "workloadId cannot be null");
        this.configDependencies = new HashSet<>(configDependencies);
        this.watchEnabled = watchEnabled;
    }

    @Override
    public Set<ConfigResourceId> configDependencies() {
        return Collections.unmodifiableSet(configDependencies);
    }
}
