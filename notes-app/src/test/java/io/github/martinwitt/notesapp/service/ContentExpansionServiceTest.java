package io.github.martinwitt.notesapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

@ExtendWith(MockitoExtension.class)
class ContentExpansionServiceTest {

    private static final String TEST_PROMPT = "{title} {content}";

    @Mock private ChatClient chatClient;
    @Mock private ChatClientRequestSpec requestSpec;
    @Mock private CallResponseSpec callSpec;

    private ContentExpansionService contentExpansionService;

    @BeforeEach
    void setUp() {
        contentExpansionService = new ContentExpansionService(chatClient, TEST_PROMPT);
    }

    @Test
    void shouldReturnExpandedContent() {
        when(chatClient.prompt(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Ausführlicher Text über Spring Boot.");

        String result = contentExpansionService.expandContent("Spring Boot", "kurze notiz");

        assertThat(result).isEqualTo("Ausführlicher Text über Spring Boot.");
    }

    @Test
    void shouldReturnOriginalContentWhenAiReturnsNull() {
        when(chatClient.prompt(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(null);

        String result = contentExpansionService.expandContent("Titel", "original");

        assertThat(result).isEqualTo("original");
    }

    @Test
    void shouldThrowOllamaUnavailableExceptionOnAiError() {
        when(chatClient.prompt(any(String.class))).thenThrow(new RuntimeException("Ollama down"));

        assertThatThrownBy(() -> contentExpansionService.expandContent("Titel", "Inhalt"))
                .isInstanceOf(OllamaUnavailableException.class)
                .hasMessageContaining("Ollama");
    }
}
