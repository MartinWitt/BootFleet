package io.github.martinwitt.codesweeper;

import io.github.martinwitt.codesweeper.ai.FixerOutput;
import io.github.martinwitt.codesweeper.ai.FixerService;
import io.github.martinwitt.codesweeper.ai.JudgeService;
import io.github.martinwitt.codesweeper.ai.JudgeVerdict;
import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import io.github.martinwitt.codesweeper.domain.TrustedRepo;
import io.github.martinwitt.codesweeper.process.BranchNaming;
import io.github.martinwitt.codesweeper.process.GhClient;
import io.github.martinwitt.codesweeper.process.GitClient;
import io.github.martinwitt.codesweeper.process.MavenClient;
import io.github.martinwitt.codesweeper.process.ModulePath;
import io.github.martinwitt.codesweeper.sarif.SarifFinding;
import io.github.martinwitt.codesweeper.sarif.SarifParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * For every trusted repo: pull the latest Qodana SARIF, fix each not-yet-actioned finding, let the
 * judge gate it, run spotless + tests, and open a draft PR only if all of that holds.
 */
@Component
public class CodesweeperRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CodesweeperRunner.class);

    private final CodesweeperProperties properties;
    private final GhClient ghClient;
    private final GitClient gitClient;
    private final MavenClient mavenClient;
    private final SarifParser sarifParser;
    private final FixerService fixerService;
    private final JudgeService judgeService;

    public CodesweeperRunner(
            CodesweeperProperties properties,
            GhClient ghClient,
            GitClient gitClient,
            MavenClient mavenClient,
            SarifParser sarifParser,
            FixerService fixerService,
            JudgeService judgeService) {
        this.properties = properties;
        this.ghClient = ghClient;
        this.gitClient = gitClient;
        this.mavenClient = mavenClient;
        this.sarifParser = sarifParser;
        this.fixerService = fixerService;
        this.judgeService = judgeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path workspaceDir = Path.of(properties.workspaceDir());
        for (TrustedRepo repo : properties.repos()) {
            try {
                processRepo(workspaceDir, repo);
            } catch (Exception e) {
                log.error("codesweeper failed for {}", repo.fullName(), e);
            }
        }
    }

    private void processRepo(Path workspaceDir, TrustedRepo repo) throws IOException {
        Files.createDirectories(workspaceDir);
        Optional<Path> sarifFile =
                ghClient.downloadLatestSarif(workspaceDir, repo, properties.sarifArtifactName());
        if (sarifFile.isEmpty()) {
            log.info("No SARIF available for {}, skipping", repo.fullName());
            return;
        }

        List<SarifFinding> findings;
        try (InputStream in = Files.newInputStream(sarifFile.get())) {
            findings = sarifParser.parse(in);
        }
        log.info("{} findings in {}", findings.size(), repo.fullName());

        Path checkout = gitClient.cloneOrUpdate(workspaceDir, repo);
        for (SarifFinding finding : findings) {
            try {
                processFinding(workspaceDir, checkout, repo, finding);
            } catch (Exception e) {
                log.error(
                        "Failed to process finding {} in {}", finding.ruleId(), repo.fullName(), e);
                gitClient.discardChanges(checkout);
            }
        }
    }

    private void processFinding(
            Path workspaceDir, Path checkout, TrustedRepo repo, SarifFinding finding)
            throws IOException {
        String branch = BranchNaming.branchFor(finding);
        if (ghClient.prExistsForBranch(workspaceDir, repo, branch)) {
            log.info("PR already exists for {} ({}), skipping", finding.ruleId(), branch);
            return;
        }

        Path file = checkout.resolve(finding.filePath());
        if (!Files.isRegularFile(file)) {
            log.warn("{} no longer exists at {}, skipping", finding.filePath(), repo.fullName());
            return;
        }
        String originalContent = Files.readString(file, StandardCharsets.UTF_8);

        FixerOutput fix = fixerService.fix(finding, originalContent);
        JudgeVerdict verdict = judgeService.judge(finding, originalContent, fix.fixedFileContent());
        if (!verdict.useful()) {
            log.info("Judge rejected fix for {}: {}", finding.ruleId(), verdict.reason());
            return;
        }

        Files.writeString(file, fix.fixedFileContent(), StandardCharsets.UTF_8);
        String module = ModulePath.moduleFor(finding.filePath());
        mavenClient.spotlessApply(checkout, module);
        if (!mavenClient.test(checkout, module)) {
            log.info("Tests failed for fix on {}, discarding", finding.ruleId());
            gitClient.discardChanges(checkout);
            return;
        }

        gitClient.createBranch(checkout, branch);
        gitClient.commitAll(checkout, "fix: " + finding.ruleId() + " in " + finding.filePath());
        gitClient.push(checkout, branch);
        ghClient.createDraftPr(
                checkout,
                repo,
                branch,
                "fix: " + finding.ruleId() + " in " + finding.filePath(),
                prBody(finding, fix, verdict));

        gitClient.cloneOrUpdate(workspaceDir, repo); // back to default branch for the next finding
    }

    private String prBody(SarifFinding finding, FixerOutput fix, JudgeVerdict verdict) {
        return """
        Auto-generated by codesweeper from a Qodana finding.

        **Rule:** %s
        **Message:** %s
        **File:** %s:%d

        **Fix explanation:** %s

        **Judge verdict:** %s
        """
                .formatted(
                        finding.ruleId(),
                        finding.message(),
                        finding.filePath(),
                        finding.startLine(),
                        fix.explanation(),
                        verdict.reason());
    }
}
