package io.github.martinwitt.mcputils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that enforces API key authentication on protected MCP/SSE endpoints.
 *
 * <p>Requests to any path configured in {@link McpApiKeyProperties#getProtectedPaths()} must
 * include the API key in the header defined by {@link McpApiKeyProperties#getHeaderName()}. If the
 * key is missing or incorrect the filter short-circuits with {@code 401 Unauthorized}.
 */
public class McpApiKeyFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(McpApiKeyFilter.class);

    private final McpApiKeyProperties properties;

    public McpApiKeyFilter(McpApiKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String pathToCheck = request.getServletPath();

        if (isProtectedPath(pathToCheck)) {
            String providedKey = request.getHeader(properties.getHeaderName());

            if (properties.getApiKey() == null || !properties.getApiKey().equals(providedKey)) {
                logger.warn(
                        "Rejected request to {} – invalid or missing API key in header '{}'",
                        pathToCheck,
                        properties.getHeaderName());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter()
                        .write(
                                """
                                {"error": "Unauthorized", "message": "Invalid or missing\
                                 API key"}\
                                """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(String requestUri) {
        return properties.getProtectedPaths().stream().anyMatch(requestUri::startsWith);
    }
}
