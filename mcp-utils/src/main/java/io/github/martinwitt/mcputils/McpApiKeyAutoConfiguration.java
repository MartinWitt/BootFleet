package io.github.martinwitt.mcputils;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Boot autoconfiguration for MCP API key security.
 *
 * <p>Activates automatically when:
 *
 * <ul>
 *   <li>the application is a servlet-based web application,
 *   <li>{@code spring-web} is on the classpath, and
 *   <li>the property {@code mcp.security.api-key} is set.
 * </ul>
 *
 * <p>Once active it registers a {@link McpApiKeyFilter} with the highest priority so that
 * unauthenticated requests are rejected before reaching any MCP or SSE endpoint.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnProperty(prefix = "mcp.security", name = "api-key")
@EnableConfigurationProperties(McpApiKeyProperties.class)
public class McpApiKeyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpApiKeyFilter mcpApiKeyFilter(McpApiKeyProperties properties) {
        return new McpApiKeyFilter(properties);
    }

    @Bean
    public FilterRegistrationBean<McpApiKeyFilter> mcpApiKeyFilterRegistration(
            McpApiKeyFilter filter) {
        FilterRegistrationBean<McpApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
