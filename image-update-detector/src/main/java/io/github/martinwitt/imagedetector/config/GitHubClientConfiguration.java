package io.github.martinwitt.imagedetector.config;

import io.github.martinwitt.imagedetector.ImageDetectorProperties;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for GitHub API client.
 *
 * <p>Provides a singleton GitHub client bean configured with authentication token from properties.
 * The bean is only created if github.token property is set.
 */
@Configuration
public class GitHubClientConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(GitHubClientConfiguration.class);

    /**
     * Create a GitHub API client bean with OAuth authentication.
     *
     * @param properties Image detector properties containing GitHub token and credentials
     * @return Configured GitHub client instance
     * @throws Exception if authentication token is missing or invalid
     */
    @Bean
    public GitHub gitHubClient(ImageDetectorProperties properties) throws Exception {
        String authToken = properties.getToken();
        if (authToken == null || authToken.isEmpty()) {
            logger.warn("GitHub token not configured. GitOps features will be disabled.");
            throw new IllegalArgumentException("GitHub token is required for GitOps integration");
        }
        logger.info("Connecting to GitHub with OAuth authentication");
        return GitHub.connectUsingOAuth(authToken);
    }
}
