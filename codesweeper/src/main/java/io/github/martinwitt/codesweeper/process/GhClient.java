package io.github.martinwitt.codesweeper.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.martinwitt.codesweeper.domain.TrustedRepo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around the gh CLI - reuses its existing auth instead of managing GitHub tokens
 * ourselves.
 */
@Component
public class GhClient {

    private static final Logger log = LoggerFactory.getLogger(GhClient.class);

    private final ProcessRunner processRunner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GhClient(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Downloads the SARIF artifact from the latest successful run of repo's workflow, returning the
     * *.sarif.json file inside it, if any.
     */
    public Optional<Path> downloadLatestSarif(Path workDir, TrustedRepo repo, String artifactName) {
        ProcessResult listResult =
                processRunner.run(
                        workDir,
                        "gh",
                        "run",
                        "list",
                        "-R",
                        repo.fullName(),
                        "--workflow",
                        repo.workflowFile(),
                        "--status",
                        "success",
                        "--limit",
                        "1",
                        "--json",
                        "databaseId");
        if (!listResult.success()) {
            log.warn("gh run list failed for {}: {}", repo.fullName(), listResult.output());
            return Optional.empty();
        }
        String runId = firstDatabaseId(listResult.output());
        if (runId == null) {
            log.info("No successful {} run found for {}", repo.workflowFile(), repo.fullName());
            return Optional.empty();
        }

        Path artifactDir = workDir.resolve("sarif-" + runId);
        ProcessResult downloadResult =
                processRunner.run(
                        workDir,
                        "gh",
                        "run",
                        "download",
                        runId,
                        "-R",
                        repo.fullName(),
                        "-n",
                        artifactName,
                        "-D",
                        artifactDir.toString());
        if (!downloadResult.success()) {
            log.warn(
                    "gh run download failed for {} run {}: {}",
                    repo.fullName(),
                    runId,
                    downloadResult.output());
            return Optional.empty();
        }
        return findSarifFile(artifactDir);
    }

    private String firstDatabaseId(String json) {
        try {
            JsonNode array = objectMapper.readTree(json);
            if (!array.isArray() || array.isEmpty()) {
                return null;
            }
            return array.get(0).path("databaseId").asText(null);
        } catch (IOException e) {
            log.warn("Could not parse gh run list output: {}", json, e);
            return null;
        }
    }

    private Optional<Path> findSarifFile(Path artifactDir) {
        if (!Files.isDirectory(artifactDir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(artifactDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sarif.json"))
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** True if repo already has an open PR from branch - used to skip findings already actioned. */
    public boolean prExistsForBranch(Path workDir, TrustedRepo repo, String branch) {
        ProcessResult result =
                processRunner.run(
                        workDir,
                        "gh",
                        "pr",
                        "list",
                        "-R",
                        repo.fullName(),
                        "--head",
                        branch,
                        "--json",
                        "number");
        if (!result.success()) {
            log.warn("gh pr list failed for {}/{}: {}", repo.fullName(), branch, result.output());
            return true; // fail closed: skip rather than risk a duplicate PR
        }
        try {
            JsonNode array = objectMapper.readTree(result.output());
            return array.isArray() && !array.isEmpty();
        } catch (IOException e) {
            log.warn("Could not parse gh pr list output: {}", result.output(), e);
            return true;
        }
    }

    public void createDraftPr(
            Path checkout, TrustedRepo repo, String branch, String title, String body) {
        ProcessResult result =
                processRunner.run(
                        checkout,
                        "gh",
                        "pr",
                        "create",
                        "-R",
                        repo.fullName(),
                        "--draft",
                        "--base",
                        repo.defaultBranch(),
                        "--head",
                        branch,
                        "--title",
                        title,
                        "--body",
                        body);
        if (!result.success()) {
            throw new IllegalStateException(
                    "gh pr create failed for "
                            + repo.fullName()
                            + "/"
                            + branch
                            + ": "
                            + result.output());
        }
    }
}
