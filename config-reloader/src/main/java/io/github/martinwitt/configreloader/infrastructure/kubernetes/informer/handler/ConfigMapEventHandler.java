package io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.handler;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.github.martinwitt.configreloader.application.service.ConfigResourceUpdateService;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceType;
import io.micrometer.core.annotation.Counted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Event handler for ConfigMap informer events. */
public class ConfigMapEventHandler implements ResourceEventHandler<ConfigMap> {
    private static final Logger logger = LoggerFactory.getLogger(ConfigMapEventHandler.class);

    private final ConfigResourceUpdateService configResourceUpdateService;

    public ConfigMapEventHandler(ConfigResourceUpdateService configResourceUpdateService) {
        this.configResourceUpdateService = configResourceUpdateService;
    }

    @Override
    public void onAdd(ConfigMap configMap) {
        // We don't need to handle add events for config resources
        logger.trace(
                "ConfigMap added: {}/{}",
                configMap.getMetadata().getNamespace(),
                configMap.getMetadata().getName());
    }

    @Override
    @Counted(value = "informer.configmap.update", description = "Count of configmap update events")
    public void onUpdate(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
        String namespace = newConfigMap.getMetadata().getNamespace();
        String name = newConfigMap.getMetadata().getName();

        // Check if the resourceVersion changed (indicating actual data change)
        if (oldConfigMap != null
                && oldConfigMap
                        .getMetadata()
                        .getResourceVersion()
                        .equals(newConfigMap.getMetadata().getResourceVersion())) {
            logger.trace("ConfigMap {}/{} updated but resourceVersion unchanged", namespace, name);
            return;
        }

        logger.info("ConfigMap {}/{} was updated", namespace, name);

        ConfigResourceId resourceId =
                new ConfigResourceId(namespace, name, ConfigResourceType.CONFIGMAP);

        configResourceUpdateService.handleConfigResourceUpdate(resourceId);
    }

    @Override
    public void onDelete(ConfigMap configMap, boolean deletedFinalStateUnknown) {
        // ConfigMap deletion doesn't require pod restarts
        logger.debug(
                "ConfigMap deleted: {}/{}",
                configMap.getMetadata().getNamespace(),
                configMap.getMetadata().getName());
    }
}
