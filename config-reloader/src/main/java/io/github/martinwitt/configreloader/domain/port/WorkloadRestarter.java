package io.github.martinwitt.configreloader.domain.port;

import io.github.martinwitt.configreloader.domain.model.WorkloadId;

/** Port for restarting workload pods. */
public interface WorkloadRestarter {
    /**
     * Restart all pods for the given workload.
     *
     * @param workloadId the workload to restart
     */
    void restartWorkload(WorkloadId workloadId);
}
