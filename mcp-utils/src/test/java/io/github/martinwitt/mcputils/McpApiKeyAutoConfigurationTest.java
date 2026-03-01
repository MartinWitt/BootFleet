package io.github.martinwitt.mcputils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * Tests for {@link McpApiKeyAutoConfiguration}.
 *
 * <p>Verifies that the autoconfiguration activates only when {@code mcp.security.api-key} is set
 * and that a custom {@link FilterRegistrationBean} takes precedence over the auto-registered one.
 */
class McpApiKeyAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(McpApiKeyAutoConfiguration.class));

    @Test
    void autoConfigDoesNotActivateWithoutApiKey() {
        contextRunner.run(
                ctx -> {
                    assertThat(ctx).doesNotHaveBean(McpApiKeyFilter.class);
                    assertThat(ctx).doesNotHaveBean("mcpApiKeyFilterRegistration");
                });
    }

    @Test
    void autoConfigActivatesWhenApiKeyIsSet() {
        contextRunner
                .withPropertyValues("mcp.security.api-key=test-key")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(McpApiKeyFilter.class);
                            assertThat(ctx).hasBean("mcpApiKeyFilterRegistration");
                        });
    }

    @Test
    void customFilterRegistrationBeanTakesPrecedence() {
        contextRunner
                .withPropertyValues("mcp.security.api-key=test-key")
                .withBean(
                        "mcpApiKeyFilterRegistration",
                        FilterRegistrationBean.class,
                        () ->
                                new FilterRegistrationBean<>(
                                        new McpApiKeyFilter(new McpApiKeyProperties())))
                .run(
                        ctx -> {
                            // Custom bean provided → auto-configured one must not be added
                            assertThat(ctx).getBeans(FilterRegistrationBean.class).hasSize(1);
                        });
    }
}
