package io.github.martinwitt.imagedetector.service;

import io.github.martinwitt.imagedetector.model.HelmChartDependencyEntity;
import io.github.martinwitt.imagedetector.model.HelmChartDependencyRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

@Service
public class HelmVersionCheckService {
    private static final Logger logger = LoggerFactory.getLogger(HelmVersionCheckService.class);
    // Large Helm repos can have big index.yaml files (Bitnami ~60MB), allow up to 256MB
    private static final int MAX_INDEX_SIZE = 256 * 1024 * 1024; // 256MB max
    private final HelmChartDependencyRepository dependencyRepo;
    private final RestTemplate restTemplate;

    public HelmVersionCheckService(HelmChartDependencyRepository dependencyRepo) {
        this.dependencyRepo = dependencyRepo;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 seconds
        factory.setReadTimeout(30000); // 30 seconds

        this.restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(factory));
    }

    private Yaml createYamlParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(256 * 1024 * 1024);
        loaderOptions.setMaxAliasesForCollections(1000000);
        loaderOptions.setAllowDuplicateKeys(false);
        return new Yaml(loaderOptions);
    }

    @Scheduled(fixedDelayString = "${app.helm-check-interval-ms:600000}")
    public void checkForUpdates() {
        logger.info("Starting Helm version check");

        try {
            // Cache per repository: repoUrl -> (chartName -> latestVersion)
            Map<String, Map<String, String>> repoCache = new HashMap<>();
            int pageSize = 100;
            int pageNum = 0;
            boolean hasMore = true;

            while (hasMore) {
                Pageable pageable = PageRequest.of(pageNum, pageSize);
                var page = dependencyRepo.findAll(pageable);

                for (HelmChartDependencyEntity dep : page.getContent()) {
                    try {
                        String latestVersion = findLatestVersion(dep, repoCache);
                        if (latestVersion != null
                                && !latestVersion.equals(dep.getLatestVersion())) {
                            dep.setLatestVersion(latestVersion);
                            if (!latestVersion.equals(dep.getVersion())) {
                                logger.info(
                                        "Update available for {}/{}: {} -> {}",
                                        dep.getAppName(),
                                        dep.getDependencyName(),
                                        dep.getVersion(),
                                        latestVersion);
                            }
                            dependencyRepo.save(dep);
                        }
                    } catch (Exception e) {
                        logger.warn(
                                "Failed to check version for {}/{}: {}",
                                dep.getAppName(),
                                dep.getDependencyName(),
                                e.getMessage());
                    }
                }

                hasMore = page.hasNext();
                pageNum++;
            }

            logger.info("Helm version check completed");
        } catch (Exception e) {
            logger.error("Helm version check failed: {}", e.getMessage());
        }
    }

    private String findLatestVersion(
            HelmChartDependencyEntity dep, Map<String, Map<String, String>> repoCache)
            throws IOException {
        if (dep.getRepository() == null || dep.getRepository().isBlank()) {
            return null;
        }

        String repoUrl = dep.getRepository().trim();
        if (!repoUrl.endsWith("/")) {
            repoUrl += "/";
        }

        String indexUrl = repoUrl + "index.yaml";
        String chartName = dep.getDependencyName();

        // Check cache first
        Map<String, String> chartCache = repoCache.computeIfAbsent(repoUrl, k -> new HashMap<>());
        if (chartCache.containsKey(chartName)) {
            return chartCache.get(chartName);
        }

        // Fetch and parse streaming
        try {
            String latestVersion = fetchLatestChartVersionStreaming(indexUrl, chartName);
            if (latestVersion != null && isStableVersion(latestVersion)) {
                chartCache.put(chartName, latestVersion);
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
            ResponseEntity<byte[]> response = restTemplate.getForEntity(indexUrl, byte[].class);

            if (response.getBody() == null || response.getBody().length == 0) {
                throw new IOException("Empty response from " + indexUrl);
            }

            // Safety check on response size
            if (response.getBody().length > MAX_INDEX_SIZE) {
                logger.warn(
                        "Helm index.yaml response too large ({}MB) from {}, skipping",
                        response.getBody().length / (1024 * 1024),
                        indexUrl);
                return null;
            }

            // Stream parse YAML looking only for our chart name and its versions
            try (InputStream inputStream = new java.io.ByteArrayInputStream(response.getBody())) {
                return parseLatestVersionFromStream(inputStream, chartName);
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch chart versions from {}: {}", indexUrl, e.getMessage());
            throw new IOException(
                    "Failed to fetch index from " + indexUrl + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parses YAML stream and extracts only the latest version for the specified chart. Stops early
     * once latest version is found - no need to load entire YAML.
     */
    private String parseLatestVersionFromStream(InputStream inputStream, String chartName)
            throws IOException {
        try {
            Yaml yaml = createYamlParser();
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                // Load the entire YAML as a Map
                @SuppressWarnings("unchecked")
                Map<String, Object> root = yaml.load(reader);

                String latestVersion = null;

                if (root == null) {
                    return null;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> entries = (Map<String, Object>) root.get("entries");
                if (entries == null) {
                    return null;
                }

                Object chartData = entries.get(chartName);
                if (chartData == null) {
                    return null;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> chartVersions = (List<Map<String, Object>>) chartData;
                if (chartVersions == null || chartVersions.isEmpty()) {
                    return null;
                }

                // Iterate through all versions and find the latest stable one
                for (Map<String, Object> versionEntry : chartVersions) {
                    Object versionObj = versionEntry.get("version");
                    if (versionObj == null) {
                        continue;
                    }

                    String version = versionObj.toString().trim();
                    if (version.isEmpty()) {
                        continue;
                    }

                    boolean stable = isStableVersion(version);
                    logger.debug("Found version {} for chart {} - stable: {}", version, chartName, stable);

                    if (stable) {
                        if (latestVersion == null) {
                            latestVersion = version;
                            logger.debug("Set initial latestVersion to {}", version);
                        } else {
                            int cmp = compareVersions(version, latestVersion);
                            logger.debug("Comparing {} vs {} = {}", version, latestVersion, cmp);
                            if (cmp > 0) {
                                latestVersion = version;
                                logger.debug("Updated latestVersion to {}", version);
                            }
                        }
                    }
                }

                return latestVersion;
            }
        } catch (Exception e) {
            logger.warn("YAML parsing error for chart {}: {}", chartName, e.getMessage());
            throw new IOException("YAML parse error: " + e.getMessage(), e);
        }
    }

    private boolean isStableVersion(String version) {
        String lower = version.toLowerCase();
        // Reject pre-release versions, snapshots, 0.0.0, and versions with wildcards or special chars
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
}
