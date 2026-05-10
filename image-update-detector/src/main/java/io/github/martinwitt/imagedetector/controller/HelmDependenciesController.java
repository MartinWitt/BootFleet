package io.github.martinwitt.imagedetector.controller;

import io.github.martinwitt.imagedetector.model.HelmChartDependencyEntity;
import io.github.martinwitt.imagedetector.model.HelmChartDependencyRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/helm-dependencies")
public class HelmDependenciesController {
    private final HelmChartDependencyRepository dependencyRepo;

    public HelmDependenciesController(HelmChartDependencyRepository dependencyRepo) {
        this.dependencyRepo = dependencyRepo;
    }

    @GetMapping
    public List<HelmChartDependencyEntity> getAllDependencies() {
        return dependencyRepo.findAll();
    }

    @GetMapping("/app/{appName}")
    public List<HelmChartDependencyEntity> getDependenciesByApp(String appName) {
        return dependencyRepo.findByAppName(appName);
    }
}
