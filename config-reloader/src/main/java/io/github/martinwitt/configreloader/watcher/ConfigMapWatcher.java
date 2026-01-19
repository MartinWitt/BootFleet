package io.github.martinwitt.configreloader.watcher;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.github.martinwitt.configreloader.manager.ResourceManager;
import io.github.martinwitt.configreloader.model.WatchedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMapWatcher implements Watcher<ConfigMap> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final WatchedResource resource;
    private final KubernetesClient client;
    private final ResourceManager resourceManager;

    public ConfigMapWatcher(
            WatchedResource resource, KubernetesClient client, ResourceManager resourceManager) {
        this.resource = resource;
        this.client = client;
        this.resourceManager = resourceManager;
    }

    @Override
    public void eventReceived(Action action, ConfigMap configMapResource) {
        if (action == Action.MODIFIED) {
            logger.info(
                    "ConfigMap {} modified, restarting deployments: {}",
                    resource.name(),
                    resource.deploymentNames());
            resourceManager.restartPodsForResource(resource, client);
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        logger.error("Watcher closed for configmap {}", resource.name(), cause);
    }
}
