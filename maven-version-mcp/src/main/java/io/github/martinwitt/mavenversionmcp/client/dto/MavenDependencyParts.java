package io.github.martinwitt.mavenversionmcp.client.dto;

/**
 * Parsed Maven dependency parts (groupId:artifactId).
 *
 * @param groupId Maven groupId
 * @param artifactId Maven artifactId
 */
public record MavenDependencyParts(String groupId, String artifactId) {}
