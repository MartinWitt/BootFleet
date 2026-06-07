package io.github.martinwitt.mavenversionmcp.mcp;

import io.github.martinwitt.mavenversionmcp.mcp.dto.ArtifactExistsResult;
import io.github.martinwitt.mavenversionmcp.mcp.dto.VersionComparisonResult;
import io.github.martinwitt.mavenversionmcp.mcp.dto.VersionInfoResult;
import io.github.martinwitt.mavenversionmcp.mcp.dto.VersionResult;
import io.github.martinwitt.mavenversionmcp.mcp.dto.VersionsListResult;
import io.github.martinwitt.mavenversionmcp.service.MavenMetadataService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Tools for Maven version queries.
 *
 * <p>Exposes callable tools for getting Maven artifact versions, comparing versions, and finding
 * latest releases using Spring AI MCP annotations.
 */
@Component
public class MavenVersionMcpTools {

    private static final Logger logger = LoggerFactory.getLogger(MavenVersionMcpTools.class);

    private final MavenMetadataService metadataService;

    public MavenVersionMcpTools(MavenMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @McpTool(
            name = "maven-get-latest-version",
            description =
                    """
                    Look up the newest published version of a Maven artifact on Maven Central,\
                     including snapshots and pre-releases. Use this when you need the\
                     absolute latest version regardless of stability, for example when\
                     tracking bleeding-edge releases or checking whether a new version\
                     was published. Prefer maven-get-latest-stable-version when updating\
                     production pom.xml dependencies.\
                    """)
    public VersionResult getLatestVersion(
            @McpToolParam(description = "Maven groupId (e.g., com.google.inject)", required = true)
                    String groupId,
            @McpToolParam(description = "Maven artifactId (e.g., guice)", required = true)
                    String artifactId) {

        logger.info("Getting latest version for {}:{}", groupId, artifactId);
        String latest = metadataService.getLatestVersion(groupId, artifactId);
        boolean found = !latest.isEmpty();
        return new VersionResult(groupId, artifactId, latest, found);
    }

    @McpTool(
            name = "maven-get-latest-stable-version",
            description =
                    """
                    Look up the latest production-ready version of a Maven artifact, excluding\
                     snapshots and pre-releases. Use this whenever you need to upgrade or\
                     pin a dependency version in a pom.xml file. This is faster and more\
                     reliable than running Maven commands or searching Maven Central\
                     manually. Always prefer this over maven-get-latest-version for\
                     production dependency updates.\
                    """)
    public VersionResult getLatestStableVersion(
            @McpToolParam(description = "Maven groupId (e.g., com.google.inject)", required = true)
                    String groupId,
            @McpToolParam(description = "Maven artifactId (e.g., guice)", required = true)
                    String artifactId) {

        logger.info("Getting latest stable version for {}:{}", groupId, artifactId);
        String latest = metadataService.getLatestStableVersion(groupId, artifactId);
        boolean found = !latest.isEmpty();
        return new VersionResult(groupId, artifactId, latest, found);
    }

    @McpTool(
            name = "maven-get-all-versions",
            description =
                    """
                    Retrieve the complete release history of a Maven artifact, including snapshots\
                     and pre-releases. Use this when you need to pick a specific version\
                     range, check what versions exist between two releases, or understand\
                     the full release history. For just finding the best version to use,\
                     prefer maven-get-latest-stable-version or maven-get-stable-versions\
                     instead.\
                    """)
    public VersionsListResult getAllVersions(
            @McpToolParam(description = "Maven groupId (e.g., com.google.inject)", required = true)
                    String groupId,
            @McpToolParam(description = "Maven artifactId (e.g., guice)", required = true)
                    String artifactId) {

        logger.info("Getting all versions for {}:{}", groupId, artifactId);
        List<String> versions = metadataService.getAvailableVersions(groupId, artifactId);
        return new VersionsListResult(groupId, artifactId, versions, versions.size());
    }

    @McpTool(
            name = "maven-get-stable-versions",
            description =
                    """
                    List all stable, production-ready versions of a Maven artifact, excluding\
                     snapshots and pre-releases. Use this when you need to choose from\
                     available stable releases, for example to downgrade to a previous\
                     stable version or to verify which stable versions are available in a\
                     given major/minor range.\
                    """)
    public VersionsListResult getStableVersions(
            @McpToolParam(description = "Maven groupId (e.g., com.google.inject)", required = true)
                    String groupId,
            @McpToolParam(description = "Maven artifactId (e.g., guice)", required = true)
                    String artifactId) {

        logger.info("Getting stable versions for {}:{}", groupId, artifactId);
        List<String> versions = metadataService.getStableVersions(groupId, artifactId);
        return new VersionsListResult(groupId, artifactId, versions, versions.size());
    }

    @McpTool(
            name = "maven-get-version-info",
            description =
                    """
                    Get a complete version summary for a Maven artifact in one call: total number\
                     of releases, the latest stable version, and the absolute latest\
                     version (including pre-releases). Use this as the first step when\
                     you need a quick overview before deciding which version to use, or\
                     when you want both the stable and bleeding-edge version at once\
                     without making two separate calls. Input format: 'groupId:artifactId'\
                     (e.g., 'org.springframework:spring-core').\
                    """)
    public VersionInfoResult getVersionInfo(
            @McpToolParam(
                            description = "Maven dependency string in format 'groupId:artifactId'",
                            required = true)
                    String dependency) {

        logger.info("Getting version info for {}", dependency);
        MavenMetadataService.VersionInfo versionInfo = metadataService.getVersionInfo(dependency);
        return new VersionInfoResult(
                dependency,
                versionInfo.allVersions().size(),
                versionInfo.latestStable().isEmpty() ? "" : versionInfo.latestStable(),
                versionInfo.latest().isEmpty() ? "" : versionInfo.latest());
    }

    @McpTool(
            name = "maven-compare-versions",
            description =
                    """
                    Compare two Maven version strings using official Maven versioning semantics.\
                     Returns whether version1 is greater, less, or equal to version2.\
                     Use this instead of string comparison when order matters — Maven\
                     versioning is not lexicographic (e.g., '1.10' > '1.9', '1.0-SNAPSHOT'\
                     < '1.0'). Useful for validating upgrade paths or checking if a\
                     currently used version is outdated.\
                    """)
    public VersionComparisonResult compareVersions(
            @McpToolParam(description = "First version to compare", required = true)
                    String version1,
            @McpToolParam(description = "Second version to compare", required = true)
                    String version2) {

        logger.info("Comparing versions {} and {}", version1, version2);
        int comparison = metadataService.compareVersions(version1, version2);
        String result;
        if (comparison > 0) {
            result = "greater";
        } else if (comparison < 0) {
            result = "less";
        } else {
            result = "equal";
        }
        return new VersionComparisonResult(version1, version2, result);
    }

    @McpTool(
            name = "maven-artifact-exists",
            description =
                    """
                    Verify that a Maven artifact exists on Maven Central before adding it to a\
                     pom.xml. Use this to catch typos in groupId or artifactId early,\
                     avoiding broken builds. Also returns the total number of available\
                     versions as a quick sanity check. Call this before adding any new\
                     dependency you are not 100% certain about.\
                    """)
    public ArtifactExistsResult artifactExists(
            @McpToolParam(description = "Maven groupId (e.g., com.google.inject)", required = true)
                    String groupId,
            @McpToolParam(description = "Maven artifactId (e.g., guice)", required = true)
                    String artifactId) {

        logger.info("Checking if artifact exists for {}:{}", groupId, artifactId);
        List<String> versions = metadataService.getAvailableVersions(groupId, artifactId);
        boolean exists = !versions.isEmpty();
        return new ArtifactExistsResult(groupId, artifactId, exists, versions.size());
    }
}
