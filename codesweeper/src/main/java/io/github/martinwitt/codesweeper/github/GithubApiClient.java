package io.github.martinwitt.codesweeper.github;

import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import io.github.martinwitt.codesweeper.domain.TrustedRepo;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Talks to the GitHub REST API via the hub4j github-api client, authenticated with an explicit
 * token.
 */
@Component
public class GithubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GithubApiClient.class);

    private final GitHub gitHub;

    public GithubApiClient(CodesweeperProperties properties) {
        try {
            this.gitHub = new GitHubBuilder().withOAuthToken(properties.githubToken()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Downloads the SARIF artifact from the latest successful run of repo's workflow, returning the
     * *.sarif.json file inside it, if any.
     */
    public Optional<Path> downloadLatestSarif(Path workDir, TrustedRepo repo, String artifactName) {
        try {
            GHRepository ghRepo = gitHub.getRepository(repo.fullName());
            GHWorkflow workflow = ghRepo.getWorkflow(repo.workflowFile());
            for (GHWorkflowRun run : workflow.listRuns()) {
                if (run.getConclusion() != GHWorkflowRun.Conclusion.SUCCESS
                        || !repo.defaultBranch().equals(run.getHeadBranch())) {
                    continue;
                }
                Path artifactDir = workDir.resolve("sarif-" + run.getId());
                for (GHArtifact artifact : run.listArtifacts()) {
                    if (artifactName.equals(artifact.getName())) {
                        Path sarif = artifact.download(in -> extractSarifFile(in, artifactDir));
                        if (sarif == null) {
                            log.warn(
                                    "Artifact '{}' on run {} for {} had no *.sarif.json inside",
                                    artifactName,
                                    run.getId(),
                                    repo.fullName());
                        }
                        return Optional.ofNullable(sarif);
                    }
                }
                log.info(
                        "No '{}' artifact on run {}, checking earlier successful runs for {}",
                        artifactName,
                        run.getId(),
                        repo.fullName());
            }
            log.info("No successful {} run found for {}", repo.workflowFile(), repo.fullName());
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Failed to fetch SARIF for {}: {}", repo.fullName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The artifact download endpoint returns a zip; Qodana's own output is itself a nested zip
     * inside that (containing both the full SARIF and a "-short" summary with zero results), so
     * descend into any nested zip entries and keep the largest *.sarif.json match found.
     */
    Path extractSarifFile(InputStream zipStream, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            SarifCandidate best = largestSarifEntry(zis);
            if (best == null) {
                return null;
            }
            Path target = destDir.resolve(Path.of(best.name()).getFileName());
            Files.write(target, best.content());
            return target;
        }
    }

    private record SarifCandidate(String name, byte[] content) {}

    private SarifCandidate largestSarifEntry(ZipInputStream zis) throws IOException {
        SarifCandidate best = null;
        for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
            String name = entry.getName();
            if (entry.isDirectory()) {
                continue;
            }
            SarifCandidate candidate;
            if (name.endsWith(".sarif.json")) {
                candidate = new SarifCandidate(name, zis.readAllBytes());
            } else if (name.endsWith(".zip")) {
                candidate = largestSarifEntry(new ZipInputStream(zis));
            } else {
                continue;
            }
            if (candidate != null
                    && (best == null || candidate.content().length > best.content().length)) {
                best = candidate;
            }
        }
        return best;
    }

    /** True if repo already has an open PR from branch - used to skip findings already actioned. */
    public boolean prExistsForBranch(TrustedRepo repo, String branch) {
        try {
            GHRepository ghRepo = gitHub.getRepository(repo.fullName());
            return !ghRepo.queryPullRequests().head(branch).list().toList().isEmpty();
        } catch (IOException e) {
            log.warn(
                    "Failed to check existing PRs for {}/{}: {}",
                    repo.fullName(),
                    branch,
                    e.getMessage());
            return true; // fail closed: skip rather than risk a duplicate PR
        }
    }

    public String createDraftPr(TrustedRepo repo, String branch, String title, String body) {
        try {
            GHRepository ghRepo = gitHub.getRepository(repo.fullName());
            return ghRepo.createPullRequest(title, branch, repo.defaultBranch(), body, true, true)
                    .getHtmlUrl()
                    .toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
