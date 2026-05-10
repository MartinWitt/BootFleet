package io.github.martinwitt.imagedetector.service;

import io.github.martinwitt.imagedetector.model.HelmChartDependencyEntity;
import io.github.martinwitt.imagedetector.model.HelmChartDependencyRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class HelmVersionCheckService {
    private static final Logger logger = LoggerFactory.getLogger(HelmVersionCheckService.class);
    private final HelmChartDependencyRepository dependencyRepo;
    private final RestTemplate restTemplate;
    private final Yaml yaml;

    public HelmVersionCheckService(HelmChartDependencyRepository dependencyRepo) {
        this.dependencyRepo = dependencyRepo;
        this.restTemplate = new RestTemplate();
        restTemplate
                .getMessageConverters()
                .addFirst(new StringHttpMessageConverter(StandardCharsets.UTF_8));

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(100 * 1024 * 1024); // 100MB
        loaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
        loaderOptions.setAllowDuplicateKeys(true);

        this.yaml = new Yaml(loaderOptions);
    }

    @Scheduled(fixedDelayString = "${app.helm-check-interval-ms:600000}")
    public void checkForUpdates() {
        logger.info("Starting Helm version check");

        try {
            List<HelmChartDependencyEntity> dependencies = dependencyRepo.findAll();
            Map<String, Set<String>> repoCache = new HashMap<>();

            for (HelmChartDependencyEntity dep : dependencies) {
                try {
                    String latestVersion = findLatestVersion(dep, repoCache);
                    if (latestVersion != null && !latestVersion.equals(dep.getLatestVersion())) {
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

            logger.info("Helm version check completed");
        } catch (Exception e) {
            logger.error("Helm version check failed: {}", e.getMessage());
        }
    }

    private String findLatestVersion(
            HelmChartDependencyEntity dep, Map<String, Set<String>> repoCache) throws IOException {
        if (dep.getRepository() == null || dep.getRepository().isBlank()) {
            return null;
        }

        String repoUrl = dep.getRepository().trim();
        if (!repoUrl.endsWith("/")) {
            repoUrl += "/";
        }

        String indexUrl = repoUrl + "index.yaml";

        // Prüfe Cache
        if (repoCache.containsKey(repoUrl)) {
            Set<String> versions = repoCache.get(repoUrl);
            return versions.stream()
                    .filter(v -> !v.isEmpty())
                    .max(this::compareVersions)
                    .orElse(null);
        }

        // Fetch index.yaml
        try {
            Set<String> availableVersions = fetchChartVersions(indexUrl, dep.getDependencyName());
            repoCache.put(repoUrl, availableVersions);

            return availableVersions.stream()
                    .filter(v -> !v.isEmpty())
                    .filter(this::isStableVersion)
                    .max(this::compareVersions)
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Failed to fetch index from {}: {}", indexUrl, e.getMessage());
            return null;
        }
    }

    private Set<String> fetchChartVersions(String indexUrl, String chartName) throws IOException {
        try {
            String response = restTemplate.getForObject(indexUrl, String.class);

            if (response == null) {
                throw new IOException("Empty response from " + indexUrl);
            }

            Map<String, Object> index = yaml.load(sanitizeYaml(response));
            if (index == null || !index.containsKey("entries")) {
                return Collections.emptySet();
            }

            Map<?, ?> entries = (Map<?, ?>) index.get("entries");
            Object chartObj = entries.get(chartName);

            if (!(chartObj instanceof List<?> charts)) {
                return Collections.emptySet();
            }

            Set<String> versions = new HashSet<>();

            for (Object chart : charts) {
                if (chart instanceof Map<?, ?> chartMap) {
                    Object version = chartMap.get("version");
                    if (version != null) {
                        versions.add(version.toString());
                    }
                }
            }

            return versions;
        } catch (Exception e) {
            logger.warn("Failed to fetch chart versions from {}: {}", indexUrl, e.getMessage());
            throw new IOException(
                    "Failed to fetch index from " + indexUrl + ": " + e.getMessage(), e);
        }
    }

    private boolean isStableVersion(String version) {
        String lower = version.toLowerCase();
        return !lower.matches(".*(-alpha|-beta|-rc|v?0\\.0\\.0-|-dev|-snapshot|-next).*");
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

    private static String sanitizeYaml(String input) {
        if (input == null) return null;

        StringBuilder out = new StringBuilder(input.length());

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // Entferne BOM
            if (c == '\uFEFF') continue;

            // Entferne NBSP
            if (c == '\u00A0') continue;

            // Entferne Zero-width characters
            if (c == '\u200B' || c == '\u200C' || c == '\u200D') continue;

            // Entferne alle Control Characters außer erlaubte
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') continue;

            out.append(c);
        }

        return out.toString();
    }
}
