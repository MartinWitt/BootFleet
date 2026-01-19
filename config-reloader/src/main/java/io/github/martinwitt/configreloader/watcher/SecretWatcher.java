package io.github.martinwitt.configreloader.watcher;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.github.martinwitt.configreloader.manager.ResourceManager;
import io.github.martinwitt.configreloader.model.WatchedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretWatcher implements Watcher<Secret> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final WatchedResource resource;
    private final KubernetesClient client;
    private final ResourceManager resourceManager;

    public SecretWatcher(
            WatchedResource resource, KubernetesClient client, ResourceManager resourceManager) {
        this.resource = resource;
        this.client = client;
        this.resourceManager = resourceManager;
    }

    @Override
    public void eventReceived(Action action, Secret secretResource) {
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
                    "Secret {} modified, restarting deployments: {}",
                    currentResource.name(),
                    currentResource.deploymentNames());
            resourceManager.restartPodsForResource(currentResource, client);
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        logger.error("Watcher closed for secret {}", resource.name(), cause);
    }
}
