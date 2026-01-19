package io.github.martinwitt.configreloader.watcher;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.github.martinwitt.configreloader.ResourceWatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatefulSetWatcher implements Watcher<StatefulSet> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ResourceWatcherService resourceWatcherService;
    private final KubernetesClient client;

    public StatefulSetWatcher(
            ResourceWatcherService resourceWatcherService, KubernetesClient client) {
        this.resourceWatcherService = resourceWatcherService;
        this.client = client;
    }

    @Override
    public void eventReceived(Action action, StatefulSet statefulSet) {
        if (action == Action.ADDED || action == Action.MODIFIED) {
            resourceWatcherService.updateStatefulSet(statefulSet, client);
        } else if (action == Action.DELETED) {
            String deploymentId =
                    statefulSet.getMetadata().getNamespace()
                            + "/"
                            + statefulSet.getMetadata().getName();
            resourceWatcherService.removeDeployment(deploymentId);
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        logger.error("StatefulSet watcher closed", cause);
    }
}
