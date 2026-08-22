package io.github.martinwitt.codesweeper.process;

import io.github.martinwitt.codesweeper.sarif.SarifFinding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic branch name per finding and model, so re-running codesweeper doesn't open duplicate
 * PRs for the same finding fixed by the same model - while still letting different models produce
 * separate, comparable PRs for the same finding.
 */
public final class BranchNaming {

    private BranchNaming() {}

    public static String branchFor(SarifFinding finding, String model) {
        String slug = finding.ruleId().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String modelSlug = model.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String hash = shortHash(finding.filePath() + ":" + finding.startLine() + ":" + model);
        return "codesweeper/" + slug + "-" + modelSlug + "-" + hash;
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
