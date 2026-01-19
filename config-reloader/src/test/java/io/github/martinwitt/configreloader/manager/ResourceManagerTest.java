package io.github.martinwitt.configreloader.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.*;
import io.github.martinwitt.configreloader.model.ResourceType;
import io.github.martinwitt.configreloader.model.WatchedResource;
import io.github.martinwitt.configreloader.util.PodRestarter;
import io.github.martinwitt.configreloader.util.ResourceReferenceFinder;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceManagerTest {

    @Mock
    private ResourceReferenceFinder finder;

    @Mock
    private PodRestarter restarter;

    @Mock
    private KubernetesClient client;

    @Mock
    private MixedOperation<?, ?, ?> secretsOperation;

    @Mock
    private MixedOperation<?, ?, ?> configMapsOperation;

    @Mock
    private NonNamespaceOperation<?, ?, ?> namespacedSecretsOperation;

    @Mock
    private NonNamespaceOperation<?, ?, ?> namespacedConfigMapsOperation;

    @Mock
    private Resource<?, ?> secretResource;

    @Mock
    private Resource<?, ?> configMapResource;

    @Mock
    private Watch watch;

    private ResourceManager resourceManager;

    @BeforeEach
    void setUp() {
        resourceManager = new ResourceManager(finder, restarter);

        // Setup mock chain for secrets
        when(client.secrets()).thenReturn((MixedOperation) secretsOperation);
        when(secretsOperation.inNamespace(anyString()))
                .thenReturn((NonNamespaceOperation) namespacedSecretsOperation);
        when(namespacedSecretsOperation.withName(anyString())).thenReturn((Resource) secretResource);
        when(secretResource.watch(any())).thenReturn(watch);

        // Setup mock chain for configmaps
        when(client.configMaps()).thenReturn((MixedOperation) configMapsOperation);
        when(configMapsOperation.inNamespace(anyString()))
                .thenReturn((NonNamespaceOperation) namespacedConfigMapsOperation);
        when(namespacedConfigMapsOperation.withName(anyString()))
                .thenReturn((Resource) configMapResource);
        when(configMapResource.watch(any())).thenReturn(watch);
    }

    @Test
    void testUpdateDeploymentAddsNewResource() {
        // Given
        Deployment deployment = createDeployment("default", "test-deployment");
        when(finder.findReferencedSecrets(deployment)).thenReturn(Set.of("my-secret"));
        when(finder.findReferencedConfigMaps(deployment)).thenReturn(Set.of());

        // When
        resourceManager.updateDeployment(deployment, client);

        // Then
        Map<String, WatchedResource> resources = resourceManager.getWatchedResources();
        assertEquals(1, resources.size());
        WatchedResource resource = resources.get("default/my-secret/SECRET");
        assertNotNull(resource);
        assertEquals("default", resource.namespace());
        assertEquals("my-secret", resource.name());
        assertEquals(ResourceType.SECRET, resource.type());
        assertTrue(resource.deploymentNames().contains("default/test-deployment"));
    }

    @Test
    void testUpdateDeploymentPreventsD
uplicates() {
        // Given
        Deployment deployment = createDeployment("default", "test-deployment");
        when(finder.findReferencedSecrets(deployment)).thenReturn(Set.of("my-secret"));
        when(finder.findReferencedConfigMaps(deployment)).thenReturn(Set.of());

        // When - call twice with same deployment
        resourceManager.updateDeployment(deployment, client);
        resourceManager.updateDeployment(deployment, client);

        // Then - should not have duplicate deployment IDs
        Map<String, WatchedResource> resources = resourceManager.getWatchedResources();
        WatchedResource resource = resources.get("default/my-secret/SECRET");
        assertNotNull(resource);
        assertEquals(1, resource.deploymentNames().size());
        assertEquals("default/test-deployment", resource.deploymentNames().get(0));
    }

    @Test
    void testUpdateDeploymentWithBothSecretsAndConfigMaps() {
        // Given
        Deployment deployment = createDeployment("default", "test-deployment");
        when(finder.findReferencedSecrets(deployment)).thenReturn(Set.of("my-secret"));
        when(finder.findReferencedConfigMaps(deployment)).thenReturn(Set.of("my-configmap"));

        // When
        resourceManager.updateDeployment(deployment, client);

        // Then - should track both resources
        Map<String, WatchedResource> resources = resourceManager.getWatchedResources();
        assertEquals(2, resources.size());
        assertTrue(resources.containsKey("default/my-secret/SECRET"));
        assertTrue(resources.containsKey("default/my-configmap/CONFIGMAP"));
    }

    @Test
    void testRemoveDeploymentCleansUpResources() {
        // Given
        Deployment deployment = createDeployment("default", "test-deployment");
        when(finder.findReferencedSecrets(deployment)).thenReturn(Set.of("my-secret"));
        when(finder.findReferencedConfigMaps(deployment)).thenReturn(Set.of());
        resourceManager.updateDeployment(deployment, client);

        // When
        resourceManager.removeDeployment("default/test-deployment");

        // Then
        Map<String, WatchedResource> resources = resourceManager.getWatchedResources();
        assertEquals(0, resources.size());
        verify(watch).close(); // Watch should be closed
    }

    @Test
    void testRemoveDeploymentKeepsResourceIfOtherDeploymentsReference() {
        // Given
        Deployment deployment1 = createDeployment("default", "deployment1");
        Deployment deployment2 = createDeployment("default", "deployment2");
        when(finder.findReferencedSecrets(deployment1)).thenReturn(Set.of("shared-secret"));
        when(finder.findReferencedConfigMaps(deployment1)).thenReturn(Set.of());
        when(finder.findReferencedSecrets(deployment2)).thenReturn(Set.of("shared-secret"));
        when(finder.findReferencedConfigMaps(deployment2)).thenReturn(Set.of());

        resourceManager.updateDeployment(deployment1, client);
        resourceManager.updateDeployment(deployment2, client);

        // When - remove first deployment
        resourceManager.removeDeployment("default/deployment1");

        // Then - resource should still exist for deployment2
        Map<String, WatchedResource> resources = resourceManager.getWatchedResources();
        assertEquals(1, resources.size());
        WatchedResource resource = resources.get("default/shared-secret/SECRET");
        assertNotNull(resource);
        assertEquals(1, resource.deploymentNames().size());
        assertEquals("default/deployment2", resource.deploymentNames().get(0));
        verify(watch, never()).close(); // Watch should not be closed
    }

    @Test
    void testGetWatchedResourceReturnsCorrectResource() {
        // Given
        Deployment deployment = createDeployment("default", "test-deployment");
        when(finder.findReferencedSecrets(deployment)).thenReturn(Set.of("my-secret"));
        when(finder.findReferencedConfigMaps(deployment)).thenReturn(Set.of());
        resourceManager.updateDeployment(deployment, client);

        // When
        WatchedResource resource =
                resourceManager.getWatchedResource("default", "my-secret", ResourceType.SECRET);

        // Then
        assertNotNull(resource);
        assertEquals("default", resource.namespace());
        assertEquals("my-secret", resource.name());
        assertEquals(ResourceType.SECRET, resource.type());
    }

    @Test
    void testGetWatchedResourceReturnsNullForNonExistent() {
        // When
        WatchedResource resource =
                resourceManager.getWatchedResource("default", "non-existent", ResourceType.SECRET);

        // Then
        assertNull(resource);
    }

    @Test
    void testGetTotalSecretsAndConfigMaps() {
        // Given
        Deployment deployment = createDeployment("default", "test-deployment");
        when(finder.findReferencedSecrets(deployment)).thenReturn(Set.of("secret1", "secret2"));
        when(finder.findReferencedConfigMaps(deployment)).thenReturn(Set.of("configmap1"));
        resourceManager.updateDeployment(deployment, client);

        // When/Then
        assertEquals(2, resourceManager.getTotalSecrets());
        assertEquals(1, resourceManager.getTotalConfigMaps());
    }

    @Test
    void testRestartPodsForResource() {
        // Given
        WatchedResource resource =
                new WatchedResource(
                        "default",
                        "my-secret",
                        ResourceType.SECRET,
                        java.util.List.of("default/deployment1", "default/deployment2"));

        // When
        resourceManager.restartPodsForResource(resource, client);

        // Then
        verify(restarter).restartPodsForWorkload("default/deployment1", client);
        verify(restarter).restartPodsForWorkload("default/deployment2", client);
    }

    private Deployment createDeployment(String namespace, String name) {
        ObjectMeta metadata =
                new ObjectMetaBuilder().withNamespace(namespace).withName(name).build();
        return new DeploymentBuilder().withMetadata(metadata).build();
    }
}
