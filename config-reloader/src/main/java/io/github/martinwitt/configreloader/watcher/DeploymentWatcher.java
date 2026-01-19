package io.github.martinwitt.configreloader.watcher;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.github.martinwitt.configreloader.ResourceWatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeploymentWatcher implements Watcher<Deployment> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ResourceWatcherService resourceWatcherService;
    private final KubernetesClient client;

    public DeploymentWatcher(
            ResourceWatcherService resourceWatcherService, KubernetesClient client) {
        this.resourceWatcherService = resourceWatcherService;
        this.client = client;
    }

    @Override
    public void eventReceived(Action action, Deployment deployment) {
        if (action == Action.ADDED || action == Action.MODIFIED) {
            resourceWatcherService.updateDeployment(deployment, client);
        } else if (action == Action.DELETED) {
            String deploymentId =
                    deployment.getMetadata().getNamespace()
                            + "/"
                            + deployment.getMetadata().getName();
            resourceWatcherService.removeDeployment(deploymentId);
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        logger.error("Deployment watcher closed", cause);
    }
}
