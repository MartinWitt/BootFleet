package io.github.martinwitt.imagedetector.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple test class to verify Helm version detection works locally. Run this to test version
 * detection for each tracked chart before deploying.
 */
public class HelmVersionCheckServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(HelmVersionCheckServiceTest.class);

    public static void main(String[] args) {
        logger.info("=== Starting Helm Version Check Service Test ===");

        HelmVersionCheckService service = new HelmVersionCheckService();

        // Get all tracked charts
        Map<String, HelmVersionCheckService.ChartInfo> charts = service.getTrackedCharts();
        logger.info("Found {} tracked charts", charts.size());

        // Test each chart
        for (String chartId : charts.keySet()) {
            logger.info("\n--- Testing chart: {} ---", chartId);
            try {
                String latestVersion = service.getLatestVersionForChart(chartId);
                HelmVersionCheckService.ChartInfo info = charts.get(chartId);

                if (latestVersion != null) {
                    logger.info("Chart: {}", chartId);
                    logger.info("  Repository: {}", info.repoUrl);
                    logger.info("  Chart Name: {}", info.chartName);
                    logger.info("  Current Version: {}", info.currentVersion);
                    logger.info("  Latest Version: {}", latestVersion);

                    if (!latestVersion.equals(info.currentVersion)) {
                        logger.info(
                                "  => UPDATE AVAILABLE: {} -> {}",
                                info.currentVersion,
                                latestVersion);
                    } else {
                        logger.info("  => Already up to date");
                    }
                } else {
                    logger.warn("  => Failed to fetch latest version for {}", chartId);
                }
            } catch (Exception e) {
                logger.error("  => Error checking chart {}: {}", chartId, e.getMessage());
            }
        }

        logger.info("\n=== Helm Version Check Service Test Complete ===");
    }
}
