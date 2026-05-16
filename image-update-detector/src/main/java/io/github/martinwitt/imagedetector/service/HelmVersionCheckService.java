package io.github.martinwitt.imagedetector.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class HelmVersionCheckService {
    private static final Logger logger = LoggerFactory.getLogger(HelmVersionCheckService.class);
    private static final int MAX_INDEX_SIZE = 100 * 1024 * 1024;
    private static final long REPO_CACHE_TTL_MS = 3600000;

    private final Map<String, ChartInfo> trackedCharts = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> perRepoCaches = new ConcurrentHashMap<>();
    private final org.springframework.web.client.RestTemplate restTemplate;

    private record CacheEntry(Map<String, String> cache, long timestamp) {
        CacheEntry(Map<String, String> cache) {
            this(cache, System.currentTimeMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > REPO_CACHE_TTL_MS;
        }
    }

    public static class ChartInfo {
        private final String currentVersion;
        private volatile String latestVersion;
        private final String repoUrl;
        private final String chartName;

        ChartInfo(String currentVersion, String repoUrl, String chartName) {
            this.currentVersion = currentVersion;
            this.latestVersion = currentVersion;
            this.repoUrl = repoUrl;
            this.chartName = chartName;
        }

        public String currentVersion() {
            return currentVersion;
        }

        public String latestVersion() {
            return latestVersion;
        }

        public String repoUrl() {
            return repoUrl;
        }

        public String chartName() {
            return chartName;
        }

        void updateLatestVersion(String version) {
            this.latestVersion = version;
        }
    }

    public HelmVersionCheckService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        this.restTemplate =
                new org.springframework.web.client.RestTemplate(
                        new BufferingClientHttpRequestFactory(factory));
    }

    private Yaml createYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(MAX_INDEX_SIZE);
        loaderOptions.setMaxAliasesForCollections(50);
        loaderOptions.setAllowDuplicateKeys(false);
        return new Yaml(loaderOptions);
    }

    @Scheduled(fixedDelayString = "${app.helm-check-interval-ms:600000}", initialDelay = 10000)
    public void checkForUpdates() {
        logger.info("Starting Helm version check for {} charts", trackedCharts.size());

        try {
            perRepoCaches.entrySet().removeIf(e -> e.getValue().isExpired());

            for (Map.Entry<String, ChartInfo> entry : trackedCharts.entrySet()) {
                String chartId = entry.getKey();
                ChartInfo chartInfo = entry.getValue();

                try {
                    String latestVersion = findLatestVersion(chartInfo);
                    if (latestVersion != null) {
                        chartInfo.updateLatestVersion(latestVersion);
                        if (!latestVersion.equals(chartInfo.currentVersion())) {
                            logger.info(
                                    "Update available for {}: {} -> {}",
                                    chartId,
                                    chartInfo.currentVersion(),
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
        if (chartInfo.repoUrl() == null || chartInfo.repoUrl().isBlank()) {
            return null;
        }

        String repoUrl = chartInfo.repoUrl().trim();
        if (!repoUrl.endsWith("/")) {
            repoUrl += "/";
        }

        String indexUrl = repoUrl + "index.yaml";
        String chartName = chartInfo.chartName();

        CacheEntry cacheEntry = perRepoCaches.get(repoUrl);
        if (cacheEntry != null
                && !cacheEntry.isExpired()
                && cacheEntry.cache().containsKey(chartName)) {
            return cacheEntry.cache().get(chartName);
        }

        try {
            String latestVersion = fetchLatestChartVersionStreaming(indexUrl, chartName);
            if (latestVersion != null && isStableVersion(latestVersion)) {
                CacheEntry entry =
                        (cacheEntry == null || cacheEntry.isExpired())
                                ? new CacheEntry(new HashMap<>())
                                : cacheEntry;
                if (cacheEntry == null || cacheEntry.isExpired()) {
                    perRepoCaches.put(repoUrl, entry);
                }
                entry.cache().put(chartName, latestVersion);
                return latestVersion;
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to fetch index from {}: {}", indexUrl, e.getMessage());
            return null;
        }
    }

    private String fetchLatestChartVersionStreaming(String indexUrl, String chartName)
            throws IOException {
        try {
            return restTemplate.execute(
                    indexUrl,
                    org.springframework.http.HttpMethod.GET,
                    clientHttpRequest -> {},
                    clientHttpResponse -> {
                        String contentLength =
                                clientHttpResponse.getHeaders().getFirst("Content-Length");
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

    private String parseLatestVersionFromStream(InputStream inputStream, String chartName)
            throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream);
                java.io.BufferedReader buffered = new java.io.BufferedReader(reader, 16384)) {

            String latestVersion = null;
            boolean foundChart = false;
            int chartIndentLevel = -1;
            boolean inDependencies = false;
            int dependenciesIndent = -1;
            int versionCount = 0;
            int stableCount = 0;
            int linesAfterChart = 0;
            final int MAX_LINES_AFTER_CHART = 10000;

            String line;
            while ((line = buffered.readLine()) != null) {
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') indent++;
                    else if (c == '\t') indent += 4;
                    else break;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                if (!foundChart) {
                    if (trimmed.startsWith(chartName + ":")
                            && (trimmed.length() == chartName.length() + 1
                                    || trimmed.charAt(chartName.length() + 1) != '-')) {
                        foundChart = true;
                        chartIndentLevel = indent;
                        logger.info(
                                "Found chart {} in entries at indent {}",
                                chartName,
                                chartIndentLevel);
                    }
                    continue;
                }

                if (trimmed.startsWith("dependencies:")) {
                    inDependencies = true;
                    dependenciesIndent = indent;
                    continue;
                }

                if (inDependencies
                        && indent <= dependenciesIndent
                        && trimmed.endsWith(":")
                        && !trimmed.startsWith("-")) {
                    inDependencies = false;
                }

                linesAfterChart++;
                if (linesAfterChart > MAX_LINES_AFTER_CHART) {
                    logger.warn("Exceeded max lines after finding chart {}, stopping", chartName);
                    break;
                }

                if (indent <= chartIndentLevel
                        && trimmed.endsWith(":")
                        && !trimmed.startsWith("-")) {
                    break;
                }

                if (trimmed.contains("version:") && !inDependencies) {
                    String version = extractVersionValue(trimmed);
                    if (version != null && !version.isEmpty()) {
                        versionCount++;
                        if (isStableVersion(version)) {
                            stableCount++;
                            if (latestVersion == null
                                    || compareVersions(version, latestVersion) > 0) {
                                latestVersion = version;
                            }
                        }
                    }
                }
            }

            if (latestVersion != null) {
                logger.info(
                        "Chart {}: found latest={} ({} versions, {} stable)",
                        chartName,
                        latestVersion,
                        versionCount,
                        stableCount);
            } else {
                logger.warn(
                        "Chart {}: no stable version found ({} total, {} stable)",
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

    private String extractVersionValue(String line) {
        if (!line.contains("version:")) {
            return null;
        }
        String afterVersion = line.substring(line.indexOf("version:") + 8).trim();
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

    public Map<String, ChartInfo> getTrackedCharts() {
        return trackedCharts;
    }

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

    public void unregisterChart(String appName, String dependencyName) {
        String chartId = appName + "/" + dependencyName;
        if (trackedCharts.remove(chartId) != null) {
            logger.debug("Unregistered chart: {}", chartId);
        }
    }
}
