package io.github.martinwitt.configreloader.watcher;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.github.martinwitt.configreloader.manager.ResourceManager;
import io.github.martinwitt.configreloader.manager.WatchRecoveryManager;
import io.github.martinwitt.configreloader.model.WatchedResource;
import io.micrometer.core.annotation.Counted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMapWatcher implements Watcher<ConfigMap> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final WatchedResource resource;
    private final KubernetesClient client;
    private final ResourceManager resourceManager;
    private final WatchRecoveryManager watchRecoveryManager;
    private final String resourceKey;

    public ConfigMapWatcher(
            WatchedResource resource,
            KubernetesClient client,
            ResourceManager resourceManager,
            WatchRecoveryManager watchRecoveryManager) {
        this.resource = resource;
        this.client = client;
        this.resourceManager = resourceManager;
        this.watchRecoveryManager = watchRecoveryManager;
        this.resourceKey = resource.namespace() + "/" + resource.name() + "/CONFIGMAP";
    }

    @Override
    @Counted(value = "watcher.configmap.event", description = "Count of configmap watch events")
    public void eventReceived(Action action, ConfigMap configMapResource) {
        if (action == Action.MODIFIED) {
            // Re-resolve the current WatchedResource from the ResourceManager to avoid
            // using a potentially stale snapshot captured when this watcher was created.
            WatchedResource currentResource =
                    resourceManager.getWatchedResource(
                            resource.namespace(), resource.name(), resource.type());
            if (currentResource == null) {
                currentResource = resource;
            }
            logger.info(
                    "ConfigMap {} modified, restarting deployments: {}",
                    currentResource.name(),
                    currentResource.deploymentNames());
            resourceManager.restartPodsForResource(currentResource, client);
        }
    }

    @Override
    @Counted(value = "watcher.configmap.closed", description = "Count of closed configmap watchers")
    public void onClose(WatcherException cause) {
        logger.error("Watcher closed for configmap {}", resource.name(), cause);
        watchRecoveryManager.handleWatcherException(
                resourceKey,
                cause,
                () -> resourceManager.restartWatch(resource, client));
    }
}
