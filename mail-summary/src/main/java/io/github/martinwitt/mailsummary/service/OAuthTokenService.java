package io.github.martinwitt.mailsummary.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;
import io.github.martinwitt.mailsummary.config.GmailConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OAuthTokenService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);

    private final GmailConfig gmailConfig;

    public OAuthTokenService(GmailConfig gmailConfig) {
        this.gmailConfig = gmailConfig;
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

    public String buildAuthorizationUrl() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return buildFlow(httpTransport)
                .newAuthorizationUrl()
                .setRedirectUri(gmailConfig.getOauth().redirectUri())
                .setAccessType("offline")
                .build();
    }

    public boolean isAuthorized() {
        try {
            final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = loadCredential(httpTransport);
            return credential != null && credential.getAccessToken() != null;
        } catch (Exception e) {
            logger.debug("Authorization check failed: {}", e.getMessage());
            return false;
        }
    }

    public Credential getOrRedirect(NetHttpTransport httpTransport) throws IOException {
        Credential credential = loadCredential(httpTransport);

        if (credential == null) {
            throw new IllegalStateException("Not authorized. Please sign in via /auth/login.");
        }

        if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
            if (!credential.refreshToken()) {
                throw new IllegalStateException(
                        "Authorization expired. Please sign in via /auth/login.");
            }
        }

        if (credential.getAccessToken() == null) {
            if (!credential.refreshToken()) {
                throw new IllegalStateException(
                        "Authorization missing. Please sign in via /auth/login.");
            }
        }

        return credential;
    }

    public void exchangeCodeForToken(String authorizationCode)
            throws GeneralSecurityException, IOException {
        logger.info("Exchanging authorization code for token");

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets clientSecrets = buildClientSecretsFromConfig();

        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(
                                new FileDataStoreFactory(
                                        new File(gmailConfig.getTokensDirectory())))
                        .setAccessType("offline")
                        .build();

        // Exchange the authorization code for a token
        TokenResponse tokenResponse =
                flow.newTokenRequest(authorizationCode)
                        .setRedirectUri(gmailConfig.getOauth().redirectUri())
                        .execute();

        // Store the credential in the data store
        flow.createAndStoreCredential(tokenResponse, "user");

        logger.info("Token successfully stored");
    }

    private Credential loadCredential(NetHttpTransport httpTransport) throws IOException {
        return buildFlow(httpTransport).loadCredential("user");
    }

    private GoogleAuthorizationCodeFlow buildFlow(NetHttpTransport httpTransport)
            throws IOException {
        validateGmailConfig();

        GoogleClientSecrets clientSecrets = buildClientSecretsFromConfig();

        return new GoogleAuthorizationCodeFlow.Builder(
                        httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(
                        new FileDataStoreFactory(new File(gmailConfig.getTokensDirectory())))
                .setAccessType("offline")
                .build();
    }

    private void validateGmailConfig() {
        GmailConfig.OAuth oauth = gmailConfig.getOauth();

        if (oauth == null || oauth.clientId() == null || oauth.clientSecret() == null) {
            throw new IllegalArgumentException(
                    "Gmail OAuth configuration is incomplete. Please set the required properties in"
                            + " application.yaml");
        }
    }

    private GoogleClientSecrets buildClientSecretsFromConfig() {
        GmailConfig.OAuth oauth = gmailConfig.getOauth();

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();

        details.setClientId(oauth.clientId());
        details.setClientSecret(oauth.clientSecret());
        details.setAuthUri(oauth.authUri());
        details.setTokenUri(oauth.tokenUri());
        details.setRedirectUris(List.of(oauth.redirectUri()));

        clientSecrets.setInstalled(details);

        return clientSecrets;
    }
}
