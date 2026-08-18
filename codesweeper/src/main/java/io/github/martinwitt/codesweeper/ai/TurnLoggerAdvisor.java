package io.github.martinwitt.codesweeper.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Logs only the model's own reasoning text for each turn of a tool-calling exchange, instead of
 * SimpleLoggerAdvisor's full request/response dump - that reprints the entire growing conversation
 * (including full file contents already logged once by {@link FileTools}) on every turn.
 *
 * <p>Also accumulates token usage across every turn, since the codesweeper pipeline processes one
 * finding at a time sequentially - callers should {@link #reset()} before each finding they want a
 * separate usage total for.
 *
 * <p>The streaming path prints raw deltas to stdout as they arrive (thinking dimmed, answer text
 * normal) instead of going through the line-based logger, since a per-token log line each would be
 * unreadable - this is for watching a slow local run live, not a permanent log format.
 */
@Component
public class TurnLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String ANSI_DIM = "[90m";
    private static final String ANSI_RESET = "[0m";

    private static final Logger log = LoggerFactory.getLogger(TurnLoggerAdvisor.class);

    private int promptTokens;
    private int completionTokens;
    private String model;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        var output = response.chatResponse().getResult().getOutput();
        Object thinking = output.getMetadata().get("thinking");
        if (thinking instanceof String s && !s.isBlank()) {
            log.info("model thinking: {}", s);
        }
        String text = output.getText();
        if (text != null && !text.isBlank()) {
            log.info("model: {}", text);
        }
        Usage usage = response.chatResponse().getMetadata().getUsage();
        if (usage != null) {
            promptTokens += usage.getPromptTokens();
            completionTokens += usage.getCompletionTokens();
        }
        model = response.chatResponse().getMetadata().getModel();
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(request)
                .doOnNext(
                        response -> {
                            if (response.chatResponse() == null) {
                                return;
                            }
                            var output = response.chatResponse().getResult().getOutput();
                            Object thinking = output.getMetadata().get("thinking");
                            if (thinking instanceof String s && !s.isEmpty()) {
                                System.out.print(ANSI_DIM + s + ANSI_RESET);
                            }
                            String text = output.getText();
                            if (text != null && !text.isEmpty()) {
                                System.out.print(text);
                            }
                            Usage usage = response.chatResponse().getMetadata().getUsage();
                            if (usage != null) {
                                promptTokens += usage.getPromptTokens();
                                completionTokens += usage.getCompletionTokens();
                            }
                            model = response.chatResponse().getMetadata().getModel();
                        });
    }

    public void reset() {
        promptTokens = 0;
        completionTokens = 0;
        model = null;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return promptTokens + completionTokens;
    }

    public String getModel() {
        return model;
    }

    @Override
    public String getName() {
        return "TurnLoggerAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
