package io.github.martinwitt.configreloader.infrastructure.kubernetes;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.github.martinwitt.configreloader.ConfigReloaderProperties;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubernetesWorkloadReaderTest {

    private KubernetesWorkloadReader reader;
    private ConfigReloaderProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ConfigReloaderProperties();
        properties.setWatchMode("annotation");
        properties.setEnabledAnnotation("config-reloader.io/enabled");
        reader = new KubernetesWorkloadReader(properties);
    }

    @Test
    void testExtractConfigReferencesFromSecretEnvVar() {
        // Given
        Deployment deployment = createDeploymentWithSecretEnvVar("my-secret");
        String namespace = "default";

        // When
        Set<ConfigResourceId> resources =
                reader.extractConfigReferences(
                        namespace, deployment.getSpec().getTemplate().getSpec());

        // Then
        assertEquals(1, resources.size());
        ConfigResourceId secretId =
                new ConfigResourceId(namespace, "my-secret", ConfigResourceType.SECRET);
        assertTrue(resources.contains(secretId));
    }

    @Test
    void testExtractConfigReferencesFromSecretVolume() {
        // Given
        Deployment deployment = createDeploymentWithSecretVolume("volume-secret");
        String namespace = "default";

        // When
        Set<ConfigResourceId> resources =
                reader.extractConfigReferences(
                        namespace, deployment.getSpec().getTemplate().getSpec());

        // Then
        assertEquals(1, resources.size());
        ConfigResourceId secretId =
                new ConfigResourceId(namespace, "volume-secret", ConfigResourceType.SECRET);
        assertTrue(resources.contains(secretId));
    }

    @Test
    void testExtractConfigReferencesMultipleSecrets() {
        // Given
        EnvVar envVar =
                new EnvVarBuilder()
                        .withName("SECRET_KEY")
                        .withValueFrom(
                                new EnvVarSourceBuilder()
                                        .withSecretKeyRef(
                                                new SecretKeySelectorBuilder()
                                                        .withName("env-secret")
                                                        .withKey("key")
                                                        .build())
                                        .build())
                        .build();

        Volume volume =
                new VolumeBuilder()
                        .withName("secret-volume")
                        .withSecret(
                                new SecretVolumeSourceBuilder()
                                        .withSecretName("volume-secret")
                                        .build())
                        .build();

        Deployment deployment = createDeployment(List.of(envVar), List.of(volume));
        String namespace = "default";

        // When
        Set<ConfigResourceId> resources =
                reader.extractConfigReferences(
                        namespace, deployment.getSpec().getTemplate().getSpec());

        // Then
        assertEquals(2, resources.size());
        ConfigResourceId envSecretId =
                new ConfigResourceId(namespace, "env-secret", ConfigResourceType.SECRET);
        ConfigResourceId volSecretId =
                new ConfigResourceId(namespace, "volume-secret", ConfigResourceType.SECRET);
        assertTrue(resources.contains(envSecretId));
        assertTrue(resources.contains(volSecretId));
    }

    @Test
    void testExtractConfigReferencesWithNoSecrets() {
        // Given
        Deployment deployment = createEmptyDeployment();
        String namespace = "default";

        // When
        Set<ConfigResourceId> resources =
                reader.extractConfigReferences(
                        namespace, deployment.getSpec().getTemplate().getSpec());

        // Then
        assertTrue(resources.isEmpty());
    }

    @Test
    void testExtractConfigReferencesFromConfigMapEnvVar() {
        // Given
        Deployment deployment = createDeploymentWithConfigMapEnvVar("my-configmap");
        String namespace = "default";

        // When
        Set<ConfigResourceId> resources =
                reader.extractConfigReferences(
                        namespace, deployment.getSpec().getTemplate().getSpec());

        // Then
        assertEquals(1, resources.size());
        ConfigResourceId configMapId =
                new ConfigResourceId(namespace, "my-configmap", ConfigResourceType.CONFIGMAP);
        assertTrue(resources.contains(configMapId));
    }

    @Test
    void testExtractConfigReferencesFromConfigMapVolume() {
        // Given
        Deployment deployment = createDeploymentWithConfigMapVolume("volume-configmap");
        String namespace = "default";

        // When
        Set<ConfigResourceId> resources =
                reader.extractConfigReferences(
                        namespace, deployment.getSpec().getTemplate().getSpec());

        // Then
        assertEquals(1, resources.size());
        ConfigResourceId configMapId =
                new ConfigResourceId(namespace, "volume-configmap", ConfigResourceType.CONFIGMAP);
        assertTrue(resources.contains(configMapId));
    }

    @Test
    void testExtractConfigReferencesMultipleConfigMaps() {
        // Given
        EnvVar envVar =
                new EnvVarBuilder()
                        .withName("CONFIG_KEY")
                        .withValueFrom(
                                new EnvVarSourceBuilder()
                                        .withConfigMapKeyRef(
                                                new ConfigMapKeySelectorBuilder()
                                                        .withName("env-configmap")
                                                        .withKey("key")
                                                        .build())
                                        .build())
                        .build();

        Volume volume =
                new VolumeBuilder()
                        .withName("configmap-volume")
                        .withConfigMap(
                                new ConfigMapVolumeSourceBuilder()
                                        .withName("volume-configmap")
                                        .build())
                        .build();

        Deployment deployment = createDeployment(List.of(envVar), List.of(volume));
        String namespace = "default";

        // When
        Set<ConfigResourceId> resources =
                reader.extractConfigReferences(
                        namespace, deployment.getSpec().getTemplate().getSpec());

        // Then
        assertEquals(2, resources.size());
        ConfigResourceId envConfigMapId =
                new ConfigResourceId(namespace, "env-configmap", ConfigResourceType.CONFIGMAP);
        ConfigResourceId volConfigMapId =
                new ConfigResourceId(namespace, "volume-configmap", ConfigResourceType.CONFIGMAP);
        assertTrue(resources.contains(envConfigMapId));
        assertTrue(resources.contains(volConfigMapId));
    }

    @Test
    void testExtractConfigReferencesWithNoConfigMaps() {
        // Given
        Deployment deployment = createEmptyDeployment();
        String namespace = "default";

        // When
        Set<ConfigResourceId> resources =
                reader.extractConfigReferences(
                        namespace, deployment.getSpec().getTemplate().getSpec());

        // Then
        assertTrue(resources.isEmpty());
    }

    @Test
    void testShouldWatchWithAnnotationModeAndEnabledAnnotation() {
        // Given
        Map<String, String> annotations = Map.of("config-reloader.io/enabled", "true");

        // When
        boolean shouldWatch = reader.shouldWatch(annotations);

        // Then
        assertTrue(shouldWatch);
    }

    @Test
    void testShouldWatchWithAnnotationModeAndDisabledAnnotation() {
        // Given
        Map<String, String> annotations = Map.of("config-reloader.io/enabled", "false");

        // When
        boolean shouldWatch = reader.shouldWatch(annotations);

        // Then
        assertFalse(shouldWatch);
    }

    @Test
    void testShouldWatchWithAnnotationModeAndNoAnnotation() {
        // Given
        Map<String, String> annotations = Map.of();

        // When
        boolean shouldWatch = reader.shouldWatch(annotations);

        // Then
        assertFalse(shouldWatch);
    }

    @Test
    void testShouldWatchWithAllMode() {
        // Given
        properties.setWatchMode("all");
        Map<String, String> annotations = Map.of();

        // When
        boolean shouldWatch = reader.shouldWatch(annotations);

        // Then
        assertTrue(shouldWatch);
    }

    private Deployment createDeploymentWithSecretEnvVar(String secretName) {
        EnvVar envVar =
                new EnvVarBuilder()
                        .withName("SECRET_KEY")
                        .withValueFrom(
                                new EnvVarSourceBuilder()
                                        .withSecretKeyRef(
                                                new SecretKeySelectorBuilder()
                                                        .withName(secretName)
                                                        .withKey("key")
                                                        .build())
                                        .build())
                        .build();
        return createDeployment(List.of(envVar), List.of());
    }

    private Deployment createDeploymentWithSecretVolume(String secretName) {
        Volume volume =
                new VolumeBuilder()
                        .withName("secret-volume")
                        .withSecret(
                                new SecretVolumeSourceBuilder().withSecretName(secretName).build())
                        .build();
        return createDeployment(List.of(), List.of(volume));
    }

    private Deployment createDeploymentWithConfigMapEnvVar(String configMapName) {
        EnvVar envVar =
                new EnvVarBuilder()
                        .withName("CONFIG_KEY")
                        .withValueFrom(
                                new EnvVarSourceBuilder()
                                        .withConfigMapKeyRef(
                                                new ConfigMapKeySelectorBuilder()
                                                        .withName(configMapName)
                                                        .withKey("key")
                                                        .build())
                                        .build())
                        .build();
        return createDeployment(List.of(envVar), List.of());
    }

    private Deployment createDeploymentWithConfigMapVolume(String configMapName) {
        Volume volume =
                new VolumeBuilder()
                        .withName("configmap-volume")
                        .withConfigMap(
                                new ConfigMapVolumeSourceBuilder().withName(configMapName).build())
                        .build();
        return createDeployment(List.of(), List.of(volume));
    }

    private Deployment createDeployment(List<EnvVar> envVars, List<Volume> volumes) {
        Container container =
                new ContainerBuilder().withName("test-container").withEnv(envVars).build();

        PodSpec podSpec =
                new PodSpecBuilder().withContainers(container).withVolumes(volumes).build();

        PodTemplateSpec template = new PodTemplateSpecBuilder().withSpec(podSpec).build();

        DeploymentSpec spec = new DeploymentSpecBuilder().withTemplate(template).build();

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName("test-deployment")
                .withNamespace("default")
                .endMetadata()
                .withSpec(spec)
                .build();
    }

    private Deployment createEmptyDeployment() {
        Container container = new ContainerBuilder().withName("test-container").build();
        PodSpec podSpec = new PodSpecBuilder().withContainers(container).build();
        PodTemplateSpec template = new PodTemplateSpecBuilder().withSpec(podSpec).build();
        DeploymentSpec spec = new DeploymentSpecBuilder().withTemplate(template).build();
        return new DeploymentBuilder()
                .withNewMetadata()
                .withName("test-deployment")
                .withNamespace("default")
                .endMetadata()
                .withSpec(spec)
                .build();
    }
}
