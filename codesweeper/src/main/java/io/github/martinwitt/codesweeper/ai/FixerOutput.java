package io.github.martinwitt.codesweeper.ai;

/**
 * @param fixedFileContent the complete corrected file, not a diff
 */
public record FixerOutput(String fixedFileContent, String explanation) {}
