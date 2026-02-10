package io.github.martinwitt.configreloader.domain.service;

import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.model.WatchedConfigResource;
import io.github.martinwitt.configreloader.domain.model.WorkloadConfiguration;
import io.github.martinwitt.configreloader.domain.model.WorkloadId;
import io.github.martinwitt.configreloader.domain.port.WorkloadReader;
import io.github.martinwitt.configreloader.domain.port.WorkloadRestarter;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Domain service for managing workload configuration dependencies. This implements the core
 * business logic for tracking which workloads depend on which config resources.
 */
public class WorkloadConfigurationService {
    private static final Logger logger =
            LoggerFactory.getLogger(WorkloadConfigurationService.class);

    private final ConfigResourceRepository repository;
    private final WorkloadReader workloadReader;
    private final WorkloadRestarter workloadRestarter;

    public WorkloadConfigurationService(
            ConfigResourceRepository repository,
            WorkloadReader workloadReader,
            WorkloadRestarter workloadRestarter) {
        this.repository = repository;
        this.workloadReader = workloadReader;
        this.workloadRestarter = workloadRestarter;
    }

    /** Register a workload and its configuration dependencies. */
    public void registerWorkload(WorkloadConfiguration workloadConfig) {
        if (!workloadConfig.watchEnabled()) {
            logger.debug("Workload {} is not enabled for watching", workloadConfig.workloadId());
            unregisterWorkload(workloadConfig.workloadId());
            return;
        }

        WorkloadId workloadId = workloadConfig.workloadId();
        Set<ConfigResourceId> newDependencies = workloadConfig.configDependencies();

        logger.info(
                "Registering workload {} with {} config dependencies",
                workloadId,
                newDependencies.size());

        // Remove old dependencies that are no longer referenced
        Set<ConfigResourceId> oldDependencies = repository.findByWorkload(workloadId);
        for (ConfigResourceId oldDep : oldDependencies) {
            if (!newDependencies.contains(oldDep)) {
                removeWorkloadFromResource(workloadId, oldDep);
            }
        }

        // Add new dependencies
        for (ConfigResourceId configResourceId : newDependencies) {
            addWorkloadToResource(workloadId, configResourceId);
        }
    }

    /** Unregister a workload and clean up its dependencies. */
    public void unregisterWorkload(WorkloadId workloadId) {
        logger.info("Unregistering workload {}", workloadId);

        Set<ConfigResourceId> dependencies = repository.findByWorkload(workloadId);
        for (ConfigResourceId configResourceId : dependencies) {
            removeWorkloadFromResource(workloadId, configResourceId);
        }
    }

    /** Handle configuration resource update event. */
    public void handleConfigResourceUpdate(ConfigResourceId configResourceId) {
        logger.info("Configuration resource {} was updated", configResourceId);

        repository
                .findById(configResourceId)
                .ifPresent(
                        resource -> {
                            Set<WorkloadId> affectedWorkloads = resource.dependentWorkloads();
                            logger.info(
                                    "Restarting {} workloads affected by update to {}",
                                    affectedWorkloads.size(),
                                    configResourceId);

                            for (WorkloadId workloadId : affectedWorkloads) {
                                try {
                                    workloadRestarter.restartWorkload(workloadId);
                                } catch (Exception e) {
                                    logger.error(
                                            "Failed to restart workload {} after config update",
                                            workloadId,
                                            e);
                                }
                            }
                        });
    }

    private void addWorkloadToResource(WorkloadId workloadId, ConfigResourceId configResourceId) {
        WatchedConfigResource resource =
                repository
                        .findById(configResourceId)
                        .map(r -> r.addWorkload(workloadId))
                        .orElseGet(
                                () -> {
                                    Set<WorkloadId> workloads = new HashSet<>();
                                    workloads.add(workloadId);
                                    return new WatchedConfigResource(configResourceId, workloads);
                                });

        repository.save(resource);
        logger.debug("Added workload {} to config resource {}", workloadId, configResourceId);
    }

    private void removeWorkloadFromResource(
            WorkloadId workloadId, ConfigResourceId configResourceId) {
        repository
                .findById(configResourceId)
                .ifPresent(
                        resource -> {
                            WatchedConfigResource updated = resource.removeWorkload(workloadId);
                            if (updated.hasWorkloads()) {
                                repository.save(updated);
                                logger.debug(
                                        "Removed workload {} from config resource {}",
                                        workloadId,
                                        configResourceId);
                            } else {
                                repository.remove(configResourceId);
                                logger.info(
                                        "Removed unwatched config resource {} (no more dependents)",
                                        configResourceId);
                            }
                        });
    }
}
