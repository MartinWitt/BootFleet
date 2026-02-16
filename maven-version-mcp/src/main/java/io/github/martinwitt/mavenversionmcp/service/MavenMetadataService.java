package io.github.martinwitt.mavenversionmcp.service;

import io.github.martinwitt.mavenversionmcp.client.dto.MavenDependencyParts;
import io.github.martinwitt.mavenversionmcp.utils.MavenDependencyUtil;
import io.github.martinwitt.mavenversionmcp.utils.MavenVersioning;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for processing Maven artifact metadata.
 *
 * <p>Handles version filtering, comparisons, and business logic. Remote fetching and caching is
 * delegated to {@link MavenMetadataCachingService}.
 */
@Service
public class MavenMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(MavenMetadataService.class);

    private final MavenMetadataCachingService cachingService;

    public MavenMetadataService(MavenMetadataCachingService cachingService) {
        this.cachingService = cachingService;
    }

    /**
     * Fetches all available versions for a Maven artifact from Maven Central.
     *
     * <p>Fetches maven-metadata.xml from Maven Central and extracts the list of all available
     * versions.
     *
     * @param groupId Maven groupId (e.g., "com.example")
     * @param artifactId Maven artifactId (e.g., "mylib")
     * @return List of version strings, or empty list if metadata cannot be fetched
     */
    public List<String> getAvailableVersions(String groupId, String artifactId) {
        return cachingService.fetchAndCacheVersions(
                MavenDependencyUtil.MAVEN_CENTRAL_URL, groupId, artifactId);
    }

    /**
     * Fetches all available versions for a Maven artifact from a custom repository.
     *
     * @param registryUrl Maven repository URL (e.g., "https://repo1.maven.org/maven2")
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @return List of version strings, or empty list if metadata cannot be fetched
     */
    public List<String> getAvailableVersions(
            String registryUrl, String groupId, String artifactId) {
        return cachingService.fetchAndCacheVersions(registryUrl, groupId, artifactId);
    }

    /**
     * Gets all stable versions (excluding snapshots, alphas, betas) for a Maven artifact.
     *
     * <p>Filters out pre-release and snapshot versions, keeping only stable releases.
     *
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @return List of stable versions sorted by version number (highest first)
     */
    public List<String> getStableVersions(String groupId, String artifactId) {
        List<String> versions = getAvailableVersions(groupId, artifactId);
        return MavenVersioning.filterStableVersions(versions);
    }

    /**
     * Gets the latest stable version for a Maven artifact.
     *
     * <p>Returns the highest stable release version, excluding snapshots and pre-releases.
     *
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @return The latest stable version string, or empty string if none found
     */
    public String getLatestStableVersion(String groupId, String artifactId) {
        List<String> versions = getAvailableVersions(groupId, artifactId);
        return MavenVersioning.findLatestStable(versions);
    }

    /**
     * Gets the latest stable version from a custom repository.
     *
     * @param registryUrl Maven repository URL
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @return The latest stable version string, or empty string if none found
     */
    public String getLatestStableVersion(String registryUrl, String groupId, String artifactId) {
        List<String> versions = getAvailableVersions(registryUrl, groupId, artifactId);
        return MavenVersioning.findLatestStable(versions);
    }

    /**
     * Gets the latest version (including pre-releases) for a Maven artifact.
     *
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @return The latest version string (could be a pre-release), or empty string if none found
     */
    public String getLatestVersion(String groupId, String artifactId) {
        List<String> versions = getAvailableVersions(groupId, artifactId);
        if (versions.isEmpty()) {
            return "";
        }
        return versions.stream().max((v1, v2) -> MavenVersioning.compare(v1, v2)).orElse("");
    }

    /**
     * Gets version information for a dependency string.
     *
     * <p>Parses a Maven dependency string (e.g., "com.example:mylib") and returns all available
     * versions along with the latest stable version.
     *
     * @param dependency Maven dependency string in format "groupId:artifactId"
     * @return VersionInfo containing all versions and latest stable version
     */
    public VersionInfo getVersionInfo(String dependency) {
        try {
            MavenDependencyParts parts = MavenDependencyUtil.parseDependency(dependency);
            List<String> allVersions = getAvailableVersions(parts.groupId(), parts.artifactId());
            String latestStable = MavenVersioning.findLatestStable(allVersions);
            String latest =
                    allVersions.stream()
                            .max((v1, v2) -> MavenVersioning.compare(v1, v2))
                            .orElse("");

            return new VersionInfo(
                    parts.groupId(), parts.artifactId(), allVersions, latestStable, latest);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid dependency format: {}", dependency, e);
            throw e;
        }
    }

    /**
     * Compares two Maven versions according to Maven versioning rules.
     *
     * @param version1 First version to compare
     * @param version2 Second version to compare
     * @return negative if version1 < version2, zero if equal, positive if version1 > version2
     */
    public int compareVersions(String version1, String version2) {
        return MavenVersioning.compare(version1, version2);
    }

    /**
     * Checks if a version is stable (not a snapshot or pre-release).
     *
     * @param version Version string to check
     * @return true if the version is stable, false otherwise
     */
    public boolean isStableVersion(String version) {
        return MavenVersioning.isStable(version);
    }

    /**
     * Container class for version information.
     *
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @param allVersions All available versions
     * @param latestStable Latest stable (non-pre-release) version
     * @param latest Latest version (including pre-releases)
     */
    public record VersionInfo(
            String groupId,
            String artifactId,
            List<String> allVersions,
            String latestStable,
            String latest) {}
}
