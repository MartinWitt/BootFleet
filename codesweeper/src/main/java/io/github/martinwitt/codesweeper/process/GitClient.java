package io.github.martinwitt.codesweeper.process;

import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import io.github.martinwitt.codesweeper.domain.TrustedRepo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around the git CLI, authenticated the same way actions/checkout does - no gh CLI
 * involved.
 */
@Component
public class GitClient {

    private final ProcessRunner processRunner;
    private final String authHeader;

    public GitClient(ProcessRunner processRunner, CodesweeperProperties properties) {
        this.processRunner = processRunner;
        // actions/checkout's own convention: basic auth, "x-access-token" as the username, the PAT
        // as the password, base64-encoded - a raw bearer token isn't a scheme github.com's git HTTP
        // backend accepts.
        String credentials = "x-access-token:" + properties.githubToken();
        this.authHeader =
                "AUTHORIZATION: basic "
                        + Base64.getEncoder()
                                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Clones repo into workspaceDir if missing, otherwise resets the existing checkout to origin's
     * default branch.
     */
    public Path cloneOrUpdate(Path workspaceDir, TrustedRepo repo) {
        Path checkout = workspaceDir.resolve(repo.fullName().replace('/', '-'));
        if (!Files.isDirectory(checkout.resolve(".git"))) {
            run(
                    workspaceDir,
                    "clone",
                    "https://github.com/" + repo.fullName() + ".git",
                    checkout.toString());
        } else {
            run(checkout, "checkout", repo.defaultBranch());
            run(checkout, "clean", "-fd");
            run(checkout, "fetch", "origin", repo.defaultBranch());
            run(checkout, "reset", "--hard", "origin/" + repo.defaultBranch());
        }
        return checkout;
    }

    public void discardChanges(Path checkout) {
        run(checkout, "checkout", ".");
        run(checkout, "clean", "-fd");
    }

    public void createBranch(Path checkout, String branch) {
        run(checkout, "checkout", "-b", branch);
    }

    public void commitAll(Path checkout, String message) {
        run(checkout, "add", "-A");
        // These are automated bot commits on a disposable clone - signing them would need
        // interactive pinentry/Hello with no TTY to satisfy it, so skip it for this call only.
        run(checkout, List.of("-c", "commit.gpgsign=false"), "commit", "-m", message);
    }

    public void push(Path checkout, String branch) {
        run(checkout, "push", "-u", "origin", branch);
    }

    /** Repo-relative paths of every file the fixer added or modified in checkout. */
    public List<String> changedFiles(Path checkout) {
        return run(checkout, "status", "--porcelain")
                .output()
                .lines()
                .filter(line -> !line.isBlank())
                .map(line -> line.substring(3))
                .toList();
    }

    private ProcessResult run(Path workDir, String... gitArgs) {
        return run(workDir, List.of(), gitArgs);
    }

    private ProcessResult run(Path workDir, List<String> extraConfig, String... gitArgs) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("http.extraheader=" + authHeader);
        command.addAll(extraConfig);
        command.addAll(List.of(gitArgs));

        ProcessResult result = processRunner.run(workDir, command.toArray(new String[0]));
        if (!result.success()) {
            throw new IllegalStateException(
                    "Command failed: git " + String.join(" ", gitArgs) + "\n" + result.output());
        }
        return result;
    }
}
