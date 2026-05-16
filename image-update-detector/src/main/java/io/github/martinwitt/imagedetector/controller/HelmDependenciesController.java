package io.github.martinwitt.imagedetector.controller;

import io.github.martinwitt.imagedetector.service.HelmChartScanService;
import io.github.martinwitt.imagedetector.service.HelmVersionCheckService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        Map<String, List<HelmChartScanService.ChartDependency>> scanned =
                scanService.getScannedCharts();
        Map<String, HelmVersionCheckService.ChartInfo> trackedCharts =
                versionCheckService.getTrackedCharts();

        Map<String, List<Map<String, Object>>> enrichedApps = new LinkedHashMap<>();
        for (Map.Entry<String, List<HelmChartScanService.ChartDependency>> entry :
                scanned.entrySet()) {
            String appName = entry.getKey();
            List<Map<String, Object>> enrichedCharts = new ArrayList<>();
            for (HelmChartScanService.ChartDependency chart : entry.getValue()) {
                enrichedCharts.add(enrichChart(appName, chart, trackedCharts));
            }
            enrichedApps.put(appName, enrichedCharts);
        }

        return Map.of("scannedApps", scanned.size(), "apps", enrichedApps);
    }

    @GetMapping("/app/{appName}")
    public Map<String, Object> getDependenciesByApp(@PathVariable String appName) {
        Map<String, List<HelmChartScanService.ChartDependency>> scanned =
                scanService.getScannedCharts();
        Map<String, HelmVersionCheckService.ChartInfo> trackedCharts =
                versionCheckService.getTrackedCharts();

        List<HelmChartScanService.ChartDependency> appDeps =
                scanned.getOrDefault(appName, List.of());

        List<Map<String, Object>> enrichedDeps = new ArrayList<>();
        for (HelmChartScanService.ChartDependency chart : appDeps) {
            enrichedDeps.add(enrichChart(appName, chart, trackedCharts));
        }

        return Map.of(
                "app",
                appName,
                "dependencyCount",
                enrichedDeps.size(),
                "dependencies",
                enrichedDeps);
    }

    private Map<String, Object> enrichChart(
            String appName,
            HelmChartScanService.ChartDependency chart,
            Map<String, HelmVersionCheckService.ChartInfo> trackedCharts) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", chart.name());
        data.put("version", chart.version());
        data.put("repository", chart.repository());
        data.put("lastUpdated", chart.lastUpdated());

        HelmVersionCheckService.ChartInfo chartInfo =
                trackedCharts.get(appName + "/" + chart.name());
        if (chartInfo != null && chartInfo.latestVersion() != null) {
            data.put("latestVersion", chartInfo.latestVersion());
        }
        return data;
    }
}
