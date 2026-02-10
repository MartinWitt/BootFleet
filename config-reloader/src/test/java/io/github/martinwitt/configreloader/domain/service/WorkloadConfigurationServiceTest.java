package io.github.martinwitt.configreloader.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.martinwitt.configreloader.domain.model.*;
import io.github.martinwitt.configreloader.domain.port.WorkloadReader;
import io.github.martinwitt.configreloader.domain.port.WorkloadRestarter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkloadConfigurationServiceTest {

    @Mock private ConfigResourceRepository repository;
    @Mock private WorkloadReader workloadReader;
    @Mock private WorkloadRestarter workloadRestarter;

    private WorkloadConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new WorkloadConfigurationService(repository, workloadReader, workloadRestarter);
    }

    @Test
    void testRegisterWorkloadWithNoExistingDependencies() {
        // Given
        WorkloadId workloadId = new WorkloadId("default", "my-deployment", WorkloadType.DEPLOYMENT);
        ConfigResourceId secretId =
                new ConfigResourceId("default", "my-secret", ConfigResourceType.SECRET);
        Set<ConfigResourceId> dependencies = Set.of(secretId);

        WorkloadConfiguration config = new WorkloadConfiguration(workloadId, dependencies, true);

        when(repository.findByWorkload(workloadId)).thenReturn(new HashSet<>());
        when(repository.findById(secretId)).thenReturn(Optional.empty());

        // When
        service.registerWorkload(config);

        // Then
        ArgumentCaptor<WatchedConfigResource> captor =
                ArgumentCaptor.forClass(WatchedConfigResource.class);
        verify(repository).save(captor.capture());

        WatchedConfigResource saved = captor.getValue();
        assertEquals(secretId, saved.resourceId());
        assertTrue(saved.dependentWorkloads().contains(workloadId));
    }

    @Test
    void testRegisterWorkloadWithExistingDependencies() {
        // Given
        WorkloadId workloadId = new WorkloadId("default", "my-deployment", WorkloadType.DEPLOYMENT);
        WorkloadId otherWorkloadId =
                new WorkloadId("default", "other-deployment", WorkloadType.DEPLOYMENT);
        ConfigResourceId secretId =
                new ConfigResourceId("default", "my-secret", ConfigResourceType.SECRET);
        Set<ConfigResourceId> dependencies = Set.of(secretId);

        WorkloadConfiguration config = new WorkloadConfiguration(workloadId, dependencies, true);

        // Existing resource already watched by another workload
        Set<WorkloadId> existingWorkloads = new HashSet<>();
        existingWorkloads.add(otherWorkloadId);
        WatchedConfigResource existingResource =
                new WatchedConfigResource(secretId, existingWorkloads);

        when(repository.findByWorkload(workloadId)).thenReturn(new HashSet<>());
        when(repository.findById(secretId)).thenReturn(Optional.of(existingResource));

        // When
        service.registerWorkload(config);

        // Then
        ArgumentCaptor<WatchedConfigResource> captor =
                ArgumentCaptor.forClass(WatchedConfigResource.class);
        verify(repository).save(captor.capture());

        WatchedConfigResource saved = captor.getValue();
        assertEquals(secretId, saved.resourceId());
        assertEquals(2, saved.dependentWorkloads().size());
        assertTrue(saved.dependentWorkloads().contains(workloadId));
        assertTrue(saved.dependentWorkloads().contains(otherWorkloadId));
    }

    @Test
    void testRegisterWorkloadNotEnabled() {
        // Given
        WorkloadId workloadId = new WorkloadId("default", "my-deployment", WorkloadType.DEPLOYMENT);
        Set<ConfigResourceId> dependencies = Set.of();

        WorkloadConfiguration config = new WorkloadConfiguration(workloadId, dependencies, false);

        when(repository.findByWorkload(workloadId)).thenReturn(new HashSet<>());

        // When
        service.registerWorkload(config);

        // Then
        verify(repository, never()).save(any());
    }

    @Test
    void testUnregisterWorkload() {
        // Given
        WorkloadId workloadId = new WorkloadId("default", "my-deployment", WorkloadType.DEPLOYMENT);
        ConfigResourceId secretId =
                new ConfigResourceId("default", "my-secret", ConfigResourceType.SECRET);

        Set<WorkloadId> workloads = new HashSet<>();
        workloads.add(workloadId);
        WatchedConfigResource resource = new WatchedConfigResource(secretId, workloads);

        when(repository.findByWorkload(workloadId)).thenReturn(Set.of(secretId));
        when(repository.findById(secretId)).thenReturn(Optional.of(resource));

        // When
        service.unregisterWorkload(workloadId);

        // Then
        verify(repository).remove(secretId);
    }

    @Test
    void testUnregisterWorkloadWithOtherDependents() {
        // Given
        WorkloadId workloadId = new WorkloadId("default", "my-deployment", WorkloadType.DEPLOYMENT);
        WorkloadId otherWorkloadId =
                new WorkloadId("default", "other-deployment", WorkloadType.DEPLOYMENT);
        ConfigResourceId secretId =
                new ConfigResourceId("default", "my-secret", ConfigResourceType.SECRET);

        Set<WorkloadId> workloads = new HashSet<>();
        workloads.add(workloadId);
        workloads.add(otherWorkloadId);
        WatchedConfigResource resource = new WatchedConfigResource(secretId, workloads);

        when(repository.findByWorkload(workloadId)).thenReturn(Set.of(secretId));
        when(repository.findById(secretId)).thenReturn(Optional.of(resource));

        // When
        service.unregisterWorkload(workloadId);

        // Then
        verify(repository, never()).remove(secretId);
        ArgumentCaptor<WatchedConfigResource> captor =
                ArgumentCaptor.forClass(WatchedConfigResource.class);
        verify(repository).save(captor.capture());

        WatchedConfigResource saved = captor.getValue();
        assertEquals(1, saved.dependentWorkloads().size());
        assertTrue(saved.dependentWorkloads().contains(otherWorkloadId));
        assertFalse(saved.dependentWorkloads().contains(workloadId));
    }

    @Test
    void testHandleConfigResourceUpdate() {
        // Given
        WorkloadId workloadId1 = new WorkloadId("default", "deployment-1", WorkloadType.DEPLOYMENT);
        WorkloadId workloadId2 = new WorkloadId("default", "deployment-2", WorkloadType.DEPLOYMENT);
        ConfigResourceId secretId =
                new ConfigResourceId("default", "my-secret", ConfigResourceType.SECRET);

        Set<WorkloadId> dependentWorkloads = Set.of(workloadId1, workloadId2);
        WatchedConfigResource resource = new WatchedConfigResource(secretId, dependentWorkloads);

        when(repository.findById(secretId)).thenReturn(Optional.of(resource));

        // When
        service.handleConfigResourceUpdate(secretId);

        // Then
        verify(workloadRestarter).restartWorkload(workloadId1);
        verify(workloadRestarter).restartWorkload(workloadId2);
    }

    @Test
    void testHandleConfigResourceUpdateWithNoWatchers() {
        // Given
        ConfigResourceId secretId =
                new ConfigResourceId("default", "my-secret", ConfigResourceType.SECRET);

        when(repository.findById(secretId)).thenReturn(Optional.empty());

        // When
        service.handleConfigResourceUpdate(secretId);

        // Then
        verify(workloadRestarter, never()).restartWorkload(any());
    }

    @Test
    void testHandleConfigResourceUpdateContinuesOnError() {
        // Given
        WorkloadId workloadId1 = new WorkloadId("default", "deployment-1", WorkloadType.DEPLOYMENT);
        WorkloadId workloadId2 = new WorkloadId("default", "deployment-2", WorkloadType.DEPLOYMENT);
        ConfigResourceId secretId =
                new ConfigResourceId("default", "my-secret", ConfigResourceType.SECRET);

        Set<WorkloadId> dependentWorkloads = Set.of(workloadId1, workloadId2);
        WatchedConfigResource resource = new WatchedConfigResource(secretId, dependentWorkloads);

        when(repository.findById(secretId)).thenReturn(Optional.of(resource));
        doThrow(new RuntimeException("Restart failed"))
                .when(workloadRestarter)
                .restartWorkload(workloadId1);

        // When
        service.handleConfigResourceUpdate(secretId);

        // Then
        verify(workloadRestarter).restartWorkload(workloadId1);
        verify(workloadRestarter).restartWorkload(workloadId2);
    }
}
