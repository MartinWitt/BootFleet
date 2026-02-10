package io.github.martinwitt.configreloader.application.service;

import io.github.martinwitt.configreloader.domain.model.WorkloadConfiguration;
import io.github.martinwitt.configreloader.domain.model.WorkloadId;
import io.github.martinwitt.configreloader.domain.service.WorkloadConfigurationService;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Application service for managing workloads and their configuration dependencies. */
@Service
public class WorkloadManagementService {
    private static final Logger logger = LoggerFactory.getLogger(WorkloadManagementService.class);

    private final WorkloadConfigurationService workloadConfigurationService;

    public WorkloadManagementService(WorkloadConfigurationService workloadConfigurationService) {
        this.workloadConfigurationService = workloadConfigurationService;
    }

    /** Register a workload and track its configuration dependencies. */
    @Timed(value = "workload.register.duration", description = "Time taken to register a workload")
    public void registerWorkload(WorkloadConfiguration workloadConfig) {
        try {
            workloadConfigurationService.registerWorkload(workloadConfig);
        } catch (Exception e) {
            logger.error("Failed to register workload {}", workloadConfig.workloadId(), e);
            throw new WorkloadManagementException(
                    "Failed to register workload: " + workloadConfig.workloadId(), e);
        }
    }

    /** Unregister a workload and clean up its configuration dependencies. */
    @Timed(
            value = "workload.unregister.duration",
            description = "Time taken to unregister a workload")
    public void unregisterWorkload(WorkloadId workloadId) {
        try {
            workloadConfigurationService.unregisterWorkload(workloadId);
        } catch (Exception e) {
            logger.error("Failed to unregister workload {}", workloadId, e);
            throw new WorkloadManagementException(
                    "Failed to unregister workload: " + workloadId, e);
        }
    }

    public static class WorkloadManagementException extends RuntimeException {
        public WorkloadManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
