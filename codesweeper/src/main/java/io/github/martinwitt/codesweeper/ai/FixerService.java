package io.github.martinwitt.codesweeper.ai;

import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import io.github.martinwitt.codesweeper.sarif.SarifFinding;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

@Service
public class FixerService {

    private final ChatClient chatClient;
    private final String promptTemplate;

    public FixerService(ChatClient chatClient, CodesweeperProperties properties) {
        this.chatClient = chatClient;
        this.promptTemplate = properties.fixerPrompt();
    }

    public FixerOutput fix(SarifFinding finding, String fileContent) {
        String prompt =
                new PromptTemplate(promptTemplate)
                        .render(
                                Map.of(
                                        "ruleId", finding.ruleId(),
                                        "message", finding.message(),
                                        "filePath", finding.filePath(),
                                        "startLine", finding.startLine(),
                                        "fileContent", fileContent));
        return chatClient.prompt(prompt).call().entity(FixerOutput.class);
    }
}
