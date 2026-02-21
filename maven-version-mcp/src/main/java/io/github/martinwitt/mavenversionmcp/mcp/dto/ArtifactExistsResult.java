package io.github.martinwitt.mavenversionmcp.mcp.dto;

/** Result of checking if an artifact exists. */
public record ArtifactExistsResult(
        String groupId, String artifactId, boolean exists, int versionCount) {}
