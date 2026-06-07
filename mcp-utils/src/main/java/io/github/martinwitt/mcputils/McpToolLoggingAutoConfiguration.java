package io.github.martinwitt.mcputils;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(value = Aspect.class, name = "org.springframework.ai.mcp.annotation.McpTool")
public class McpToolLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpToolLoggingAspect mcpToolLoggingAspect() {
        return new McpToolLoggingAspect();
    }
}
