package io.github.martinwitt.configreloader.manager;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.martinwitt.configreloader.model.ResourceType;
import io.github.martinwitt.configreloader.model.WatchedResource;
import io.github.martinwitt.configreloader.util.PodRestarter;
import io.github.martinwitt.configreloader.util.ResourceReferenceFinder;
import io.github.martinwitt.configreloader.watcher.ConfigMapWatcher;
import io.github.martinwitt.configreloader.watcher.SecretWatcher;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Manages the mapping of deployments to resources and handles watching/unwatching. */
@Component
public class ResourceManager {

    private final Map<String, Set<String>> deploymentToResourceKeys = new HashMap<>();
    private final Map<String, WatchedResource> resources = new HashMap<>();
    private final Set<String> watchingKeys = new HashSet<>();
    private final ResourceReferenceFinder finder;
    private final PodRestarter restarter;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    public ResourceManager(ResourceReferenceFinder finder, PodRestarter restarter) {
        this.finder = finder;
        this.restarter = restarter;
    }

    public void updateDeployment(Deployment deployment, KubernetesClient client) {
        updateWorkload(deployment, client);
    }

    public void updateStatefulSet(StatefulSet statefulSet, KubernetesClient client) {
        updateWorkload(statefulSet, client);
    }

    private void updateWorkload(Object workload, KubernetesClient client) {
        String deploymentId;
        String ns;
        Set<String> secrets;
        Set<String> configMaps;

        if (workload instanceof Deployment deployment) {
            deploymentId =
                    deployment.getMetadata().getNamespace()
                            + "/"
                            + deployment.getMetadata().getName();
            ns = deployment.getMetadata().getNamespace();
            secrets = finder.findReferencedSecrets(deployment);
            configMaps = finder.findReferencedConfigMaps(deployment);
        } else if (workload instanceof StatefulSet statefulSet) {
            deploymentId =
                    statefulSet.getMetadata().getNamespace()
                            + "/"
                            + statefulSet.getMetadata().getName();
            ns = statefulSet.getMetadata().getNamespace();
            secrets = finder.findReferencedSecrets(statefulSet);
            configMaps = finder.findReferencedConfigMaps(statefulSet);
        } else {
            return;
        }

        removeOldKeys(deploymentId);

        addResources(ns, secrets, ResourceType.SECRET, deploymentId, client);
        addResources(ns, configMaps, ResourceType.CONFIGMAP, deploymentId, client);
    }

    public void removeDeployment(String deploymentId) {
        removeOldKeys(deploymentId);
    }

    private void removeOldKeys(String deploymentId) {
        Set<String> oldKeys = deploymentToResourceKeys.get(deploymentId);
        if (oldKeys != null) {
            for (String key : oldKeys) {
                WatchedResource wr = resources.get(key);
                if (wr != null) {
                    List<String> newDeps =
                            wr.deploymentNames().stream()
                                    .filter(d -> !d.equals(deploymentId))
                                    .toList();
                    if (newDeps.isEmpty()) {
                        resources.remove(key);
                        watchingKeys.remove(key);
                        logger.info(
                                "Removed {} {}/{} as no deployments are watching it",
                                wr.type().name().toLowerCase(),
                                wr.namespace(),
                                wr.name());
                    } else {
                        resources.put(
                                key,
                                new WatchedResource(wr.namespace(), wr.name(), wr.type(), newDeps));
                        logger.info(
                                "Removed deployment {} from {} {}/{}",
                                deploymentId,
                                wr.type().name().toLowerCase(),
                                wr.namespace(),
                                wr.name());
                    }
                }
            }
        }
        deploymentToResourceKeys.remove(deploymentId);
    }

    private void addResources(
            String ns,
            Set<String> resourceNames,
            ResourceType type,
            String deploymentId,
            KubernetesClient client) {
        for (String name : resourceNames) {
            String key = ns + "/" + name + "/" + type;
            WatchedResource existing = resources.get(key);
            if (existing == null) {
                WatchedResource wr = new WatchedResource(ns, name, type, List.of(deploymentId));
                resources.put(key, wr);
                logger.info(
                        "Added new {} {}/{} for deployment {}",
                        type.name().toLowerCase(),
                        ns,
                        name,
                        deploymentId);
                if (!watchingKeys.contains(key)) {
                    watchingKeys.add(key);
                    watchResource(wr, client);
                }
            } else {
                resources.put(key, existing.addDeployment(deploymentId));
                logger.info(
                        "Added deployment {} to existing {} {}/{}",
                        deploymentId,
                        type.name().toLowerCase(),
                        ns,
                        name);
            }
        }
        deploymentToResourceKeys.put(
                deploymentId,
                resourceNames.stream()
                        .map(name -> ns + "/" + name + "/" + type)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private void watchResource(WatchedResource resource, KubernetesClient client) {
        if (resource.type() == ResourceType.SECRET) {
            logger.info(
                    "Starting to watch secret: {}/{} for deployments: {}",
                    resource.namespace(),
                    resource.name(),
                    resource.deploymentNames());
            client.secrets()
                    .inNamespace(resource.namespace())
                    .withName(resource.name())
                    .watch(new SecretWatcher(resource, client, this));
        } else {
            logger.info(
                    "Starting to watch configmap: {}/{} for deployments: {}",
                    resource.namespace(),
                    resource.name(),
                    resource.deploymentNames());
            client.configMaps()
                    .inNamespace(resource.namespace())
                    .withName(resource.name())
                    .watch(new ConfigMapWatcher(resource, client, this));
        }
    }

    public void restartPodsForResource(WatchedResource resource, KubernetesClient client) {
        for (String deploymentId : resource.deploymentNames()) {
            restarter.restartPodsForWorkload(deploymentId, client);
        }
    }

    public int getTotalSecrets() {
        return (int)
                resources.values().stream().filter(r -> r.type() == ResourceType.SECRET).count();
    }

    public int getTotalConfigMaps() {
        return (int)
                resources.values().stream().filter(r -> r.type() == ResourceType.CONFIGMAP).count();
    }

    public Map<String, WatchedResource> getWatchedResources() {
        return Map.copyOf(resources);
    }
}
