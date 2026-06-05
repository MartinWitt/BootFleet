package io.github.martinwitt.notesapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.martinwitt.notesapp.domain.Note;
import io.github.martinwitt.notesapp.domain.Tag;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

@ExtendWith(MockitoExtension.class)
class TagSuggestionServiceTest {

    private static final String TEST_PROMPT = "{title} {content} {existingTags}";

    @Mock private ChatClient chatClient;
    @Mock private ChatClientRequestSpec requestSpec;
    @Mock private CallResponseSpec callSpec;

    private TagSuggestionService tagSuggestionService;

    @BeforeEach
    void setUp() {
        tagSuggestionService = new TagSuggestionService(chatClient, TEST_PROMPT);
    }

    @Test
    void shouldReturnSuggestedTagsExcludingExistingOnes() {
        Note note = new Note();
        note.setTitle("Spring Boot Tutorial");
        note.setContent("A guide to building REST APIs with Spring Boot and Java.");
        Tag existing = new Tag("java");
        note.setTags(Set.of(existing));

        when(chatClient.prompt(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(TagSuggestions.class))
                .thenReturn(new TagSuggestions(List.of("spring", "rest", "backend", "tutorial")));

        List<String> suggestions = tagSuggestionService.suggestTags(note);

        assertThat(suggestions).containsExactlyInAnyOrder("spring", "rest", "backend", "tutorial");
        assertThat(suggestions).doesNotContain("java");
    }

    @Test
    void shouldReturnEmptyListWhenAiReturnsBlank() {
        Note note = new Note();
        note.setTitle("Empty");
        note.setContent("...");

        when(chatClient.prompt(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(TagSuggestions.class)).thenReturn(new TagSuggestions(List.of()));

        List<String> suggestions = tagSuggestionService.suggestTags(note);

        assertThat(suggestions).isEmpty();
    }

    @Test
    void shouldThrowOllamaUnavailableExceptionOnAiError() {
        Note note = new Note();
        note.setTitle("Fail");
        note.setContent("content");

        when(chatClient.prompt(any(String.class))).thenThrow(new RuntimeException("Ollama down"));

        assertThatThrownBy(() -> tagSuggestionService.suggestTags(note))
                .isInstanceOf(OllamaUnavailableException.class)
                .hasMessageContaining("Ollama");
    }
}
