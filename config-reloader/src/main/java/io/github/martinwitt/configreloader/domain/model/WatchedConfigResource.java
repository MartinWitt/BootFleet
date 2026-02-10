package io.github.martinwitt.configreloader.domain.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Domain entity representing a watched configuration resource and the workloads that depend on it.
 */
public record WatchedConfigResource(
        ConfigResourceId resourceId, Set<WorkloadId> dependentWorkloads) {
    public WatchedConfigResource(ConfigResourceId resourceId, Set<WorkloadId> dependentWorkloads) {
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId cannot be null");
        this.dependentWorkloads = new HashSet<>(dependentWorkloads);
    }

    public WatchedConfigResource(ConfigResourceId resourceId) {
        this(resourceId, new HashSet<>());
    }

    @Override
    public Set<WorkloadId> dependentWorkloads() {
        return Collections.unmodifiableSet(dependentWorkloads);
    }

    public WatchedConfigResource addWorkload(WorkloadId workloadId) {
        Set<WorkloadId> newWorkloads = new HashSet<>(dependentWorkloads);
        newWorkloads.add(workloadId);
        return new WatchedConfigResource(resourceId, newWorkloads);
    }

    public WatchedConfigResource removeWorkload(WorkloadId workloadId) {
        Set<WorkloadId> newWorkloads = new HashSet<>(dependentWorkloads);
        newWorkloads.remove(workloadId);
        return new WatchedConfigResource(resourceId, newWorkloads);
    }

    public boolean hasWorkloads() {
        return !dependentWorkloads.isEmpty();
    }

    public boolean isDependedOnBy(WorkloadId workloadId) {
        return dependentWorkloads.contains(workloadId);
    }
}
