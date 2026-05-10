package io.github.martinwitt.imagedetector.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelmChartDependencyRepository
        extends JpaRepository<HelmChartDependencyEntity, Long> {
    List<HelmChartDependencyEntity> findByAppName(String appName);

    Optional<HelmChartDependencyEntity> findByAppNameAndDependencyName(
            String appName, String dependencyName);
}
