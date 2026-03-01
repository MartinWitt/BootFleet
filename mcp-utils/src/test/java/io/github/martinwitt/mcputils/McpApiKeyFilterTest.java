package io.github.martinwitt.mcputils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Tests for {@link McpApiKeyFilter}.
 *
 * <p>Verifies that:
 *
 * <ul>
 *   <li>protected paths require a valid API key,
 *   <li>unprotected paths pass through without a key,
 *   <li>null API key configuration is handled safely (no NPE).
 * </ul>
 */
class McpApiKeyFilterTest {

    private McpApiKeyProperties properties;
    private McpApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        properties = new McpApiKeyProperties();
        properties.setApiKey("secret");
        properties.setHeaderName("X-API-Key");
        properties.setProtectedPaths(List.of("/mcp", "/sse"));
        filter = new McpApiKeyFilter(properties);
    }

    @Test
    void allowsRequestWithCorrectApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/tools");
        request.setServletPath("/mcp/tools");
        request.addHeader("X-API-Key", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejects401WhenApiKeyMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/tools");
        request.setServletPath("/mcp/tools");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejects401WhenApiKeyIsWrong() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sse");
        request.setServletPath("/sse");
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowsUnprotectedPathWithoutApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setServletPath("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nullApiKeyConfigurationDoesNotThrowNpe() throws Exception {
        properties.setApiKey(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp");
        request.setServletPath("/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // A null configured API key means no valid key is set – the request must be rejected
        // (fail-secure) rather than accidentally allowing through.
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}
