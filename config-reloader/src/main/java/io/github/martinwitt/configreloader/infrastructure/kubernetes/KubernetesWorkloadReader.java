package io.github.martinwitt.configreloader.infrastructure.kubernetes;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.github.martinwitt.configreloader.ConfigReloaderProperties;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceType;
import io.github.martinwitt.configreloader.domain.model.WorkloadConfiguration;
import io.github.martinwitt.configreloader.domain.port.WorkloadReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Kubernetes adapter for reading workload configuration dependencies. */
@Component
public class KubernetesWorkloadReader implements WorkloadReader {

    private final ConfigReloaderProperties properties;

    public KubernetesWorkloadReader(ConfigReloaderProperties properties) {
        this.properties = properties;
    }

    @Override
    public Set<ConfigResourceId> extractConfigDependencies(WorkloadConfiguration workloadConfig) {
        // This method is not used in this implementation as extraction happens in the informer
        // handlers
        return workloadConfig.configDependencies();
    }

    @Override
    public boolean shouldWatch(Map<String, String> annotations) {
        return switch (properties.getWatchMode()) {
            case "all" -> true;
            case "annotation" ->
                    annotations != null
                            && "true".equals(annotations.get(properties.getEnabledAnnotation()));
            default -> false;
        };
    }

    /** Extract all configuration resource references from a pod spec. */
    public Set<ConfigResourceId> extractConfigReferences(String namespace, PodSpec podSpec) {
        Set<ConfigResourceId> references = new HashSet<>();

        if (podSpec == null) {
            return references;
        }

        // Extract from containers
        if (podSpec.getContainers() != null) {
            for (Container container : podSpec.getContainers()) {
                references.addAll(extractFromContainer(namespace, container));
            }
        }

        // Extract from init containers
        if (podSpec.getInitContainers() != null) {
            for (Container container : podSpec.getInitContainers()) {
                references.addAll(extractFromContainer(namespace, container));
            }
        }

        // Extract from volumes
        if (podSpec.getVolumes() != null) {
            for (Volume volume : podSpec.getVolumes()) {
                references.addAll(extractFromVolume(namespace, volume));
            }
        }

        return references;
    }

    private Set<ConfigResourceId> extractFromContainer(String namespace, Container container) {
        Set<ConfigResourceId> references = new HashSet<>();

        if (container.getEnv() != null) {
            for (EnvVar env : container.getEnv()) {
                if (env.getValueFrom() != null) {
                    if (env.getValueFrom().getSecretKeyRef() != null) {
                        String secretName = env.getValueFrom().getSecretKeyRef().getName();
                        references.add(
                                new ConfigResourceId(
                                        namespace, secretName, ConfigResourceType.SECRET));
                    }
                    if (env.getValueFrom().getConfigMapKeyRef() != null) {
                        String configMapName = env.getValueFrom().getConfigMapKeyRef().getName();
                        references.add(
                                new ConfigResourceId(
                                        namespace, configMapName, ConfigResourceType.CONFIGMAP));
                    }
                }
            }
        }

        if (container.getEnvFrom() != null) {
            container
                    .getEnvFrom()
                    .forEach(
                            envFromSource -> {
                                if (envFromSource.getSecretRef() != null) {
                                    String secretName = envFromSource.getSecretRef().getName();
                                    references.add(
                                            new ConfigResourceId(
                                                    namespace,
                                                    secretName,
                                                    ConfigResourceType.SECRET));
                                }
                                if (envFromSource.getConfigMapRef() != null) {
                                    String configMapName =
                                            envFromSource.getConfigMapRef().getName();
                                    references.add(
                                            new ConfigResourceId(
                                                    namespace,
                                                    configMapName,
                                                    ConfigResourceType.CONFIGMAP));
                                }
                            });
        }

        return references;
    }

    private Set<ConfigResourceId> extractFromVolume(String namespace, Volume volume) {
        Set<ConfigResourceId> references = new HashSet<>();

        if (volume.getSecret() != null) {
            String secretName = volume.getSecret().getSecretName();
            if (secretName != null) {
                references.add(
                        new ConfigResourceId(namespace, secretName, ConfigResourceType.SECRET));
            }
        }

        if (volume.getConfigMap() != null) {
            String configMapName = volume.getConfigMap().getName();
            if (configMapName != null) {
                references.add(
                        new ConfigResourceId(
                                namespace, configMapName, ConfigResourceType.CONFIGMAP));
            }
        }

        if (volume.getProjected() != null && volume.getProjected().getSources() != null) {
            volume.getProjected()
                    .getSources()
                    .forEach(
                            source -> {
                                if (source.getSecret() != null) {
                                    String secretName = source.getSecret().getName();
                                    if (secretName != null) {
                                        references.add(
                                                new ConfigResourceId(
                                                        namespace,
                                                        secretName,
                                                        ConfigResourceType.SECRET));
                                    }
                                }
                                if (source.getConfigMap() != null) {
                                    String configMapName = source.getConfigMap().getName();
                                    if (configMapName != null) {
                                        references.add(
                                                new ConfigResourceId(
                                                        namespace,
                                                        configMapName,
                                                        ConfigResourceType.CONFIGMAP));
                                    }
                                }
                            });
        }

        return references;
    }
}
