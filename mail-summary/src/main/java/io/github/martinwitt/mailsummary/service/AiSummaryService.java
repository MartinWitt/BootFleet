package io.github.martinwitt.mailsummary.service;

import io.github.martinwitt.mailsummary.config.AiConfig;
import io.github.martinwitt.mailsummary.model.EmailMessage;
import io.github.martinwitt.mailsummary.model.EmailSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

@Service
public class AiSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(AiSummaryService.class);
    private static final String DEFAULT_SUMMARY = "Keine Zusammenfassung verfügbar.";

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final BeanOutputConverter<IndividualEmailAnalysis> converter;

    public AiSummaryService(ChatClient.Builder chatClientBuilder, AiConfig aiConfig) {
        this.chatClient = chatClientBuilder.build();
        this.promptTemplate = new PromptTemplate(aiConfig.getIndividualEmailPrompt());
        this.converter = new BeanOutputConverter<>(IndividualEmailAnalysis.class);
    }

    public EmailSummary analyzeEmail(EmailMessage email) {
        Prompt prompt = buildPrompt(email);
        IndividualEmailAnalysis analysis = executeModel(prompt, email);
        List<String> actionItems = normalizeActionItems(analysis.actionItems());
        String summaryText =
                Optional.ofNullable(analysis.summary())
                        .map(String::trim)
                        .filter(text -> !text.isBlank())
                        .orElse(DEFAULT_SUMMARY);

        logger.debug(
                "Email analysis complete for: '{}' with {} action items",
                email.subject(),
                actionItems.size());

        return new EmailSummary(email.from(), email.subject(), summaryText, actionItems);
    }

    private Prompt buildPrompt(EmailMessage email) {
        return promptTemplate.create(
                Map.of(
                        "from", safe(email.from()),
                        "subject", safe(email.subject()),
                        "content", safe(email.body()),
                        "format", converter.getFormat()));
    }

    private IndividualEmailAnalysis executeModel(Prompt prompt, EmailMessage email) {
        Instant start = Instant.now();
        try {
            IndividualEmailAnalysis response =
                    chatClient.prompt(prompt).call().entity(IndividualEmailAnalysis.class);
            Duration duration = Duration.between(start, Instant.now());

            return validateResponse(response, email, duration);
        } catch (Exception ex) {
            Duration duration = Duration.between(start, Instant.now());
            logger.warn(
                    "LLM response not JSON-conform after {} ms, falling back to raw parse: {}",
                    duration.toMillis(),
                    ex.getMessage());
            logger.debug("Exception details:", ex);

            return fallbackWithRaw(prompt);
        }
    }

    private IndividualEmailAnalysis validateResponse(
            IndividualEmailAnalysis response, EmailMessage email, Duration duration) {
        if (response == null) {
            logger.warn(
                    "Model returned null response after {} ms for email: '{}'",
                    duration.toMillis(),
                    email.subject());
            return fallbackParse(null);
        }

        String summaryPreview = Optional.ofNullable(response.summary()).orElse("").trim();
        int actionCount = Optional.ofNullable(response.actionItems()).map(List::size).orElse(0);
        logger.info(
                "Model call completed successfully in {} ms for email: '{}'. Summary: {} chars,"
                        + " Action items: {}",
                duration.toMillis(),
                email.subject(),
                summaryPreview.length(),
                actionCount);
        logger.debug("Model summary result: {}", summaryPreview);
        return response;
    }

    private IndividualEmailAnalysis fallbackWithRaw(Prompt prompt) {
        Instant fallbackStart = Instant.now();
        try {
            String raw = chatClient.prompt(prompt).call().content();
            Duration fallbackDuration = Duration.between(fallbackStart, Instant.now());
            logger.info(
                    "Fallback model call completed in {} ms. Raw content: {} characters",
                    fallbackDuration.toMillis(),
                    raw != null ? raw.length() : 0);
            logger.debug("Fallback raw result: {}", raw);

            return fallbackParse(raw);
        } catch (Exception fallbackEx) {
            Duration fallbackDuration = Duration.between(fallbackStart, Instant.now());
            logger.warn(
                    "Fallback call failed after {} ms: {}",
                    fallbackDuration.toMillis(),
                    fallbackEx.getMessage());
            logger.debug("Fallback exception details:", fallbackEx);
            return fallbackParse(null);
        }
    }

    private List<String> normalizeActionItems(List<String> actionItems) {
        if (actionItems == null || actionItems.isEmpty()) {
            return Collections.emptyList();
        }
        return actionItems.stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isBlank())
                .collect(Collectors.toList());
    }

    private IndividualEmailAnalysis fallbackParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new IndividualEmailAnalysis(DEFAULT_SUMMARY, Collections.emptyList());
        }
        return new IndividualEmailAnalysis(raw.trim(), Collections.emptyList());
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private record IndividualEmailAnalysis(String summary, List<String> actionItems) {}
}
