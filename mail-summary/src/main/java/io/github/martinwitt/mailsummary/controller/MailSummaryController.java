package io.github.martinwitt.mailsummary.controller;

import io.github.martinwitt.mailsummary.model.EmailLabel;
import io.github.martinwitt.mailsummary.model.EmailMessage;
import io.github.martinwitt.mailsummary.model.EmailPage;
import io.github.martinwitt.mailsummary.model.EmailSummary;
import io.github.martinwitt.mailsummary.service.AiSummaryService;
import io.github.martinwitt.mailsummary.service.GmailService;
import io.github.martinwitt.mailsummary.service.OAuthTokenService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MailSummaryController {

    private static final Logger logger = LoggerFactory.getLogger(MailSummaryController.class);

    private final GmailService gmailService;
    private final AiSummaryService aiSummaryService;
    private final OAuthTokenService oAuthTokenService;

    public MailSummaryController(
            GmailService gmailService,
            AiSummaryService aiSummaryService,
            OAuthTokenService oAuthTokenService) {
        this.gmailService = gmailService;
        this.aiSummaryService = aiSummaryService;
        this.oAuthTokenService = oAuthTokenService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("authorized", oAuthTokenService.isAuthorized());
        return "index";
    }

    @GetMapping("/auth/login")
    public String login(Model model) {
        try {
            return "redirect:" + oAuthTokenService.buildAuthorizationUrl();
        } catch (Exception e) {
            logger.error("Failed to build authorization URL", e);
            model.addAttribute("error", "Unable to start Google login: " + e.getMessage());
            model.addAttribute("authorized", oAuthTokenService.isAuthorized());
            return "index";
        }
    }

    @GetMapping("/auth/status")
    @ResponseBody
    public Map<String, Boolean> authStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put("authorized", oAuthTokenService.isAuthorized());
        return status;
    }

    @GetMapping("/callback")
    public String oauthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            Model model) {
        if (error != null) {
            logger.error("OAuth error: {}", error);
            model.addAttribute("error", "Authorization failed: " + error);
            return "index";
        }

        if (code == null || code.isEmpty()) {
            logger.warn("No authorization code provided");
            model.addAttribute("error", "No authorization code received");
            return "index";
        }

        try {
            logger.info("Processing OAuth callback with code");
            oAuthTokenService.exchangeCodeForToken(code);
            logger.info("Token exchanged and stored successfully");

            model.addAttribute(
                    "success", "Authorization successful! You can now summarize your emails.");
            model.addAttribute("authorized", true);
            return "index";
        } catch (Exception e) {
            logger.error("Failed to exchange code for token", e);
            model.addAttribute("error", "Authorization failed: " + e.getMessage());
            model.addAttribute("authorized", oAuthTokenService.isAuthorized());
            return "index";
        }
    }

    @GetMapping("/api/emails/recent")
    @ResponseBody
    public ResponseEntity<?> recentEmails(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "pageToken", required = false) String pageToken,
            @RequestParam(value = "labelId", required = false) String labelId) {
        if (!oAuthTokenService.isAuthorized()) {
            return ResponseEntity.status(401).body("Not authorized");
        }
        try {
            EmailPage page =
                    gmailService.getEmailsByLabelPage(
                            labelId, limit != null ? limit : 10, pageToken);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            logger.error("Failed to fetch recent emails", e);
            return ResponseEntity.status(500).body("Error fetching emails: " + e.getMessage());
        }
    }

    @GetMapping("/api/emails/labels")
    @ResponseBody
    public ResponseEntity<?> listLabels() {
        if (!oAuthTokenService.isAuthorized()) {
            return ResponseEntity.status(401).body("Not authorized");
        }
        try {
            List<EmailLabel> labels = gmailService.listLabels();
            return ResponseEntity.ok(labels);
        } catch (Exception e) {
            logger.error("Failed to list labels", e);
            return ResponseEntity.status(500).body("Error listing labels: " + e.getMessage());
        }
    }

    @PostMapping("/api/emails/{id}/summary")
    @ResponseBody
    public ResponseEntity<?> summarizeEmail(@PathVariable("id") String messageId) {
        if (!oAuthTokenService.isAuthorized()) {
            return ResponseEntity.status(401).body("Not authorized");
        }
        try {
            EmailMessage email = gmailService.getEmailById(messageId);
            EmailSummary summary = aiSummaryService.analyzeEmail(email);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Failed to summarize email {}", messageId, e);
            return ResponseEntity.status(500).body("Error summarizing email: " + e.getMessage());
        }
    }
}
