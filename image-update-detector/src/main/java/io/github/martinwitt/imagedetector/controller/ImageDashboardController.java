package io.github.martinwitt.imagedetector.controller;

import io.github.martinwitt.imagedetector.service.HelmChartScanService;
import io.github.martinwitt.imagedetector.service.HelmVersionCheckService;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class ImageDashboardController {
    private final HelmChartScanService scanService;
    private final HelmVersionCheckService versionCheckService;

    public ImageDashboardController(
            HelmChartScanService scanService, HelmVersionCheckService versionCheckService) {
        this.scanService = scanService;
        this.versionCheckService = versionCheckService;
    }

    @GetMapping
    public String index(Model model) {
        Map<String, List<HelmChartScanService.ChartDependency>> scannedApps =
                scanService.getScannedCharts();
        Map<String, HelmVersionCheckService.ChartInfo> trackedCharts =
                versionCheckService.getTrackedCharts();

        // Flatten the structure for the template and enrich with latest version info
        List<Map<String, Object>> allDependencies = new ArrayList<>();
        int outOfDateCount = 0;

        for (Map.Entry<String, List<HelmChartScanService.ChartDependency>> entry :
                scannedApps.entrySet()) {
            String appName = entry.getKey();
            for (HelmChartScanService.ChartDependency dep : entry.getValue()) {
                Map<String, Object> depMap = new LinkedHashMap<>();
                depMap.put("app", appName);
                depMap.put("name", dep.name);
                depMap.put("version", dep.version);
                depMap.put("repository", dep.repository);

                // Find latest version from tracked charts
                String chartId = appName + "/" + dep.name;
                HelmVersionCheckService.ChartInfo chartInfo = trackedCharts.get(chartId);
                if (chartInfo != null && chartInfo.latestVersion != null) {
                    depMap.put("latestVersion", chartInfo.latestVersion);
                    // Count if this chart is out of date
                    if (!chartInfo.latestVersion.equals(dep.version)) {
                        outOfDateCount++;
                    }
                }

                allDependencies.add(depMap);
            }
        }

        model.addAttribute("dependencies", allDependencies);
        model.addAttribute("dependencyCount", allDependencies.size());
        model.addAttribute("appCount", scannedApps.size());
        model.addAttribute("outOfDateCount", outOfDateCount);

        return "index";
    }
}
