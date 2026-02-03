package io.github.martinwitt.configreloader;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.martinwitt.configreloader.manager.ResourceManager;
import io.github.martinwitt.configreloader.watcher.DeploymentWatcher;
import io.github.martinwitt.configreloader.watcher.StatefulSetWatcher;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "configreloader.enabled", havingValue = "true")
public class ResourceWatcherService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ConfigReloaderProperties properties;
    private final ResourceManager resourceManager;
    private final KubernetesClient kubernetesClient;

    @Autowired
    public ResourceWatcherService(
            ConfigReloaderProperties properties,
            ResourceManager resourceManager,
            KubernetesClient kubernetesClient) {
        this.properties = properties;
        this.resourceManager = resourceManager;
        this.kubernetesClient = kubernetesClient;
    }

    @PostConstruct
    public void startWatching() {
        logger.info("Starting Config Reloader service");

        scanAndUpdateWorkloads(kubernetesClient);

        // Watch for changes
        logger.info("Setting up watchers for deployments and statefulsets");
        kubernetesClient
                .apps()
                .deployments()
                .inAnyNamespace()
                .watch(new DeploymentWatcher(this, kubernetesClient));
        kubernetesClient
                .apps()
                .statefulSets()
                .inAnyNamespace()
                .watch(new StatefulSetWatcher(this, kubernetesClient));

        logger.info(
                "Watching {} secrets and {} configmaps for enabled deployments/statefulsets",
                resourceManager.getTotalSecrets(),
                resourceManager.getTotalConfigMaps());
    }

    private void scanAndUpdateWorkloads(KubernetesClient client) {
        logger.info("Scanning existing deployments and statefulsets");
        // Scan deployments with enabled annotation
        List<Deployment> deployments =
                client.apps().deployments().inAnyNamespace().list().getItems();
        logger.info("Found {} deployments", deployments.size());
        for (Deployment deployment : deployments) {
            updateDeployment(deployment, client);
        }

        // Similarly for StatefulSets
        List<StatefulSet> statefulSets =
                client.apps().statefulSets().inAnyNamespace().list().getItems();
        logger.info("Found {} statefulsets", statefulSets.size());
        for (StatefulSet statefulSet : statefulSets) {
            updateStatefulSet(statefulSet, client);
        }
    }

    @Timed(
            value = "resourcewatcher.deployment.update",
            description = "Time taken to update a deployment")
    public void updateDeployment(Deployment deployment, KubernetesClient client) {
        if (shouldWatchWorkload(deployment.getMetadata().getAnnotations())) {
            resourceManager.updateDeployment(deployment, client);
        } else {
            // If previously watched, remove it
            String deploymentId =
                    deployment.getMetadata().getNamespace()
                            + "/"
                            + deployment.getMetadata().getName();
            resourceManager.removeDeployment(deploymentId);
        }
    }

    @Timed(
            value = "resourcewatcher.statefulset.update",
            description = "Time taken to update a statefulset")
    public void updateStatefulSet(StatefulSet statefulSet, KubernetesClient client) {
        if (shouldWatchWorkload(statefulSet.getMetadata().getAnnotations())) {
            resourceManager.updateStatefulSet(statefulSet, client);
        } else {
            String deploymentId =
                    statefulSet.getMetadata().getNamespace()
                            + "/"
                            + statefulSet.getMetadata().getName();
            resourceManager.removeDeployment(deploymentId);
        }
    }

    private boolean shouldWatchWorkload(Map<String, String> annotations) {
        return switch (properties.getWatchMode()) {
            case "all" -> true;
            case "annotation" ->
                    annotations != null
                            && "true".equals(annotations.get(properties.getEnabledAnnotation()));
            default -> false;
        };
    }

    public void removeDeployment(String deploymentId) {
        resourceManager.removeDeployment(deploymentId);
    }
}
