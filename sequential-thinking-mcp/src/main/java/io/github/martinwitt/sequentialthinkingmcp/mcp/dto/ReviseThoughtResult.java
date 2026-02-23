package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

import java.util.List;

/** Result of revising a previous thought. */
public record ReviseThoughtResult(
        String sessionId,
        int revisedThoughtNumber,
        int newThoughtNumber,
        boolean isRevision,
        List<Integer> affectedThoughts,
        int totalThoughts) {}
