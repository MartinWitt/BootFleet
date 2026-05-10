package io.github.martinwitt.imagedetector.service;

import io.github.martinwitt.imagedetector.client.GitOpsClient;
import io.github.martinwitt.imagedetector.model.HelmChartDependencyEntity;
import io.github.martinwitt.imagedetector.model.HelmChartDependencyRepository;
import io.github.martinwitt.imagedetector.model.HelmChartRepository;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class HelmChartScanService {
    private static final Logger logger = LoggerFactory.getLogger(HelmChartScanService.class);
    private final HelmChartDependencyRepository dependencyRepo;
    private final GitOpsClient gitOpsClient;
    private final Yaml yaml = new Yaml();

    public HelmChartScanService(
            HelmChartRepository helmChartRepo,
            HelmChartDependencyRepository dependencyRepo,
            GitOpsClient gitOpsClient) {
        this.dependencyRepo = dependencyRepo;
        this.gitOpsClient = gitOpsClient;
    }

    @Scheduled(fixedDelayString = "${app.gitops.refresh-interval-ms:300000}")
    public void scanHelmCharts() {
        logger.info("Starting Helm chart scan from GitOps repository");

        try {
            List<String> apps = gitOpsClient.listApps();
            logger.info("Found {} apps in /apps/", apps.size());

            for (String appName : apps) {
                try {
                    scanApp(appName);
                } catch (Exception e) {
                    logger.warn("Failed to scan app {}: {}", appName, e.getMessage());
                }
            }

            logger.info("Helm chart scan completed");
        } catch (Exception e) {
            logger.error("Helm chart scan failed: {}", e.getMessage());
        }
    }

    private void scanApp(String appName) {
        String chartPath = "/apps/" + appName + "/Chart.yaml";

        String chartContent = gitOpsClient.getFileContent(chartPath);
        if (chartContent == null) {
            logger.info("No Chart.yaml found for app: {}", appName);
            return;
        }

        try {
            Map<String, Object> chartYaml = yaml.load(chartContent);
            if (chartYaml == null) {
                logger.warn("Empty Chart.yaml for app: {}", appName);
                return;
            }

            extractAndSaveDependencies(appName, chartYaml);
            logger.info("Scanned dependencies for app: {}", appName);
        } catch (Exception e) {
            logger.warn("Failed to parse Chart.yaml for {}: {}", appName, e.getMessage());
        }
    }

    private void extractAndSaveDependencies(String appName, Map<String, Object> chartYaml) {
        Object depsObj = chartYaml.get("dependencies");
        if (depsObj == null) {
            logger.debug("No dependencies found in Chart.yaml for app: {}", appName);
            return;
        }

        if (!(depsObj instanceof List<?> dependenciesList)) {
            logger.warn("Dependencies is not a list in Chart.yaml for app: {}", appName);
            return;
        }

        Set<String> currentDeps = new HashSet<>();

        for (Object depObj : dependenciesList) {
            if (!(depObj instanceof Map<?, ?> depMap)) {
                logger.warn("Dependency entry is not a map for app: {}", appName);
                continue;
            }

            String name = getString(depMap.get("name"));
            String version = getString(depMap.get("version"));
            String repository = getString(depMap.get("repository"));

            if (name == null || version == null) {
                logger.warn("Dependency missing name or version for app: {}", appName);
                continue;
            }

            currentDeps.add(name);

            var existing = dependencyRepo.findByAppNameAndDependencyName(appName, name);
            HelmChartDependencyEntity dep =
                    existing.orElseGet(
                            () ->
                                    new HelmChartDependencyEntity(
                                            appName,
                                            name,
                                            version,
                                            repository != null ? repository : ""));

            if (!version.equals(dep.getVersion())) {
                logger.info(
                        "Version update for {}/{}: {} -> {}",
                        appName,
                        name,
                        dep.getVersion(),
                        version);
            }

            dep.setVersion(version);
            if (repository != null) {
                dep.setRepository(repository);
            }
            dep.setLastUpdated(Instant.now());
            dependencyRepo.save(dep);
        }

        // Clean up removed dependencies
        dependencyRepo.findByAppName(appName).stream()
                .filter(dep -> !currentDeps.contains(dep.getDependencyName()))
                .forEach(dependencyRepo::delete);
    }

    private String getString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
