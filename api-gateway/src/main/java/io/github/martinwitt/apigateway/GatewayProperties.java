package io.github.martinwitt.apigateway;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Central configuration for the BootFleet API Gateway.
 *
 * <pre>
 * gateway:
 *   route-refresh-interval-ms: 30000
 *   auth:
 *     jwt:
 *       protected-paths:
 *         - /mcp/**
 *       subject-header: X-Auth-Sub
 *       email-header: X-Auth-Email
 *       username-header: X-Auth-Username
 *       email-claim: email
 *       username-claim: preferred_username
 *     api-key:
 *       default-header-name: X-API-Key
 * </pre>
 */
@ConfigurationProperties("gateway")
public class GatewayProperties {

    private long routeRefreshIntervalMs = 30_000;
    private Auth auth = new Auth();

    public long getRouteRefreshIntervalMs() {
        return routeRefreshIntervalMs;
    }

    public void setRouteRefreshIntervalMs(long routeRefreshIntervalMs) {
        this.routeRefreshIntervalMs = routeRefreshIntervalMs;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public static class Auth {
        private Jwt jwt = new Jwt();
        private ApiKey apiKey = new ApiKey();

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }

        public ApiKey getApiKey() {
            return apiKey;
        }

        public void setApiKey(ApiKey apiKey) {
            this.apiKey = apiKey;
        }

        public static class Jwt {
            /**
             * Static path patterns that always require JWT, in addition to annotation-based routes.
             * Use this for static routes defined in {@code spring.cloud.gateway.routes}.
             */
            private List<String> protectedPaths = List.of();

            /** Header name forwarded upstream for the JWT subject claim. */
            private String subjectHeader = "X-Auth-Sub";

            /** Header name forwarded upstream for the email claim. */
            private String emailHeader = "X-Auth-Email";

            /** Header name forwarded upstream for the username claim. */
            private String usernameHeader = "X-Auth-Username";

            /** JWT claim that holds the user's e-mail address. */
            private String emailClaim = "email";

            /**
             * JWT claim that holds the user's login name (Keycloak: {@code preferred_username}).
             */
            private String usernameClaim = "preferred_username";

            public List<String> getProtectedPaths() {
                return protectedPaths;
            }

            public void setProtectedPaths(List<String> protectedPaths) {
                this.protectedPaths = protectedPaths;
            }

            public String getSubjectHeader() {
                return subjectHeader;
            }

            public void setSubjectHeader(String subjectHeader) {
                this.subjectHeader = subjectHeader;
            }

            public String getEmailHeader() {
                return emailHeader;
            }

            public void setEmailHeader(String emailHeader) {
                this.emailHeader = emailHeader;
            }

            public String getUsernameHeader() {
                return usernameHeader;
            }

            public void setUsernameHeader(String usernameHeader) {
                this.usernameHeader = usernameHeader;
            }

            public String getEmailClaim() {
                return emailClaim;
            }

            public void setEmailClaim(String emailClaim) {
                this.emailClaim = emailClaim;
            }

            public String getUsernameClaim() {
                return usernameClaim;
            }

            public void setUsernameClaim(String usernameClaim) {
                this.usernameClaim = usernameClaim;
            }
        }

        public static class ApiKey {
            /**
             * Default header name. Override per-service with annotation {@code
             * gateway.bootfleet.io/api-key-header}.
             */
            private String defaultHeaderName = "X-API-Key";

            public String getDefaultHeaderName() {
                return defaultHeaderName;
            }

            public void setDefaultHeaderName(String defaultHeaderName) {
                this.defaultHeaderName = defaultHeaderName;
            }
        }
    }
}
