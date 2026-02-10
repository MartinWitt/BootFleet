package io.github.martinwitt.configreloader.infrastructure.kubernetes;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.martinwitt.configreloader.ConfigReloaderProperties;
import io.github.martinwitt.configreloader.domain.model.WorkloadId;
import io.github.martinwitt.configreloader.domain.port.WorkloadRestarter;
import io.micrometer.core.annotation.Timed;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Kubernetes adapter for restarting workload pods. */
@Component
public class KubernetesWorkloadRestarter implements WorkloadRestarter {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesWorkloadRestarter.class);

    private final KubernetesClient kubernetesClient;
    private final ConfigReloaderProperties properties;

    public KubernetesWorkloadRestarter(
            KubernetesClient kubernetesClient, ConfigReloaderProperties properties) {
        this.kubernetesClient = kubernetesClient;
        this.properties = properties;
    }

    @Override
    @Timed(value = "workload.restart.duration", description = "Time taken to restart workload pods")
    public void restartWorkload(WorkloadId workloadId) {
        try {
            Map<String, String> labels = getWorkloadLabels(workloadId);
            if (labels != null && !labels.isEmpty()) {
                String labelSelector = buildLabelSelector(labels);
                List<Pod> pods = getPods(workloadId.namespace(), labelSelector);
                deletePods(pods, workloadId);
            } else {
                logger.warn("No labels found for workload {}", workloadId);
            }
        } catch (Exception e) {
            logger.error("Failed to restart workload {}", workloadId, e);
            throw new WorkloadRestartException("Failed to restart workload: " + workloadId, e);
        }
    }

    private Map<String, String> getWorkloadLabels(WorkloadId workloadId) {
        return switch (workloadId.type()) {
            case DEPLOYMENT -> {
                Deployment deployment =
                        kubernetesClient
                                .apps()
                                .deployments()
                                .inNamespace(workloadId.namespace())
                                .withName(workloadId.name())
                                .get();
                yield deployment != null
                        ? deployment.getSpec().getSelector().getMatchLabels()
                        : null;
            }
            case STATEFULSET -> {
                StatefulSet statefulSet =
                        kubernetesClient
                                .apps()
                                .statefulSets()
                                .inNamespace(workloadId.namespace())
                                .withName(workloadId.name())
                                .get();
                yield statefulSet != null
                        ? statefulSet.getSpec().getSelector().getMatchLabels()
                        : null;
            }
        };
    }

    private String buildLabelSelector(Map<String, String> labels) {
        return labels.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private List<Pod> getPods(String namespace, String labelSelector) {
        return kubernetesClient
                .pods()
                .inNamespace(namespace)
                .withLabelSelector(labelSelector)
                .list()
                .getItems();
    }

    private void deletePods(List<Pod> pods, WorkloadId workloadId) {
        for (Pod pod : pods) {
            String podName = pod.getMetadata().getName();
            if (properties.isDryRun()) {
                logger.info(
                        "[DRY RUN] Would delete pod {}/{} for workload {}",
                        workloadId.namespace(),
                        podName,
                        workloadId);
            } else {
                kubernetesClient
                        .pods()
                        .inNamespace(workloadId.namespace())
                        .withName(podName)
                        .delete();
                logger.info(
                        "Deleted pod {}/{} for workload {}",
                        workloadId.namespace(),
                        podName,
                        workloadId);
            }
        }
    }

    public static class WorkloadRestartException extends RuntimeException {
        public WorkloadRestartException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
