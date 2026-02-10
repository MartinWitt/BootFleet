package io.github.martinwitt.configreloader.infrastructure.kubernetes.informer;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating and managing Kubernetes informers. This replaces the old Watcher-based
 * approach with the modern SharedIndexInformer API.
 */
@Component
public class InformerFactory {
    private static final Logger logger = LoggerFactory.getLogger(InformerFactory.class);
    private static final long RESYNC_PERIOD_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final SharedInformerFactory sharedInformerFactory;
    private SharedIndexInformer<Deployment> deploymentInformer;
    private SharedIndexInformer<StatefulSet> statefulSetInformer;
    private SharedIndexInformer<ConfigMap> configMapInformer;
    private SharedIndexInformer<Secret> secretInformer;

    public InformerFactory(KubernetesClient kubernetesClient) {
        this.sharedInformerFactory = kubernetesClient.informers();
    }

    public SharedIndexInformer<Deployment> getDeploymentInformer() {
        if (deploymentInformer == null) {
            deploymentInformer =
                    sharedInformerFactory.sharedIndexInformerFor(
                            Deployment.class, RESYNC_PERIOD_MILLIS);
            logger.info("Created Deployment informer");
        }
        return deploymentInformer;
    }

    public SharedIndexInformer<StatefulSet> getStatefulSetInformer() {
        if (statefulSetInformer == null) {
            statefulSetInformer =
                    sharedInformerFactory.sharedIndexInformerFor(
                            StatefulSet.class, RESYNC_PERIOD_MILLIS);
            logger.info("Created StatefulSet informer");
        }
        return statefulSetInformer;
    }

    public SharedIndexInformer<ConfigMap> getConfigMapInformer() {
        if (configMapInformer == null) {
            configMapInformer =
                    sharedInformerFactory.sharedIndexInformerFor(
                            ConfigMap.class, RESYNC_PERIOD_MILLIS);
            logger.info("Created ConfigMap informer");
        }
        return configMapInformer;
    }

    public SharedIndexInformer<Secret> getSecretInformer() {
        if (secretInformer == null) {
            secretInformer =
                    sharedInformerFactory.sharedIndexInformerFor(
                            Secret.class, RESYNC_PERIOD_MILLIS);
            logger.info("Created Secret informer");
        }
        return secretInformer;
    }

    /** Start all informers. */
    public void startAllInformers() {
        logger.info("Starting all informers...");
        sharedInformerFactory.startAllRegisteredInformers();
        logger.info("All informers started successfully");
    }

    /** Stop all informers gracefully. */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down informer factory...");
        try {
            sharedInformerFactory.stopAllRegisteredInformers();
            logger.info("All informers stopped successfully");
        } catch (Exception e) {
            logger.error("Error while stopping informers", e);
        }
    }
}
