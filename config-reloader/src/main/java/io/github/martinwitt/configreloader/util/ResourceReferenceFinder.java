package io.github.martinwitt.configreloader.util;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Utility class for finding referenced secrets and configmaps in Kubernetes workloads. */
@Component
public class ResourceReferenceFinder {

    public Set<String> findReferencedSecrets(Deployment deployment) {
        return findReferencedSecrets(deployment.getSpec().getTemplate().getSpec());
    }

    public Set<String> findReferencedSecrets(StatefulSet statefulSet) {
        return findReferencedSecrets(statefulSet.getSpec().getTemplate().getSpec());
    }

    public Set<String> findReferencedSecrets(PodSpec spec) {
        Set<String> secrets = new HashSet<>();
        if (spec.getContainers() != null) {
            for (Container container : spec.getContainers()) {
                // Env
                if (container.getEnv() != null) {
                    for (EnvVar env : container.getEnv()) {
                        if (env.getValueFrom() != null
                                && env.getValueFrom().getSecretKeyRef() != null) {
                            secrets.add(env.getValueFrom().getSecretKeyRef().getName());
                        }
                    }
                }
            }
        }
        // Volumes
        if (spec.getVolumes() != null) {
            for (Volume volume : spec.getVolumes()) {
                if (volume.getSecret() != null) {
                    secrets.add(volume.getSecret().getSecretName());
                }
            }
        }
        return secrets;
    }

    public Set<String> findReferencedConfigMaps(Deployment deployment) {
        return findReferencedConfigMaps(deployment.getSpec().getTemplate().getSpec());
    }

    public Set<String> findReferencedConfigMaps(StatefulSet statefulSet) {
        return findReferencedConfigMaps(statefulSet.getSpec().getTemplate().getSpec());
    }

    public Set<String> findReferencedConfigMaps(PodSpec spec) {
        Set<String> configMaps = new HashSet<>();
        if (spec.getContainers() != null) {
            for (Container container : spec.getContainers()) {
                // Env
                if (container.getEnv() != null) {
                    for (EnvVar env : container.getEnv()) {
                        if (env.getValueFrom() != null
                                && env.getValueFrom().getConfigMapKeyRef() != null) {
                            configMaps.add(env.getValueFrom().getConfigMapKeyRef().getName());
                        }
                    }
                }
            }
        }
        // Volumes
        if (spec.getVolumes() != null) {
            for (Volume volume : spec.getVolumes()) {
                if (volume.getConfigMap() != null) {
                    configMaps.add(volume.getConfigMap().getName());
                }
            }
        }
        return configMaps;
    }
}
