package io.github.martinwitt.imagedetector.service;

import io.github.martinwitt.imagedetector.client.GitOpsClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class HelmChartScanService {
    private static final Logger logger = LoggerFactory.getLogger(HelmChartScanService.class);

    private final Map<String, List<ChartDependency>> scannedCharts = new ConcurrentHashMap<>();
    private final GitOpsClient gitOpsClient;
    private final HelmVersionCheckService versionCheckService;

    public record ChartDependency(
            String name, String version, String repository, long lastUpdated) {
        public ChartDependency(String name, String version, String repository) {
            this(name, version, repository, System.currentTimeMillis());
        }
    }

    public HelmChartScanService(
            GitOpsClient gitOpsClient, HelmVersionCheckService versionCheckService) {
        this.gitOpsClient = gitOpsClient;
        this.versionCheckService = versionCheckService;
    }

    public Map<String, List<ChartDependency>> getScannedCharts() {
        return Map.copyOf(scannedCharts);
    }

    private Yaml createYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(50 * 1024 * 1024);
        loaderOptions.setMaxAliasesForCollections(50);
        loaderOptions.setAllowDuplicateKeys(false);
        return new Yaml(loaderOptions);
    }

    @Scheduled(fixedDelayString = "${app.gitops.refresh-interval-ms:300000}", initialDelay = 0)
    public void scanHelmCharts() {
        logger.info("Starting Helm chart scan from GitOps repository");

        try {
            List<String> apps = gitOpsClient.listApps();
            logger.info("Found {} apps in /apps/", apps.size());

            Set<String> currentApps = new HashSet<>(apps);

            for (String appName : apps) {
                try {
                    scanApp(appName);
                } catch (Exception e) {
                    logger.warn("Failed to scan app {}: {}", appName, e.getMessage());
                }
            }

            scannedCharts.keySet().stream()
                    .filter(app -> !currentApps.contains(app))
                    .toList()
                    .forEach(
                            app -> {
                                List<ChartDependency> charts = scannedCharts.remove(app);
                                logger.info(
                                        "Removed app from scanning: {} (had {} charts)",
                                        app,
                                        charts.size());
                                charts.forEach(
                                        chart ->
                                                versionCheckService.unregisterChart(
                                                        app, chart.name()));
                            });

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
            Yaml yaml = createYamlParser();
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
            scannedCharts.remove(appName);
            return;
        }

        if (!(depsObj instanceof List<?> dependenciesList)) {
            logger.warn("Dependencies is not a list in Chart.yaml for app: {}", appName);
            return;
        }

        List<ChartDependency> newCharts = new ArrayList<>();
        Set<String> newDepNames = new HashSet<>();

        for (Object depObj : dependenciesList) {
            if (!(depObj instanceof Map<?, ?> depMap)) {
                logger.warn("Dependency entry is not a map for app: {}", appName);
                continue;
            }

            String name = stringify(depMap.get("name"));
            String version = stringify(depMap.get("version"));
            String repository = stringify(depMap.get("repository"));

            if (name == null || version == null) {
                logger.warn("Dependency missing name or version for app: {}", appName);
                continue;
            }

            newDepNames.add(name);
            newCharts.add(new ChartDependency(name, version, repository != null ? repository : ""));

            if (repository != null && !repository.isBlank()) {
                versionCheckService.registerChart(appName, name, version, repository);
            }
        }

        if (!newCharts.isEmpty()) {
            List<ChartDependency> oldCharts = scannedCharts.put(appName, newCharts);
            if (oldCharts != null) {
                oldCharts.stream()
                        .filter(old -> !newDepNames.contains(old.name()))
                        .forEach(
                                old -> {
                                    logger.info("Chart removed for {}/{}", appName, old.name());
                                    versionCheckService.unregisterChart(appName, old.name());
                                });
            }
            logger.info("Updated {} with {} Helm chart dependencies", appName, newCharts.size());
        } else {
            List<ChartDependency> oldCharts = scannedCharts.remove(appName);
            if (oldCharts != null) {
                oldCharts.forEach(
                        chart -> versionCheckService.unregisterChart(appName, chart.name()));
            }
        }
    }

    private String stringify(Object value) {
        return value == null ? null : value.toString();
    }
}
