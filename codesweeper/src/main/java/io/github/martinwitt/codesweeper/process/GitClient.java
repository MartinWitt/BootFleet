package io.github.martinwitt.codesweeper.process;

import io.github.martinwitt.codesweeper.domain.TrustedRepo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Thin wrapper around the git CLI, operating on a disposable local clone per trusted repo. */
@Component
public class GitClient {

    private final ProcessRunner processRunner;

    public GitClient(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Clones repo into workspaceDir if missing, otherwise resets the existing checkout to origin's
     * default branch.
     */
    public Path cloneOrUpdate(Path workspaceDir, TrustedRepo repo) {
        Path checkout = workspaceDir.resolve(repo.fullName().replace('/', '-'));
        if (!Files.isDirectory(checkout.resolve(".git"))) {
            run(workspaceDir, "gh", "repo", "clone", repo.fullName(), checkout.toString());
        } else {
            run(checkout, "git", "checkout", repo.defaultBranch());
            run(checkout, "git", "clean", "-fd");
            run(checkout, "git", "fetch", "origin", repo.defaultBranch());
            run(checkout, "git", "reset", "--hard", "origin/" + repo.defaultBranch());
        }
        return checkout;
    }

    public void discardChanges(Path checkout) {
        run(checkout, "git", "checkout", ".");
        run(checkout, "git", "clean", "-fd");
    }

    public void createBranch(Path checkout, String branch) {
        run(checkout, "git", "checkout", "-b", branch);
    }

    public void commitAll(Path checkout, String message) {
        run(checkout, "git", "add", "-A");
        run(checkout, "git", "commit", "-m", message);
    }

    public void push(Path checkout, String branch) {
        run(checkout, "git", "push", "-u", "origin", branch);
    }

    private ProcessResult run(Path workDir, String... command) {
        ProcessResult result = processRunner.run(workDir, command);
        if (!result.success()) {
            throw new IllegalStateException(
                    "Command failed: " + String.join(" ", command) + "\n" + result.output());
        }
        return result;
    }
}
