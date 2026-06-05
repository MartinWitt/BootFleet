package io.github.martinwitt.notesapp.service;

import io.github.martinwitt.notesapp.domain.Note;
import io.github.martinwitt.notesapp.domain.Tag;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TagSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(TagSuggestionService.class);

    private final ChatClient chatClient;
    private final String promptTemplate;

    public TagSuggestionService(
            ChatClient chatClient,
            @Value("${notes-app.ai.tag-suggestion-prompt}") String promptTemplate) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
    }

    public List<String> suggestTags(Note note) {
        Set<String> existingTagNames =
                note.getTags().stream().map(Tag::getName).collect(Collectors.toSet());
        return suggestTags(note.getTitle(), note.getContent(), existingTagNames);
    }

    public List<String> suggestTags(String title, String content) {
        return suggestTags(title, content, Set.of());
    }

    private List<String> suggestTags(String title, String content, Set<String> existingTagNames) {
        String prompt =
                new PromptTemplate(promptTemplate)
                        .render(
                                Map.of(
                                        "existingTags",
                                        existingTagNames.isEmpty()
                                                ? "keine"
                                                : String.join(", ", existingTagNames),
                                        "title",
                                        title,
                                        "content",
                                        content != null ? content : ""));

        try {
            TagSuggestions result = chatClient.prompt(prompt).call().entity(TagSuggestions.class);
            if (result == null || result.tags() == null) {
                log.info("No tags found");
                return List.of();
            }
            return result.tags().stream()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isBlank())
                    .filter(s -> !existingTagNames.contains(s))
                    .toList();
        } catch (Exception e) {
            log.warn("Tag suggestion failed: {}", e.getMessage());
            throw new OllamaUnavailableException("Ollama nicht erreichbar: " + e.getMessage(), e);
        }
    }
}
