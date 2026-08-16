package io.github.martinwitt.codesweeper;

import io.github.martinwitt.codesweeper.ai.FixerOutput;
import io.github.martinwitt.codesweeper.ai.FixerService;
import io.github.martinwitt.codesweeper.ai.JudgeService;
import io.github.martinwitt.codesweeper.ai.JudgeVerdict;
import io.github.martinwitt.codesweeper.ai.TurnLoggerAdvisor;
import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import io.github.martinwitt.codesweeper.domain.TrustedRepo;
import io.github.martinwitt.codesweeper.github.GithubApiClient;
import io.github.martinwitt.codesweeper.process.BranchNaming;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
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
    private static final int MAX_FIX_ATTEMPTS = 2;

    private final CodesweeperProperties properties;
    private final GithubApiClient githubApiClient;
    private final GitClient gitClient;
    private final MavenClient mavenClient;
    private final SarifParser sarifParser;
    private final FixerService fixerService;
    private final JudgeService judgeService;
    private final TurnLoggerAdvisor turnLoggerAdvisor;

    public CodesweeperRunner(
            CodesweeperProperties properties,
            GithubApiClient githubApiClient,
            GitClient gitClient,
            MavenClient mavenClient,
            SarifParser sarifParser,
            FixerService fixerService,
            JudgeService judgeService,
            TurnLoggerAdvisor turnLoggerAdvisor) {
        this.properties = properties;
        this.githubApiClient = githubApiClient;
        this.gitClient = gitClient;
        this.mavenClient = mavenClient;
        this.sarifParser = sarifParser;
        this.fixerService = fixerService;
        this.judgeService = judgeService;
        this.turnLoggerAdvisor = turnLoggerAdvisor;
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
                githubApiClient.downloadLatestSarif(
                        workspaceDir, repo, properties.sarifArtifactName());
        if (sarifFile.isEmpty()) {
            log.info("No SARIF available for {}, skipping", repo.fullName());
            return;
        }

        List<SarifFinding> findings;
        try (InputStream in = Files.newInputStream(sarifFile.get())) {
            findings = sarifParser.parse(in);
        }
        log.info("{} findings in {}", findings.size(), repo.fullName());
        if (findings.isEmpty()) {
            return;
        }

        // ponytail: local LLM fixes are slow (tens of seconds each) - pick one finding per run
        // instead of grinding through all of them, so the fix/judge loop stays fast to iterate on.
        SarifFinding finding = findings.get(ThreadLocalRandom.current().nextInt(findings.size()));
        log.info(
                "Picked {} at {}:{} ({} of {} findings): {}",
                finding.ruleId(),
                finding.filePath(),
                finding.startLine(),
                findings.indexOf(finding) + 1,
                findings.size(),
                finding.message());

        Path checkout = gitClient.cloneOrUpdate(workspaceDir, repo);
        try {
            processFinding(workspaceDir, checkout, repo, finding);
        } catch (Exception e) {
            log.error("Failed to process finding {} in {}", finding.ruleId(), repo.fullName(), e);
            gitClient.discardChanges(checkout);
        }
    }

    private void processFinding(
            Path workspaceDir, Path checkout, TrustedRepo repo, SarifFinding finding)
            throws IOException {
        String branch = BranchNaming.branchFor(finding);
        if (githubApiClient.prExistsForBranch(repo, branch)) {
            log.info("PR already exists for {} ({}), skipping", finding.ruleId(), branch);
            return;
        }

        Path file = checkout.resolve(finding.filePath());
        if (!Files.isRegularFile(file)) {
            log.warn("{} no longer exists at {}, skipping", finding.filePath(), repo.fullName());
            return;
        }
        String originalContent = Files.readString(file, StandardCharsets.UTF_8);

        // The fixer writes fixes directly via its file tools, so the checkout is already mutated
        // by the time it returns - anything short of judge approval must be discarded.
        turnLoggerAdvisor.reset();
        FixerOutput fix = null;
        JudgeVerdict verdict = null;
        String fixedContent = null;
        String rejectionFeedback = null;
        for (int attempt = 1; attempt <= MAX_FIX_ATTEMPTS; attempt++) {
            log.info(
                    "Asking fixer to fix {} in {} (attempt {}/{})",
                    finding.ruleId(),
                    finding.filePath(),
                    attempt,
                    MAX_FIX_ATTEMPTS);
            long fixStart = System.currentTimeMillis();
            fix = fixerService.fix(finding, checkout, rejectionFeedback);
            fixedContent = Files.readString(file, StandardCharsets.UTF_8);
            log.info(
                    "Fixer finished {} in {}s: {}",
                    finding.ruleId(),
                    (System.currentTimeMillis() - fixStart) / 1000,
                    fix.explanation());
            if (fixedContent.equals(originalContent)) {
                log.warn(
                        "Fixer made no changes to {} for {} - it explained a fix but never wrote"
                                + " one, skipping judge",
                        finding.filePath(),
                        finding.ruleId());
                gitClient.discardChanges(checkout);
                return;
            }

            verdict = judgeService.judge(finding, originalContent, fixedContent);
            if (verdict.useful()) {
                log.info("Judge approved fix for {}: {}", finding.ruleId(), verdict.reason());
                break;
            }
            log.info(
                    "Judge rejected fix for {} (attempt {}/{}): {}",
                    finding.ruleId(),
                    attempt,
                    MAX_FIX_ATTEMPTS,
                    verdict.reason());
            gitClient.discardChanges(checkout);
            if (attempt == MAX_FIX_ATTEMPTS) {
                return;
            }
            rejectionFeedback = verdict.reason();
        }

        Set<String> modules =
                gitClient.changedFiles(checkout).stream()
                        .map(f -> ModulePath.moduleFor(checkout, f).orElse(null))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        modules.forEach(module -> mavenClient.spotlessApply(checkout, module));
        for (String module : modules) {
            if (!mavenClient.test(checkout, module)) {
                log.info(
                        "Tests failed for fix on {} (module {}), discarding",
                        finding.ruleId(),
                        module == null ? "<repo root>" : module);
                gitClient.discardChanges(checkout);
                return;
            }
        }

        gitClient.createBranch(checkout, branch);
        gitClient.commitAll(checkout, "fix: " + finding.ruleId() + " in " + finding.filePath());
        gitClient.push(checkout, branch);
        String prUrl =
                githubApiClient.createDraftPr(
                        repo,
                        branch,
                        "fix: " + finding.ruleId() + " in " + finding.filePath(),
                        prBody(finding, fix, verdict));
        log.info("Opened draft PR for {}: {}", finding.ruleId(), prUrl);

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

        **Model:** %s
        **Tokens used:** %d prompt + %d completion = %d total
        """
                .formatted(
                        finding.ruleId(),
                        finding.message(),
                        finding.filePath(),
                        finding.startLine(),
                        fix.explanation(),
                        verdict.reason(),
                        turnLoggerAdvisor.getModel(),
                        turnLoggerAdvisor.getPromptTokens(),
                        turnLoggerAdvisor.getCompletionTokens(),
                        turnLoggerAdvisor.getTotalTokens());
    }
}
