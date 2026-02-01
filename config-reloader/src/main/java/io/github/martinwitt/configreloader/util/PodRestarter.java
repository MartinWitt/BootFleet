package io.github.martinwitt.configreloader.util;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.martinwitt.configreloader.ConfigReloaderProperties;
import io.micrometer.core.annotation.Timed;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Handles restarting pods for deployments and statefulsets. */
@Component
public class PodRestarter {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ConfigReloaderProperties properties;

    @Autowired
    public PodRestarter(ConfigReloaderProperties properties) {
        this.properties = properties;
    }

    @Timed(value = "podsrestart.duration", description = "Time taken to restart pods")
    public void restartPodsForWorkload(String deploymentId, KubernetesClient client) {
        String[] parts = deploymentId.split("/");
        String ns = parts[0];
        String name = parts[1];
        try {
            Map<String, String> labels = getWorkloadLabels(ns, name, client);
            if (labels != null) {
                String labelSelector = buildLabelSelector(labels);
                List<Pod> pods = getPods(ns, labelSelector, client);
                deletePods(pods, ns, client);
            }
        } catch (Exception e) {
            logger.error("Failed to restart workload {}/{}", ns, name, e);
        }
    }

    private Map<String, String> getWorkloadLabels(String ns, String name, KubernetesClient client) {
        Deployment deployment = client.apps().deployments().inNamespace(ns).withName(name).get();
        if (deployment != null) {
            return deployment.getSpec().getSelector().getMatchLabels();
        }
        StatefulSet statefulSet = client.apps().statefulSets().inNamespace(ns).withName(name).get();
        if (statefulSet != null) {
            return statefulSet.getSpec().getSelector().getMatchLabels();
        }
        return null;
    }

    private String buildLabelSelector(Map<String, String> labels) {
        return labels.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private List<Pod> getPods(String ns, String labelSelector, KubernetesClient client) {
        return client.pods().inNamespace(ns).withLabelSelector(labelSelector).list().getItems();
    }

    private void deletePods(List<Pod> pods, String ns, KubernetesClient client) {
        for (Pod pod : pods) {
            if (properties.isDryRun()) {
                logger.info("[DRY RUN] Would delete pod {}/{}", ns, pod.getMetadata().getName());
            } else {
                client.pods().inNamespace(ns).withName(pod.getMetadata().getName()).delete();
                logger.info("Deleted pod {}/{}", ns, pod.getMetadata().getName());
            }
        }
    }
}
