package io.github.martinwitt.mcputils;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class McpToolLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(McpToolLoggingAspect.class);
    private static final int MAX_LENGTH = 500;

    @Around("@annotation(org.springframework.ai.mcp.annotation.McpTool)")
    public Object logMcpToolCall(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        log.info("MCP tool '{}' called with: {}", method, truncate(Arrays.toString(pjp.getArgs())));
        Object result = pjp.proceed();
        log.info("MCP tool '{}' returned: {}", method, truncate(String.valueOf(result)));
        return result;
    }

    private static String truncate(String s) {
        return s.length() <= MAX_LENGTH ? s : s.substring(0, MAX_LENGTH) + "…";
    }
}
