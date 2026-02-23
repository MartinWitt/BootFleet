package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

/** Result of completing a thinking session. */
public record CompleteThinkingResult(
        String sessionId,
        String finalAnswer,
        int totalThoughtsUsed,
        int totalBranches,
        SessionStatus status,
        long durationSeconds) {}
