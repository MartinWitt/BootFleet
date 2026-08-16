package io.github.martinwitt.codesweeper.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/**
 * Logs only the model's own reasoning text for each turn of a tool-calling exchange, instead of
 * SimpleLoggerAdvisor's full request/response dump - that reprints the entire growing conversation
 * (including full file contents already logged once by {@link FileTools}) on every turn.
 *
 * <p>Also accumulates token usage across every turn, since the codesweeper pipeline processes one
 * finding at a time sequentially - callers should {@link #reset()} before each finding they want a
 * separate usage total for.
 */
@Component
public class TurnLoggerAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TurnLoggerAdvisor.class);

    private int promptTokens;
    private int completionTokens;
    private String model;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        String text = response.chatResponse().getResult().getOutput().getText();
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
