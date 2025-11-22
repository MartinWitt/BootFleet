package io.github.martinwitt.mailsummary.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import io.github.martinwitt.mailsummary.config.GmailConfig;
import io.github.martinwitt.mailsummary.model.EmailLabel;
import io.github.martinwitt.mailsummary.model.EmailMessage;
import io.github.martinwitt.mailsummary.model.EmailPage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GmailService {

    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String USER_ID = "me";
    private static final String DEFAULT_LABEL_ID = "INBOX";
    private static final String PRIMARY_CATEGORY_QUERY = "category:primary";
    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final int MAX_RESULTS = 50;
    private static final int BODY_PREVIEW_LENGTH = 1200;

    private final GmailConfig gmailConfig;
    private final OAuthTokenService oauthTokenService;

    public GmailService(GmailConfig gmailConfig, OAuthTokenService oauthTokenService) {
        this.gmailConfig = gmailConfig;
        this.oauthTokenService = oauthTokenService;
        ensureTokenDirectoryExists();
    }

    private void ensureTokenDirectoryExists() {
        try {
            Path tokenPath = Paths.get(gmailConfig.getTokensDirectory());
            Files.createDirectories(tokenPath);
            logger.debug("Token directory ensured: {}", tokenPath.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("Failed to create token directory: {}", e.getMessage());
        }
    }

    private Credential getCredentials(final NetHttpTransport httpTransport) throws IOException {
        return oauthTokenService.getOrRedirect(httpTransport);
    }

    public Gmail getGmailService() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName("MailSummary")
                .build();
    }

    public List<EmailMessage> getLastEmails(int count) {
        int maxResults = clampMaxResults(count);
        return fetchEmails(buildPrimaryQuery(null), maxResults);
    }

    public List<EmailMessage> searchEmails(String query, int maxResults) {
        int clamped = clampMaxResults(maxResults);
        return fetchEmails(buildPrimaryQuery(query), clamped);
    }

    public List<EmailMessage> getEmailsBySubject(String subject, int maxResults) {
        int clamped = clampMaxResults(maxResults);
        return fetchEmails(buildPrimaryQuery("subject:" + subject), clamped);
    }

    public List<EmailMessage> getEmailsFromSender(String sender, int maxResults) {
        int clamped = clampMaxResults(maxResults);
        return fetchEmails(buildPrimaryQuery("from:" + sender), clamped);
    }

    public EmailPage getLastEmailsPage(int count, String pageToken) {
        int maxResults = clampMaxResults(count);
        return fetchEmailsPage(buildPrimaryQuery(null), maxResults, pageToken);
    }

    public EmailPage getEmailsByLabelPage(String labelId, int count, String pageToken) {
        int maxResults = clampMaxResults(count);
        String targetLabel = (labelId == null || labelId.isBlank()) ? DEFAULT_LABEL_ID : labelId;
        return fetchEmailsPageByLabel(targetLabel, maxResults, pageToken);
    }

    public List<EmailLabel> listLabels() {
        try {
            Gmail service = getGmailService();
            ListLabelsResponse response = service.users().labels().list(USER_ID).execute();
            if (response.getLabels() == null) {
                return Collections.emptyList();
            }
            return response.getLabels().stream()
                    .map(label -> new EmailLabel(label.getId(), label.getName()))
                    .toList();
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Failed to list labels: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to list labels: " + e.getMessage(), e);
        }
    }

    public EmailMessage getEmailById(String messageId) {
        try {
            logger.info("Fetching email by id: {}", messageId);
            Gmail service = getGmailService();
            Message fullMessage =
                    service.users().messages().get(USER_ID, messageId).setFormat("full").execute();
            return parseMessage(fullMessage);
        } catch (GeneralSecurityException e) {
            logger.error(
                    "Security error while fetching email {}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("Security error while fetching email: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("IO error while fetching email {}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("IO error while fetching email: " + e.getMessage(), e);
        }
    }

    private List<EmailMessage> fetchEmails(String query, int maxResults) {
        try {
            logger.info("Fetching up to {} emails with query '{}'", maxResults, query);
            Gmail service = getGmailService();
            ListMessagesResponse response =
                    service.users()
                            .messages()
                            .list(USER_ID)
                            .setQ(query)
                            .setMaxResults((long) maxResults)
                            .execute();

            return parseResponse(response, service);

        } catch (GeneralSecurityException e) {
            logger.error("Security error while fetching emails: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "Security error while fetching emails: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("IO error while fetching emails: {}", e.getMessage(), e);
            throw new RuntimeException("IO error while fetching emails: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to fetch emails: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch emails: " + e.getMessage(), e);
        }
    }

    private EmailPage fetchEmailsPage(String query, int maxResults, String pageToken) {
        try {
            logger.info(
                    "Fetching up to {} emails with query '{}' and pageToken {}",
                    maxResults,
                    query,
                    pageToken);
            Gmail service = getGmailService();
            ListMessagesResponse response =
                    service.users()
                            .messages()
                            .list(USER_ID)
                            .setQ(query)
                            .setMaxResults((long) maxResults)
                            .setPageToken(pageToken)
                            .execute();

            List<EmailMessage> messages = parseResponse(response, service);
            return new EmailPage(messages, response.getNextPageToken());

        } catch (GeneralSecurityException e) {
            logger.error("Security error while fetching emails: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "Security error while fetching emails: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("IO error while fetching emails: {}", e.getMessage(), e);
            throw new RuntimeException("IO error while fetching emails: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to fetch emails: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch emails: " + e.getMessage(), e);
        }
    }

    private EmailPage fetchEmailsPageByLabel(String labelId, int maxResults, String pageToken) {
        try {
            logger.info(
                    "Fetching up to {} emails for label {} with pageToken {}",
                    maxResults,
                    labelId,
                    pageToken);
            Gmail service = getGmailService();
            ListMessagesResponse response =
                    service.users()
                            .messages()
                            .list(USER_ID)
                            .setLabelIds(Collections.singletonList(labelId))
                            .setMaxResults((long) maxResults)
                            .setPageToken(pageToken)
                            .execute();

            List<EmailMessage> messages = parseResponse(response, service);
            return new EmailPage(messages, response.getNextPageToken());

        } catch (GeneralSecurityException e) {
            logger.error("Security error while fetching emails: {}", e.getMessage(), e);
            throw new RuntimeException(
                    "Security error while fetching emails: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("IO error while fetching emails: {}", e.getMessage(), e);
            throw new RuntimeException("IO error while fetching emails: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to fetch emails: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch emails: " + e.getMessage(), e);
        }
    }

    private List<EmailMessage> parseResponse(ListMessagesResponse response, Gmail service)
            throws IOException {
        List<EmailMessage> emailMessages = new ArrayList<>();
        if (response.getMessages() == null || response.getMessages().isEmpty()) {
            logger.info("No messages matched query");
            return emailMessages;
        }

        for (Message message : response.getMessages()) {
            try {
                Message fullMessage =
                        service.users()
                                .messages()
                                .get(USER_ID, message.getId())
                                .setFormat("full")
                                .execute();

                EmailMessage emailMessage = parseMessage(fullMessage);
                emailMessages.add(emailMessage);
                logger.debug(
                        "Parsed email {} with subject '{}'",
                        message.getId(),
                        emailMessage.subject());
            } catch (Exception e) {
                logger.warn("Failed to parse message {}: {}", message.getId(), e.getMessage());
            }
        }

        return emailMessages;
    }

    private EmailMessage parseMessage(Message message) {
        String subject = "";
        String from = "";
        String body = truncate(extractBody(message), BODY_PREVIEW_LENGTH);

        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (var header : message.getPayload().getHeaders()) {
                if ("Subject".equalsIgnoreCase(header.getName())) {
                    subject = header.getValue();
                } else if ("From".equalsIgnoreCase(header.getName())) {
                    from = header.getValue();
                }
            }
        }

        return new EmailMessage(message.getId(), from, subject, body);
    }

    private String extractBody(Message message) {
        if (message.getPayload() == null) {
            return decodeRawFallback(message);
        }

        String plainText = collectTextParts(message.getPayload(), "text/plain");
        if (!plainText.isBlank()) {
            return plainText;
        }

        String htmlText = collectTextParts(message.getPayload(), "text/html");
        if (!htmlText.isBlank()) {
            return htmlText;
        }

        return decodeRawFallback(message);
    }

    private String collectTextParts(MessagePart part, String mimeType) {
        if (part == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (mimeType.equalsIgnoreCase(part.getMimeType())
                && part.getBody() != null
                && part.getBody().getData() != null) {
            builder.append(decodeBase64(part.getBody().getData()));
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String subText = collectTextParts(subPart, mimeType);
                if (!subText.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append("\n");
                    }
                    builder.append(subText);
                }
            }
        }

        return builder.toString();
    }

    private String decodeRawFallback(Message message) {
        if (message.getRaw() == null) {
            return "";
        }
        return decodeBase64(message.getRaw());
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }

        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        }
    }

    String buildPrimaryQuery(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return PRIMARY_CATEGORY_QUERY;
        }
        return PRIMARY_CATEGORY_QUERY + " " + userQuery.trim();
    }

    int clampMaxResults(Integer requested) {
        int value = requested != null ? requested : DEFAULT_MAX_RESULTS;
        return Math.max(1, Math.min(value, MAX_RESULTS));
    }

    private String truncate(String text, int length) {
        if (text == null) {
            return "";
        }
        return text.length() <= length ? text : text.substring(0, length) + "...";
    }
}
