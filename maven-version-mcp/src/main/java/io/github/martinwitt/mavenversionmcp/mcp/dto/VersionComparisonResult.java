package io.github.martinwitt.mavenversionmcp.mcp.dto;

/** Result of version comparison. */
public record VersionComparisonResult(String version1, String version2, String relation) {
    // relation is one of: "greater", "less", "equal"
}
