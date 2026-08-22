package io.github.martinwitt.codesweeper.config;

import io.github.martinwitt.codesweeper.ai.TurnLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, TurnLoggerAdvisor turnLoggerAdvisor) {
        // The shared Ollama pod defaults to a 16384-token context window; multi-file tool-calling
        // fixes can outgrow that, so ask for more headroom explicitly rather than relying on the
        // pod-wide default other consumers of that instance also rely on.
        return builder.defaultOptions(OllamaChatOptions.builder().disableThinking().numCtx(32768))
                .defaultAdvisors(turnLoggerAdvisor)
                .build();
    }
}
