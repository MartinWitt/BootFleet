package io.github.martinwitt.configreloader.infrastructure.repository;

import io.github.martinwitt.configreloader.domain.model.ConfigResourceId;
import io.github.martinwitt.configreloader.domain.model.ConfigResourceType;
import io.github.martinwitt.configreloader.domain.model.WatchedConfigResource;
import io.github.martinwitt.configreloader.domain.model.WorkloadId;
import io.github.martinwitt.configreloader.domain.service.ConfigResourceRepository;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** In-memory implementation of the config resource repository. */
@Component
public class InMemoryConfigResourceRepository implements ConfigResourceRepository {

    private final Map<ConfigResourceId, WatchedConfigResource> resources =
            new ConcurrentHashMap<>();

    @Override
    public void save(WatchedConfigResource resource) {
        resources.put(resource.resourceId(), resource);
    }

    @Override
    public Optional<WatchedConfigResource> findById(ConfigResourceId resourceId) {
        return Optional.ofNullable(resources.get(resourceId));
    }

    @Override
    public void remove(ConfigResourceId resourceId) {
        resources.remove(resourceId);
    }

    @Override
    public Map<ConfigResourceId, WatchedConfigResource> findAll() {
        return Collections.unmodifiableMap(resources);
    }

    @Override
    public Set<ConfigResourceId> findByWorkload(WorkloadId workloadId) {
        return resources.values().stream()
                .filter(resource -> resource.isDependedOnBy(workloadId))
                .map(WatchedConfigResource::resourceId)
                .collect(Collectors.toSet());
    }

    @Override
    public int countSecrets() {
        return (int)
                resources.keySet().stream()
                        .filter(id -> id.type() == ConfigResourceType.SECRET)
                        .count();
    }

    @Override
    public int countConfigMaps() {
        return (int)
                resources.keySet().stream()
                        .filter(id -> id.type() == ConfigResourceType.CONFIGMAP)
                        .count();
    }
}
