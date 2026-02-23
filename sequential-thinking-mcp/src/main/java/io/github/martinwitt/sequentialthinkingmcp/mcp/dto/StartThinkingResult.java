package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

/** Result of starting a new thinking session. */
public record StartThinkingResult(
        String sessionId,
        int initialThoughtCount,
        int totalThoughtsEstimate,
        SessionStatus status) {}
