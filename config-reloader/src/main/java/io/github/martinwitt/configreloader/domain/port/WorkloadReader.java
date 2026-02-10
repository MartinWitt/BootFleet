package io.github.martinwitt.configreloader.domain.port;

import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.model.WorkloadConfiguration;
import java.util.Set;

/** Port for reading workload configurations from Kubernetes. */
public interface WorkloadReader {
    /**
     * Extract configuration dependencies from a workload.
     *
     * @param workloadConfig the workload to analyze
     * @return the configuration resource dependencies
     */
    Set<ConfigResourceId> extractConfigDependencies(WorkloadConfiguration workloadConfig);

    /**
     * Check if a workload should be watched based on annotations and settings.
     *
     * @param annotations the workload annotations
     * @return true if the workload should be watched
     */
    boolean shouldWatch(java.util.Map<String, String> annotations);
}
