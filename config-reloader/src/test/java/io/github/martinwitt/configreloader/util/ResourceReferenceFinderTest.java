package io.github.martinwitt.configreloader.util;

import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceReferenceFinderTest {

    private ResourceReferenceFinder finder;

    @BeforeEach
    void setUp() {
        finder = new ResourceReferenceFinder();
    }

    @Test
    void testFindReferencedSecretsFromEnvVar() {
        // Given
        Deployment deployment = createDeploymentWithSecretEnvVar("my-secret");

        // When
        Set<String> secrets = finder.findReferencedSecrets(deployment);

        // Then
        assertEquals(1, secrets.size());
        assertTrue(secrets.contains("my-secret"));
    }

    @Test
    void testFindReferencedSecretsFromVolume() {
        // Given
        Deployment deployment = createDeploymentWithSecretVolume("volume-secret");

        // When
        Set<String> secrets = finder.findReferencedSecrets(deployment);

        // Then
        assertEquals(1, secrets.size());
        assertTrue(secrets.contains("volume-secret"));
    }

    @Test
    void testFindReferencedSecretsMultiple() {
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
                        .withSecret(new SecretVolumeSourceBuilder().withSecretName("volume-secret").build())
                        .build();

        Deployment deployment = createDeployment(List.of(envVar), List.of(volume));

        // When
        Set<String> secrets = finder.findReferencedSecrets(deployment);

        // Then
        assertEquals(2, secrets.size());
        assertTrue(secrets.contains("env-secret"));
        assertTrue(secrets.contains("volume-secret"));
    }

    @Test
    void testFindReferencedSecretsWithNoSecrets() {
        // Given
        Deployment deployment = createEmptyDeployment();

        // When
        Set<String> secrets = finder.findReferencedSecrets(deployment);

        // Then
        assertTrue(secrets.isEmpty());
    }

    @Test
    void testFindReferencedConfigMapsFromEnvVar() {
        // Given
        Deployment deployment = createDeploymentWithConfigMapEnvVar("my-configmap");

        // When
        Set<String> configMaps = finder.findReferencedConfigMaps(deployment);

        // Then
        assertEquals(1, configMaps.size());
        assertTrue(configMaps.contains("my-configmap"));
    }

    @Test
    void testFindReferencedConfigMapsFromVolume() {
        // Given
        Deployment deployment = createDeploymentWithConfigMapVolume("volume-configmap");

        // When
        Set<String> configMaps = finder.findReferencedConfigMaps(deployment);

        // Then
        assertEquals(1, configMaps.size());
        assertTrue(configMaps.contains("volume-configmap"));
    }

    @Test
    void testFindReferencedConfigMapsMultiple() {
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

        // When
        Set<String> configMaps = finder.findReferencedConfigMaps(deployment);

        // Then
        assertEquals(2, configMaps.size());
        assertTrue(configMaps.contains("env-configmap"));
        assertTrue(configMaps.contains("volume-configmap"));
    }

    @Test
    void testFindReferencedConfigMapsWithNoConfigMaps() {
        // Given
        Deployment deployment = createEmptyDeployment();

        // When
        Set<String> configMaps = finder.findReferencedConfigMaps(deployment);

        // Then
        assertTrue(configMaps.isEmpty());
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
                        .withSecret(new SecretVolumeSourceBuilder().withSecretName(secretName).build())
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
                        .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(configMapName).build())
                        .build();
        return createDeployment(List.of(), List.of(volume));
    }

    private Deployment createDeployment(List<EnvVar> envVars, List<Volume> volumes) {
        Container container =
                new ContainerBuilder().withName("test-container").withEnv(envVars).build();

        PodSpec podSpec =
                new PodSpecBuilder().withContainers(container).withVolumes(volumes).build();

        PodTemplateSpec template =
                new PodTemplateSpecBuilder().withSpec(podSpec).build();

        DeploymentSpec spec = new DeploymentSpecBuilder().withTemplate(template).build();

        return new DeploymentBuilder().withSpec(spec).build();
    }

    private Deployment createEmptyDeployment() {
        Container container = new ContainerBuilder().withName("test-container").build();
        PodSpec podSpec = new PodSpecBuilder().withContainers(container).build();
        PodTemplateSpec template = new PodTemplateSpecBuilder().withSpec(podSpec).build();
        DeploymentSpec spec = new DeploymentSpecBuilder().withTemplate(template).build();
        return new DeploymentBuilder().withSpec(spec).build();
    }
}
