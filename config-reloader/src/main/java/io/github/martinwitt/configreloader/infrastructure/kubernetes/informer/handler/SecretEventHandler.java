package io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.handler;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.github.martinwitt.configreloader.application.service.ConfigResourceUpdateService;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceType;
import io.micrometer.core.annotation.Counted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Event handler for Secret informer events. */
public class SecretEventHandler implements ResourceEventHandler<Secret> {
    private static final Logger logger = LoggerFactory.getLogger(SecretEventHandler.class);

    private final ConfigResourceUpdateService configResourceUpdateService;

    public SecretEventHandler(ConfigResourceUpdateService configResourceUpdateService) {
        this.configResourceUpdateService = configResourceUpdateService;
    }

    @Override
    public void onAdd(Secret secret) {
        // We don't need to handle add events for config resources
        logger.trace(
                "Secret added: {}/{}",
                secret.getMetadata().getNamespace(),
                secret.getMetadata().getName());
    }

    @Override
    @Counted(value = "informer.secret.update", description = "Count of secret update events")
    public void onUpdate(Secret oldSecret, Secret newSecret) {
        String namespace = newSecret.getMetadata().getNamespace();
        String name = newSecret.getMetadata().getName();

        // Check if the resourceVersion changed (indicating actual data change)
        if (oldSecret != null
                && oldSecret
                        .getMetadata()
                        .getResourceVersion()
                        .equals(newSecret.getMetadata().getResourceVersion())) {
            logger.trace("Secret {}/{} updated but resourceVersion unchanged", namespace, name);
            return;
        }

        logger.info("Secret {}/{} was updated", namespace, name);

        ConfigResourceId resourceId =
                new ConfigResourceId(namespace, name, ConfigResourceType.SECRET);

        configResourceUpdateService.handleConfigResourceUpdate(resourceId);
    }

    @Override
    public void onDelete(Secret secret, boolean deletedFinalStateUnknown) {
        // Secret deletion doesn't require pod restarts
        logger.debug(
                "Secret deleted: {}/{}",
                secret.getMetadata().getNamespace(),
                secret.getMetadata().getName());
    }
}
