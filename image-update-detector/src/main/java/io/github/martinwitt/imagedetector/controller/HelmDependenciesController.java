package io.github.martinwitt.imagedetector.controller;

import io.github.martinwitt.imagedetector.service.HelmChartScanService;
import io.github.martinwitt.imagedetector.service.HelmVersionCheckService;
import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/helm-dependencies")
public class HelmDependenciesController {
    private final HelmChartScanService scanService;
    private final HelmVersionCheckService versionCheckService;

    public HelmDependenciesController(
            HelmChartScanService scanService, HelmVersionCheckService versionCheckService) {
        this.scanService = scanService;
        this.versionCheckService = versionCheckService;
    }

    @GetMapping
    public Map<String, Object> getAllDependencies() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, List<HelmChartScanService.ChartDependency>> scanned =
                scanService.getScannedCharts();
        Map<String, HelmVersionCheckService.ChartInfo> trackedCharts =
                versionCheckService.getTrackedCharts();

        // Combine scanned charts with latest version info
        Map<String, List<Map<String, Object>>> enrichedApps = new LinkedHashMap<>();
        for (String appName : scanned.keySet()) {
            List<Map<String, Object>> enrichedCharts = new ArrayList<>();
            for (HelmChartScanService.ChartDependency chart : scanned.get(appName)) {
                Map<String, Object> chartData = new LinkedHashMap<>();
                chartData.put("name", chart.name);
                chartData.put("version", chart.version);
                chartData.put("repository", chart.repository);
                chartData.put("lastUpdated", chart.lastUpdated);

                // Find latest version from tracked charts
                String chartId = appName + "/" + chart.name;
                HelmVersionCheckService.ChartInfo chartInfo = trackedCharts.get(chartId);
                if (chartInfo != null && chartInfo.latestVersion != null) {
                    chartData.put("latestVersion", chartInfo.latestVersion);
                }

                enrichedCharts.add(chartData);
            }
            enrichedApps.put(appName, enrichedCharts);
        }

        result.put("scannedApps", scanned.size());
        result.put("apps", enrichedApps);

        return result;
    }

    @GetMapping("/app/{appName}")
    public Map<String, Object> getDependenciesByApp(String appName) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, List<HelmChartScanService.ChartDependency>> scanned =
                scanService.getScannedCharts();
        Map<String, HelmVersionCheckService.ChartInfo> trackedCharts =
                versionCheckService.getTrackedCharts();
        List<HelmChartScanService.ChartDependency> appDeps = scanned.get(appName);

        if (appDeps == null) {
            appDeps = new ArrayList<>();
        }

        // Enrich dependencies with latest version info
        List<Map<String, Object>> enrichedDeps = new ArrayList<>();
        for (HelmChartScanService.ChartDependency chart : appDeps) {
            Map<String, Object> chartData = new LinkedHashMap<>();
            chartData.put("name", chart.name);
            chartData.put("version", chart.version);
            chartData.put("repository", chart.repository);
            chartData.put("lastUpdated", chart.lastUpdated);

            // Find latest version from tracked charts
            String chartId = appName + "/" + chart.name;
            HelmVersionCheckService.ChartInfo chartInfo = trackedCharts.get(chartId);
            if (chartInfo != null && chartInfo.latestVersion != null) {
                chartData.put("latestVersion", chartInfo.latestVersion);
            }

            enrichedDeps.add(chartData);
        }

        result.put("app", appName);
        result.put("dependencyCount", enrichedDeps.size());
        result.put("dependencies", enrichedDeps);

        return result;
    }
}
