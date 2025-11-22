package io.github.martinwitt.mailsummary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.martinwitt.mailsummary.config.GmailConfig;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GmailServiceTest {

    private GmailService gmailService;

    @BeforeEach
    void setUp() throws IOException {
        GmailConfig gmailConfig = new GmailConfig();
        gmailConfig.setTokensDirectory(Files.createTempDirectory("gmail-test").toString());
        gmailConfig.setOauth(
                new GmailConfig.OAuth(
                        "client-id", "client-secret", "auth-uri", "token-uri", "http://localhost"));

        gmailService = new GmailService(gmailConfig, mock(OAuthTokenService.class));
    }

    @Test
    void buildPrimaryQueryKeepsPrimaryWhenBlank() {
        assertThat(gmailService.buildPrimaryQuery(null)).isEqualTo("category:primary");
        assertThat(gmailService.buildPrimaryQuery("   ")).isEqualTo("category:primary");
    }

    @Test
    void buildPrimaryQueryAppendsUserQuery() {
        assertThat(gmailService.buildPrimaryQuery("from:alice"))
                .isEqualTo("category:primary from:alice");
    }

    @Test
    void clampMaxResultsEnforcesBounds() {
        assertThat(gmailService.clampMaxResults(null)).isEqualTo(10);
        assertThat(gmailService.clampMaxResults(0)).isEqualTo(1);
        assertThat(gmailService.clampMaxResults(200)).isEqualTo(50);
        assertThat(gmailService.clampMaxResults(25)).isEqualTo(25);
    }
}
