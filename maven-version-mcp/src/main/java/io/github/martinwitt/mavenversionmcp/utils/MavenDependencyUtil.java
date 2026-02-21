package io.github.martinwitt.mavenversionmcp.utils;

import io.github.martinwitt.mavenversionmcp.client.dto.MavenDependencyParts;

/**
 * Utility for parsing Maven dependency strings and constructing Maven repository URLs.
 *
 * <p>Handles conversion of Maven coordinates (groupId:artifactId:version) and construction of Maven
 * repository URL paths following the standard Maven repository structure.
 */
public class MavenDependencyUtil {

    public static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2";

    /**
     * Parses a Maven dependency string into its components.
     *
     * <p>Supports formats:
     *
     * <ul>
     *   <li>groupId:artifactId
     *   <li>groupId:artifactId:version
     * </ul>
     *
     * @param dependency Dependency string in Maven coordinate format
     * @return MavenDependencyParts containing parsed groupId, artifactId, and optional version
     * @throws IllegalArgumentException if dependency format is invalid
     */
    public static MavenDependencyParts parseDependency(String dependency) {
        String[] parts = dependency.split(":");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException(
                    "Invalid dependency format. Expected 'groupId:artifactId' or"
                            + " 'groupId:artifactId:version', got: "
                            + dependency);
        }

        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts.length == 3 ? parts[2].trim() : null;

        if (groupId.isEmpty() || artifactId.isEmpty()) {
            throw new IllegalArgumentException(
                    "groupId and artifactId must not be empty in dependency: " + dependency);
        }

        return new MavenDependencyParts(groupId, artifactId);
    }

    /**
     * Constructs the URL for fetching maven-metadata.xml from a repository.
     *
     * @param registryUrl Base URL of the Maven repository (e.g., https://repo1.maven.org/maven2)
     * @param groupId Maven groupId (e.g., com.example)
     * @param artifactId Maven artifactId (e.g., mylib)
     * @return Complete URL to maven-metadata.xml
     */
    public static String getMavenMetadataUrl(
            String registryUrl, String groupId, String artifactId) {
        String groupPath = convertGroupIdToPath(groupId);
        return String.format(
                "%s/%s/%s/maven-metadata.xml",
                registryUrl.replaceAll("/$", ""), // Remove trailing slash if present
                groupPath,
                artifactId);
    }

    /**
     * Constructs the URL for fetching maven-metadata.xml from Maven Central.
     *
     * @param groupId Maven groupId (e.g., com.example)
     * @param artifactId Maven artifactId (e.g., mylib)
     * @return Complete URL to maven-metadata.xml on Maven Central
     */
    public static String getMavenMetadataUrl(String groupId, String artifactId) {
        return getMavenMetadataUrl(MAVEN_CENTRAL_URL, groupId, artifactId);
    }

    /**
     * Constructs the URL for fetching a POM file.
     *
     * @param registryUrl Base URL of the Maven repository
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @param version Artifact version
     * @return Complete URL to the POM file
     */
    public static String getPomUrl(
            String registryUrl, String groupId, String artifactId, String version) {
        String groupPath = convertGroupIdToPath(groupId);
        return String.format(
                "%s/%s/%s/%s/%s-%s.pom",
                registryUrl.replaceAll("/$", ""),
                groupPath,
                artifactId,
                version,
                artifactId,
                version);
    }

    /**
     * Constructs the URL for fetching a POM file from Maven Central.
     *
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @param version Artifact version
     * @return Complete URL to the POM file on Maven Central
     */
    public static String getPomUrl(String groupId, String artifactId, String version) {
        return getPomUrl(MAVEN_CENTRAL_URL, groupId, artifactId, version);
    }

    /**
     * Converts a Maven groupId to a repository path.
     *
     * <p>Example: "com.example" becomes "com/example"
     *
     * @param groupId Maven groupId with dot notation
     * @return Repository path with slashes
     */
    public static String convertGroupIdToPath(String groupId) {
        return groupId.replace(".", "/");
    }

    /**
     * Validates a Maven version string.
     *
     * @param version Version string to validate
     * @return true if the version appears to be valid, false otherwise
     */
    public static boolean isValidVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return false;
        }
        // Maven versions typically contain numbers and dots, may contain qualifiers
        return version.matches("[\\w.\\-]+");
    }
}
