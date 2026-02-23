package io.github.martinwitt.sequentialthinkingmcp.mcp.dto;

import java.util.List;

/** Result of getting the complete thought chain. */
public record GetThoughtChainResult(
        String sessionId,
        int thoughtCount,
        int totalThoughts,
        List<String> branches,
        int thoughtHistoryLength,
        SessionStatus status) {}
