package io.github.martinwitt.imagedetector.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class HelmVersionCheckService {
    private static final Logger logger = LoggerFactory.getLogger(HelmVersionCheckService.class);
    // Large Helm repos can have big index.yaml files (Bitnami ~60MB), allow up to 100MB
    private static final int MAX_INDEX_SIZE = 100 * 1024 * 1024; // 100MB max

    // Local in-memory tracking: chartId -> {currentVersion, repoUrl, chartName}
    private final Map<String, ChartInfo> trackedCharts = new HashMap<>();
    private final RestTemplate restTemplate;
    // Per-repo cache with TTL: repoUrl -> {timestamp, cache}
    private static final long REPO_CACHE_TTL_MS = 3600000; // 1 hour
    private final Map<String, CacheEntry> perRepoCaches = new HashMap<>();
    
    private static class CacheEntry {
        final Map<String, String> cache;
        final long timestamp;
        
        CacheEntry(Map<String, String> cache) {
            this.cache = cache;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > REPO_CACHE_TTL_MS;
        }
    }

    public HelmVersionCheckService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 seconds
        factory.setReadTimeout(30000); // 30 seconds

        this.restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(factory));
    }

    /** Simple holder for tracked chart information */
    public static class ChartInfo {
        public String currentVersion;
        public String latestVersion;
        public String repoUrl;
        public String chartName;

        public ChartInfo(String currentVersion, String repoUrl, String chartName) {
            this.currentVersion = currentVersion;
            this.latestVersion = currentVersion; // Initially same as current
            this.repoUrl = repoUrl;
            this.chartName = chartName;
        }
    }

    private Yaml createYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(100 * 1024 * 1024);  // 100MB max to prevent memory exhaustion
        loaderOptions.setMaxAliasesForCollections(50);        // Helm charts don't need any alias support really
        loaderOptions.setAllowDuplicateKeys(false);
        return new Yaml(loaderOptions);
    }

    @Scheduled(fixedDelayString = "${app.helm-check-interval-ms:600000}", initialDelay = 10000)
    public void checkForUpdates() {
        logger.info("Starting Helm version check for {} charts", trackedCharts.size());

        try {
            // Clean up expired per-repo caches to prevent memory buildup
            perRepoCaches.entrySet().removeIf(e -> e.getValue().isExpired());
            logger.debug("Cache cleanup: {} active repo caches", perRepoCaches.size());

            for (Map.Entry<String, ChartInfo> entry : trackedCharts.entrySet()) {
                String chartId = entry.getKey();
                ChartInfo chartInfo = entry.getValue();

                try {
                    logger.debug("Checking chart: {} ({})", chartId, chartInfo.chartName);
                    String latestVersion = findLatestVersion(chartInfo);

                    if (latestVersion != null) {
                        chartInfo.latestVersion = latestVersion;
                        if (!latestVersion.equals(chartInfo.currentVersion)) {
                            logger.info(
                                    "Update available for {}: {} -> {}",
                                    chartId,
                                    chartInfo.currentVersion,
                                    latestVersion);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to check version for {}: {}", chartId, e.getMessage());
                }
            }

            logger.info("Helm version check completed");
        } catch (Exception e) {
            logger.error("Helm version check failed: {}", e.getMessage());
        }
    }

    private String findLatestVersion(ChartInfo chartInfo) throws IOException {
        if (chartInfo.repoUrl == null || chartInfo.repoUrl.isBlank()) {
            return null;
        }

        String repoUrl = chartInfo.repoUrl.trim();
        if (!repoUrl.endsWith("/")) {
            repoUrl += "/";
        }

        String indexUrl = repoUrl + "index.yaml";
        String chartName = chartInfo.chartName;

        // Check persistent per-repo cache first
        CacheEntry cacheEntry = perRepoCaches.get(repoUrl);
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            if (cacheEntry.cache.containsKey(chartName)) {
                logger.debug("Using cached version for {}", chartName);
                return cacheEntry.cache.get(chartName);
            }
        }

        // Fetch and parse complete YAML
        try {
            String latestVersion = fetchLatestChartVersionStreaming(indexUrl, chartName);
            if (latestVersion != null && isStableVersion(latestVersion)) {
                // Update or create cache entry
                CacheEntry entry = cacheEntry;
                if (entry == null || entry.isExpired()) {
                    entry = new CacheEntry(new HashMap<>());
                    perRepoCaches.put(repoUrl, entry);
                }
                entry.cache.put(chartName, latestVersion);
                return latestVersion;
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to fetch index from {}: {}", indexUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Streaming YAML parser that only extracts the latest stable version of a specific chart. This
     * keeps memory usage constant regardless of index.yaml size.
     */
    private String fetchLatestChartVersionStreaming(String indexUrl, String chartName)
            throws IOException {
        try {
            // Use streaming approach - never load entire file into memory
            return restTemplate.execute(
                    indexUrl,
                    org.springframework.http.HttpMethod.GET,
                    clientHttpRequest -> {},
                    clientHttpResponse -> {
                        // Check size before processing
                        String contentLength = clientHttpResponse.getHeaders().getFirst("Content-Length");
                        if (contentLength != null) {
                            try {
                                long size = Long.parseLong(contentLength);
                                if (size > MAX_INDEX_SIZE) {
                                    logger.warn(
                                            "Helm index.yaml too large ({}MB) from {}, skipping",
                                            size / (1024 * 1024),
                                            indexUrl);
                                    return null;
                                }
                            } catch (NumberFormatException e) {
                                logger.debug("Could not parse Content-Length from {}", indexUrl);
                            }
                        }

                        // Stream directly into parser - no byte[] buffer!
                        try (InputStream is = clientHttpResponse.getBody()) {
                            return parseLatestVersionFromStream(is, chartName);
                        }
                    });
        } catch (Exception e) {
            logger.warn("Failed to fetch chart versions from {}: {}", indexUrl, e.getMessage());
            throw new IOException(
                    "Failed to fetch index from " + indexUrl + ": " + e.getMessage(), e);
        }
    }

    /**
     * Streaming parser that uses YAML indentation to find chart versions.
     * Tracks indentation levels to distinguish between:
     * - Chart entries (same indent as the found chart key)
     * - Metadata fields (greater indent than chart key)
     * - Next chart (same indent as chart key, ends with ":")
     */
    private String parseLatestVersionFromStream(InputStream inputStream, String chartName)
            throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream);
                java.io.BufferedReader buffered = new java.io.BufferedReader(reader, 16384)) {
            
            String latestVersion = null;
            boolean foundChart = false;
            int chartIndentLevel = -1;
            boolean inDependencies = false;      // Track if we're inside dependencies: section
            int dependenciesIndent = -1;          // Indentation level of the "dependencies:" line
            int versionCount = 0;
            int stableCount = 0;
            int linesAfterChart = 0;
            final int MAX_LINES_AFTER_CHART = 10000;

            String line;
            while ((line = buffered.readLine()) != null) {
                // Calculate indentation level (spaces count as 1, tabs as 4)
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') {
                        indent++;
                    } else if (c == '\t') {
                        indent += 4;
                    } else {
                        break;
                    }
                }
                
                String trimmed = line.trim();
                
                // Skip empty lines
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Stage 1: Look for chart name as a key
                if (!foundChart) {
                    // Match: "  chartname:" or "chartname:" at any indentation
                    if (trimmed.startsWith(chartName + ":") && 
                        (trimmed.length() == chartName.length() + 1 || 
                         trimmed.charAt(chartName.length() + 1) != '-')) {
                        foundChart = true;
                        chartIndentLevel = indent;
                        logger.info("Found chart {} in entries at indent {}", chartName, chartIndentLevel);
                        continue;
                    }
                    continue;
                }

                // Stage 2: Track dependencies section - only extract chart versions, not dependency versions
                if (trimmed.startsWith("dependencies:")) {
                    inDependencies = true;
                    dependenciesIndent = indent;
                    logger.debug("Entering dependencies section at indent {}", dependenciesIndent);
                    continue;
                }
                
                // Exit dependencies section when we find a key at same or lower indentation
                if (inDependencies && indent <= dependenciesIndent && trimmed.endsWith(":") && !trimmed.startsWith("-")) {
                    inDependencies = false;
                    logger.debug("Exiting dependencies section");
                }

                // Stage 3: After finding chart, look for versions
                linesAfterChart++;
                if (linesAfterChart > MAX_LINES_AFTER_CHART) {
                    logger.warn("Exceeded max lines after finding chart {}, stopping", chartName);
                    break;
                }

                // Stop if we hit the next chart entry
                if (indent <= chartIndentLevel && trimmed.endsWith(":") && !trimmed.startsWith("-")) {
                    logger.debug("Found next chart at indent {} for {}, stopping", indent, chartName);
                    break;
                }

                // Extract version ONLY if not in dependencies section
                if (trimmed.contains("version:") && !inDependencies) {
                    String version = extractVersionValue(trimmed);
                    if (version != null && !version.isEmpty()) {
                        versionCount++;
                        
                        if (isStableVersion(version)) {
                            stableCount++;
                            if (latestVersion == null) {
                                latestVersion = version;
                            } else if (compareVersions(version, latestVersion) > 0) {
                                latestVersion = version;
                            }
                        }
                    }
                }
            }

            if (latestVersion != null) {
                logger.info(
                        "Chart {}: FOUND latest={} (scanned {} versions, {} stable)",
                        chartName,
                        latestVersion,
                        versionCount,
                        stableCount);
            } else {
                logger.warn(
                        "Chart {}: no version (found {} versions total, {} stable)",
                        chartName,
                        versionCount,
                        stableCount);
            }

            return latestVersion;
        } catch (Exception e) {
            logger.error("YAML parse error for {}: {}", chartName, e.getMessage(), e);
            throw new IOException("Parse failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract version value from "- version: 1.2.3" line.
     */
    private String extractVersionValue(String line) {
        // Handle: "- version: 1.2.3" or "- version: '1.2.3'" or "- version: \"1.2.3\""
        if (!line.contains("version:")) {
            return null;
        }
        
        String afterVersion = line.substring(line.indexOf("version:") + 8).trim();
        
        // Remove quotes if present
        if (afterVersion.startsWith("\"") && afterVersion.endsWith("\"")) {
            return afterVersion.substring(1, afterVersion.length() - 1);
        }
        if (afterVersion.startsWith("'") && afterVersion.endsWith("'")) {
            return afterVersion.substring(1, afterVersion.length() - 1);
        }
        
        return afterVersion;
    }

    private boolean isStableVersion(String version) {
        String lower = version.toLowerCase();
        // Reject pre-release versions, snapshots, 0.0.0, and versions with wildcards or special
        // chars
        return !lower.matches(".*(-alpha|-beta|-rc|v?0\\.0\\.0|-dev|-snapshot|-next|\\*|\\?).*");
    }

    private int compareVersions(String v1, String v2) {
        return parseVersion(v1).compareTo(parseVersion(v2));
    }

    private VersionParts parseVersion(String version) {
        try {
            String clean = version.replaceAll("[^0-9.]", "");
            String[] parts = clean.split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new VersionParts(major, minor, patch);
        } catch (Exception e) {
            return new VersionParts(0, 0, 0);
        }
    }

    private record VersionParts(int major, int minor, int patch)
            implements Comparable<VersionParts> {
        @Override
        public int compareTo(VersionParts other) {
            if (major != other.major) return Integer.compare(major, other.major);
            if (minor != other.minor) return Integer.compare(minor, other.minor);
            return Integer.compare(patch, other.patch);
        }
    }

    /**
     * Testing method to check version of a specific chart Usage: call this to verify version
     * detection works correctly
     */
    public String getLatestVersionForChart(String chartId) {
        ChartInfo chartInfo = trackedCharts.get(chartId);
        if (chartInfo == null) {
            logger.warn("Chart not found: {}", chartId);
            return null;
        }

        try {
            String latestVersion = findLatestVersion(chartInfo);
            logger.info(
                    "Latest version for {} ({}): {}", chartId, chartInfo.chartName, latestVersion);
            return latestVersion;
        } catch (Exception e) {
            logger.error("Failed to get latest version for {}: {}", chartId, e.getMessage());
            return null;
        }
    }

    /** Testing method: Get current tracked versions */
    public Map<String, ChartInfo> getTrackedCharts() {
        return trackedCharts;
    }

    /** Add a chart for tracking (can be called dynamically from HelmChartScanService) */
    public void registerChart(
            String appName, String dependencyName, String currentVersion, String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            logger.debug(
                    "Skipping chart registration for {}/{} - no repository URL",
                    appName,
                    dependencyName);
            return;
        }

        String chartId = appName + "/" + dependencyName;
        trackedCharts.put(chartId, new ChartInfo(currentVersion, repoUrl, dependencyName));
        logger.debug("Registered chart for tracking: {} (v{})", chartId, currentVersion);
    }

    /** Remove a chart from tracking */
    public void unregisterChart(String appName, String dependencyName) {
        String chartId = appName + "/" + dependencyName;
        if (trackedCharts.remove(chartId) != null) {
            logger.debug("Unregistered chart: {}", chartId);
        }
    }

    /** Testing method: Add a chart for tracking */
    public void addTrackedChart(
            String chartId, String currentVersion, String repoUrl, String chartName) {
        trackedCharts.put(chartId, new ChartInfo(currentVersion, repoUrl, chartName));
        logger.info("Added chart for tracking: {} -> {}/{}", chartId, repoUrl, chartName);
    }
}
