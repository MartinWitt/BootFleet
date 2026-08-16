package io.github.martinwitt.codesweeper.domain;

/**
 * A repository codesweeper is allowed to clone, fix, and open pull requests against.
 *
 * @param fullName "owner/repo" as used by the gh CLI
 * @param defaultBranch branch to fetch findings for and base PRs against
 * @param workflowFile Qodana workflow file name in .github/workflows whose latest successful run
 *     holds the SARIF artifact
 */
public record TrustedRepo(String fullName, String defaultBranch, String workflowFile) {

    public String owner() {
        return fullName.substring(0, fullName.indexOf('/'));
    }

    public String name() {
        return fullName.substring(fullName.indexOf('/') + 1);
    }
}
