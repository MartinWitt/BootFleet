package io.github.martinwitt.codesweeper.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import io.github.martinwitt.codesweeper.domain.TrustedRepo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to the GitHub REST API directly - no gh CLI, so auth is an explicit token instead of gh's
 * own session.
 */
@Component
public class GithubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GithubApiClient.class);

    private final RestClient restClient;
    private final HttpClient httpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String githubToken;

    public GithubApiClient(CodesweeperProperties properties) {
        this.githubToken = properties.githubToken();
        this.restClient =
                RestClient.builder()
                        .baseUrl("https://api.github.com")
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                        .build();
    }

    /**
     * Downloads the SARIF artifact from the latest successful run of repo's workflow, returning the
     * *.sarif.json file inside it, if any.
     */
    public Optional<Path> downloadLatestSarif(Path workDir, TrustedRepo repo, String artifactName) {
        Optional<Long> runId = latestSuccessfulRunId(repo);
        if (runId.isEmpty()) {
            log.info("No successful {} run found for {}", repo.workflowFile(), repo.fullName());
            return Optional.empty();
        }
        Optional<Long> artifactId = findArtifactId(repo, runId.get(), artifactName);
        if (artifactId.isEmpty()) {
            log.info(
                    "No '{}' artifact on run {} for {}",
                    artifactName,
                    runId.get(),
                    repo.fullName());
            return Optional.empty();
        }
        try {
            byte[] zip = downloadArtifactZip(repo, artifactId.get());
            Path artifactDir = workDir.resolve("sarif-" + runId.get());
            unzip(zip, artifactDir);
            return findSarifFile(artifactDir);
        } catch (IOException e) {
            log.warn("Failed to download artifact {} for {}", artifactId.get(), repo.fullName(), e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Interrupted downloading artifact {} for {}",
                    artifactId.get(),
                    repo.fullName());
            return Optional.empty();
        }
    }

    private Optional<Long> latestSuccessfulRunId(TrustedRepo repo) {
        try {
            JsonNode response =
                    restClient
                            .get()
                            .uri(
                                    "/repos/{owner}/{repo}/actions/workflows/{workflow}/runs?status=success&per_page=1",
                                    repo.owner(),
                                    repo.name(),
                                    repo.workflowFile())
                            .retrieve()
                            .body(JsonNode.class);
            if (response == null) {
                return Optional.empty();
            }
            JsonNode runs = response.path("workflow_runs");
            return runs.isArray() && !runs.isEmpty()
                    ? Optional.of(runs.get(0).path("id").asLong())
                    : Optional.empty();
        } catch (RestClientException e) {
            log.warn("Failed to list workflow runs for {}", repo.fullName(), e);
            return Optional.empty();
        }
    }

    private Optional<Long> findArtifactId(TrustedRepo repo, long runId, String artifactName) {
        try {
            JsonNode response =
                    restClient
                            .get()
                            .uri(
                                    "/repos/{owner}/{repo}/actions/runs/{runId}/artifacts",
                                    repo.owner(),
                                    repo.name(),
                                    runId)
                            .retrieve()
                            .body(JsonNode.class);
            if (response == null) {
                return Optional.empty();
            }
            for (JsonNode artifact : response.path("artifacts")) {
                if (artifactName.equals(artifact.path("name").asText())) {
                    return Optional.of(artifact.path("id").asLong());
                }
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Failed to list artifacts for run {} on {}", runId, repo.fullName(), e);
            return Optional.empty();
        }
    }

    /**
     * The artifact endpoint 302s to a pre-signed blob URL; fetch the Location by hand so the GitHub
     * bearer token is never sent to that third-party host.
     */
    private byte[] downloadArtifactZip(TrustedRepo repo, long artifactId)
            throws IOException, InterruptedException {
        HttpRequest redirectRequest =
                HttpRequest.newBuilder(
                                URI.create(
                                        "https://api.github.com/repos/%s/%s/actions/artifacts/%d/zip"
                                                .formatted(repo.owner(), repo.name(), artifactId)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .GET()
                        .build();
        HttpResponse<Void> redirect =
                httpClient.send(redirectRequest, HttpResponse.BodyHandlers.discarding());
        String location =
                redirect.headers()
                        .firstValue("Location")
                        .orElseThrow(
                                () ->
                                        new IOException(
                                                "No redirect Location for artifact " + artifactId));

        HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(location)).GET().build();
        return httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    private void unzip(byte[] zipBytes, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) {
                    continue; // zip-slip guard: artifact contents come from outside our control
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
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
    public boolean prExistsForBranch(TrustedRepo repo, String branch) {
        try {
            JsonNode prs =
                    restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/repos/{owner}/{repo}/pulls")
                                                    .queryParam("head", repo.owner() + ":" + branch)
                                                    .queryParam("state", "open")
                                                    .build(repo.owner(), repo.name()))
                            .retrieve()
                            .body(JsonNode.class);
            return prs != null && prs.isArray() && !prs.isEmpty();
        } catch (RestClientException e) {
            log.warn("Failed to check existing PRs for {}/{}", repo.fullName(), branch, e);
            return true; // fail closed: skip rather than risk a duplicate PR
        }
    }

    public void createDraftPr(TrustedRepo repo, String branch, String title, String body) {
        restClient
                .post()
                .uri("/repos/{owner}/{repo}/pulls", repo.owner(), repo.name())
                .body(
                        Map.of(
                                "title", title,
                                "head", branch,
                                "base", repo.defaultBranch(),
                                "body", body,
                                "draft", true))
                .retrieve()
                .toBodilessEntity();
    }
}
