package io.github.martinwitt.imagedetector.service;

import io.github.martinwitt.imagedetector.client.GitOpsClient;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class HelmChartScanService {
    private static final Logger logger = LoggerFactory.getLogger(HelmChartScanService.class);

    // Local in-memory storage: appName -> List of chart dependencies
    private final Map<String, List<ChartDependency>> scannedCharts = new HashMap<>();
    private final GitOpsClient gitOpsClient;
    private final HelmVersionCheckService versionCheckService;

    /** Get all scanned charts */
    public Map<String, List<ChartDependency>> getScannedCharts() {
        return new HashMap<>(scannedCharts);
    }

    public HelmChartScanService(
            GitOpsClient gitOpsClient, HelmVersionCheckService versionCheckService) {
        this.gitOpsClient = gitOpsClient;
        this.versionCheckService = versionCheckService;
    }

    /** Local holder for scanned chart dependency information */
    public static class ChartDependency {
        public String name;
        public String version;
        public String repository;
        public long lastUpdated;

        public ChartDependency(String name, String version, String repository) {
            this.name = name;
            this.version = version;
            this.repository = repository;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    private Yaml createYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(
                50 * 1024 * 1024); // 50MB max for Chart.yaml (much smaller than index.yaml)
        loaderOptions.setMaxAliasesForCollections(50); // Helm charts don't need alias support
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

            // Scan all apps
            for (String appName : apps) {
                try {
                    scanApp(appName);
                } catch (Exception e) {
                    logger.warn("Failed to scan app {}: {}", appName, e.getMessage());
                }
            }

            // Clean up removed apps
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

                                // Unregister all charts for this app from version check service
                                for (ChartDependency chart : charts) {
                                    versionCheckService.unregisterChart(app, chart.name);
                                }
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
            logger.debug("No dependencies found in Chart.yaml for app: {}", appName);
            // Clear existing charts for this app if they exist
            scannedCharts.remove(appName);
            return;
        }

        if (!(depsObj instanceof List<?> dependenciesList)) {
            logger.warn("Dependencies is not a list in Chart.yaml for app: {}", appName);
            return;
        }

        List<ChartDependency> newCharts = new ArrayList<>();
        Set<String> newDepsNames = new HashSet<>();

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

            newDepsNames.add(name);
            ChartDependency chart =
                    new ChartDependency(name, version, repository != null ? repository : "");
            newCharts.add(chart);

            logger.debug("Found chart {}/{}: {} from {}", appName, name, version, repository);

            // Register with version check service for automatic update detection
            if (repository != null && !repository.isBlank()) {
                versionCheckService.registerChart(appName, name, version, repository);
            }
        }

        // Store the charts locally
        if (!newCharts.isEmpty()) {
            List<ChartDependency> oldCharts = scannedCharts.put(appName, newCharts);

            // If there were old charts, unregister any that are no longer present
            if (oldCharts != null) {
                for (ChartDependency oldChart : oldCharts) {
                    if (!newDepsNames.contains(oldChart.name)) {
                        logger.info("Chart removed for {}/{}", appName, oldChart.name);
                        versionCheckService.unregisterChart(appName, oldChart.name);
                    }
                }
            }

            logger.info("Updated {} with {} Helm chart dependencies", appName, newCharts.size());
        } else {
            // Remove if no dependencies found
            List<ChartDependency> oldCharts = scannedCharts.remove(appName);
            if (oldCharts != null) {
                for (ChartDependency chart : oldCharts) {
                    versionCheckService.unregisterChart(appName, chart.name);
                }
            }
        }
    }

    private String getString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
