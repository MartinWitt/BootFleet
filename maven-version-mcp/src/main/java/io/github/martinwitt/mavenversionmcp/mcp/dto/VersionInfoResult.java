package io.github.martinwitt.mavenversionmcp.mcp.dto;

/** Comprehensive version information. */
public record VersionInfoResult(
        String dependency, int totalVersions, String latestStable, String latest) {}
