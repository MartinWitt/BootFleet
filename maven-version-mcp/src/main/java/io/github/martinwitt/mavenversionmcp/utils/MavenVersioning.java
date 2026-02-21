package io.github.martinwitt.mavenversionmcp.utils;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maven versioning rules and comparison logic.
 *
 * <p>Implements Maven's semantic versioning rules as defined in the Maven Versioning documentation.
 * Properly handles version qualifiers like SNAPSHOT, alpha, beta, rc, etc.
 *
 * <p>Stability hierarchy (from highest to lowest):
 *
 * <ul>
 *   <li>Release versions (e.g., 1.0.0)
 *   <li>Release candidates (e.g., 1.0.0-rc.1)
 *   <li>Beta versions (e.g., 1.0.0-beta.1)
 *   <li>Alpha versions (e.g., 1.0.0-alpha.1)
 *   <li>Snapshot versions (e.g., 1.0.0-SNAPSHOT)
 * </ul>
 */
public class MavenVersioning {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-(.+))?(?:(?:(?!)()))?$");

    private static final Pattern QUALIFIER_PATTERN =
            Pattern.compile("^([a-zA-Z]+)?(?:[.\\-]?(\\d+))?.*$");

    // Qualifier priority (higher number = more stable)
    private static final int SNAPSHOT_PRIORITY = 0;
    private static final int ALPHA_PRIORITY = 1;
    private static final int BETA_PRIORITY = 2;
    private static final int RC_PRIORITY = 3;
    private static final int RELEASE_PRIORITY = 4;

    /**
     * Compares two Maven versions.
     *
     * @param v1 First version string
     * @param v2 Second version string
     * @return negative if v1 < v2, zero if equal, positive if v1 > v2
     */
    public static int compare(String v1, String v2) {
        if (v1.equals(v2)) {
            return 0;
        }

        VersionParts parts1 = parseVersion(v1);
        VersionParts parts2 = parseVersion(v2);

        // Compare major version
        int majorCmp = Integer.compare(parts1.major, parts2.major);
        if (majorCmp != 0) {
            return majorCmp;
        }

        // Compare minor version
        int minorCmp = Integer.compare(parts1.minor, parts2.minor);
        if (minorCmp != 0) {
            return minorCmp;
        }

        // Compare patch version
        int patchCmp = Integer.compare(parts1.patch, parts2.patch);
        if (patchCmp != 0) {
            return patchCmp;
        }

        // Compare qualifiers (e.g., alpha, beta, rc, SNAPSHOT)
        return compareQualifiers(parts1.qualifier, parts2.qualifier);
    }

    /**
     * Gets the comparator for sorting Maven versions.
     *
     * @return Comparator for Maven versions (highest version first)
     */
    public static Comparator<String> getComparator() {
        return (v1, v2) -> compare(v2, v1); // Reverse order for descending sort
    }

    /**
     * Determines if a version is considered stable (not a snapshot/alpha/beta/rc).
     *
     * @param version Version string to check
     * @return true if the version is stable, false otherwise
     */
    public static boolean isStable(String version) {
        VersionParts parts = parseVersion(version);
        return parts.qualifierPriority >= RELEASE_PRIORITY;
    }

    /**
     * Determines if a version is a snapshot.
     *
     * @param version Version string to check
     * @return true if the version contains SNAPSHOT qualifier
     */
    public static boolean isSnapshot(String version) {
        return version.toUpperCase().contains("SNAPSHOT");
    }

    /**
     * Filters versions to return only stable releases.
     *
     * @param versions List of version strings
     * @return List of stable versions sorted by version number (highest first)
     */
    public static List<String> filterStableVersions(List<String> versions) {
        return versions.stream().filter(MavenVersioning::isStable).sorted(getComparator()).toList();
    }

    /**
     * Finds the latest stable version from a list.
     *
     * @param versions List of version strings
     * @return The latest stable version, or empty string if none found
     */
    public static String findLatestStable(List<String> versions) {
        return filterStableVersions(versions).stream().findFirst().orElse("");
    }

    private static VersionParts parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());

        if (!matcher.matches()) {
            // Fallback for non-standard versions
            return new VersionParts(0, 0, 0, version, getQualifierPriority(version));
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        String qualifier = matcher.group(4) != null ? matcher.group(4) : "";
        String suffix = matcher.group(5);

        if (suffix != null && !suffix.isEmpty()) {
            qualifier = qualifier + "." + suffix;
        }

        int priority = getQualifierPriority(qualifier);

        return new VersionParts(major, minor, patch, qualifier, priority);
    }

    private static int getQualifierPriority(String qualifier) {
        if (qualifier == null || qualifier.isEmpty()) {
            return RELEASE_PRIORITY;
        }

        String lowerQualifier = qualifier.toLowerCase();

        if (lowerQualifier.contains("snapshot")) {
            return SNAPSHOT_PRIORITY;
        }

        // Treat "alpha" and shorthand forms like "a", "a1", "a01" as alpha.
        boolean isAlphaShorthand = lowerQualifier.matches("a\\d*");
        if (lowerQualifier.contains("alpha") || isAlphaShorthand) {
            return ALPHA_PRIORITY;
        }

        // Treat "beta" and shorthand forms like "b", "b1", "b02" as beta.
        boolean isBetaShorthand = lowerQualifier.matches("b\\d*");
        if (lowerQualifier.contains("beta") || isBetaShorthand) {
            return BETA_PRIORITY;
        }
        if (lowerQualifier.contains("rc") || lowerQualifier.contains("cr")) {
            return RC_PRIORITY;
        }

        // Unknown qualifier - treat as pre-release (less stable than release)
        if (lowerQualifier.isEmpty() || !Character.isDigit(lowerQualifier.charAt(0))) {
            return BETA_PRIORITY;
        }

        return RELEASE_PRIORITY;
    }

    private static int compareQualifiers(String q1, String q2) {
        // Both are release versions (no qualifier)
        if ((q1 == null || q1.isEmpty()) && (q2 == null || q2.isEmpty())) {
            return 0;
        }

        // One is a release, the other isn't - release is higher
        if ((q1 == null || q1.isEmpty()) && (q2 != null && !q2.isEmpty())) {
            return 1;
        }
        if ((q1 != null && !q1.isEmpty()) && (q2 == null || q2.isEmpty())) {
            return -1;
        }

        int p1 = getQualifierPriority(q1);
        int p2 = getQualifierPriority(q2);

        if (p1 != p2) {
            return Integer.compare(p1, p2);
        }

        // Same qualifier type - compare numerically
        return compareQualifierNumbers(q1, q2);
    }

    private static int compareQualifierNumbers(String q1, String q2) {
        Matcher m1 = QUALIFIER_PATTERN.matcher(q1);
        Matcher m2 = QUALIFIER_PATTERN.matcher(q2);

        m1.matches();
        m2.matches();

        String type1 = m1.group(1) != null ? m1.group(1) : "";
        String type2 = m2.group(1) != null ? m2.group(1) : "";

        // If types are different, compare strings
        if (!type1.equals(type2)) {
            return type1.compareTo(type2);
        }

        // Compare numeric parts if available
        if (m1.group(2) != null && m2.group(2) != null) {
            int num1 = Integer.parseInt(m1.group(2));
            int num2 = Integer.parseInt(m2.group(2));
            return Integer.compare(num1, num2);
        }

        // Fallback to string comparison
        return q1.compareTo(q2);
    }

    /** Internal class for holding parsed version parts. */
    private static class VersionParts {
        int major;
        int minor;
        int patch;
        String qualifier;
        int qualifierPriority;

        VersionParts(int major, int minor, int patch, String qualifier, int qualifierPriority) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.qualifier = qualifier;
            this.qualifierPriority = qualifierPriority;
        }
    }
}
