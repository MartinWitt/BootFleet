package io.github.martinwitt.mailsummary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.ai")
public class AiConfig {

    private String individualEmailPrompt;

    public String getIndividualEmailPrompt() {
        return individualEmailPrompt;
    }

    public void setIndividualEmailPrompt(String individualEmailPrompt) {
        this.individualEmailPrompt = individualEmailPrompt;
    }
}
