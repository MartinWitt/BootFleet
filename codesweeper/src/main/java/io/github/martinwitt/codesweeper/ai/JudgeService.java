package io.github.martinwitt.codesweeper.ai;

import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import io.github.martinwitt.codesweeper.sarif.SarifFinding;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

/**
 * Hard gate between a proposed fix and the test/PR steps - a fix only proceeds if the judge finds
 * it genuinely correct.
 */
@Service
public class JudgeService {

    private final ChatClient chatClient;
    private final String promptTemplate;

    public JudgeService(ChatClient chatClient, CodesweeperProperties properties) {
        this.chatClient = chatClient;
        this.promptTemplate = properties.judgePrompt();
    }

    public JudgeVerdict judge(SarifFinding finding, String diff, Path checkout) {
        String prompt =
                new PromptTemplate(promptTemplate)
                        .render(
                                Map.of(
                                        "ruleId", finding.ruleId(),
                                        "message", finding.message(),
                                        "filePath", finding.filePath(),
                                        "diff", diff));
        return chatClient
                .prompt(prompt)
                .tools(new ReadOnlyFileTools(checkout))
                .call()
                .entity(JudgeVerdict.class);
    }
}
