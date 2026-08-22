package io.github.martinwitt.codesweeper.sarif;

/**
 * One Qodana finding, flattened out of a SARIF result entry.
 *
 * @param filePath repo-relative path, e.g.
 *     "maven-version-mcp/src/main/java/.../MavenVersioning.java"
 */
public record SarifFinding(String ruleId, String message, String filePath, int startLine) {}
