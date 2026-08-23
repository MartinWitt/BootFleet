package io.github.martinwitt.codesweeper.ai;

import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import io.github.martinwitt.codesweeper.sarif.SarifFinding;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
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

    /**
     * Lets the model read and fix files itself via {@link ReadOnlyFileTools} and {@link
     * WriteFileTools}, scoped to checkout.
     *
     * <p>Deliberately plain text, not structured output: a JSON-schema response format gives the
     * model an easy way to satisfy the schema by just describing a fix in prose instead of actually
     * calling a file tool - observed happening on both a 3B and a 9B model.
     *
     * @param previousAttemptRejection the judge's reason for rejecting a prior attempt at this same
     *     finding, or null for a first attempt - fed back in so a retry doesn't repeat the mistake
     */
    public FixerOutput fix(SarifFinding finding, Path checkout, String previousAttemptRejection) {
        String prompt =
                new PromptTemplate(promptTemplate)
                        .render(
                                Map.of(
                                        "ruleId", finding.ruleId(),
                                        "message", finding.message(),
                                        "filePath", finding.filePath(),
                                        "startLine", finding.startLine()));
        if (previousAttemptRejection != null) {
            prompt =
                    "Your previous attempt at this exact fix was reviewed and rejected for this"
                            + " reason: "
                            + previousAttemptRejection
                            + "\n\nTry again, taking that into account.\n\n"
                            + prompt;
        }
        // Streamed (not .call()) so TurnLoggerAdvisor can print thinking/answer tokens live as
        // they're generated, instead of the whole turn appearing silently once it's done.
        String explanation =
                chatClient
                        .prompt(prompt)
                        .tools(new ReadOnlyFileTools(checkout), new WriteFileTools(checkout))
                        .stream()
                        .content()
                        .collect(Collectors.joining())
                        .block();
        return new FixerOutput(explanation);
    }
}
