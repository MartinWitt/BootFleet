package io.github.martinwitt.configreloader.domain.service;

import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.model.WatchedConfigResource;
import io.github.martinwitt.configreloader.domain.model.WorkloadId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Repository interface for managing watched configuration resources. */
public interface ConfigResourceRepository {
    /** Save or update a watched configuration resource. */
    void save(WatchedConfigResource resource);

    /** Find a watched configuration resource by its ID. */
    Optional<WatchedConfigResource> findById(ConfigResourceId resourceId);

    /** Remove a watched configuration resource. */
    void remove(ConfigResourceId resourceId);

    /** Get all watched configuration resources. */
    Map<ConfigResourceId, WatchedConfigResource> findAll();

    /** Find all configuration resources that a workload depends on. */
    Set<ConfigResourceId> findByWorkload(WorkloadId workloadId);

    /** Get count of watched secrets. */
    int countSecrets();

    /** Get count of watched config maps. */
    int countConfigMaps();
}
