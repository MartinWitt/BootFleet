package io.github.martinwitt.configreloader.application.service;

import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.service.WorkloadConfigurationService;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Application service for handling configuration resource updates. */
@Service
public class ConfigResourceUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigResourceUpdateService.class);

    private final WorkloadConfigurationService workloadConfigurationService;

    public ConfigResourceUpdateService(WorkloadConfigurationService workloadConfigurationService) {
        this.workloadConfigurationService = workloadConfigurationService;
    }

    /** Handle a configuration resource update by restarting affected workloads. */
    @Timed(
            value = "config.resource.update.duration",
            description = "Time taken to process config resource update")
    public void handleConfigResourceUpdate(ConfigResourceId configResourceId) {
        try {
            workloadConfigurationService.handleConfigResourceUpdate(configResourceId);
        } catch (Exception e) {
            logger.error("Failed to handle config resource update for {}", configResourceId, e);
            throw new ConfigResourceUpdateException(
                    "Failed to handle config resource update: " + configResourceId, e);
        }
    }

    public static class ConfigResourceUpdateException extends RuntimeException {
        public ConfigResourceUpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
