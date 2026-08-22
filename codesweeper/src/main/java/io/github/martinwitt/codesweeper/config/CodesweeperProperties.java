package io.github.martinwitt.codesweeper.config;

import io.github.martinwitt.codesweeper.domain.TrustedRepo;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codesweeper")
public record CodesweeperProperties(
        String workspaceDir,
        String sarifArtifactName,
        String githubToken,
        String fixerPrompt,
        String judgePrompt,
        List<TrustedRepo> repos,
        // Pins finding selection to one exact finding instead of a random pick - set both to
        // benchmark different models against the same finding. Leave unset for normal runs.
        String pinnedRuleId,
        String pinnedFilePath) {}
