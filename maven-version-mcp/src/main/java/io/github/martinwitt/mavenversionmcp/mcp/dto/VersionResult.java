package io.github.martinwitt.mavenversionmcp.mcp.dto;

/** Result of a single version query. */
public record VersionResult(String groupId, String artifactId, String version, boolean found) {}
