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
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
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
            description = "Get the latest version of a Maven artifact (including pre-releases)")
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
                    "Get the latest stable version of a Maven artifact (excluding snapshots and"
                            + " pre-releases)")
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
            description = "Get all available versions of a Maven artifact")
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
                    "Get all stable versions of a Maven artifact (excluding snapshots and"
                            + " pre-releases)")
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
            description = "Get comprehensive version information for a Maven artifact")
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
            description = "Compare two Maven versions according to Maven versioning rules")
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
            description = "Check if a Maven artifact (groupId + artifactId) exists in any version")
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
