package io.github.martinwitt.mavenversionmcp.mcp.dto;

import java.util.List;

/** Result of a versions list query. */
public record VersionsListResult(
        String groupId, String artifactId, List<String> versions, int count) {}
