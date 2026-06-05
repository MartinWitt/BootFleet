package io.github.martinwitt.notesapp.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ContentExpansionService {

    private static final Logger log = LoggerFactory.getLogger(ContentExpansionService.class);

    private final ChatClient chatClient;
    private final String promptTemplate;

    public ContentExpansionService(
            ChatClient chatClient,
            @Value("${notes-app.ai.content-expansion-prompt}") String promptTemplate) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
    }

    public String expandContent(String title, String content) {
        String prompt =
                new PromptTemplate(promptTemplate)
                        .render(Map.of("title", title, "content", content != null ? content : ""));

        try {
            String result = chatClient.prompt(prompt).call().content();
            log.debug("ContentExpansion returned {} chars", result != null ? result.length() : 0);
            if (result == null || result.isBlank()) return content;
            return result;
        } catch (Exception e) {
            log.warn("Content expansion failed", e);
            throw new OllamaUnavailableException("Ollama nicht erreichbar: " + e.getMessage(), e);
        }
    }
}
