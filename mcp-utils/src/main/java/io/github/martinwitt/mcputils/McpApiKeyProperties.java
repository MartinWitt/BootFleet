package io.github.martinwitt.mcputils;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MCP API key security.
 *
 * <p>Set {@code mcp.security.api-key} to enable API key authentication on the {@code /mcp} and
 * {@code /sse} endpoints.
 *
 * <p>Example configuration:
 *
 * <pre>
 * mcp:
 *   security:
 *     api-key: my-secret-key
 *     header-name: X-API-Key        # optional, default is X-API-Key
 *     protected-paths:              # optional, defaults to /mcp and /sse
 *       - /mcp
 *       - /sse
 * </pre>
 */
@ConfigurationProperties("mcp.security")
public class McpApiKeyProperties {

    /** The API key that clients must supply in the configured header. */
    private String apiKey;

    /** The HTTP header name that must carry the API key. Defaults to {@code X-API-Key}. */
    private String headerName = "X-API-Key";

    /**
     * URL path prefixes that are protected by API key authentication. Defaults to {@code /mcp} and
     * {@code /sse}.
     */
    private List<String> protectedPaths = List.of("/mcp", "/sse");

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }
}
