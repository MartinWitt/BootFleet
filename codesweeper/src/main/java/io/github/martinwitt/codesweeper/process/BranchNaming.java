package io.github.martinwitt.codesweeper.process;

import io.github.martinwitt.codesweeper.sarif.SarifFinding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic branch name per finding, so re-running codesweeper doesn't open duplicate PRs for
 * the same finding.
 */
public final class BranchNaming {

    private BranchNaming() {}

    public static String branchFor(SarifFinding finding) {
        String slug = finding.ruleId().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String hash = shortHash(finding.filePath() + ":" + finding.startLine());
        return "codesweeper/" + slug + "-" + hash;
    }

    private static String shortHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
