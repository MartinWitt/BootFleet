package io.github.martinwitt.imagedetector.model;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelmChartRepository extends JpaRepository<HelmChartEntity, Long> {
    Optional<HelmChartEntity> findByAppName(String appName);
}
