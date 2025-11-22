package io.github.martinwitt.mailsummary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gmail")
public class GmailConfig {

    private String projectId;

    @NestedConfigurationProperty private OAuth oauth;

    private String tokensDirectory = "tokens";

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public OAuth getOauth() {
        return oauth;
    }

    public void setOauth(OAuth oauth) {
        this.oauth = oauth;
    }

    public String getTokensDirectory() {
        return tokensDirectory;
    }

    public void setTokensDirectory(String tokensDirectory) {
        this.tokensDirectory = tokensDirectory;
    }

    public record OAuth(
            String clientId,
            String clientSecret,
            String authUri,
            String tokenUri,
            String redirectUri) {}
}
